package hunter_alpha_champion_9;
import battlecode.common.*;

/**
 * Scout Tide - Centralized Bullet Spending (v4 mutation)
 *
 * Phase 1 (Rounds 1-150): Dense tree farm construction at 92% plant rate.
 *   Aggressive gardener hiring (25%) to maximize parallel tree planting.
 *   More trees = more shake targets for scout economy.
 * Phase 2 (Round 150+): Minimal tree planting (10%), focus on scout harassment.
 *   Unit priority: SOLDIER (45%), SCOUT (40%), LUMBERJACK (15%).
 *   Scouts move through trees at 1.25 stride, shake neutral trees for bullets,
 *   and disrupt enemy gardeners. No VP donations.
 */
public class BulletSpending {
    static RobotController rc;
    static final float BULLET_RESERVE = 200f;

    // Phase transition - Rapid Bloom: shortened eco to round 150
    static final int ECO_PHASE_END = 150;

    // Gardener hire rates
    static final double GARDENER_HIRE_RATE_ECO = 0.25;
    static final double GARDENER_HIRE_RATE_SWARM = 0.25;

    // Tree planting rates - dense farm during eco, minimal during swarm
    static final double TREE_PLANT_RATE_ECO = 0.92;
    static final double TREE_PLANT_RATE_SWARM = 0.10;

    // Scout Tide unit build rates (Phase 2 only) - Scout-heavy harassment
    static final double SOLDIER_BUILD_RATE = 0.45;
    static final double LUMBERJACK_BUILD_RATE = 0.15;
    static final double SCOUT_BUILD_RATE = 0.40;

    public static void init(RobotController rc) {
        BulletSpending.rc = rc;
    }

    public static void spendPolicy() throws GameActionException {
        int round = rc.getRoundNum();
        boolean isEcoPhase = round < ECO_PHASE_END;

        if (rc.getType() == RobotType.ARCHON) {
            Direction dir = randomDirection();
            double hireRate = isEcoPhase ? GARDENER_HIRE_RATE_ECO : GARDENER_HIRE_RATE_SWARM;
            if (rc.canHireGardener(dir) && Math.random() < hireRate) {
                rc.hireGardener(dir);
            }
            // No VP donations - all bullets feed the swarm
            return;
        }

        if (rc.getType() == RobotType.GARDENER) {
            // Plant a tree first if possible (for ongoing bullet income)
            Direction dir = randomDirection();
            double plantRate = isEcoPhase ? TREE_PLANT_RATE_ECO : TREE_PLANT_RATE_SWARM;
            if (rc.canPlantTree(dir) && Math.random() < plantRate) {
                rc.plantTree(dir);
                return; // Build cooldown after planting
            }

            // Phase 2: build soldiers, lumberjacks, and scouts - NO tanks
            if (!isEcoPhase) {
                // Priority: soldiers (100 bullets, good combat stats)
                dir = randomDirection();
                if (rc.canBuildRobot(RobotType.SOLDIER, dir) && Math.random() < SOLDIER_BUILD_RATE) {
                    rc.buildRobot(RobotType.SOLDIER, dir);
                    return;
                }
                // Timber support: lumberjacks (100 bullets, AOE strike, tree clearing)
                dir = randomDirection();
                if (rc.canBuildRobot(RobotType.LUMBERJACK, dir) && Math.random() < LUMBERJACK_BUILD_RATE) {
                    rc.buildRobot(RobotType.LUMBERJACK, dir);
                    return;
                }
                // Secondary: scouts (80 bullets, fast, harassment)
                dir = randomDirection();
                if (rc.canBuildRobot(RobotType.SCOUT, dir) && Math.random() < SCOUT_BUILD_RATE) {
                    rc.buildRobot(RobotType.SCOUT, dir);
                    return;
                }
            }
        }
    }

    private static Direction randomDirection() {
        return new Direction((float)Math.random() * 2 * (float)Math.PI);
    }
}
