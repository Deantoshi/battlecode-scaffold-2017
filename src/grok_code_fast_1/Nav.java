package grok_code_fast_1;
import battlecode.common.*;

public strictfp class Nav {
    static RobotController rc;

    public static void init(RobotController rc) {
        Nav.rc = rc;
    }

    public static boolean tryMove(Direction dir) throws GameActionException {
        if (rc.canMove(dir)) {
            rc.move(dir);
            return true;
        }
        // Sense trees in the direction to avoid clusters
        TreeInfo[] trees = rc.senseNearbyTrees(rc.getType().bodyRadius + 1.0f);
        for (TreeInfo tree : trees) {
            Direction toTree = rc.getLocation().directionTo(tree.location);
            if (Math.abs(dir.degreesBetween(toTree)) < 45) {
                // Avoid directions with trees nearby
                continue;
            }
        }
        // Try wider rotations for better tree avoidance
        for (int i = 1; i <= 4; i++) {
            Direction left = dir.rotateLeftDegrees(45 * i);
            boolean leftClear = true;
            for (TreeInfo tree : trees) {
                Direction toTree = rc.getLocation().directionTo(tree.location);
                if (Math.abs(left.degreesBetween(toTree)) < 45) {
                    leftClear = false;
                    break;
                }
            }
            if (leftClear && rc.canMove(left)) {
                rc.move(left);
                return true;
            }
            Direction right = dir.rotateRightDegrees(45 * i);
            boolean rightClear = true;
            for (TreeInfo tree : trees) {
                Direction toTree = rc.getLocation().directionTo(tree.location);
                if (Math.abs(right.degreesBetween(toTree)) < 45) {
                    rightClear = false;
                    break;
                }
            }
            if (rightClear && rc.canMove(right)) {
                rc.move(right);
                return true;
            }
        }
        return false;
    }

    public static boolean moveToward(MapLocation target) throws GameActionException {
        if (target == null) return false;
        Direction dir = rc.getLocation().directionTo(target);
        if (tryMove(dir)) return true;
        // Fuzzy movement: try adjacent directions
        for (int i = 1; i <= 3; i++) {
            if (tryMove(dir.rotateLeftDegrees(30 * i))) return true;
            if (tryMove(dir.rotateRightDegrees(30 * i))) return true;
        }
        return false;
    }

    public static Direction randomDirection() {
        return new Direction((float)(Math.random() * 2 * Math.PI));
    }
}
