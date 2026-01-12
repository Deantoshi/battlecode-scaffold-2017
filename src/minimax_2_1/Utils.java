package minimax_2_1;
import battlecode.common.*;

public strictfp class Utils {

    public static RobotInfo findLowestHealthTarget(RobotInfo[] enemies) {
        RobotInfo best = null;
        float lowestHealth = Float.MAX_VALUE;
        for (RobotInfo enemy : enemies) {
            float score = enemy.health;
            if (enemy.type == RobotType.ARCHON) score -= 100;
            if (enemy.type == RobotType.GARDENER) score -= 50;
            if (enemy.type == RobotType.SCOUT) score += 20;
            if (score < lowestHealth) {
                lowestHealth = score;
                best = enemy;
            }
        }
        return best;
    }

    public static TreeInfo findLowestHealthTree(TreeInfo[] trees) {
        TreeInfo best = null;
        float lowestHealth = Float.MAX_VALUE;
        for (TreeInfo tree : trees) {
            if (tree.health < lowestHealth) {
                lowestHealth = tree.health;
                best = tree;
            }
        }
        return best;
    }

    public static RobotInfo findClosestEnemy(RobotController rc, RobotInfo[] enemies) {
        RobotInfo closest = null;
        float closestDist = Float.MAX_VALUE;
        MapLocation myLoc = rc.getLocation();
        for (RobotInfo enemy : enemies) {
            float dist = myLoc.distanceTo(enemy.location);
            if (dist < closestDist) {
                closestDist = dist;
                closest = enemy;
            }
        }
        return closest;
    }
}
