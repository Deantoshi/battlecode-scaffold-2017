package grok_code_fast_1;
import battlecode.common.*;

public class Gardener {
    static RobotController rc;

    public static void init(RobotController rc) {
        Gardener.rc = rc;
    }

    public static void buildRobot() throws GameActionException {
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        int localScouts = 0;
        for (RobotInfo ally : allies) {
            if (ally.type == RobotType.SCOUT) localScouts++;
        }

        if (localScouts < 5) {
            // Build scout
            for (int attempt = 0; attempt < 8; attempt++) {
                Direction dir = randomDirection();
                if (rc.canBuildRobot(RobotType.SCOUT, dir) && rc.isBuildReady()) {
                    rc.buildRobot(RobotType.SCOUT, dir);
                    return;
                }
            }
        } else {
            // Build soldiers
            for (int attempt = 0; attempt < 8; attempt++) {
                Direction dir = randomDirection();
                if (rc.canBuildRobot(RobotType.SOLDIER, dir) && rc.isBuildReady()) {
                    rc.buildRobot(RobotType.SOLDIER, dir);
                    return;
                }
            }
        }
    }

    static Direction randomDirection() {
        return new Direction((float)Math.random() * 2 * (float)Math.PI);
    }
}