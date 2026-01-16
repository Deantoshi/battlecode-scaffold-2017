package grok_code_fast_1;
import battlecode.common.*;

public strictfp class Nav {
    static RobotController rc;

    // Bug navigation variables
    static Direction bugDir = null;
    static boolean bugTracing = false;
    static float bugStartDistSq = 0;
    static int bugSteps = 0;
    static final int MAX_BUG_STEPS = 25;
    static Direction lastBugTurn = null;

    // Zigzag variables
    static int zigzagCount = 0;
    static Direction lastZigzagDir = null;

    public static void init(RobotController rc) {
        Nav.rc = rc;
    }

    public static boolean tryMove(Direction dir) throws GameActionException {
        // For tanks, only move if canMove is true
        if (rc.getType() == RobotType.TANK) {
            if (rc.canMove(dir)) {
                rc.move(dir);
                return true;
            }
        } else {
            boolean avoidTrees = true;
            // Defensive: keep tree avoidance for cover
            if (!avoidTrees) {
                if (rc.canMove(dir)) {
                    rc.move(dir);
                    return true;
                }
            } else {
                // For tree avoidance
                TreeInfo[] trees = rc.senseNearbyTrees(4.0f);
                boolean clear = true;
                for (TreeInfo tree : trees) {
                    Direction toTree = rc.getLocation().directionTo(tree.location);
                    if (Math.abs(dir.degreesBetween(toTree)) < 90) {
                        clear = false;
                        break;
                    }
                }
                if (clear && rc.canMove(dir)) {
                    rc.move(dir);
                    return true;
                }
            }
        }
        // Try rotations - increase to 90 degrees for better open-area coverage
        for (int i = 1; i <= 8; i++) {  // Changed from 4 to 8 for finer steps
            Direction left = dir.rotateLeftDegrees(22.5f * i);  // Finer rotation
            if (rc.getType() == RobotType.TANK) {
                if (rc.canMove(left)) {
                    rc.move(left);
                    return true;
                }
            } else {
                boolean avoidTrees = true;
                // Defensive: keep tree avoidance
                if (!avoidTrees) {
                    if (rc.canMove(left)) {
                        rc.move(left);
                        return true;
                    }
                } else {
                    TreeInfo[] trees = rc.senseNearbyTrees(4.0f);
                    boolean clear = true;
                    for (TreeInfo tree : trees) {
                        Direction toTree = rc.getLocation().directionTo(tree.location);
                        if (Math.abs(left.degreesBetween(toTree)) < 90) {
                            clear = false;
                            break;
                        }
                    }
                    if (clear && rc.canMove(left)) {
                        rc.move(left);
                        return true;
                    }
                }
            }
            Direction right = dir.rotateRightDegrees(22.5f * i);  // Finer rotation
            if (rc.getType() == RobotType.TANK) {
                if (rc.canMove(right)) {
                    rc.move(right);
                    return true;
                }
            } else {
                boolean avoidTrees = true;
                // Defensive: keep tree avoidance
                if (!avoidTrees) {
                    if (rc.canMove(right)) {
                        rc.move(right);
                        return true;
                    }
                } else {
                    TreeInfo[] trees = rc.senseNearbyTrees(4.0f);
                    boolean clear = true;
                    for (TreeInfo tree : trees) {
                        Direction toTree = rc.getLocation().directionTo(tree.location);
                        if (Math.abs(right.degreesBetween(toTree)) < 90) {
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
        }
        return false;
    }

    public static boolean moveToward(MapLocation target) throws GameActionException {
        if (target == null) return false;

        Direction dir = rc.getLocation().directionTo(target);
        float distSq = rc.getLocation().distanceSquaredTo(target);

        // Check map density
        TreeInfo[] nearbyTrees = rc.senseNearbyTrees(10.0f);
        boolean denseArea = nearbyTrees.length > 5;

        if (!bugTracing) {
            // Check map density for mode selection
            boolean openMap = nearbyTrees.length < 3;

            if (openMap) {
                // Direct LOS movement on open maps
                if (tryMove(dir)) return true;
                // Wider angles for open exploration
                for (int i = 1; i <= 4; i++) {
                    if (tryMove(dir.rotateLeftDegrees(30 * i))) return true;
                    if (tryMove(dir.rotateRightDegrees(30 * i))) return true;
                }
                // Spiral scouting if direct fails
                spiralScout(target);
                return true;
            } else {
                // Dense areas: use improved Bug
                if (tryMove(dir)) return true;
                bugTracing = true;
                bugDir = dir;
                bugStartDistSq = distSq;
            }
        } else {
            // Continue bug navigation
            if (distSq < bugStartDistSq) {
                // Closer to target, stop bugging
                bugTracing = false;
                bugSteps = 0;
                return moveToward(target);
            }
            // Simplified alternating turns
            Direction left = bugDir.rotateLeftDegrees(45);
            Direction right = bugDir.rotateRightDegrees(45);
            if (tryMove(left)) {
                if (lastBugTurn == left) {
                    bugTracing = false;
                    bugSteps = 0;
                    return moveToward(target);
                }
                bugDir = left;
                lastBugTurn = left;
                bugSteps++;
                return true;
            } else if (tryMove(right)) {
                if (lastBugTurn == right) {
                    bugTracing = false;
                    bugSteps = 0;
                    return moveToward(target);
                }
                bugDir = right;
                lastBugTurn = right;
                bugSteps++;
                return true;
            }
            if (bugSteps >= MAX_BUG_STEPS) {
                bugTracing = false;
                bugSteps = 0;
                for (int i = 0; i < 20; i++) {
                    if (tryMove(randomDirection())) return true;
                }
                return false;
            }
        }
        if (!rc.hasMoved()) {
            // Add zigzag when stuck
            if (zigzagCount > 5) {
                Direction zigDir = lastZigzagDir == null ? randomDirection() : lastZigzagDir.rotateLeftDegrees(60);
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
        // Lumberjack-specific clearing if stuck and is lumberjack
        if (rc.getType() == RobotType.LUMBERJACK && denseArea && !rc.hasMoved()) {
            TreeInfo[] blockingTrees = rc.senseNearbyTrees(rc.getType().bodyRadius + rc.getType().strideRadius + 2.0f, Team.NEUTRAL);
            for (TreeInfo tree : blockingTrees) {
                if (rc.canChop(tree.ID)) {
                    rc.chop(tree.ID);
                    return false; // Don't move this turn, chop instead
                }
            }
        }
        return false;
    }

    public static Direction randomDirection() {
        return new Direction((float)(Math.random() * 2 * Math.PI + (Math.random() - 0.5) * 0.5)); // Add small bias
    }

    static boolean spiralScout(MapLocation target) throws GameActionException {
        Direction spiralDir = rc.getLocation().directionTo(target);
        for (int radius = 1; radius <= 5; radius++) {
            for (int angle = 0; angle < 360; angle += 45) {
                Direction checkDir = spiralDir.rotateLeftDegrees(angle);
                MapLocation checkLoc = rc.getLocation().add(checkDir, radius * rc.getType().strideRadius);
                if (rc.canSenseLocation(checkLoc) && !rc.isLocationOccupiedByTree(checkLoc)) {
                    if (tryMove(checkDir)) return true;
                }
            }
        }
        return false;
    }
}