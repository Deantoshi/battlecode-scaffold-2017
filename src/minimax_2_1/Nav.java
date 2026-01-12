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
        RobotInfo[] nearbyRobots = rc.senseNearbyRobots(5, rc.getTeam());
        TreeInfo[] nearbyTrees = rc.senseNearbyTrees(5);
        boolean[] blocked = new boolean[8];
        for (RobotInfo r : nearbyRobots) {
            if (r.type != RobotType.ARCHON && r.type != RobotType.GARDENER) continue;
            Direction d = rc.getLocation().directionTo(r.location);
            int idx = ((int)Math.round(d.radians / (Math.PI / 4)) % 8 + 8) % 8;
            blocked[idx] = true;
        }
        for (TreeInfo t : nearbyTrees) {
            Direction d = rc.getLocation().directionTo(t.location);
            int idx = ((int)Math.round(d.radians / (Math.PI / 4)) % 8 + 8) % 8;
            blocked[idx] = true;
        }
        for (int i = 1; i <= 6; i++) {
            Direction left = dir.rotateLeftDegrees(15 * i);
            int leftIdx = ((int)Math.round(left.radians / (Math.PI / 4)) % 8 + 8) % 8;
            if (!blocked[leftIdx] && rc.canMove(left)) {
                rc.move(left);
                return true;
            }
            Direction right = dir.rotateRightDegrees(15 * i);
            int rightIdx = ((int)Math.round(right.radians / (Math.PI / 4)) % 8 + 8) % 8;
            if (!blocked[rightIdx] && rc.canMove(right)) {
                rc.move(right);
                return true;
            }
        }
        for (int i = 0; i < 8; i++) {
            Direction randomDir = new Direction((float)(i * Math.PI / 4));
            if (rc.canMove(randomDir)) {
                rc.move(randomDir);
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
