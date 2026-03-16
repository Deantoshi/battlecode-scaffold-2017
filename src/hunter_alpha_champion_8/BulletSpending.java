package hunter_alpha_champion_8;
import battlecode.common.*;

/**
 * Timber Support - Centralized Bullet Spending (v5 mutation)
 *
 * Phase 1 (Rounds 1-150): Dense tree farm construction at 90% plant rate.
 *   Aggressive gardener hiring (25%) to maximize parallel tree planting.
 *   Front-loaded economy generates massive bullet income for military pivot.
 * Phase 2 (Round 150+): Minimal tree planting (10%), focus on military output.
 *   Unit priority: SOLDIER (55%), LUMBERJACK (20%), SCOUT (25%).
 *   Lumberjacks clear neutral trees for instant bullet income and provide
 *   devastating AOE strike in mixed engagements. No VP donations.
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
    static final double TREE_PLANT_RATE_ECO = 0.90;
    static final double TREE_PLANT_RATE_SWARM = 0.10;

    // Swarm unit build rates (Phase 2 only) - Mixed assault with lumberjack support
    static final double SOLDIER_BUILD_RATE = 0.55;
    static final double LUMBERJACK_BUILD_RATE = 0.20;
    static final double SCOUT_BUILD_RATE = 0.25;

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
