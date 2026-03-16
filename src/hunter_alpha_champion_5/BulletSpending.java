package hunter_alpha_champion_5;
import battlecode.common.*;

/**
 * Swarm Blitz - Centralized Bullet Spending
 *
 * Phase 1 (Rounds 1-200): Build economy foundation - hire gardeners, plant trees.
 * Phase 2 (Round 200+): Rapid cheap unit spam - soldiers (40%) and scouts (20%).
 *   Zero tank production. Overwhelm the opponent with sheer unit count.
 *   No VP donations - all bullets feed the swarm.
 */
public class BulletSpending {
    static RobotController rc;
    static final float BULLET_RESERVE = 200f;

    // Phase transition - swarm begins early at round 200
    static final int ECO_PHASE_END = 200;

    // Gardener hire rates
    static final double GARDENER_HIRE_RATE_ECO = 0.20;
    static final double GARDENER_HIRE_RATE_SWARM = 0.10;

    // Tree planting rates
    static final double TREE_PLANT_RATE_ECO = 0.80;
    static final double TREE_PLANT_RATE_SWARM = 0.25;

    // Swarm unit build rates (Phase 2 only)
    static final double SOLDIER_BUILD_RATE = 0.40;
    static final double SCOUT_BUILD_RATE = 0.20;

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

            // Phase 2: build soldiers and scouts - NO tanks
            if (!isEcoPhase) {
                // Priority: soldiers (100 bullets, good combat stats)
                dir = randomDirection();
                if (rc.canBuildRobot(RobotType.SOLDIER, dir) && Math.random() < SOLDIER_BUILD_RATE) {
                    rc.buildRobot(RobotType.SOLDIER, dir);
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
