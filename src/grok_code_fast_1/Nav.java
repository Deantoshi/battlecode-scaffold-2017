package grok_code_fast_1;
import battlecode.common.*;

public strictfp class Nav {
    static RobotController rc;

    // Bug navigation variables
    static Direction bugDir = null;
    static boolean bugTracing = false;
    static float bugStartDistSq = 0;
    static int bugSteps = 0;
    static final int MAX_BUG_STEPS = 100;

    // Zigzag variables
    static int zigzagCount = 0;
    static Direction lastZigzagDir = null;

    public static void init(RobotController rc) {
        Nav.rc = rc;
    }

    public static boolean tryMove(Direction dir) throws GameActionException {
        // For tanks, allow movement through trees
        if (rc.getType() == RobotType.TANK) {
            if (rc.canMove(dir)) {
                rc.move(dir);
                return true;
            } else {
                try {
                    rc.move(dir);
                    return true;
                } catch (GameActionException e) {
                    // Can't move
                }
            }
        } else {
            // For tree avoidance
            TreeInfo[] trees = rc.senseNearbyTrees(7.0f);
            boolean clear = true;
            for (TreeInfo tree : trees) {
                Direction toTree = rc.getLocation().directionTo(tree.location);
                if (Math.abs(dir.degreesBetween(toTree)) < 45) {
                    clear = false;
                    break;
                }
            }
            if (clear && rc.canMove(dir)) {
                rc.move(dir);
                return true;
            }
        }
        // Try rotations
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
                        // Can't move
                    }
                }
            } else {
                TreeInfo[] trees = rc.senseNearbyTrees(7.0f);
                boolean clear = true;
                for (TreeInfo tree : trees) {
                    Direction toTree = rc.getLocation().directionTo(tree.location);
                    if (Math.abs(left.degreesBetween(toTree)) < 45) {
                        clear = false;
                        break;
                    }
                }
                if (clear && rc.canMove(left)) {
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
                        // Can't move
                    }
                }
            } else {
                TreeInfo[] trees = rc.senseNearbyTrees(7.0f);
                boolean clear = true;
                for (TreeInfo tree : trees) {
                    Direction toTree = rc.getLocation().directionTo(tree.location);
                    if (Math.abs(right.degreesBetween(toTree)) < 45) {
                        clear = false;
                        break;
                    }
                }
                if (clear && rc.canMove(right)) {
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
        float distSq = rc.getLocation().distanceSquaredTo(target);

        // Check map density
        TreeInfo[] nearbyTrees = rc.senseNearbyTrees(5.0f);
        BulletInfo[] nearbyBullets = rc.senseNearbyBullets(5.0f);
        boolean sparseArea = nearbyTrees.length + nearbyBullets.length < 3;

        if (!bugTracing) {
            // Try direct move first
            if (tryMove(dir)) return true;
            if (sparseArea) {
                // In sparse areas, try wider direct angles
                for (int i = 1; i <= 2; i++) {
                    if (tryMove(dir.rotateLeftDegrees(45 * i))) return true;
                    if (tryMove(dir.rotateRightDegrees(45 * i))) return true;
                }
            }
            // Start bug navigation
            bugTracing = true;
            bugDir = dir;
            bugStartDistSq = distSq;
        } else {
            // Continue bug navigation
            if (distSq < bugStartDistSq) {
                // Closer to target, stop bugging
                bugTracing = false;
                bugSteps = 0;
                return moveToward(target);
            }
            // Follow obstacle with alternating turns
            Direction left = bugDir.rotateLeftDegrees(90);
            Direction right = bugDir.rotateRightDegrees(90);
            if (bugSteps % 2 == 0) {
                if (tryMove(left)) {
                    bugDir = left;
                    bugSteps++;
                    return true;
                } else if (tryMove(right)) {
                    bugDir = right;
                    bugSteps++;
                    return true;
                }
            } else {
                if (tryMove(right)) {
                    bugDir = right;
                    bugSteps++;
                    return true;
                } else if (tryMove(left)) {
                    bugDir = left;
                    bugSteps++;
                    return true;
                }
            }
            // Stuck, rotate bug direction or reset if max steps reached
            if (bugSteps >= MAX_BUG_STEPS) {
                // Check for edge: if location is near boundary and bugDir is towards it
                MapLocation loc = rc.getLocation();
                if ((loc.x < 5 && bugDir.radians > Math.PI) || (loc.x > 95 && bugDir.radians < Math.PI) ||
                    (loc.y < 5 && (bugDir.radians > Math.PI/2 && bugDir.radians < 3*Math.PI/2)) ||
                    (loc.y > 95 && (bugDir.radians < Math.PI/2 || bugDir.radians > 3*Math.PI/2))) {
                    // At edge, reset away from edge
                    bugTracing = false;
                    bugSteps = 0;
                    Direction awayFromEdge = bugDir.opposite();
                    tryMove(awayFromEdge);
                    return false;
                } else {
                    bugTracing = false;
                    bugSteps = 0;
                    return tryMove(randomDirection());
                }
            }
            bugDir = bugDir.rotateLeftDegrees(45);
            bugSteps++;
        }
        if (!rc.hasMoved()) {
            // Add zigzag when stuck
            if (zigzagCount > 5) {
                Direction zigDir = lastZigzagDir == null ? Nav.randomDirection() : lastZigzagDir.rotateLeftDegrees(60);
                if (tryMove(zigDir)) {
                    lastZigzagDir = zigDir;
                    zigzagCount = 0;
                    return true;
                }
                zigzagCount++;
            } else {
                zigzagCount++;
            }
        }
        return false;
    }

    public static Direction randomDirection() {
        return new Direction((float)(Math.random() * 2 * Math.PI + (Math.random() - 0.5) * 0.5)); // Add small bias
    }
}
