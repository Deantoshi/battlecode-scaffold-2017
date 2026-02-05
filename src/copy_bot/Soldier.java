package copy_bot;
import battlecode.common.*;

public strictfp class Soldier {
    static RobotController rc;
    static MapLocation lastEnemyLocation = null;
    
    public static void run(RobotController rc) throws GameActionException {
        Soldier.rc = rc;
        Nav.init(rc);
        Comms.init(rc);
        
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
        // Report alive
        Comms.reportAlive();
        
        // Dodge bullets first
        Nav.dodgeBullets();
        
        // Find enemies
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        
        if (enemies.length > 0) {
            // Find priority target
            RobotInfo target = Utils.findPriorityTarget(enemies);
            lastEnemyLocation = target.location;
            
            // Attack!
            attackTarget(target);
            
            // Kite: maintain optimal distance
            float dist = rc.getLocation().distanceTo(target.location);
            if (dist < 3.0f) {
                // Too close, back up
                Nav.moveAway(target.location);
            } else if (dist > 5.0f && !rc.hasMoved()) {
                // Too far, move closer
                Nav.moveToward(target.location);
            }
        } else {
            // No enemies visible
            // Check for broadcast enemy Archon location
            MapLocation enemyArchon = Comms.getEnemyArchonLocation();
            if (enemyArchon != null) {
                Nav.moveToward(enemyArchon);
            } else if (lastEnemyLocation != null) {
                // Move toward last known enemy position
                Nav.moveToward(lastEnemyLocation);
                if (rc.getLocation().distanceTo(lastEnemyLocation) < 3.0f) {
                    lastEnemyLocation = null; // Clear if we reached it
                }
            } else {
                // Explore toward enemy spawn
                MapLocation[] enemyArchons = rc.getInitialArchonLocations(rc.getTeam().opponent());
                if (enemyArchons.length > 0) {
                    Nav.moveToward(enemyArchons[0]);
                } else {
                    Nav.tryMove(Nav.randomDirection());
                }
            }
        }
    }
    
    /**
     * Attack a target robot.
     */
    static void attackTarget(RobotInfo target) throws GameActionException {
        MapLocation myLoc = rc.getLocation();
        Direction toEnemy = myLoc.directionTo(target.location);
        float dist = myLoc.distanceTo(target.location);
        
        // Check if we can fire without hitting allies
        if (!willHitAllies(toEnemy, dist)) {
            if (rc.canFirePentadShot() && dist < 4.0f) {
                rc.firePentadShot(toEnemy);
            } else if (rc.canFireTriadShot() && dist < 5.0f) {
                rc.fireTriadShot(toEnemy);
            } else if (rc.canFireSingleShot()) {
                rc.fireSingleShot(toEnemy);
            }
        }
    }
    
    /**
     * Check if firing in a direction will hit allies.
     */
    static boolean willHitAllies(Direction dir, float range) throws GameActionException {
        RobotInfo[] allies = rc.senseNearbyRobots(range, rc.getTeam());
        MapLocation myLoc = rc.getLocation();
        
        for (RobotInfo ally : allies) {
            Direction toAlly = myLoc.directionTo(ally.location);
            float angleDiff = Math.abs(dir.radiansBetween(toAlly));
            float distToAlly = myLoc.distanceTo(ally.location);
            
            // If ally is in firing cone and closer than target
            if (angleDiff < Math.PI / 6 && distToAlly < range) {
                return true;
            }
        }
        return false;
    }
}
