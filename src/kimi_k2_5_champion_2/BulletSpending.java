package kimi_k2_5_champion_2;
import battlecode.common.*;

public class BulletSpending {
    static RobotController rc;
    // Hybrid Dominance: Balanced reserves for steady economy and army production
    static final float BULLET_RESERVE_EARLY = 120f;   // Moderate reserve for early game flexibility
    static final float BULLET_RESERVE_MID = 80f;      // Lower mid-game to enable sustained production
    static final float BULLET_RESERVE_LATE = 50f;     // Minimal late-game to maximize VP potential

    public static void init(RobotController rc) {
        BulletSpending.rc = rc;
    }

    public static void spendPolicy() throws GameActionException {
        int round = rc.getRoundNum();
        float reserve = getReserveForRound(round);

        // Centralized spend order based on game phase
        if (rc.getType() == RobotType.ARCHON) {
            Direction dir = randomDirection();
            // Hire gardeners at 0.025f for first 600 rounds (high early expansion)
            if (shouldHireGardener(dir, round)) {
                rc.hireGardener(dir);
            }
            // VP donations: Start at round 1500 with moderate frequency
            if (round >= 1500) {
                float donateAmount = getDonateAmount(reserve, round);
                if (donateAmount > 0f) {
                    rc.donate(donateAmount);
                }
            }
            return;
        }
        if (rc.getType() == RobotType.GARDENER) {
            // Plant trees at 0.03f throughout the game (sustained economy)
            Direction dir = randomDirection();
            if (shouldPlantTree(dir, round)) {
                rc.plantTree(dir);
            }
            // Build scouts at 0.015f consistently (recon and harassment)
            dir = randomDirection();
            if (shouldBuildScout(dir, round)) {
                rc.buildRobot(RobotType.SCOUT, dir);
            }
            // Build soldiers at 0.02f consistently (main army)
            dir = randomDirection();
            if (shouldBuildSoldier(dir, round)) {
                rc.buildRobot(RobotType.SOLDIER, dir);
            }
            // VP donations: Moderate donations from gardeners starting round 1500
            if (round >= 1500) {
                float donateAmount = getDonateAmount(reserve, round);
                if (donateAmount > 0f) {
                    rc.donate(donateAmount);
                }
            }
        }
    }

    private static float getReserveForRound(int round) {
        if (round < 1000) {
            return BULLET_RESERVE_EARLY;  // 120f - moderate early reserve
        } else if (round < 2000) {
            return BULLET_RESERVE_MID;    // 80f - enable sustained production
        } else {
            return BULLET_RESERVE_LATE;   // 50f - minimal late reserve
        }
    }

    private static boolean shouldHireGardener(Direction dir, int round) {
        if (!rc.canHireGardener(dir)) return false;
        // Hire gardeners at 0.025f for first 600 rounds
        float hireChance = (round < 600) ? 0.025f : 0.015f;
        return Math.random() < hireChance;
    }

    private static boolean shouldPlantTree(Direction dir, int round) {
        if (!rc.canPlantTree(dir)) return false;
        // Plant trees at 0.03f throughout the game (sustained economy focus)
        return Math.random() < 0.03f;
    }

    private static boolean shouldBuildScout(Direction dir, int round) {
        if (!rc.canBuildRobot(RobotType.SCOUT, dir)) return false;
        // Build scouts at 0.015f consistently
        return Math.random() < 0.015f;
    }

    private static boolean shouldBuildSoldier(Direction dir, int round) {
        if (!rc.canBuildRobot(RobotType.SOLDIER, dir)) return false;
        // Build soldiers at 0.02f consistently (higher than scouts for army strength)
        return Math.random() < 0.02f;
    }

    private static float getDonateAmount(float reserve, int round) throws GameActionException {
        float bullets = rc.getTeamBullets();
        float cost = rc.getVictoryPointCost();
        
        // Moderate donations - adapt to whatever victory condition presents itself first
        float effectiveReserve = (round >= 2500) ? BULLET_RESERVE_LATE : reserve;
        
        float donateAmount = bullets - effectiveReserve;
        if (donateAmount >= cost) {
            int pointsToBuy = (int)(donateAmount / cost);
            // Moderate frequency - buy in chunks rather than every round
            return pointsToBuy * cost;
        }
        return 0f;
    }

    private static Direction randomDirection() {
        return new Direction((float)Math.random() * 2 * (float)Math.PI);
    }
}
