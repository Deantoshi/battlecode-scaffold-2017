package copy_bot;
import battlecode.common.*;

public class Gardener {
    static RobotController rc;

    public static void init(RobotController rc) {
        Gardener.rc = rc;
    }

    public static void waterTree() throws GameActionException {
        TreeInfo[] nearbyTrees = rc.senseNearbyTrees(2, rc.getTeam());
        // For optimization, water the tree with lowest health if possible.
        TreeInfo bestTree = null;
        float lowestHealth = Float.MAX_VALUE;
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

    static Direction randomDirection() {
        return new Direction((float)Math.random() * 2 * (float)Math.PI);
    }
}
