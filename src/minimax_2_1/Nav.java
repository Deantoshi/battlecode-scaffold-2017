package minimax_2_1;
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
        for (int i = 1; i <= 6; i++) {
            Direction left = dir.rotateLeftDegrees(15 * i);
            if (rc.canMove(left)) {
                rc.move(left);
                return true;
            }
            Direction right = dir.rotateRightDegrees(15 * i);
            if (rc.canMove(right)) {
                rc.move(right);
                return true;
            }
        }
        return false;
    }

    public static boolean moveToward(MapLocation target) throws GameActionException {
        if (target == null) return false;
        Direction dir = rc.getLocation().directionTo(target);
        return tryMove(dir);
    }

    public static Direction randomDirection() {
        return new Direction((float)(Math.random() * 2 * Math.PI));
    }
}
