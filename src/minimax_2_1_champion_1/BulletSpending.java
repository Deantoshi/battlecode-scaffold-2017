package minimax_2_1_champion_1;
import battlecode.common.*;

public class BulletSpending {
    static RobotController rc;
    static final float BULLET_RESERVE = 100f;

    public static void init(RobotController rc) {
        BulletSpending.rc = rc;
    }

    public static void spendPolicy() throws GameActionException {
        // Centralized spend order: hire gardener -> plant tree -> hire scout -> donate.
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
            // Reduced tree planting to 0.05 to maximize scout production
            if (shouldPlantTree(dir)) {
                rc.plantTree(dir);
            }
            dir = randomDirection();
            // Prioritize scouts with 0.4 probability
            if (shouldBuildScout(dir)) {
                rc.buildRobot(RobotType.SCOUT, dir);
            }
            float donateAmount = getDonateAmount();
            if (donateAmount > 0f) {
                rc.donate(donateAmount);
            }
        }
    }

    private static boolean shouldHireGardener(Direction dir) {
        return rc.canHireGardener(dir) && Math.random() < .01;
    }

    private static boolean shouldPlantTree(Direction dir) {
        // Reduced from 0.01 to 0.05 for scout swarm - minimal tree planting to maximize unit production
        return rc.canPlantTree(dir) && Math.random() < 0.05;
    }

    private static boolean shouldBuildScout(Direction dir) {
        // Scout swarm: prioritize scouts with 0.4 probability and check we have enough bullets (scout costs 100)
        return rc.canBuildRobot(RobotType.SCOUT, dir) && rc.getTeamBullets() > 100 && Math.random() < 0.4;
    }

    private static boolean shouldBuildSoldier(Direction dir) {
        // No longer used in scout swarm - kept for reference
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
