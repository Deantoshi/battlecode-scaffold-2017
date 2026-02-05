package copy_bot;
import battlecode.common.*;

/**
 * Soldier - Main combat unit.
 * - Target priority: Gardener > Archon > Scout > Soldier > Lumberjack > Tank
 * - Friendly fire prevention (check allies)
 * - Kiting behavior
 * - Triad shots vs clusters
 */
public strictfp class Soldier {
    static RobotController rc;
    static final float FRIENDLY_FIRE_ANGLE = 15.0f;
    static final float KITE_DISTANCE = 4.0f;
    static final float SENSOR_RADIUS = 7.0f;
    
    public static void run(RobotController rc) throws GameActionException {
        Soldier.rc = rc;
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
        // Dodge bullets first
        Nav.tryDodgeBullets();
        
        // Sense enemies
        RobotInfo[] enemies = rc.senseNearbyRobots(SENSOR_RADIUS, rc.getTeam().opponent());
        
        if (enemies.length > 0) {
            // Report enemies to comms
            RobotInfo closest = Utils.findClosestEnemy(rc, enemies);
            if (closest != null) {
                Comms.reportEnemy(closest);
            }
            
            // Find best target based on priority
            RobotInfo target = Utils.findBestTarget(rc, enemies);
            
            if (target != null) {
                MapLocation myLoc = rc.getLocation();
                Direction toTarget = myLoc.directionTo(target.location);
                float distToTarget = myLoc.distanceTo(target.location);
                
                // Determine shot type based on enemy clustering
                int clusterSize = countNearbyEnemies(target.location, 3.0f);
                BodyInfo[] targets = new BodyInfo[1];
                targets[0] = target;
                
                // Check for friendly fire
                boolean clearShot = !Utils.wouldHitAllies(rc, toTarget, distToTarget, FRIENDLY_FIRE_ANGLE);
                
                // Fire if we have a clear shot
                if (clearShot) {
                    if (clusterSize >= 2 && rc.canFireTriadShot()) {
                        rc.fireTriadShot(toTarget);
                    } else if (rc.canFireSingleShot()) {
                        rc.fireSingleShot(toTarget);
                    }
                }
                
                // Kiting: maintain distance
                if (distToTarget < KITE_DISTANCE) {
                    Nav.moveAwayFrom(target.location);
                } else if (distToTarget > rc.getType().bulletSpeed) {
                    Nav.moveToward(target.location);
                }
            }
        } else {
            // No enemies visible - move toward enemy base or rally point
            MapLocation rally = Comms.getRallyPoint();
            MapLocation enemyArchon = Comms.getEnemyArchonLocation();
            
            if (rally != null) {
                Nav.moveToward(rally);
            } else if (enemyArchon != null) {
                Nav.moveToward(enemyArchon);
            } else {
                // Explore randomly
                Nav.tryMove(Nav.randomDirection());
            }
        }
        
        // Handle spending (though Soldiers don't spend directly)
        BulletSpending.spendPolicy(RobotType.SOLDIER);
    }
    
    static int countNearbyEnemies(MapLocation loc, float radius) {
        RobotInfo[] nearby = rc.senseNearbyRobots(loc, radius, rc.getTeam().opponent());
        return nearby.length;
    }
}
