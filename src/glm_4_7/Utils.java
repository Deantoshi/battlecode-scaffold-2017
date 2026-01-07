package glm_4_7;
import battlecode.common.*;

public strictfp class Utils {
    public static RobotInfo findLowestHealthTarget(RobotInfo[] enemies) {
        if (enemies.length == 0) return null;
        RobotInfo lowest = enemies[0];
        for (RobotInfo enemy : enemies) {
            if (enemy.health < lowest.health) {
                lowest = enemy;
            }
        }
        return lowest;
    }

    public static TreeInfo findLowestHealthTree(TreeInfo[] trees) {
        if (trees.length == 0) return null;
        TreeInfo lowest = trees[0];
        for (TreeInfo tree : trees) {
            if (tree.health < lowest.health) {
                lowest = tree;
            }
        }
        return lowest;
    }

    public static boolean willCollideWithMe(RobotController rc, BulletInfo bullet) {
        MapLocation myLocation = rc.getLocation();
        Direction propagationDirection = bullet.dir;
        MapLocation bulletLocation = bullet.location;
        Direction directionToRobot = bulletLocation.directionTo(myLocation);
        float distToRobot = bulletLocation.distanceTo(myLocation);
        float theta = propagationDirection.radiansBetween(directionToRobot);
        if (Math.abs(theta) > Math.PI / 2) {
            return false;
        }
        float perpendicularDist = (float) Math.abs(distToRobot * Math.sin(theta));
        return (perpendicularDist <= rc.getType().bodyRadius);
    }

    public static boolean isLocationSafe(RobotController rc, MapLocation loc, BulletInfo[] bullets) {
        for (BulletInfo bullet : bullets) {
            Direction propagationDirection = bullet.dir;
            MapLocation bulletLocation = bullet.location;
            Direction directionToLoc = bulletLocation.directionTo(loc);
            float distToLoc = bulletLocation.distanceTo(loc);
            float theta = propagationDirection.radiansBetween(directionToLoc);
            if (Math.abs(theta) <= Math.PI / 2) {
                float perpendicularDist = (float) Math.abs(distToLoc * Math.sin(theta));
                if (perpendicularDist <= rc.getType().bodyRadius) {
                    return false;
                }
            }
        }
        return true;
    }

    public static int getShotType(RobotController rc, MapLocation target) {
        float distance = rc.getLocation().distanceTo(target);
        if (distance <= 3) return 3;
        if (distance <= 5) return 2;
        return 1;
    }
}
