package claude_opus_4_5_champion_2;
import battlecode.common.*;

/**
 * ARCHETYPE: Early Tank Rush
 * Tank behavior: Aggressive push - body-ram through trees, devastating 5-damage shots.
 * Tanks are our primary combat unit. Be aggressive!
 */
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
            
            // Attack FIRST (before moving) - tanks do 5 damage!
            attackTarget(target, enemies.length);
            
            // AGGRESSIVE TANK PUSH: Always move toward enemies
            // Tanks have 200 HP - we can take hits while dealing devastating damage
            float dist = myLoc.distanceTo(target.location);
            if (!rc.hasMoved()) {
                if (dist > 3.5f) {
                    // Move closer to get in range - tanks body-ram through trees
                    Nav.moveToward(target.location);
                } else if (dist < 2.0f) {
                    // Only back up if we're too close to fire effectively
                    Nav.moveAway(target.location);
                }
                // Otherwise stay at optimal range (2.0 - 3.5)
            }
        } else {
            // No enemies visible - PUSH AGGRESSIVELY toward enemy base
            MapLocation enemyArchon = Comms.getEnemyArchonLocation();
            if (enemyArchon != null) {
                Nav.moveToward(enemyArchon);
            } else if (lastEnemyLocation != null) {
                Nav.moveToward(lastEnemyLocation);
                if (myLoc.distanceTo(lastEnemyLocation) < 3.0f) {
                    lastEnemyLocation = null;
                }
            } else {
                // Push toward initial enemy spawn - tanks lead the assault!
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
     * Attack a target - be aggressive with shots!
     * Tank shots do 5 damage each - use triad more liberally for area damage.
     */
    static void attackTarget(RobotInfo target, int enemyCount) throws GameActionException {
        MapLocation myLoc = rc.getLocation();
        Direction toEnemy = myLoc.directionTo(target.location);
        float dist = myLoc.distanceTo(target.location);
        
        // Check if we can fire without hitting allies
        if (!willHitAllies(toEnemy, dist)) {
            // Use pentad against clustered enemies or high-value targets
            if (enemyCount >= 3 && rc.canFirePentadShot() && dist < 5.0f) {
                rc.firePentadShot(toEnemy);
            }
            // Use triad more aggressively - 2+ enemies or medium range
            else if (enemyCount >= 2 && rc.canFireTriadShot()) {
                rc.fireTriadShot(toEnemy);
            }
            // Triad against single high-value targets at close range
            else if (rc.canFireTriadShot() && dist < 4.0f && 
                     (target.type == RobotType.GARDENER || target.type == RobotType.ARCHON)) {
                rc.fireTriadShot(toEnemy);
            }
            // Single shot otherwise
            else if (rc.canFireSingleShot()) {
                rc.fireSingleShot(toEnemy);
            }
        }
    }
    
    /**
     * Check if firing in a direction will hit allies.
     * Be a bit more permissive - tanks are frontline units.
     */
    static boolean willHitAllies(Direction dir, float range) throws GameActionException {
        RobotInfo[] allies = rc.senseNearbyRobots(range, rc.getTeam());
        MapLocation myLoc = rc.getLocation();
        
        for (RobotInfo ally : allies) {
            Direction toAlly = myLoc.directionTo(ally.location);
            float angleDiff = Math.abs(dir.radiansBetween(toAlly));
            float distToAlly = myLoc.distanceTo(ally.location);
            
            // If ally is in firing cone and closer than target
            // Use tighter cone for tanks (they're frontline)
            if (angleDiff < Math.PI / 8 && distToAlly < range) {
                return true;
            }
        }
        return false;
    }
}
