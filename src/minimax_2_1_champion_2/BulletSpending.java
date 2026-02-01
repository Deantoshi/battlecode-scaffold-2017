package minimax_2_1_champion_2;
import battlecode.common.*;

public class BulletSpending {
    static RobotController rc;
    static final float BULLET_RESERVE = 100f;

    public static void init(RobotController rc) {
        BulletSpending.rc = rc;
    }

    public static void spendPolicy() throws GameActionException {
        // Centralized spend order: hire gardener -> plant tree -> hire lumberjack -> donate.
        if (rc.getType() == RobotType.ARCHON) {
            Direction dir = randomDirection();
            if (shouldHireGardener(dir)) {
                rc.hireGardener(dir);
            }
            float donateAmount = getDonateAmount();
            if (donateAmount > 0f) {
                rc.donate(donateAmount);
            }
            return;
        }
        if (rc.getType() == RobotType.GARDENER) {
            Direction dir = randomDirection();
            if (shouldPlantTree(dir)) {
                rc.plantTree(dir);
            }
            dir = randomDirection();
            // Lumberjack Rush: build lumberjacks with 0.5 probability instead of soldiers
            if (shouldBuildLumberjack(dir)) {
                rc.buildRobot(RobotType.LUMBERJACK, dir);
            }
            float donateAmount = getDonateAmount();
            if (donateAmount > 0f) {
                rc.donate(donateAmount);
            }
        }
    }

    private static boolean shouldHireGardener(Direction dir) {
        // Lumberjack Rush: hire gardeners very aggressively (probability 0.5) in first 500 rounds
        if (rc.canHireGardener(dir)) {
            if (rc.getRoundNum() < 500) {
                return Math.random() < 0.5;
            }
            return Math.random() < 0.01;
        }
        return false;
    }

    private static boolean shouldPlantTree(Direction dir) {
        return rc.canPlantTree(dir) && Math.random() < .01;
    }

    // New method for Lumberjack Rush: build lumberjacks with 0.5 probability
    private static boolean shouldBuildLumberjack(Direction dir) {
        return rc.canBuildRobot(RobotType.LUMBERJACK, dir) && Math.random() < 0.5;
    }

    private static boolean shouldBuildSoldier(Direction dir) {
        return rc.canBuildRobot(RobotType.SOLDIER, dir) && Math.random() < .01;
    }

    private static float getDonateAmount() throws GameActionException {
        float bullets = rc.getTeamBullets();
        float cost = rc.getVictoryPointCost();
        float donateAmount = bullets - BULLET_RESERVE;
        if (donateAmount >= cost) {
            int pointsToBuy = (int)(donateAmount / cost);
            return pointsToBuy * cost;
        }
        return 0f;
    }

    private static Direction randomDirection() {
        return new Direction((float)Math.random() * 2 * (float)Math.PI);
    }
}
