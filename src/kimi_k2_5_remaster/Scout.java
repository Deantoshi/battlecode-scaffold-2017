package kimi_k2_5_remaster;
import battlecode.common.*;

/**
 * Scout - Reconnaissance unit.
 * - DANGER_RADIUS: flee from combat
 * - Shake neutral trees for bullets
 * - Report enemy locations
 * - Harass Gardeners if safe
 */
public strictfp class Scout {
    static RobotController rc;
    static final float DANGER_RADIUS = 7.0f;
    static final float HARASS_RANGE = 3.0f;
    
    static boolean foundEnemyBase = false;
    static Direction exploreDir;
    
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
        
        // Sense for enemies
        RobotInfo[] enemies = rc.senseNearbyRobots(DANGER_RADIUS, rc.getTeam().opponent());
        
        if (enemies.length > 0) {
            // Report all enemies
            for (RobotInfo enemy : enemies) {
                Comms.reportEnemy(enemy);
            }
            
            // Check if we should flee
            boolean shouldFlee = false;
            RobotInfo threat = null;
            
            for (RobotInfo enemy : enemies) {
                // Flee from combat units
                if (enemy.type == RobotType.SOLDIER || 
                    enemy.type == RobotType.LUMBERJACK || 
                    enemy.type == RobotType.TANK ||
                    enemy.type == RobotType.SCOUT) {
                    float dist = myLoc.distanceTo(enemy.location);
                    if (dist < DANGER_RADIUS) {
                        shouldFlee = true;
                        threat = enemy;
                        break;
                    }
                }
            }
            
            if (shouldFlee && threat != null) {
                // Flee from threat
                Nav.moveAwayFrom(threat.location);
            } else {
                // Look for gardeners to harass
                RobotInfo targetGardener = null;
                for (RobotInfo enemy : enemies) {
                    if (enemy.type == RobotType.GARDENER || enemy.type == RobotType.ARCHON) {
                        targetGardener = enemy;
                        break;
                    }
                }
                
                if (targetGardener != null && rc.canFireSingleShot()) {
                    // Shoot at gardener/archon
                    Direction toTarget = myLoc.directionTo(targetGardener.location);
                    rc.fireSingleShot(toTarget);
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
                
                if (enemyArchon == null) {
                    // Explore in current direction, occasionally change
                    if (!Nav.tryMove(exploreDir)) {
                        exploreDir = Nav.randomDirection();
                        Nav.tryMove(exploreDir);
                    }
                } else {
                    // Patrol around enemy base
                    Nav.moveToward(enemyArchon);
                }
            }
        }
        
        // Handle spending
        BulletSpending.spendPolicy(RobotType.SCOUT);
    }
}
