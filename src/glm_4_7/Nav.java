package glm_4_7;
import battlecode.common.*;

public strictfp class Nav {
    public static boolean tryMove(RobotController rc, Direction dir) throws GameActionException {
        return tryMove(rc, dir, 20, 3);
    }

    public static boolean tryMove(RobotController rc, Direction dir, float degreeOffset, int checksPerSide) throws GameActionException {
        if (rc.canMove(dir)) {
            rc.move(dir);
            return true;
        }
        int currentCheck = 1;
        while (currentCheck <= checksPerSide) {
            if (rc.canMove(dir.rotateLeftDegrees(degreeOffset * currentCheck))) {
                rc.move(dir.rotateLeftDegrees(degreeOffset * currentCheck));
                return true;
            }
            if (rc.canMove(dir.rotateRightDegrees(degreeOffset * currentCheck))) {
                rc.move(dir.rotateRightDegrees(degreeOffset * currentCheck));
                return true;
            }
            currentCheck++;
        }
        return false;
    }

    public static boolean moveToward(RobotController rc, MapLocation target) throws GameActionException {
        Direction toTarget = rc.getLocation().directionTo(target);
        return tryMove(rc, toTarget, 15, 6);
    }

    public static boolean moveAway(RobotController rc, MapLocation danger) throws GameActionException {
        Direction fromDanger = rc.getLocation().directionTo(danger).opposite();
        return tryMove(rc, fromDanger, 15, 6);
    }

    public static Direction randomDirection() {
        return new Direction((float) Math.random() * 2 * (float) Math.PI);
    }
}
