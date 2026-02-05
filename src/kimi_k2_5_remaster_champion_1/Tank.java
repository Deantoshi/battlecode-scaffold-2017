package kimi_k2_5_remaster_champion_1;
import battlecode.common.*;

/**
 * Tank - Heavy combat unit.
 * - Similar to Soldier but tougher
 * - Fire triad shots
 * - Lead pushes through trees
 */
public strictfp class Tank {
    static RobotController rc;
    static final float FRIENDLY_FIRE_ANGLE = 20.0f;
    static final float SENSOR_RADIUS = 8.0f;
    static final float MIN_ATTACK_DIST = 3.0f;
    
    public static void run(RobotController rc) throws GameActionException {
        Tank.rc = rc;
        Nav.init(rc);
        Comms.init(rc);
        BulletSpending.init(rc);
        
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
        
        // Tanks are slow, prioritize targets but don't dodge as much
        
        // Sense enemies
        RobotInfo[] enemies = rc.senseNearbyRobots(SENSOR_RADIUS, rc.getTeam().opponent());
        
        if (enemies.length > 0) {
            // Report enemies
            RobotInfo closest = Utils.findClosestEnemy(rc, enemies);
            if (closest != null) {
                Comms.reportEnemy(closest);
            }
            
            // Find best target
            RobotInfo target = Utils.findBestTarget(rc, enemies);
            
            if (target != null) {
                Direction toTarget = myLoc.directionTo(target.location);
                float distToTarget = myLoc.distanceTo(target.location);
                
                // Check for friendly fire with wider angle for triad shots
                boolean clearShot = !Utils.wouldHitAllies(rc, toTarget, distToTarget, FRIENDLY_FIRE_ANGLE);
                
                if (clearShot) {
                    // Tanks prefer triad shots due to higher damage output
                    if (enemies.length >= 2 && rc.canFireTriadShot()) {
                        rc.fireTriadShot(toTarget);
                    } else if (rc.canFireSingleShot()) {
                        rc.fireSingleShot(toTarget);
                    }
                }
                
                // Move toward target if out of effective range
                if (distToTarget > rc.getType().bulletSpeed * 0.8f) {
                    Nav.moveToward(target.location);
                } else if (distToTarget < MIN_ATTACK_DIST) {
                    // Too close, back up
                    Nav.moveAwayFrom(target.location);
                }
            }
        } else {
            // No enemies - lead push toward enemy base
            MapLocation enemyArchon = Comms.getEnemyArchonLocation();
            MapLocation rally = Comms.getRallyPoint();
            
            // Clear trees in the way
            TreeInfo[] trees = rc.senseNearbyTrees(3.0f, Team.NEUTRAL);
            if (trees.length > 0 && rc.canFireSingleShot()) {
                // Shoot through trees to clear path
                Direction toTree = myLoc.directionTo(trees[0].location);
                if (!Utils.wouldHitAllies(rc, toTree, myLoc.distanceTo(trees[0].location), FRIENDLY_FIRE_ANGLE)) {
                    rc.fireSingleShot(toTree);
                }
            }
            
            // Move toward objective
            if (rally != null) {
                Nav.moveToward(rally);
            } else if (enemyArchon != null) {
                Nav.moveToward(enemyArchon);
            } else {
                Nav.tryMove(Nav.randomDirection());
            }
        }
        
        // Handle spending
        BulletSpending.spendPolicy(RobotType.TANK);
    }
}
