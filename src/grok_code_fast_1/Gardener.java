package grok_code_fast_1;
import battlecode.common.*;

public class Gardener {
    static RobotController rc;

    public static void init(RobotController rc) {
        Gardener.rc = rc;
    }

    public static void waterTree() throws GameActionException {
        TreeInfo[] nearbyTrees = rc.senseNearbyTrees(2, rc.getTeam());
        // Prioritize watering producing trees (health >80, assumed grown) to extend production period
        TreeInfo bestTree = null;
        float lowestHealth = Float.MAX_VALUE;
        for (TreeInfo tree : nearbyTrees) {
            if (rc.canWater(tree.ID) && tree.health > 80 && tree.health < lowestHealth) {
                lowestHealth = tree.health;
                bestTree = tree;
            }
        }
        if (bestTree != null) {
            rc.water(bestTree.ID);
        } else {
            // If no producing trees, water any
            lowestHealth = Float.MAX_VALUE;
            for (TreeInfo tree : nearbyTrees) {
                if (rc.canWater(tree.ID) && tree.health < lowestHealth) {
                    lowestHealth = tree.health;
                    bestTree = tree;
                }
            }
            if (bestTree != null) {
                rc.water(bestTree.ID);
            }
        }
    }

    static Direction randomDirection() {
        return new Direction((float)Math.random() * 2 * (float)Math.PI);
    }
}