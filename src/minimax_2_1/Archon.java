package minimax_2_1;
import battlecode.common.*;

public strictfp class Archon {
    static RobotController rc;
    static int lumberjackSpawnRound = -1;  // Track when to spawn lumberjack
    static boolean treeDensityDetected = false;
    static boolean shrineMapDetected = false;
    static boolean isTrapped = false;
    static int trappedTurns = 0;
    
    // Strategic thresholds (from strategic analysis)
    static final int SOLDIER_THRESHOLD_FOR_VP = 30;  // Need 30+ soldiers before aggressive VP
    static final int VP_DONATION_ROUND_THRESHOLD = 500;  // Or round 500, whichever comes first
    static final int ENEMY_SOLDIER_DEFENSE_THRESHOLD = 30;  // If enemy has 30+, switch to VP defense
    static final int TREE_DENSITY_THRESHOLD = 8;  // Trees nearby to trigger lumberjack priority
    static final int SHRINE_LUMBERJACK_DELAY = 20;  // Spawn lumberjacks within 20 rounds on Shrine
    static final int MAX_TRAPPED_TURNS = 5;  // After 5 trapped turns, call for help

    public static void run(RobotController rc) throws GameActionException {
        Archon.rc = rc;
        Nav.init(rc);
        Comms.init(rc);

        while (true) {
            try {
                doTurn();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                Clock.yield();
            }
        }
    }

    static void doTurn() throws GameActionException {
        Comms.broadcastLocation(0, 1, rc.getLocation());

        int round = rc.getRoundNum();
        float bullets = rc.getTeamBullets();
        
        // Detect tree density in early game
        int nearbyTreeCount = countNearbyTrees();
        if (!treeDensityDetected && round < 50 && nearbyTreeCount > TREE_DENSITY_THRESHOLD) {
            treeDensityDetected = true;
            // On tree-dense maps, spawn lumberjack sooner
            lumberjackSpawnRound = round + SHRINE_LUMBERJACK_DELAY;
        }
        
        // Count allies
        RobotInfo[] nearbyAllies = rc.senseNearbyRobots(-1, rc.getTeam());
        int soldierCount = 0;
        int lumberjackCount = 0;
        for (RobotInfo ally : nearbyAllies) {
            if (ally.type == RobotType.SOLDIER || ally.type == RobotType.LUMBERJACK || 
                ally.type == RobotType.TANK || ally.type == RobotType.SCOUT) {
                soldierCount++;
            }
            if (ally.type == RobotType.LUMBERJACK) {
                lumberjackCount++;
            }
        }
        
        // Count enemy soldiers for defense threshold
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        int enemySoldierCount = 0;
        for (RobotInfo enemy : enemies) {
            if (enemy.type == RobotType.SOLDIER || enemy.type == RobotType.LUMBERJACK || 
                enemy.type == RobotType.TANK || enemy.type == RobotType.SCOUT) {
                enemySoldierCount++;
            }
        }
        
        // AGGRESSIVE BULLET SPENDING: Never hoard more than 300
        
        if (bullets > 300) {
            float excess = bullets - 300;
            
            // Priority 1: Donate excess bullets for VPs (but ONLY after meeting army threshold)
            // FIX: Delay VP donation until we have sufficient army
            boolean shouldDonateVP = shouldStartVPDonation(round, soldierCount, enemySoldierCount);
            if (shouldDonateVP) {
                rc.donate(excess);
                bullets = rc.getTeamBullets();
            }
            
            // Priority 2: Spend excess on combat units - AGGRESSIVE BUILDING
            for (int i = 0; i < 8; i++) {
                Direction dir = new Direction(i * (float)Math.PI / 4);
                int cost = RobotType.SOLDIER.bulletCost;
                if (rc.canBuildRobot(RobotType.SOLDIER, dir) && bullets >= cost) {
                    rc.buildRobot(RobotType.SOLDIER, dir);
                    bullets -= cost;
                    // Break after successful build - prevents waste
                    break;
                }
            }
        }

        // VP DONATION: Strategic timing based on army strength
        // FIX: Start aggressive donations AFTER we have 30+ soldiers OR round 500
        // Exception: If enemy has 30+ soldiers, we need VP defense
        if (round > VP_DONATION_ROUND_THRESHOLD && rc.getTeamVictoryPoints() < 900) {
            // Check if we should start VP donations
            boolean shouldDonate = shouldStartVPDonation(round, soldierCount, enemySoldierCount);
            
            if (shouldDonate) {
                int vpsNeeded = 900 - rc.getTeamVictoryPoints();
                // Donate 150 bullets at a time for VPs if we have excess
                if (bullets > 300 && vpsNeeded > 0) {
                    float donateAmount = Math.min(150f, bullets - 300);
                    rc.donate(donateAmount);
                }
            }
        }
        
        // TREE-AWARE MOVEMENT: Escape tree clusters and detect if trapped
        handleTreeAwareMovement(enemies, nearbyTreeCount);
        
        // PRIORITY: Build lumberjack on tree-dense maps (Shrine-specific fix)
        // FIX: Lumberjack-first strategy when tree density is high
        boolean needLumberjack = treeDensityDetected && lumberjackCount == 0 && 
                                  lumberjackSpawnRound != -1 && round >= lumberjackSpawnRound;
        
        // Also prioritize lumberjack if we're on Shrine or tree-dense map
        if (needLumberjack || (round < 100 && nearbyTreeCount > 12)) {
            for (int i = 0; i < 8; i++) {
                Direction dir = new Direction(i * (float)Math.PI / 4);
                if (rc.canBuildRobot(RobotType.LUMBERJACK, dir)) {
                    rc.buildRobot(RobotType.LUMBERJACK, dir);
                    lumberjackCount++;
                    break;
                }
            }
        }

        // Normal unit building - AGGRESSIVE MULTI-SPAWN
        // FIX: Prioritize soldiers to reach 50+ before aggressive VP donation
        for (int spawnCount = 0; spawnCount < 3; spawnCount++) {  // Try up to 3 builds/turn
            boolean built = false;
            for (int i = 0; i < 8; i++) {
                Direction dir = new Direction(i * (float)Math.PI / 4);
                if (rc.canBuildRobot(RobotType.SOLDIER, dir)) {
                    rc.buildRobot(RobotType.SOLDIER, dir);
                    built = true;
                    soldierCount++;
                    break;  // Found a valid spot, exit inner loop
                }
            }
            if (!built) break;  // No valid spots left, exit outer loop
        }
        
        // After round 300, start hiring gardeners (but not before!)
        int gardenerCount = Comms.countFriendlyGardeners();
        
        // FIX: Fewer gardeners on tree-dense maps (Bullseye/Blitzkrieg fix)
        int maxGardeners = getMaxGardeners(treeDensityDetected, round);
        
        // Only hire gardeners after round 100 and we have some soldiers
        // Soldiers are critical for survival in early game
        if (round > 100 && gardenerCount < 2 && rc.getTeamBullets() >= 100) {
            tryHireGardener();
        }

        if (rc.getTeamBullets() >= 100 && round > 200 && gardenerCount < Math.min(4, maxGardeners)) {
            tryHireGardener();
        }

        if (rc.getTeamBullets() >= 100 && round > 400 && gardenerCount < Math.min(6, maxGardeners)) {
            tryHireGardener();
        }

        if (!rc.hasMoved()) {
            // Use tree-aware movement even when not explicitly escaping
            MapLocation escapeDir = findEscapeDirection(nearbyTreeCount);
            if (escapeDir != null) {
                Nav.tryMove(rc.getLocation().directionTo(escapeDir));
            } else {
                Nav.tryMove(Nav.randomDirection());
            }
        }
    }
    
    /**
     * FIX: Strategic VP donation timing
     * Returns true if we should start aggressive VP donations
     */
    static boolean shouldStartVPDonation(int round, int soldierCount, int enemySoldierCount) {
        // Start VP donations if:
        // 1. We have 30+ soldiers (army threshold met), OR
        // 2. Round 500+ (late game), OR
        // 3. Enemy has 30+ soldiers (need VP defense)
        return round >= VP_DONATION_ROUND_THRESHOLD || 
               soldierCount >= SOLDIER_THRESHOLD_FOR_VP ||
               enemySoldierCount >= ENEMY_SOLDIER_DEFENSE_THRESHOLD;
    }
    
    /**
     * FIX: Tree-aware Archon movement
     * Escapes tree clusters and detects if trapped
     */
    static void handleTreeAwareMovement(RobotInfo[] enemies, int nearbyTreeCount) throws GameActionException {
        // If enemies are nearby, escape from them (original logic)
        if (enemies.length > 0) {
            RobotInfo closest = Utils.findClosestEnemy(rc, enemies);
            Direction away = rc.getLocation().directionTo(closest.location).opposite();
            Nav.tryMove(away);
            return;
        }
        
        // If tree density is high and we're not moving, try to escape
        if (nearbyTreeCount > TREE_DENSITY_THRESHOLD && !rc.hasMoved()) {
            MapLocation escapeDir = findEscapeDirection(nearbyTreeCount);
            if (escapeDir != null) {
                Nav.tryMove(rc.getLocation().directionTo(escapeDir));
            }
        }
    }
    
    /**
     * Find direction to escape tree cluster
     */
    static MapLocation findEscapeDirection(int nearbyTreeCount) throws GameActionException {
        if (nearbyTreeCount <= TREE_DENSITY_THRESHOLD) {
            return null;
        }
        
        // Scan all 8 directions to find one with fewest trees
        MapLocation myLoc = rc.getLocation();
        MapLocation bestEscape = null;
        int bestTreeCount = Integer.MAX_VALUE;
        
        for (int i = 0; i < 8; i++) {
            Direction dir = new Direction(i * (float)Math.PI / 4);
            MapLocation checkLoc = myLoc.add(dir, 5.0f);  // Look 5 units ahead
            
            // Count trees in that direction
            TreeInfo[] trees = rc.senseNearbyTrees(myLoc, 10.0f, Team.NEUTRAL);
            int treeCountInDir = 0;
            for (TreeInfo tree : trees) {
                if (myLoc.directionTo(tree.location).equals(dir)) {
                    treeCountInDir++;
                }
            }
            
            if (treeCountInDir < bestTreeCount) {
                bestTreeCount = treeCountInDir;
                bestEscape = checkLoc;
            }
        }
        
        return bestEscape;
    }
    
    /**
     * FIX: Reduce gardener count on tree-dense maps
     * Bullseye/Blitzkrieg fix - fewer trees means fewer gardeners needed
     */
    static int getMaxGardeners(boolean treeDense, int round) {
        if (treeDense || round < 200) {
            // On tree-dense maps, limit gardeners to prevent over-planting
            return 4;
        } else {
            return 6;
        }
    }

    static int countNearbyGardeners() throws GameActionException {
        RobotInfo[] robots = rc.senseNearbyRobots(20, rc.getTeam());
        int count = 0;
        for (RobotInfo robot : robots) {
            if (robot.type == RobotType.GARDENER) {
                count++;
            }
        }
        return count;
    }

    static int countNearbyTrees() throws GameActionException {
        TreeInfo[] trees = rc.senseNearbyTrees(-1, Team.NEUTRAL);
        return trees.length;
    }

    static boolean tryHireGardener() throws GameActionException {
        for (int i = 0; i < 8; i++) {
            Direction dir = new Direction(i * (float)Math.PI / 4);
            if (rc.canHireGardener(dir)) {
                rc.hireGardener(dir);
                return true;
            }
        }
        return false;
    }
}
