package grok_code_fresh;
import battlecode.common.*;

public class BulletSpending {
    static RobotController rc;
    static final float BULLET_RESERVE = 300f;  // Increased to donate less and save for soldier production

    public static void init(RobotController rc) {
        BulletSpending.rc = rc;
    }

    public static void spendPolicy() throws GameActionException {
        // Centralized spend order: hire gardener -> hire soldier -> donate.
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
            // Removed tree planting to focus solely on soldier building
            Direction dir = randomDirection();
            if (shouldBuildSoldier(dir)) {
                rc.buildRobot(RobotType.SOLDIER, dir);
            }
            float donateAmount = getDonateAmount();
            if (donateAmount > 0f) {
                rc.donate(donateAmount);
            }
        }
    }

    private static boolean shouldHireGardener(Direction dir) {
        // Increased probability to hire gardeners rapidly for soldier production
        return rc.canHireGardener(dir) && Math.random() < 0.2;
    }

    // Removed shouldPlantTree since we focus solely on soldiers

    private static boolean shouldBuildSoldier(Direction dir) {
        // Increased probability to build multiple soldiers rapidly
        return rc.canBuildRobot(RobotType.SOLDIER, dir) && Math.random() < 0.5;
    }

    private static float getDonateAmount() throws GameActionException {
        float bullets = rc.getTeamBullets();
        float cost = rc.getVictoryPointCost();
        // Donate only if we have plenty of bullets to save for soldier production
        if (bullets > 500f) {
            float donateAmount = bullets - BULLET_RESERVE;
            if (donateAmount >= cost) {
                int pointsToBuy = (int)(donateAmount / cost);
                return pointsToBuy * cost;
            }
        }
        return 0f;
    }

    private static Direction randomDirection() {
        return new Direction((float)Math.random() * 2 * (float)Math.PI);
    }
}