package claude_opus_4_5;
import battlecode.common.*;

/**
 * CRITICAL: This is the ONLY file that should spend bullets.
 * All hiring, building, planting, and donating happens here.
 * 
 * ARCHETYPE: Early Tank Rush
 * Philosophy: Rush to tanks ASAP - their 5 damage shots and 200 HP dominate combat.
 * Priority: TANK > GARDENER > LUMBERJACK (skip scouts, minimal soldiers)
 */
public strictfp class BulletSpending {
    static RobotController rc;
    
    // Costs
    private static final float GARDENER_COST = 100.0f;
    private static final float SOLDIER_COST = 100.0f;
    private static final float LUMBERJACK_COST = 100.0f;
    private static final float TANK_COST = 300.0f;
    private static final float TREE_COST = 50.0f;
    
    // Thresholds
    private static final int LATE_GAME_ROUND = 2500;
    private static final int EMERGENCY_ROUND = 2900;
    private static final int MAX_GARDENERS = 8;
    private static final int MAX_TREES_PER_GARDENER = 5;
    
    // Tank Rush Strategy Thresholds
    private static final int TANK_RUSH_START = 300;     // Start building tanks at round 300
    private static final int MAX_TANKS = 6;              // Increase tank cap from 3 to 6
    private static final int MAX_SOLDIERS = 2;           // Only 1-2 soldiers for early defense
    private static final int MAX_LUMBERJACKS = 1;        // Only 1 lumberjack for tree clearing
    
    public static void init(RobotController rc) {
        BulletSpending.rc = rc;
    }
    
    /**
     * Main spending policy - called by Archon and Gardener each turn.
     * Decides what to spend bullets on based on game state.
     */
    public static void spendPolicy() throws GameActionException {
        float bullets = rc.getTeamBullets();
        int round = rc.getRoundNum();
        int vpNeeded = GameConstants.VICTORY_POINTS_TO_WIN - rc.getTeamVictoryPoints();
        
        // 1. Check for instant VP win
        float bulletsForWin = vpNeeded * rc.getVictoryPointCost();
        if (bullets >= bulletsForWin) {
            rc.donate(bulletsForWin);
            return;
        }
        
        // 2. Emergency donation at game end
        if (round > EMERGENCY_ROUND && bullets > 50) {
            float donateAmount = bullets - 50;
            donateAmount = donateAmount - (donateAmount % rc.getVictoryPointCost());
            if (donateAmount >= rc.getVictoryPointCost()) {
                rc.donate(donateAmount);
                return;
            }
        }
        
        // 3. Late game VP donation (donate excess)
        if (round > LATE_GAME_ROUND && bullets > 500) {
            float excess = bullets - 400;
            excess = excess - (excess % rc.getVictoryPointCost());
            if (excess >= rc.getVictoryPointCost()) {
                rc.donate(excess);
                bullets = rc.getTeamBullets();
            }
        }
        
        // 4. Unit-specific spending
        RobotType myType = rc.getType();
        
        if (myType == RobotType.ARCHON) {
            archonSpend(bullets, round);
        } else if (myType == RobotType.GARDENER) {
            gardenerSpend(bullets, round);
        }
    }
    
    /**
     * Archon spending: hire Gardeners aggressively to enable tank production.
     */
    private static void archonSpend(float bullets, int round) throws GameActionException {
        int gardenerCount = Comms.getCount(Comms.GARDENER_COUNT);
        
        // Hire gardeners aggressively - we need them to produce tanks!
        if (gardenerCount < MAX_GARDENERS && bullets >= GARDENER_COST + 50) {
            // Try each direction
            for (int i = 0; i < 8; i++) {
                Direction dir = new Direction(i * (float)Math.PI / 4);
                if (rc.canHireGardener(dir)) {
                    rc.hireGardener(dir);
                    return;
                }
            }
        }
    }
    
    /**
     * Gardener spending: TANK RUSH STRATEGY
     * Priority: 1 lumberjack early, 1-2 soldiers for defense, then ALL-IN on tanks!
     * Skip scouts entirely.
     */
    private static void gardenerSpend(float bullets, int round) throws GameActionException {
        int soldierCount = Comms.getCount(Comms.SOLDIER_COUNT);
        int lumberjackCount = Comms.getCount(Comms.LUMBERJACK_COUNT);
        int tankCount = Comms.getCount(Comms.TANK_COUNT);
        int treeCount = countOurTrees();
        
        // ======================================================
        // TANK RUSH: After round 300, prioritize tanks above all else
        // ======================================================
        if (round >= TANK_RUSH_START) {
            // Build tanks as our primary combat unit - cap at 6
            if (tankCount < MAX_TANKS && bullets >= TANK_COST) {
                if (tryBuildRobot(RobotType.TANK)) {
                    return;
                }
            }
            
            // Supplement with a few soldiers if we have no tanks yet (early defense backup)
            if (tankCount == 0 && soldierCount < MAX_SOLDIERS && bullets >= SOLDIER_COST) {
                if (tryBuildRobot(RobotType.SOLDIER)) {
                    return;
                }
            }
            
            // Plant trees if we have surplus bullets after tank production
            if (treeCount < MAX_TREES_PER_GARDENER && bullets >= TANK_COST + TREE_COST) {
                if (tryPlantTree()) {
                    return;
                }
            }
            
            // If we can't build tanks, build more tanks anyway (save up)
            // No fallback to soldiers - save bullets for tanks
            return;
        }
        
        // ======================================================
        // EARLY GAME (before round 300): Minimal army, focus on economy
        // ======================================================
        
        // Skip scouts entirely - no scout production
        
        // Build exactly 1 lumberjack for tree clearing
        if (lumberjackCount < MAX_LUMBERJACKS && bullets >= LUMBERJACK_COST) {
            if (tryBuildRobot(RobotType.LUMBERJACK)) {
                return;
            }
        }
        
        // Build 1-2 soldiers for early defense only
        if (soldierCount < MAX_SOLDIERS && bullets >= SOLDIER_COST) {
            if (tryBuildRobot(RobotType.SOLDIER)) {
                return;
            }
        }
        
        // Plant trees for economy - but don't over-invest before tank rush
        if (treeCount < 3 && bullets >= TREE_COST + 50) {
            if (tryPlantTree()) {
                return;
            }
        }
        
        // Save bullets for tank production starting at round 300!
    }
    
    /**
     * Try to build a robot in any available direction.
     */
    private static boolean tryBuildRobot(RobotType type) throws GameActionException {
        for (int i = 0; i < 8; i++) {
            Direction dir = new Direction(i * (float)Math.PI / 4);
            if (rc.canBuildRobot(type, dir)) {
                rc.buildRobot(type, dir);
                return true;
            }
        }
        return false;
    }
    
    /**
     * Try to plant a tree in any available direction.
     */
    private static boolean tryPlantTree() throws GameActionException {
        for (int i = 0; i < 6; i++) {
            Direction dir = new Direction(i * (float)Math.PI / 3);
            if (rc.canPlantTree(dir)) {
                rc.plantTree(dir);
                return true;
            }
        }
        return false;
    }
    
    /**
     * Count trees owned by our team nearby.
     */
    private static int countOurTrees() throws GameActionException {
        TreeInfo[] trees = rc.senseNearbyTrees(3.0f, rc.getTeam());
        return trees.length;
    }
}
