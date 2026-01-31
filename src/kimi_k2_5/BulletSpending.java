package kimi_k2_5;
import battlecode.common.*;

public class BulletSpending {
    static RobotController rc;
    // Dynamic reserve based on game phase - LOWERED for aggressive unit production (Commando Strike)
    static final float BULLET_RESERVE_EARLY = 80f;   // Lowered from 250f for aggressive production
    static final float BULLET_RESERVE_LATE = 50f;    // After round 2000
    static final float BULLET_RESERVE_MID = 100f;    // Round 1000-2000 (lowered for mid-game pushes)

    public static void init(RobotController rc) {
        BulletSpending.rc = rc;
    }

    public static void spendPolicy() throws GameActionException {
        int round = rc.getRoundNum();
        float reserve = getReserveForRound(round);

        // Centralized spend order based on game phase
        if (rc.getType() == RobotType.ARCHON) {
            Direction dir = randomDirection();
            // Hire gardeners aggressively early for economy
            if (shouldHireGardener(dir, round)) {
                rc.hireGardener(dir);
            }
            // VP donations: Minimal - focus on elimination via units
            if (round >= 2500) {
                float donateAmount = getDonateAmount(reserve, round);
                if (donateAmount > 0f) {
                    rc.donate(donateAmount);
                }
            }
            return;
        }
        if (rc.getType() == RobotType.GARDENER) {
            // Plant trees at lower priority - focus on units for Commando Strike
            Direction dir = randomDirection();
            if (shouldPlantTree(dir, round)) {
                rc.plantTree(dir);
            }
            // Build SCOUTS and SOLDIERS equally (0.015f each as per archetype)
            dir = randomDirection();
            if (shouldBuildScout(dir, round)) {
                rc.buildRobot(RobotType.SCOUT, dir);
            }
            dir = randomDirection();
            if (shouldBuildSoldier(dir, round)) {
                rc.buildRobot(RobotType.SOLDIER, dir);
            }
            // Minimal VP donations from gardeners (focus on elimination)
            if (round >= 2500) {
                float donateAmount = getDonateAmount(reserve, round);
                if (donateAmount > 0f) {
                    rc.donate(donateAmount);
                }
            }
        }
    }

    private static float getReserveForRound(int round) {
        if (round < 1000) {
            return BULLET_RESERVE_EARLY;  // 80f - aggressive early unit production
        } else if (round < 2000) {
            return BULLET_RESERVE_MID;    // 100f
        } else {
            return BULLET_RESERVE_LATE;   // 50f
        }
    }

    private static boolean shouldHireGardener(Direction dir, int round) {
        if (!rc.canHireGardener(dir)) return false;
        // Aggressive hiring early to build production capacity
        float hireChance = (round < 500) ? 0.03f : 0.015f;
        return Math.random() < hireChance;
    }

    private static boolean shouldPlantTree(Direction dir, int round) {
        if (!rc.canPlantTree(dir)) return false;
        // Lower tree planting priority - focus on units for strike force
        if (round < 1000) {
            return Math.random() < 0.02;
        }
        // Even lower after round 1000
        return Math.random() < 0.01;
    }

    private static boolean shouldBuildScout(Direction dir, int round) {
        if (!rc.canBuildRobot(RobotType.SCOUT, dir)) return false;
        // Build scouts at 0.015f probability as per archetype
        return Math.random() < 0.015f;
    }

    private static boolean shouldBuildSoldier(Direction dir, int round) {
        if (!rc.canBuildRobot(RobotType.SOLDIER, dir)) return false;
        // Build soldiers at 0.015f probability as per archetype
        return Math.random() < 0.015f;
    }

    private static float getDonateAmount(float reserve, int round) throws GameActionException {
        float bullets = rc.getTeamBullets();
        float cost = rc.getVictoryPointCost();
        
        // After round 2500, convert excess bullets to VP (only if not focusing on elimination)
        float effectiveReserve = (round >= 2500) ? BULLET_RESERVE_LATE : reserve;
        
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
