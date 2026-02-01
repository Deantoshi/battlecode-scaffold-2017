package kimi_k2_5_champion_3;
import battlecode.common.*;

public class BulletSpending {
    static RobotController rc;
    // Bullet Starvation: Moderate reserves to maintain production while controlling map
    static final float BULLET_RESERVE_EARLY = 100f;   // Moderate reserve for early expansion
    static final float BULLET_RESERVE_MID = 70f;      // Lower mid-game for map control units
    static final float BULLET_RESERVE_LATE = 40f;     // Minimal late-game for elimination push

    public static void init(RobotController rc) {
        BulletSpending.rc = rc;
    }

    public static void spendPolicy() throws GameActionException {
        int round = rc.getRoundNum();
        float reserve = getReserveForRound(round);

        // Centralized spend order based on game phase
        if (rc.getType() == RobotType.ARCHON) {
            Direction dir = randomDirection();
            // Hire gardeners at 0.022f to spread across map
            if (shouldHireGardener(dir, round)) {
                rc.hireGardener(dir);
            }
            // No VP donations in elimination mode - focus on map control
            return;
        }
        if (rc.getType() == RobotType.GARDENER) {
            // Plant trees at 0.025f to secure own bullet income
            Direction dir = randomDirection();
            if (shouldPlantTree(dir, round)) {
                rc.plantTree(dir);
            }
            // Build scouts at 0.03f - HIGHEST priority for map control and shaking trees
            dir = randomDirection();
            if (shouldBuildScout(dir, round)) {
                rc.buildRobot(RobotType.SCOUT, dir);
            }
            // Build lumberjacks at 0.015f - harvest neutral trees before enemy
            dir = randomDirection();
            if (shouldBuildLumberjack(dir, round)) {
                rc.buildRobot(RobotType.LUMBERJACK, dir);
            }
            // Build soldiers at 0.02f - secure controlled areas
            dir = randomDirection();
            if (shouldBuildSoldier(dir, round)) {
                rc.buildRobot(RobotType.SOLDIER, dir);
            }
            // No VP donations - pure elimination strategy
        }
    }

    private static float getReserveForRound(int round) {
        if (round < 1000) {
            return BULLET_RESERVE_EARLY;  // 100f - moderate early reserve
        } else if (round < 2000) {
            return BULLET_RESERVE_MID;    // 70f - enable map control units
        } else {
            return BULLET_RESERVE_LATE;   // 40f - minimal for elimination push
        }
    }

    private static boolean shouldHireGardener(Direction dir, int round) {
        if (!rc.canHireGardener(dir)) return false;
        // Hire gardeners at 0.022f to spread across map
        float hireChance = (round < 600) ? 0.022f : 0.012f;
        return Math.random() < hireChance;
    }

    private static boolean shouldPlantTree(Direction dir, int round) {
        if (!rc.canPlantTree(dir)) return false;
        // Plant trees at 0.025f throughout the game
        return Math.random() < 0.025f;
    }

    private static boolean shouldBuildScout(Direction dir, int round) {
        if (!rc.canBuildRobot(RobotType.SCOUT, dir)) return false;
        // Build scouts at 0.03f - HIGH priority for map control and shaking trees
        return Math.random() < 0.03f;
    }

    private static boolean shouldBuildLumberjack(Direction dir, int round) {
        if (!rc.canBuildRobot(RobotType.LUMBERJACK, dir)) return false;
        // Build lumberjacks at 0.015f - harvest trees before enemy
        return Math.random() < 0.015f;
    }

    private static boolean shouldBuildSoldier(Direction dir, int round) {
        if (!rc.canBuildRobot(RobotType.SOLDIER, dir)) return false;
        // Build soldiers at 0.02f - secure controlled areas
        return Math.random() < 0.02f;
    }

    private static Direction randomDirection() {
        return new Direction((float)Math.random() * 2 * (float)Math.PI);
    }
}
