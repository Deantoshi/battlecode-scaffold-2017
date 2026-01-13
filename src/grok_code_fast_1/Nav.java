package grok_code_fast_1;
import battlecode.common.*;

public strictfp class Nav {
    static RobotController rc;

    // Bug navigation variables
    static Direction bugDir = null;
    static boolean bugTracing = false;
    static float bugStartDistSq = 0;

    public static void init(RobotController rc) {
        Nav.rc = rc;
    }

    public static boolean tryMove(Direction dir) throws GameActionException {
        TreeInfo[] trees = rc.senseNearbyTrees(5.0f);
        // For tanks, allow movement through trees since they can break them
        if (rc.getType() == RobotType.TANK) {
            if (rc.canMove(dir)) {
                rc.move(dir);
                if (trees.length > 3) Comms.broadcastLocation(15, 16, rc.getLocation());
                return true;
            } else {
                // Try to move anyway, as tanks can damage trees on collision
                try {
                    rc.move(dir);
                    if (trees.length > 3) Comms.broadcastLocation(15, 16, rc.getLocation());
                    return true;
                } catch (GameActionException e) {
                    // Can't move
                }
            }
        } else {
            if (rc.canMove(dir)) {
                rc.move(dir);
                if (trees.length > 3) Comms.broadcastLocation(15, 16, rc.getLocation());
                return true;
            }
        }

        // Try wider rotations
        for (int i = 1; i <= 4; i++) {
            Direction left = dir.rotateLeftDegrees(45 * i);
            if (rc.getType() == RobotType.TANK) {
                if (rc.canMove(left)) {
                    rc.move(left);
                    if (trees.length > 3) Comms.broadcastLocation(15, 16, rc.getLocation());
                    return true;
                } else {
                    try {
                        rc.move(left);
                        if (trees.length > 3) Comms.broadcastLocation(15, 16, rc.getLocation());
                        return true;
                    } catch (GameActionException e) {
                        // Can't move left
                    }
                }
            } else {
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
                    if (trees.length > 3) Comms.broadcastLocation(15, 16, rc.getLocation());
                    return true;
                }
            }
            Direction right = dir.rotateRightDegrees(45 * i);
            if (rc.getType() == RobotType.TANK) {
                if (rc.canMove(right)) {
                    rc.move(right);
                    if (trees.length > 3) Comms.broadcastLocation(15, 16, rc.getLocation());
                    return true;
                } else {
                    try {
                        rc.move(right);
                        if (trees.length > 3) Comms.broadcastLocation(15, 16, rc.getLocation());
                        return true;
                    } catch (GameActionException e) {
                        // Can't move right
                    }
                }
            } else {
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
                    if (trees.length > 3) Comms.broadcastLocation(15, 16, rc.getLocation());
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean moveToward(MapLocation target) throws GameActionException {
        if (target == null) return false;

        Direction dir = rc.getLocation().directionTo(target);
        float distSq = rc.getLocation().distanceSquaredTo(target);

        if (!bugTracing) {
            // Try direct move first
            if (tryMove(dir)) return true;
            // Start bug navigation
            bugTracing = true;
            bugDir = dir;
            bugStartDistSq = distSq;
        } else {
            // Continue bug navigation
            if (distSq < bugStartDistSq) {
                // Closer to target, stop bugging
                bugTracing = false;
                return moveToward(target);
            }
            // Follow obstacle
            Direction left = bugDir.rotateLeftDegrees(90);
            Direction right = bugDir.rotateRightDegrees(90);
            if (tryMove(left)) {
                bugDir = left;
                return true;
            } else if (tryMove(right)) {
                bugDir = right;
                return true;
            } else {
                // Stuck, rotate bug direction
                bugDir = bugDir.rotateLeftDegrees(45);
            }
        }
        return false;
    }

    public static Direction randomDirection() {
        return new Direction((float)(Math.random() * 2 * Math.PI));
    }
}
