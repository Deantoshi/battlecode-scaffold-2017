package grok_code_fast_1;
import battlecode.common.*;

public strictfp class Utils {

    public static RobotInfo findLowestHealthTarget(RobotInfo[] enemies) {
        RobotInfo best = null;
        float lowestHealth = Float.MAX_VALUE;
        for (RobotInfo enemy : enemies) {
            if (enemy.health < lowestHealth) {
                lowestHealth = enemy.health;
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
    public static MapLocation calculateCentroid(RobotInfo[] robots) {
        if (robots.length == 0) return null;
        float x = 0, y = 0;
        for (RobotInfo r : robots) {
            x += r.location.x;
            y += r.location.y;
        }
        return new MapLocation(x / robots.length, y / robots.length);
    }

    public static Direction[] getDirections() {
        return new Direction[]{Direction.NORTH, new Direction((float)(Math.PI/4)), Direction.EAST, new Direction((float)(3*Math.PI/4)), Direction.SOUTH, new Direction((float)(5*Math.PI/4)), Direction.WEST, new Direction((float)(7*Math.PI/4))};
    }
}
