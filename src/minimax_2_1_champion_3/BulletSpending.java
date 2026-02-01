package minimax_2_1_champion_3;
import battlecode.common.*;

public class BulletSpending {
    static RobotController rc;
    static final float BULLET_RESERVE = 100f;

    public static void init(RobotController rc) {
        BulletSpending.rc = rc;
    }

    public static void spendPolicy() throws GameActionException {
        // Archon Hunter: Centralized spend order - hire gardener -> build units -> donate
        // No tree planting in this strategy
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
            // Archon Hunter: 50% lumberjack, 50% soldier build ratio
            if (shouldBuildLumberjack(dir)) {
                rc.buildRobot(RobotType.LUMBERJACK, dir);
            } else if (shouldBuildSoldier(dir)) {
                rc.buildRobot(RobotType.SOLDIER, dir);
            } else if (shouldBuildScout(dir)) {
                rc.buildRobot(RobotType.SCOUT, dir);
            }
            float donateAmount = getDonateAmount();
            if (donateAmount > 0f) {
                rc.donate(donateAmount);
            }
        }
    }

    private static boolean shouldHireGardener(Direction dir) {
        // Archon Hunter: hire gardeners to produce combat units
        if (rc.canHireGardener(dir)) {
            if (rc.getRoundNum() < 500) {
                return Math.random() < 0.3; // Moderate gardener hiring for army production
            }
            return Math.random() < 0.01;
        }
        return false;
    }

    // Archon Hunter: No tree planting - focus on combat units
    private static boolean shouldPlantTree(Direction dir) {
        return false;
    }

    // Archon Hunter: 50% lumberjack ratio
    private static boolean shouldBuildLumberjack(Direction dir) {
        return rc.canBuildRobot(RobotType.LUMBERJACK, dir) && Math.random() < 0.5;
    }

    // Archon Hunter: 50% soldier ratio
    private static boolean shouldBuildSoldier(Direction dir) {
        return rc.canBuildRobot(RobotType.SOLDIER, dir) && Math.random() < 0.5;
    }

    // Archon Hunter: Scout building for archon detection
    private static boolean shouldBuildScout(Direction dir) {
        return rc.canBuildRobot(RobotType.SCOUT, dir) && Math.random() < 0.1 && rc.getRoundNum() < 50;
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
