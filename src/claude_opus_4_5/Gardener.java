package claude_opus_4_5;
import battlecode.common.*;

public strictfp class Gardener {
    static RobotController rc;
    static boolean settled = false;
    static int settleAttempts = 0;
    
    public static void run(RobotController rc) throws GameActionException {
        Gardener.rc = rc;
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
        // Report alive
        Comms.reportAlive();
        
        // Water the lowest health tree we own
        waterTrees();
        
        // If not settled, try to find open space
        if (!settled) {
            findOpenSpace();
        }
        
        // Spend bullets (build units, plant trees)
        BulletSpending.spendPolicy();
        
        // If we still haven't moved and not settled, move randomly
        if (!settled && !rc.hasMoved()) {
            Nav.tryMove(Nav.randomDirection());
            settleAttempts++;
            
            // Give up finding perfect spot after many attempts
            if (settleAttempts > 20) {
                settled = true;
            }
        }
    }
    
    /**
     * Water the lowest health tree we can water.
     */
    static void waterTrees() throws GameActionException {
        TreeInfo[] trees = rc.senseNearbyTrees(-1, rc.getTeam());
        if (trees.length == 0) {
            return;
        }
        
        TreeInfo lowestTree = null;
        float lowestHealth = Float.MAX_VALUE;
        
        for (TreeInfo tree : trees) {
            if (rc.canWater(tree.ID) && tree.health < lowestHealth) {
                lowestTree = tree;
                lowestHealth = tree.health;
            }
        }
        
        if (lowestTree != null) {
            rc.water(lowestTree.ID);
        }
    }
    
    /**
     * Try to find an open space to settle and plant trees.
     */
    static void findOpenSpace() throws GameActionException {
        // Check if we have enough space around us
        TreeInfo[] nearbyTrees = rc.senseNearbyTrees(3.0f);
        RobotInfo[] nearbyRobots = rc.senseNearbyRobots(3.0f, rc.getTeam());
        
        // If relatively clear, settle here
        if (nearbyTrees.length < 3 && nearbyRobots.length < 2) {
            // Check if we can plant in at least 2 directions
            int plantableDirections = 0;
            for (int i = 0; i < 6; i++) {
                Direction dir = new Direction(i * (float)Math.PI / 3);
                if (rc.canPlantTree(dir)) {
                    plantableDirections++;
                }
            }
            if (plantableDirections >= 2) {
                settled = true;
                return;
            }
        }
        
        // Move away from other gardeners and trees
        if (nearbyRobots.length > 0) {
            for (RobotInfo robot : nearbyRobots) {
                if (robot.type == RobotType.GARDENER) {
                    Nav.moveAway(robot.location);
                    return;
                }
            }
        }
        
        // Move to a more open area
        if (nearbyTrees.length > 0) {
            Nav.moveAway(nearbyTrees[0].location);
        } else {
            Nav.tryMove(Nav.randomDirection());
        }
    }
}
