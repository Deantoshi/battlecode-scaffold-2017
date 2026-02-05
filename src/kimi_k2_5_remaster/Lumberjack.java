package kimi_k2_5_remaster;
import battlecode.common.*;

/**
 * Lumberjack - Melee combat unit.
 * - Strike only if enemies present AND no allies nearby
 * - Chop neutral trees
 * - Move toward enemy base
 */
public strictfp class Lumberjack {
    static RobotController rc;
    static final float STRIKE_RADIUS = RobotType.LUMBERJACK.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS;
    static final float ALLY_CHECK_RADIUS = 3.0f;
    static final float ENEMY_BASE_PRIORITY = 1000f;
    
    public static void run(RobotController rc) throws GameActionException {
        Lumberjack.rc = rc;
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
        
        // Check for enemies within strike range
        RobotInfo[] enemies = rc.senseNearbyRobots(STRIKE_RADIUS, rc.getTeam().opponent());
        
        if (enemies.length > 0) {
            // Report enemies
            Comms.reportEnemy(enemies[0]);
            
            // Check for nearby allies to avoid friendly fire
            RobotInfo[] allies = rc.senseNearbyRobots(STRIKE_RADIUS, rc.getTeam());
            
            // Strike if enemies present and few/no allies nearby
            boolean canStrike = allies.length <= 1; // Allow strike if only 1 ally nearby
            
            if (canStrike && rc.canStrike()) {
                rc.strike();
            }
            
            // Move toward enemies
            RobotInfo closest = Utils.findClosestEnemy(rc, enemies);
            if (closest != null) {
                Direction toEnemy = myLoc.directionTo(closest.location);
                Nav.tryMove(toEnemy);
            }
        } else {
            // No enemies nearby - look for neutral trees to chop
            TreeInfo[] neutralTrees = rc.senseNearbyTrees(rc.getType().sensorRadius, Team.NEUTRAL);
            
            if (neutralTrees.length > 0) {
                // Find closest tree
                TreeInfo closestTree = null;
                float minDist = Float.MAX_VALUE;
                
                for (TreeInfo tree : neutralTrees) {
                    float dist = myLoc.distanceTo(tree.location);
                    if (dist < minDist) {
                        minDist = dist;
                        closestTree = tree;
                    }
                }
                
                if (closestTree != null) {
                    // Chop or move toward tree
                    if (rc.canChop(closestTree.getID())) {
                        rc.chop(closestTree.getID());
                    } else {
                        Nav.moveToward(closestTree.location);
                    }
                }
            } else {
                // Move toward enemy base
                MapLocation enemyArchon = Comms.getEnemyArchonLocation();
                MapLocation rally = Comms.getRallyPoint();
                
                if (enemyArchon != null) {
                    Nav.moveToward(enemyArchon);
                } else if (rally != null) {
                    Nav.moveToward(rally);
                } else {
                    Nav.tryMove(Nav.randomDirection());
                }
            }
        }
        
        // Handle spending
        BulletSpending.spendPolicy(RobotType.LUMBERJACK);
    }
}
