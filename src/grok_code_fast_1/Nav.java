package grok_code_fast_1;
import battlecode.common.*;

public strictfp class Nav {
    private static RobotController rc;
    private static MapLocation target;
    private static boolean tracing = false;
    private static Direction tracingDir;

    public static void init(RobotController rc) {
        Nav.rc = rc;
    }

    public static void setTarget(MapLocation t) {
        target = t;
        tracing = false;
    }

    public static boolean tryMoveTowards(MapLocation loc) throws GameActionException {
        if (loc == null) return false;
        setTarget(loc);
        return moveTowardsTarget();
    }

    private static boolean moveTowardsTarget() throws GameActionException {
        if (target == null) return false;

        Direction dir = rc.getLocation().directionTo(target);
        if (rc.canMove(dir)) {
            rc.move(dir);
            tracing = false;
            return true;
        }

        // Bug navigation: if blocked, start tracing
        if (!tracing) {
            tracing = true;
            tracingDir = dir.rotateLeftDegrees(90); // arbitrary choice
        }

        // Try to move in tracing direction
        if (rc.canMove(tracingDir)) {
            rc.move(tracingDir);
            return true;
        }

        // If blocked, rotate tracing direction
        tracingDir = tracingDir.rotateRightDegrees(10);
        if (rc.canMove(tracingDir)) {
            rc.move(tracingDir);
            return true;
        }

        // Give up for now
        return false;
    }

    // Simple tryMove like in example
    public static boolean tryMove(Direction dir) throws GameActionException {
        if (rc.canMove(dir)) {
            rc.move(dir);
            return true;
        }
        for (int i = 1; i <= 3; i++) {
            if (rc.canMove(dir.rotateLeftDegrees(20 * i))) {
                rc.move(dir.rotateLeftDegrees(20 * i));
                return true;
            }
            if (rc.canMove(dir.rotateRightDegrees(20 * i))) {
                rc.move(dir.rotateRightDegrees(20 * i));
                return true;
            }
        }
        return false;
    }
}