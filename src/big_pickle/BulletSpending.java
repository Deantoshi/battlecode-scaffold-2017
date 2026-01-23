package big_pickle;
import battlecode.common.*;

public class BulletSpending {
    static RobotController rc;

    public static void init(RobotController rc) {
        BulletSpending.rc = rc;
    }

    public static void tryHireGardener(Direction dir) throws GameActionException {
        // Randomly attempt to build a gardener in this direction
        if (rc.canHireGardener(dir) && Math.random() < .01) {
            rc.hireGardener(dir);
        }
    }

    public static void tryBuildGardenerUnits(Direction dir) throws GameActionException {
        // Randomly attempt to build a soldier or lumberjack in this direction
        if (rc.canBuildRobot(RobotType.SOLDIER, dir) && Math.random() < .01) {
            rc.buildRobot(RobotType.SOLDIER, dir);
        } else if (rc.canBuildRobot(RobotType.LUMBERJACK, dir) && Math.random() < .01 && rc.isBuildReady()) {
            rc.buildRobot(RobotType.LUMBERJACK, dir);
        }
    }
}
