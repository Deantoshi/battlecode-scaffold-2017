package hunter_alpha_champion_4;
import battlecode.common.*;

/**
 * Pure Greed Extremist - Centralized Bullet Spending
 * 
 * Phase 1 (Rounds 1-1000): Maximum greed - aggressive gardener hiring and tree planting,
 *   zero military production. Bet everything on economic supremacy.
 * Phase 2 (Round 1000+): Convert massive bullet surplus.
 *   - If team bullets > 1500: VP rush mode (donate 50% of surplus)
 *   - If team bullets < 1500 or enemies nearby: 50% VP donation, 50% tank production
 */
public class BulletSpending {
    static RobotController rc;
    static final float BULLET_RESERVE = 300f;
    
    // Phase transition round - Extended for maximum greed
    static final int GREED_PHASE_END = 1000;
    
    // Phase 2 thresholds
    static final float VP_RUSH_THRESHOLD = 1500f;
    static final float VP_DONATE_RATIO = 0.5f;
    
    // Phase 1 rates - Maximum economic focus
    static final double GARDENER_HIRE_RATE = 0.20;
    static final double TREE_PLANT_RATE = 0.85;
    
    // Phase 2 rates - Balanced VP and tank production
    static final double GARDENER_HIRE_RATE_PHASE2 = 0.08;
    static final double TANK_BUILD_RATE = 0.15;
    
    // Track current phase mode
    static boolean vpRushMode = false;
    static boolean phase2Initialized = false;

    public static void init(RobotController rc) {
        BulletSpending.rc = rc;
        vpRushMode = false;
        phase2Initialized = false;
    }

    public static void spendPolicy() throws GameActionException {
        int round = rc.getRoundNum();
        boolean isPhase1 = round < GREED_PHASE_END;
        
        if (rc.getType() == RobotType.ARCHON) {
            if (isPhase1) {
                // Phase 1: Only hire gardeners, no donations
                Direction dir = randomDirection();
                if (shouldHireGardenerPhase1(dir)) {
                    rc.hireGardener(dir);
                }
                // NO donations in phase 1 - save all bullets for economy
            } else {
                // Phase 2: Evaluate game state on first round of phase 2
                if (!phase2Initialized) {
                    phase2Initialized = true;
                    float bullets = rc.getTeamBullets();
                    // Check for nearby enemies to decide pivot
                    RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
                    if (bullets >= VP_RUSH_THRESHOLD && enemies.length == 0) {
                        vpRushMode = true;
                    } else {
                        vpRushMode = false;
                    }
                }
                
                if (vpRushMode) {
                    // VP Rush mode: donate 70% of surplus bullets
                    float bullets = rc.getTeamBullets();
                    float surplus = bullets - BULLET_RESERVE;
                    if (surplus > 0) {
                        float donateAmount = surplus * VP_DONATE_RATIO;
                        float cost = rc.getVictoryPointCost();
                        int pointsToBuy = (int)(donateAmount / cost);
                        if (pointsToBuy > 0) {
                            rc.donate(pointsToBuy * cost);
                        }
                    }
                    // Still hire some gardeners to maintain economy
                    Direction dir = randomDirection();
                    if (shouldHireGardenerPhase2(dir)) {
                        rc.hireGardener(dir);
                    }
                } else {
                    // Phase 2: 50% VP donation, 50% tank production from surplus
                    Direction dir = randomDirection();
                    if (shouldHireGardenerPhase2(dir)) {
                        rc.hireGardener(dir);
                    }
                    // Split surplus between VP and economy
                    float bullets = rc.getTeamBullets();
                    float surplus = bullets - BULLET_RESERVE;
                    if (surplus > 0) {
                        float donateAmount = surplus * VP_DONATE_RATIO;
                        float cost = rc.getVictoryPointCost();
                        int pointsToBuy = (int)(donateAmount / cost);
                        if (pointsToBuy > 0) {
                            rc.donate(pointsToBuy * cost);
                        }
                    }
                }
            }
            return;
        }
        
        if (rc.getType() == RobotType.GARDENER) {
            if (isPhase1) {
                // Phase 1: Plant trees aggressively, NO military production
                Direction dir = randomDirection();
                if (shouldPlantTreePhase1(dir)) {
                    rc.plantTree(dir);
                }
                // No robot building in phase 1 - pure economy
            } else {
                // Phase 2: Pivot based on mode
                if (vpRushMode) {
                    // VP Rush: continue planting trees for income, minimal military
                    Direction dir = randomDirection();
                    if (shouldPlantTreePhase2(dir)) {
                        rc.plantTree(dir);
                    }
                    // Build minimal scouts for scouting only
                    dir = randomDirection();
                    if (rc.canBuildRobot(RobotType.SCOUT, dir) && Math.random() < 0.03) {
                        rc.buildRobot(RobotType.SCOUT, dir);
                    }
                } else {
                    // Tank army mode: shift production to tanks
                    Direction dir = randomDirection();
                    // Still plant some trees for income
                    if (shouldPlantTreePhase2(dir)) {
                        rc.plantTree(dir);
                    }
                    // Build tanks
                    dir = randomDirection();
                    if (shouldBuildTank(dir)) {
                        rc.buildRobot(RobotType.TANK, dir);
                    }
                }
            }
        }
    }

    // Phase 1 methods
    private static boolean shouldHireGardenerPhase1(Direction dir) {
        return rc.canHireGardener(dir) && Math.random() < GARDENER_HIRE_RATE;
    }

    private static boolean shouldPlantTreePhase1(Direction dir) {
        return rc.canPlantTree(dir) && Math.random() < TREE_PLANT_RATE;
    }

    // Phase 2 methods
    private static boolean shouldHireGardenerPhase2(Direction dir) {
        return rc.canHireGardener(dir) && Math.random() < GARDENER_HIRE_RATE_PHASE2;
    }

    private static boolean shouldPlantTreePhase2(Direction dir) {
        return rc.canPlantTree(dir) && Math.random() < 0.30;
    }

    private static boolean shouldBuildTank(Direction dir) {
        return rc.canBuildRobot(RobotType.TANK, dir) && Math.random() < TANK_BUILD_RATE;
    }

    private static float getDonateAmount() throws GameActionException {
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
