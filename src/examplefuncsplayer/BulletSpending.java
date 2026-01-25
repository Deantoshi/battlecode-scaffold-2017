package examplefuncsplayer;
import battlecode.common.*;

public class BulletSpending {
    static RobotController rc;

    public static void init(RobotController rc) {
        BulletSpending.rc = rc;
    }

    public static void spendPolicy() throws GameActionException {
        // Centralized spend order: hire gardener -> plant tree -> hire soldier.
        if (rc.getType() == RobotType.ARCHON) {
            Direction dir = randomDirection();
            if (shouldHireGardener(dir)) {
                rc.hireGardener(dir);
            }
            return;
        }
        if (rc.getType() == RobotType.GARDENER) {
            Direction dir = randomDirection();
            if (shouldPlantTree(dir)) {
                rc.plantTree(dir);
            }
            dir = randomDirection();
            if (shouldBuildSoldier(dir)) {
                rc.buildRobot(RobotType.SOLDIER, dir);
            }
        }
    }

    private static boolean shouldHireGardener(Direction dir) {
        return rc.canHireGardener(dir) && Math.random() < .01;
    }

    private static boolean shouldPlantTree(Direction dir) {
        return rc.canPlantTree(dir) && Math.random() < .01;
    }

    private static boolean shouldBuildSoldier(Direction dir) {
        return rc.canBuildRobot(RobotType.SOLDIER, dir) && Math.random() < .01;
    }

    private static Direction randomDirection() {
        return new Direction((float)Math.random() * 2 * (float)Math.PI);
    }
}
