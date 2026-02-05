package claude_opus_4_5;
import battlecode.common.*;

/**
 * CRITICAL: This is the ONLY file that should spend bullets.
 * All hiring, building, planting, and donating happens here.
 */
public strictfp class BulletSpending {
    static RobotController rc;
    
    // Costs
    private static final float GARDENER_COST = 100.0f;
    private static final float SOLDIER_COST = 100.0f;
    private static final float LUMBERJACK_COST = 100.0f;
    private static final float SCOUT_COST = 80.0f;
    private static final float TANK_COST = 300.0f;
    private static final float TREE_COST = 50.0f;
    
    // Thresholds
    private static final int LATE_GAME_ROUND = 2500;
    private static final int EMERGENCY_ROUND = 2900;
    private static final int MAX_GARDENERS = 8;
    private static final int MAX_TREES_PER_GARDENER = 5;
    
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
     * Archon spending: hire Gardeners.
     */
    private static void archonSpend(float bullets, int round) throws GameActionException {
        int gardenerCount = Comms.getCount(Comms.GARDENER_COUNT);
        
        // Hire gardeners if we need them and can afford
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
     * Gardener spending: build units and plant trees.
     */
    private static void gardenerSpend(float bullets, int round) throws GameActionException {
        int scoutCount = Comms.getCount(Comms.SCOUT_COUNT);
        int soldierCount = Comms.getCount(Comms.SOLDIER_COUNT);
        int lumberjackCount = Comms.getCount(Comms.LUMBERJACK_COUNT);
        int tankCount = Comms.getCount(Comms.TANK_COUNT);
        int treeCount = countOurTrees();
        
        // Early game: build a scout first for recon
        if (round < 200 && scoutCount < 1 && bullets >= SCOUT_COST) {
            if (tryBuildRobot(RobotType.SCOUT)) {
                return;
            }
        }
        
        // Early game: need lumberjacks to clear trees
        if (round < 500 && lumberjackCount < 2 && bullets >= LUMBERJACK_COST) {
            if (tryBuildRobot(RobotType.LUMBERJACK)) {
                return;
            }
        }
        
        // Plant trees for economy (up to 5 per gardener)
        if (treeCount < MAX_TREES_PER_GARDENER && bullets >= TREE_COST + 50) {
            if (tryPlantTree()) {
                return;
            }
        }
        
        // Mid game: build soldiers
        if (round >= 200 && round < 1500) {
            if (soldierCount < 5 && bullets >= SOLDIER_COST) {
                if (tryBuildRobot(RobotType.SOLDIER)) {
                    return;
                }
            }
        }
        
        // Late game: build tanks and more soldiers
        if (round >= 1500) {
            if (tankCount < 3 && bullets >= TANK_COST) {
                if (tryBuildRobot(RobotType.TANK)) {
                    return;
                }
            }
            if (soldierCount < 8 && bullets >= SOLDIER_COST) {
                if (tryBuildRobot(RobotType.SOLDIER)) {
                    return;
                }
            }
        }
        
        // Extra soldiers if we have excess bullets
        if (bullets > 300 && soldierCount < 10) {
            if (tryBuildRobot(RobotType.SOLDIER)) {
                return;
            }
        }
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
