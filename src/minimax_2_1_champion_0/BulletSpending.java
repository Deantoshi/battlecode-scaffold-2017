package minimax_2_1_champion_0;
import battlecode.common.*;

public class BulletSpending {
    static RobotController rc;
    static final float BULLET_RESERVE = 100f;

    public static void init(RobotController rc) {
        BulletSpending.rc = rc;
    }

    public static void spendPolicy() throws GameActionException {
        // Centralized spend order: hire gardener -> plant tree -> hire lumberjack -> donate.
        // Lumberjack Rush: prioritize lumberjack production and aggressive gardener hiring
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
            // Lumberjack Rush: Build lumberjacks with 0.5 probability instead of soldiers
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
        // Lumberjack Rush: Hire gardeners very aggressively (0.5 probability) in first 500 rounds
        if (rc.getRoundNum() < 500) {
            return rc.canHireGardener(dir) && Math.random() < 0.5;
        }
        return rc.canHireGardener(dir) && Math.random() < 0.1;
    }

    private static boolean shouldPlantTree(Direction dir) {
        // Reduce tree planting in rush strategy - focus on unit production
        return rc.canPlantTree(dir) && Math.random() < 0.001;
    }

    private static boolean shouldBuildLumberjack(Direction dir) {
        // Lumberjack Rush: Build lumberjacks with 0.5 probability
        return rc.canBuildRobot(RobotType.LUMBERJACK, dir) && Math.random() < 0.5;
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
