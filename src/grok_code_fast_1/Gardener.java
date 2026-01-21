package grok_code_fast_1;

import battlecode.common.*;

public strictfp class Gardener {
    static RobotController rc;

    public static void init(RobotController rc) {
        Gardener.rc = rc;
    }

    public static void buildRobot() throws GameActionException {
        Direction dir = randomDirection();

        // Get soldier count, perhaps from broadcast or sense
        // For simplicity, assume local sense
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        int localSoldiers = 0;
        int localLumberjacks = 0;
        for (RobotInfo ally : allies) {
            if (ally.type == RobotType.SOLDIER) localSoldiers++;
            if (ally.type == RobotType.LUMBERJACK) localLumberjacks++;
        }
        // Use local for now

        if (localLumberjacks < 4) {
            if (rc.canBuildRobot(RobotType.LUMBERJACK, dir) && rc.isBuildReady()) {
                rc.buildRobot(RobotType.LUMBERJACK, dir);
                return;
            }
        }

        if (localSoldiers < 12) {
            if (rc.canBuildRobot(RobotType.SOLDIER, dir) && rc.isBuildReady()) {
                rc.buildRobot(RobotType.SOLDIER, dir);
                return;
            }
        }

        // Tank
        if (Math.random() < 0.1) {
            if (rc.canBuildRobot(RobotType.TANK, dir) && rc.isBuildReady()) {
                rc.buildRobot(RobotType.TANK, dir);
                return;
            }
        }

        // Tree
        if (Math.random() < 0.1) {
            if (rc.canPlantTree(dir) && rc.isBuildReady()) {
                rc.plantTree(dir);
                return;
            }
        }
    }

    /**
     * Returns a random Direction
     * @return a random Direction
     */
    static Direction randomDirection() {
        return new Direction((float)Math.random() * 2 * (float)Math.PI);
    }
}