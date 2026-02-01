package kimi_k2_5_champion_4;
import battlecode.common.*;

public class BulletSpending {
    static RobotController rc;
    // Defensive Turtle: Balanced reserves for dense tree walls + VP accumulation
    static final float BULLET_RESERVE = 100f;   // Balanced for defense + VP

    public static void init(RobotController rc) {
        BulletSpending.rc = rc;
    }

    public static void spendPolicy() throws GameActionException {
        int round = rc.getRoundNum();
        float reserve = getReserveForRound(round);

        // Centralized spend order based on game phase
        if (rc.getType() == RobotType.ARCHON) {
            Direction dir = randomDirection();
            // Hire gardeners at 0.025f - maintain defensive production
            if (shouldHireGardener(dir, round)) {
                rc.hireGardener(dir);
            }
            // VP donations: Start at round 1200 - moderate timing
            if (round >= 1200) {
                float donateAmount = getDonateAmount(reserve, round);
                if (donateAmount > 0f) {
                    rc.donate(donateAmount);
                }
            }
            return;
        }
        if (rc.getType() == RobotType.GARDENER) {
            // Plant trees at 0.06f - dense defensive walls
            Direction dir = randomDirection();
            if (shouldPlantTree(dir, round)) {
                rc.plantTree(dir);
            }
            // Build scouts at 0.015f consistently (recon and harassment)
            dir = randomDirection();
            if (shouldBuildScout(dir, round)) {
                rc.buildRobot(RobotType.SCOUT, dir);
            }
            // Build soldiers at 0.025f - defenders behind trees
            dir = randomDirection();
            if (shouldBuildSoldier(dir, round)) {
                rc.buildRobot(RobotType.SOLDIER, dir);
            }
            // VP donations: Moderate donations from gardeners starting round 1200
            if (round >= 1200) {
                float donateAmount = getDonateAmount(reserve, round);
                if (donateAmount > 0f) {
                    rc.donate(donateAmount);
                }
            }
        }
    }

    private static float getReserveForRound(int round) {
        // Defensive Turtle: 100f balanced reserve throughout
        return BULLET_RESERVE;
    }

    private static boolean shouldHireGardener(Direction dir, int round) {
        if (!rc.canHireGardener(dir)) return false;
        // Hire gardeners at 0.025f - maintain defensive production
        float hireChance = (round < 600) ? 0.025f : 0.015f;
        return Math.random() < hireChance;
    }

    private static boolean shouldPlantTree(Direction dir, int round) {
        if (!rc.canPlantTree(dir)) return false;
        // Plant trees at 0.06f - dense defensive walls
        return Math.random() < 0.06f;
    }

    private static boolean shouldBuildScout(Direction dir, int round) {
        if (!rc.canBuildRobot(RobotType.SCOUT, dir)) return false;
        // Build scouts at 0.015f consistently
        return Math.random() < 0.015f;
    }

    private static boolean shouldBuildSoldier(Direction dir, int round) {
        if (!rc.canBuildRobot(RobotType.SOLDIER, dir)) return false;
        // Build soldiers at 0.025f - defenders behind trees (higher for defense)
        return Math.random() < 0.025f;
    }

    private static float getDonateAmount(float reserve, int round) throws GameActionException {
        float bullets = rc.getTeamBullets();
        float cost = rc.getVictoryPointCost();
        
        // Defensive Turtle: Moderate donations starting round 1200
        float effectiveReserve = (round >= 2500) ? 50f : reserve;
        
        float donateAmount = bullets - effectiveReserve;
        if (donateAmount >= cost) {
            int pointsToBuy = (int)(donateAmount / cost);
            // Moderate frequency - buy in chunks
            return pointsToBuy * cost;
        }
        return 0f;
    }

    private static Direction randomDirection() {
        return new Direction((float)Math.random() * 2 * (float)Math.PI);
    }
}
