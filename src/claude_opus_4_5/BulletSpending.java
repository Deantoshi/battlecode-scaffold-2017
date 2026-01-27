package claude_opus_4_5;
import battlecode.common.*;

public class BulletSpending {
    static RobotController rc;
    static final float BULLET_RESERVE = 300f;

    public static void init(RobotController rc) {
        BulletSpending.rc = rc;
    }

    public static void spendPolicy() throws GameActionException {
        // Centralized spend order: hire gardener -> plant tree -> hire soldier -> donate.
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
            if (shouldBuildSoldier(dir)) {
                rc.buildRobot(RobotType.SOLDIER, dir);
            }
            float donateAmount = getDonateAmount();
            if (donateAmount > 0f) {
                rc.donate(donateAmount);
            }
        }
    }

    private static boolean shouldHireGardener(Direction dir) throws GameActionException {
        if (!rc.canHireGardener(dir)) {
            return false;
        }
        // Count gardeners on team
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        int gardenerCount = 0;
        for (RobotInfo ally : allies) {
            if (ally.type == RobotType.GARDENER) {
                gardenerCount++;
            }
        }
        // Hire gardeners aggressively until we have 4+
        if (gardenerCount < 4) {
            float bullets = rc.getTeamBullets();
            return bullets > 140f && Math.random() < 0.35;
        }
        return false;
    }

    private static boolean shouldPlantTree(Direction dir) throws GameActionException {
        if (!rc.canPlantTree(dir)) {
            return false;
        }
        // Count trees planted by this gardener
        TreeInfo[] trees = rc.senseNearbyTrees(-1, rc.getTeam());
        int treeCount = trees.length;
        // Plant trees aggressively until we have 6+ per gardener
        // Aim for ~6 trees per gardener (high priority)
        if (treeCount < 6) {
            return Math.random() < 0.95;
        }
        return false;
    }

    private static boolean shouldBuildSoldier(Direction dir) throws GameActionException {
        if (!rc.canBuildRobot(RobotType.SOLDIER, dir)) {
            return false;
        }
        // Only build soldiers when economy is strong or game is late
        float bullets = rc.getTeamBullets();
        int round = rc.getRoundNum();
        // Delay military production until economy is established
        if (bullets > 600f || round > 500) {
            return Math.random() < 0.3;
        }
        return false;
    }

    private static float getDonateAmount() throws GameActionException {
        float bullets = rc.getTeamBullets();
        float cost = rc.getVictoryPointCost();
        // In late game, donate excess over 700 for VP victory
        if (bullets > 700f) {
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
