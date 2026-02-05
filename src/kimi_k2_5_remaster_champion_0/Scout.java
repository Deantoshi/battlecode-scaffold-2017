package kimi_k2_5_remaster_champion_0;
import battlecode.common.*;

/**
 * Scout - Reconnaissance and harassment unit.
 * - Mass produced (up to 6) to swarm enemy gardeners
 * - Kite at max range (7 distance) when attacking gardeners
 * - Ignore soldiers/lumberjacks unless blocking path
 * - Swarm behavior: 3+ scouts focus fire on same gardener
 */
public strictfp class Scout {
    static RobotController rc;
    static final float DANGER_RADIUS = 7.0f;
    static final float KITE_DISTANCE = 7.0f; // Max range for kiting
    static final float SCOUT_SENSOR_RADIUS = 14.0f; // Scouts have extended vision
    static final float SWARM_RADIUS = 10.0f; // Distance to check for allied scouts
    static final int SWARM_THRESHOLD = 3; // Number of scouts for swarm behavior
    
    static boolean foundEnemyBase = false;
    static Direction exploreDir;
    static MapLocation currentTargetGardener = null;
    
    public static void run(RobotController rc) throws GameActionException {
        Scout.rc = rc;
        Nav.init(rc);
        Comms.init(rc);
        BulletSpending.init(rc);
        
        exploreDir = Nav.randomDirection();
        
        while (true) {
            try {
                doTurn();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                Clock.yield();
            }
        }
    }
    
    static void doTurn() throws GameActionException {
        MapLocation myLoc = rc.getLocation();
        
        // Shake neutral trees for bullets
        TreeInfo[] neutralTrees = rc.senseNearbyTrees(rc.getType().sensorRadius, Team.NEUTRAL);
        for (TreeInfo tree : neutralTrees) {
            if (tree.containedBullets > 0 && rc.canShake(tree.getID())) {
                rc.shake(tree.getID());
                break;
            }
        }
        
        // Sense for enemies - use extended sensor range
        RobotInfo[] enemies = rc.senseNearbyRobots(SCOUT_SENSOR_RADIUS, rc.getTeam().opponent());
        
        // Look for enemy gardeners first
        RobotInfo targetGardener = null;
        RobotInfo targetArchon = null;
        RobotInfo blockingEnemy = null;
        
        for (RobotInfo enemy : enemies) {
            if (enemy.type == RobotType.GARDENER) {
                if (targetGardener == null) {
                    targetGardener = enemy;
                }
            } else if (enemy.type == RobotType.ARCHON) {
                if (targetArchon == null) {
                    targetArchon = enemy;
                }
            } else if (enemy.type == RobotType.SOLDIER || enemy.type == RobotType.LUMBERJACK) {
                // Check if they're blocking our path to a gardener
                if (targetGardener != null) {
                    float distToEnemy = myLoc.distanceTo(enemy.location);
                    float distToGardener = myLoc.distanceTo(targetGardener.location);
                    if (distToEnemy < distToGardener && isBlockingPath(myLoc, targetGardener.location, enemy.location)) {
                        blockingEnemy = enemy;
                    }
                }
            }
        }
        
        // Report enemy gardener if found
        if (targetGardener != null) {
            Comms.reportEnemyGardener(targetGardener.location);
            currentTargetGardener = targetGardener.location;
        }
        
        // Determine our target - prioritize gardeners
        RobotInfo primaryTarget = targetGardener != null ? targetGardener : targetArchon;
        
        if (primaryTarget != null) {
            // Report all enemies
            for (RobotInfo enemy : enemies) {
                Comms.reportEnemy(enemy);
            }
            
            float distToTarget = myLoc.distanceTo(primaryTarget.location);
            Direction toTarget = myLoc.directionTo(primaryTarget.location);
            
            // Check for nearby allied scouts for swarm behavior
            int nearbyScouts = countNearbyScouts();
            boolean isSwarming = nearbyScouts >= SWARM_THRESHOLD;
            
            // Kiting behavior: maintain KITE_DISTANCE (7.0)
            if (distToTarget < KITE_DISTANCE - 0.5f) {
                // Too close - back up while facing target
                Nav.moveAwayFrom(primaryTarget.location);
            } else if (distToTarget > KITE_DISTANCE + 0.5f) {
                // Too far - move closer
                Nav.moveToward(primaryTarget.location);
            } else {
                // At optimal range - strafe to avoid being hit
                Direction strafeDir = toTarget.rotateRightDegrees(90);
                if (!Nav.tryMove(strafeDir)) {
                    strafeDir = toTarget.rotateLeftDegrees(90);
                    Nav.tryMove(strafeDir);
                }
            }
            
            // Fire at target continuously
            if (rc.canFireSingleShot()) {
                // Check for friendly fire
                boolean clearShot = !Utils.wouldHitAllies(rc, toTarget, distToTarget, 10.0f);
                if (clearShot) {
                    rc.fireSingleShot(toTarget);
                }
            }
            
        } else if (blockingEnemy != null) {
            // Only engage blocking enemies if they're in the way
            float distToBlocker = myLoc.distanceTo(blockingEnemy.location);
            if (distToBlocker < KITE_DISTANCE) {
                Nav.moveAwayFrom(blockingEnemy.location);
            }
            if (rc.canFireSingleShot()) {
                Direction toBlocker = myLoc.directionTo(blockingEnemy.location);
                if (!Utils.wouldHitAllies(rc, toBlocker, distToBlocker, 10.0f)) {
                    rc.fireSingleShot(toBlocker);
                }
            }
        } else {
            // No enemies - explore
            
            // Try to shake trees that contain bullets
            for (TreeInfo tree : neutralTrees) {
                if (tree.containedBullets > 0) {
                    if (rc.canShake(tree.getID())) {
                        rc.shake(tree.getID());
                    } else {
                        Nav.moveToward(tree.location);
                    }
                    break;
                }
            }
            
            // Continue exploring if we haven't moved toward a tree
            if (!rc.hasMoved()) {
                // Try to find enemy base
                MapLocation enemyArchon = Comms.getEnemyArchonLocation();
                MapLocation enemyGardener = Comms.getEnemyGardenerLocation();
                
                if (enemyGardener != null) {
                    // Move toward last known gardener location
                    Nav.moveToward(enemyGardener);
                } else if (enemyArchon != null) {
                    // Move toward enemy archon
                    Nav.moveToward(enemyArchon);
                } else {
                    // Explore in current direction
                    if (!Nav.tryMove(exploreDir)) {
                        exploreDir = Nav.randomDirection();
                        Nav.tryMove(exploreDir);
                    }
                }
            }
        }
        
        // Handle spending
        BulletSpending.spendPolicy(RobotType.SCOUT);
    }
    
    /**
     * Check if an enemy is blocking the path to the target
     */
    static boolean isBlockingPath(MapLocation from, MapLocation to, MapLocation blocker) {
        Direction pathDir = from.directionTo(to);
        Direction toBlocker = from.directionTo(blocker);
        float angle = Math.abs(pathDir.degreesBetween(toBlocker));
        return angle < 30.0f; // Within 30 degrees of path
    }
    
    /**
     * Count nearby allied scouts for swarm behavior
     */
    static int countNearbyScouts() throws GameActionException {
        RobotInfo[] allies = rc.senseNearbyRobots(SWARM_RADIUS, rc.getTeam());
        int scoutCount = 0;
        for (RobotInfo ally : allies) {
            if (ally.type == RobotType.SCOUT) {
                scoutCount++;
            }
        }
        return scoutCount;
    }
}
