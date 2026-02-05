package claude_opus_4_5;
import battlecode.common.*;

public strictfp class Lumberjack {
    static RobotController rc;
    static MapLocation targetTree = null;
    
    public static void run(RobotController rc) throws GameActionException {
        Lumberjack.rc = rc;
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
        
        // Check for nearby enemies
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        RobotInfo[] allies = rc.senseNearbyRobots(GameConstants.LUMBERJACK_STRIKE_RADIUS, rc.getTeam());
        
        // If enemies in strike range and no allies, strike!
        if (enemies.length > 0) {
            RobotInfo closestEnemy = Utils.findClosestEnemy(rc, enemies);
            float enemyDist = myLoc.distanceTo(closestEnemy.location);
            
            if (enemyDist <= GameConstants.LUMBERJACK_STRIKE_RADIUS) {
                // Check no allies in strike radius
                if (allies.length == 0 && rc.canStrike()) {
                    rc.strike();
                    return;
                }
            }
            
            // Chase enemies
            if (!rc.hasMoved()) {
                Nav.moveToward(closestEnemy.location);
            }
            return;
        }
        
        // No enemies - chop neutral trees
        TreeInfo[] neutralTrees = rc.senseNearbyTrees(-1, Team.NEUTRAL);
        
        if (neutralTrees.length > 0) {
            // Find closest or one with bullets
            TreeInfo bestTree = null;
            float bestScore = Float.MAX_VALUE;
            
            for (TreeInfo tree : neutralTrees) {
                float dist = myLoc.distanceTo(tree.location);
                float score = dist;
                // Prioritize trees with bullets
                if (tree.containedBullets > 0) {
                    score -= 10;
                }
                // Prioritize low health trees
                score -= (tree.maxHealth - tree.health) / 10;
                
                if (score < bestScore) {
                    bestScore = score;
                    bestTree = tree;
                }
            }
            
            if (bestTree != null) {
                if (rc.canChop(bestTree.ID)) {
                    rc.chop(bestTree.ID);
                } else if (!rc.hasMoved()) {
                    Nav.moveToward(bestTree.location);
                }
                return;
            }
        }
        
        // No trees to chop - move toward enemy base
        MapLocation enemyArchon = Comms.getEnemyArchonLocation();
        if (enemyArchon != null) {
            Nav.moveToward(enemyArchon);
        } else {
            MapLocation[] enemyArchons = rc.getInitialArchonLocations(rc.getTeam().opponent());
            if (enemyArchons.length > 0) {
                Nav.moveToward(enemyArchons[0]);
            } else {
                Nav.tryMove(Nav.randomDirection());
            }
        }
    }
}
