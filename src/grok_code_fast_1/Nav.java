package grok_code_fast_1;
import battlecode.common.*;

public strictfp class Nav {
    static RobotController rc;

    // Bug navigation variables
    static Direction bugDir = null;
    static boolean bugTracing = false;
    static float bugStartDistSq = 0;
    static int bugSteps = 0;
    static final int MAX_BUG_STEPS = 20;

    // Zigzag variables
    static int zigzagCount = 0;
    static Direction lastZigzagDir = null;

    public static void init(RobotController rc) {
        Nav.rc = rc;
    }

    public static boolean tryMove(Direction dir) throws GameActionException {
        TreeInfo[] trees = rc.senseNearbyTrees(7.0f);
        // Check for tree clusters
        if (trees.length > 5 && rc.getType() != RobotType.TANK) {
            // Calculate cluster center
            float sumX = 0, sumY = 0;
            for (TreeInfo tree : trees) {
                sumX += tree.location.x;
                sumY += tree.location.y;
            }
            MapLocation clusterCenter = new MapLocation(sumX / trees.length, sumY / trees.length);
            Comms.broadcastLocation(17, 18, clusterCenter); // Use channels 17-18 for cluster
            // Try to move around cluster if direct path is blocked
            if (!rc.canMove(dir)) {
                Direction awayFromCluster = rc.getLocation().directionTo(clusterCenter).opposite();
                if (rc.canMove(awayFromCluster)) {
                    rc.move(awayFromCluster);
                    if (trees.length > 3) Comms.broadcastLocation(15, 16, rc.getLocation());
                    return true;
                }
            }
        }
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
                bugTracing = false;
                bugSteps = 0;
                return tryMove(randomDirection()); // Random reset
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
