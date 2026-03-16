package hunter_alpha_champion_2;
import battlecode.common.*;

public class BulletSpending {
    static RobotController rc;
    static final float BULLET_RESERVE = 300f;

    // Broadcast channels for enemy composition intel
    static final int CH_ENEMY_SOLDIER_COUNT = 10;
    static final int CH_ENEMY_TANK_COUNT = 11;
    static final int CH_ENEMY_LUMBERJACK_COUNT = 12;
    static final int CH_ENEMY_SCOUT_COUNT = 13;
    static final int CH_SCOUT_COUNT = 14;
    static final int CH_INTEL_FRESH = 15;

    // Track last build target switch round
    static int lastSwitchRound = 0;
    static RobotType currentBuildTarget = RobotType.SOLDIER;

    public static void init(RobotController rc) {
        BulletSpending.rc = rc;
        lastSwitchRound = 0;
        currentBuildTarget = RobotType.SOLDIER;
    }

    public static void spendPolicy() throws GameActionException {
        if (rc.getType() == RobotType.ARCHON) {
            // Hire gardeners
            Direction dir = randomDirection();
            if (shouldHireGardener(dir)) {
                rc.hireGardener(dir);
            }
            // Donate excess bullets for VP
            float donateAmount = getDonateAmount();
            if (donateAmount > 0f) {
                rc.donate(donateAmount);
            }
            return;
        }
        if (rc.getType() == RobotType.GARDENER) {
            // Plant trees
            Direction dir = randomDirection();
            if (shouldPlantTree(dir)) {
                rc.plantTree(dir);
            }

            // Update build target every 50 rounds based on intel
            int round = rc.getRoundNum();
            if (round - lastSwitchRound >= 50) {
                lastSwitchRound = round;
                currentBuildTarget = getCounterUnit();
            }

            // Build scouts to maintain minimum 2 for intel
            int scoutCount = rc.readBroadcast(CH_SCOUT_COUNT);
            dir = randomDirection();
            if (scoutCount < 2 && rc.canBuildRobot(RobotType.SCOUT, dir)) {
                rc.buildRobot(RobotType.SCOUT, dir);
            } else {
                // Build counter unit
                dir = randomDirection();
                if (shouldBuildCounterUnit(dir)) {
                    rc.buildRobot(currentBuildTarget, dir);
                }
            }

            // Donate excess bullets for VP
            float donateAmount = getDonateAmount();
            if (donateAmount > 0f) {
                rc.donate(donateAmount);
            }
        }
    }

    /**
     * Determine the best counter unit based on enemy composition intel.
     * Counter matrix:
     *   vs Soldiers -> build Tanks
     *   vs Tanks -> build Lumberjacks
     *   vs Lumberjacks -> build Soldiers
     *   vs Scouts -> build Soldiers
     *   Fallback to balanced build (Soldier) if no intel available.
     */
    static RobotType getCounterUnit() throws GameActionException {
        int intelFresh = rc.readBroadcast(CH_INTEL_FRESH);
        if (intelFresh == 0) {
            // No scouts providing intel, fallback to balanced Soldier
            return RobotType.SOLDIER;
        }

        int soldierCount = rc.readBroadcast(CH_ENEMY_SOLDIER_COUNT);
        int tankCount = rc.readBroadcast(CH_ENEMY_TANK_COUNT);
        int lumberjackCount = rc.readBroadcast(CH_ENEMY_LUMBERJACK_COUNT);
        int scoutCount = rc.readBroadcast(CH_ENEMY_SCOUT_COUNT);

        // Find the most numerous enemy type
        int maxCount = soldierCount;
        RobotType dominantEnemy = RobotType.SOLDIER;

        if (tankCount > maxCount) {
            maxCount = tankCount;
            dominantEnemy = RobotType.TANK;
        }
        if (lumberjackCount > maxCount) {
            maxCount = lumberjackCount;
            dominantEnemy = RobotType.LUMBERJACK;
        }
        if (scoutCount > maxCount) {
            maxCount = scoutCount;
            dominantEnemy = RobotType.SCOUT;
        }

        // If no enemies detected, build balanced force
        if (maxCount == 0) {
            return RobotType.SOLDIER;
        }

        // Counter matrix
        if (dominantEnemy == RobotType.SOLDIER) {
            return RobotType.TANK;       // Tanks crush soldiers
        } else if (dominantEnemy == RobotType.TANK) {
            return RobotType.LUMBERJACK; // Lumberjacks chop tanks
        } else if (dominantEnemy == RobotType.LUMBERJACK) {
            return RobotType.SOLDIER;    // Soldiers kite lumberjacks
        } else {
            return RobotType.SOLDIER;    // vs Scouts, build Soldiers
        }
    }

    private static boolean shouldHireGardener(Direction dir) {
        return rc.canHireGardener(dir) && Math.random() < .06;
    }

    private static boolean shouldPlantTree(Direction dir) {
        return rc.canPlantTree(dir) && Math.random() < .20;
    }

    private static boolean shouldBuildCounterUnit(Direction dir) {
        return rc.canBuildRobot(currentBuildTarget, dir) && Math.random() < .08;
    }

    private static float getDonateAmount() throws GameActionException {
        // Begin VP donation only after round 600
        if (rc.getRoundNum() < 600) {
            return 0f;
        }
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
