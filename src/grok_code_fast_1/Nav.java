package grok_code_fast_1;
import battlecode.common.*;

public strictfp class Nav {
    static RobotController rc;

    public static void init(RobotController rc) {
        Nav.rc = rc;
    }

    public static boolean tryMove(Direction dir) throws GameActionException {
        // For tanks, allow movement through trees since they can break them
        if (rc.getType() == RobotType.TANK) {
            if (rc.canMove(dir)) {
                rc.move(dir);
                return true;
            } else {
                // Try to move anyway, as tanks can damage trees on collision
                try {
                    rc.move(dir);
                    return true;
                } catch (GameActionException e) {
                    // Can't move in this direction
                }
            }
        } else {
            if (rc.canMove(dir)) {
                rc.move(dir);
                return true;
            }
        }

        // Sense trees in the direction to avoid clusters (non-tanks)
        if (rc.getType() != RobotType.TANK) {
            TreeInfo[] trees = rc.senseNearbyTrees(rc.getType().bodyRadius + rc.getType().strideRadius + 1.0f);
            for (TreeInfo tree : trees) {
                Direction toTree = rc.getLocation().directionTo(tree.location);
                if (Math.abs(dir.degreesBetween(toTree)) < 60) {
                    // Avoid directions with trees nearby
                    return false; // Don't try alternatives if trees in way, just return false
                }
            }
        }

        // Try wider rotations
        for (int i = 1; i <= 4; i++) {
            Direction left = dir.rotateLeftDegrees(45 * i);
            if (rc.getType() == RobotType.TANK) {
                if (rc.canMove(left)) {
                    rc.move(left);
                    return true;
                } else {
                    try {
                        rc.move(left);
                        return true;
                    } catch (GameActionException e) {
                        // Can't move left
                    }
                }
            } else {
                boolean leftClear = true;
                TreeInfo[] trees = rc.senseNearbyTrees(rc.getType().bodyRadius + rc.getType().strideRadius + 1.0f);
                for (TreeInfo tree : trees) {
                    Direction toTree = rc.getLocation().directionTo(tree.location);
                    if (Math.abs(left.degreesBetween(toTree)) < 60) {
                        leftClear = false;
                        break;
                    }
                }
                if (leftClear && rc.canMove(left)) {
                    rc.move(left);
                    return true;
                }
            }
            Direction right = dir.rotateRightDegrees(45 * i);
            if (rc.getType() == RobotType.TANK) {
                if (rc.canMove(right)) {
                    rc.move(right);
                    return true;
                } else {
                    try {
                        rc.move(right);
                        return true;
                    } catch (GameActionException e) {
                        // Can't move right
                    }
                }
            } else {
                boolean rightClear = true;
                TreeInfo[] trees = rc.senseNearbyTrees(rc.getType().bodyRadius + rc.getType().strideRadius + 1.0f);
                for (TreeInfo tree : trees) {
                    Direction toTree = rc.getLocation().directionTo(tree.location);
                    if (Math.abs(right.degreesBetween(toTree)) < 60) {
                        rightClear = false;
                        break;
                    }
                }
                if (rightClear && rc.canMove(right)) {
                    rc.move(right);
                    return true;
                }
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
