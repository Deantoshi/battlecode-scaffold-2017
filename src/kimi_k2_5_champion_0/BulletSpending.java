package kimi_k2_5_champion_0;
import battlecode.common.*;

public class BulletSpending {
    static RobotController rc;
    // Dynamic reserve based on game phase - EXTREME early defense
    static final float BULLET_RESERVE_EARLY = 250f;  // Until round 1000
    static final float BULLET_RESERVE_LATE = 50f;    // After round 2000
    static final float BULLET_RESERVE_MID = 150f;    // Round 1000-2000

    public static void init(RobotController rc) {
        BulletSpending.rc = rc;
    }

    public static void spendPolicy() throws GameActionException {
        int round = rc.getRoundNum();
        float reserve = getReserveForRound(round);

        // Centralized spend order based on game phase
        if (rc.getType() == RobotType.ARCHON) {
            Direction dir = randomDirection();
            // Hire gardeners more aggressively early to build economy
            if (shouldHireGardener(dir, round)) {
                rc.hireGardener(dir);
            }
            // VP donations: ZERO until round 1500, then start donating
            if (round >= 1500) {
                float donateAmount = getDonateAmount(reserve, round);
                if (donateAmount > 0f) {
                    rc.donate(donateAmount);
                }
            }
            return;
        }
        if (rc.getType() == RobotType.GARDENER) {
            // Plant trees at 0.05 probability for first 1000 rounds
            Direction dir = randomDirection();
            if (shouldPlantTree(dir, round)) {
                rc.plantTree(dir);
            }
            // Build soldiers for defense
            dir = randomDirection();
            if (shouldBuildSoldier(dir, round)) {
                rc.buildRobot(RobotType.SOLDIER, dir);
            }
            // VP donations from gardeners too (after round 1500)
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
            return BULLET_RESERVE_EARLY;  // 250f - extreme early defense
        } else if (round < 2000) {
            return BULLET_RESERVE_MID;    // 150f
        } else {
            return BULLET_RESERVE_LATE;   // 50f - convert all excess to VP
        }
    }

    private static boolean shouldHireGardener(Direction dir, int round) {
        if (!rc.canHireGardener(dir)) return false;
        // More aggressive hiring early to build economy
        float hireChance = (round < 1000) ? 0.02f : 0.01f;
        return Math.random() < hireChance;
    }

    private static boolean shouldPlantTree(Direction dir, int round) {
        if (!rc.canPlantTree(dir)) return false;
        // 0.05 probability for first 1000 rounds (as required by archetype)
        if (round < 1000) {
            return Math.random() < 0.05;
        }
        // Normal planting after round 1000
        return Math.random() < 0.02;
    }

    private static boolean shouldBuildSoldier(Direction dir, int round) {
        if (!rc.canBuildRobot(RobotType.SOLDIER, dir)) return false;
        // Build soldiers more aggressively if we have economy to support them
        float buildChance = (round < 1000) ? 0.005f : 0.01f;
        return Math.random() < buildChance;
    }

    private static float getDonateAmount(float reserve, int round) throws GameActionException {
        float bullets = rc.getTeamBullets();
        float cost = rc.getVictoryPointCost();
        
        // After round 2000, convert ALL excess bullets to VP (minimal reserve)
        float effectiveReserve = (round >= 2000) ? BULLET_RESERVE_LATE : reserve;
        
        float donateAmount = bullets - effectiveReserve;
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
