package claude_opus_4_5;
import battlecode.common.*;

public strictfp class Tank {
    static RobotController rc;
    static MapLocation lastEnemyLocation = null;
    
    public static void run(RobotController rc) throws GameActionException {
        Tank.rc = rc;
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
        
        MapLocation myLoc = rc.getLocation();
        
        // Find enemies
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        
        if (enemies.length > 0) {
            // Prioritize Gardeners and low HP targets
            RobotInfo target = Utils.findPriorityTarget(enemies);
            lastEnemyLocation = target.location;
            
            // Attack!
            attackTarget(target, enemies.length);
            
            // Tank is slow - maintain distance but don't kite aggressively
            float dist = myLoc.distanceTo(target.location);
            if (dist < 2.5f && !rc.hasMoved()) {
                // Only back up if very close
                Nav.moveAway(target.location);
            } else if (dist > 6.0f && !rc.hasMoved()) {
                // Move closer if too far
                Nav.moveToward(target.location);
            }
        } else {
            // No enemies visible
            MapLocation enemyArchon = Comms.getEnemyArchonLocation();
            if (enemyArchon != null) {
                Nav.moveToward(enemyArchon);
            } else if (lastEnemyLocation != null) {
                Nav.moveToward(lastEnemyLocation);
                if (myLoc.distanceTo(lastEnemyLocation) < 3.0f) {
                    lastEnemyLocation = null;
                }
            } else {
                // Move toward initial enemy spawn
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
     * Attack a target - use triad shots against groups.
     */
    static void attackTarget(RobotInfo target, int enemyCount) throws GameActionException {
        MapLocation myLoc = rc.getLocation();
        Direction toEnemy = myLoc.directionTo(target.location);
        float dist = myLoc.distanceTo(target.location);
        
        // Check if we can fire without hitting allies
        if (!willHitAllies(toEnemy, dist)) {
            // Use triad against groups
            if (enemyCount >= 2 && rc.canFireTriadShot()) {
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
