package copy_bot;
import battlecode.common.*;

/**
 * CRITICAL: This is the ONLY file that may call:
 * - rc.donate()
 * - rc.hireGardener()
 * - rc.buildRobot()
 * - rc.plantTree()
 * 
 * All bullet spending decisions are centralized here.
 * 
 * BALANCED ARMY COMPOSITION STRATEGY:
 * - Produce equal numbers of scouts, soldiers, and lumberjacks
 * - Individual unit caps: 4 each (12 total army units)
 * - No round-based delays - produce based on balance
 * - Cycle through SCOUT -> SOLDIER -> LUMBERJACK to maintain balance
 */
public strictfp class BulletSpending {
    static RobotController rc;
    
    // Game constants
    static final int MAX_GARDENERS = 6;
    static final float VP_DONATE_THRESHOLD = 1000f;
    
    // Balanced army composition caps
    static final int MAX_SCOUTS = 4;
    static final int MAX_SOLDIERS = 4;
    static final int MAX_LUMBERJACKS = 4;
    static final int MAX_ARMY_SIZE = 12; // 4 + 4 + 4
    
    // Production rotation for balanced army
    // 0 = SCOUT, 1 = SOLDIER, 2 = LUMBERJACK
    static final int PRODUCTION_SCOUT = 0;
    static final int PRODUCTION_SOLDIER = 1;
    static final int PRODUCTION_LUMBERJACK = 2;
    static int productionRotation = PRODUCTION_SCOUT; // Start with scout
    
    // Counters
    static int numGardeners = 0;
    static int numSoldiers = 0;
    static int numScouts = 0;
    static int numLumberjacks = 0;
    static int numTanks = 0;
    
    public static void init(RobotController rcIn) {
        rc = rcIn;
    }
    
    /**
     * Main entry point for all bullet spending decisions.
     * Called by each robot type to handle their spending.
     */
    public static void spendPolicy(RobotType type) throws GameActionException {
        switch (type) {
            case ARCHON:
                archonSpend();
                break;
            case GARDENER:
                gardenerSpend();
                break;
            case SOLDIER:
                soldierSpend();
                break;
            case LUMBERJACK:
                lumberjackSpend();
                break;
            case SCOUT:
                scoutSpend();
                break;
            case TANK:
                tankSpend();
                break;
        }
    }
    
    /**
     * Archon spending: hire Gardeners, donate VPs late game.
     */
    static void archonSpend() throws GameActionException {
        float bullets = rc.getTeamBullets();
        int round = rc.getRoundNum();
        int maxRound = rc.getRoundLimit();
        
        // Update counters from comms
        updateCounters();
        
        // Late game VP donation
        if (round > maxRound - 50 || bullets > VP_DONATE_THRESHOLD) {
            int vpsToBuy = (int)(bullets / rc.getVictoryPointCost());
            if (vpsToBuy > 0) {
                rc.donate(vpsToBuy * rc.getVictoryPointCost());
                return;
            }
        }
        
        // Hire Gardeners early and as needed
        if (numGardeners < MAX_GARDENERS && bullets >= RobotType.GARDENER.bulletCost) {
            // Try to hire in multiple directions
            for (int i = 0; i < 8; i++) {
                Direction dir = new Direction(i * (float)Math.PI / 4);
                if (rc.canHireGardener(dir)) {
                    rc.hireGardener(dir);
                    Comms.incrementCounter(5); // Increment gardener counter
                    return;
                }
            }
        }
    }
    
    /**
     * Gardener spending: plant trees, build units.
     * Strategy: Balanced army composition - cycle through SCOUT->SOLDIER->LUMBERJACK
     * No round-based delays, produce based on unit balance.
     */
    static void gardenerSpend() throws GameActionException {
        float bullets = rc.getTeamBullets();
        
        updateCounters();
        
        // Plant trees to maintain economy
        TreeInfo[] nearbyTrees = rc.senseNearbyTrees(2.0f, rc.getTeam());
        if (nearbyTrees.length < 4 && rc.canPlantTree(Direction.NORTH)) {
            // Find best direction to plant
            for (int i = 0; i < 6; i++) {
                Direction dir = new Direction(i * (float)Math.PI / 3);
                if (rc.canPlantTree(dir)) {
                    rc.plantTree(dir);
                    return;
                }
            }
        }
        
        // Calculate total army size
        int totalArmy = numScouts + numSoldiers + numLumberjacks;
        
        // Only build if we haven't reached max army size
        if (totalArmy < MAX_ARMY_SIZE) {
            // Balanced production: cycle through SCOUT -> SOLDIER -> LUMBERJACK
            // Build the unit type based on current rotation
            boolean built = false;
            
            switch (productionRotation) {
                case PRODUCTION_SCOUT:
                    built = tryBuildUnit(RobotType.SCOUT, 6);
                    if (built || numScouts >= MAX_SCOUTS) {
                        productionRotation = PRODUCTION_SOLDIER;
                    }
                    break;
                    
                case PRODUCTION_SOLDIER:
                    built = tryBuildUnit(RobotType.SOLDIER, 7);
                    if (built || numSoldiers >= MAX_SOLDIERS) {
                        productionRotation = PRODUCTION_LUMBERJACK;
                    }
                    break;
                    
                case PRODUCTION_LUMBERJACK:
                    built = tryBuildUnit(RobotType.LUMBERJACK, 8);
                    if (built || numLumberjacks >= MAX_LUMBERJACKS) {
                        productionRotation = PRODUCTION_SCOUT;
                    }
                    break;
            }
            
            // If we didn't build this turn, try the next type (skip full caps)
            if (!built) {
                // Find the next type that isn't at cap
                int attempts = 0;
                while (attempts < 3) {
                    productionRotation = (productionRotation + 1) % 3;
                    boolean canBuild = false;
                    
                    switch (productionRotation) {
                        case PRODUCTION_SCOUT:
                            canBuild = numScouts < MAX_SCOUTS;
                            break;
                        case PRODUCTION_SOLDIER:
                            canBuild = numSoldiers < MAX_SOLDIERS;
                            break;
                        case PRODUCTION_LUMBERJACK:
                            canBuild = numLumberjacks < MAX_LUMBERJACKS;
                            break;
                    }
                    
                    if (canBuild) {
                        break;
                    }
                    attempts++;
                }
            }
        }
        
        // Tanks: build if we have excess bullets (optional, not part of the 12-unit cap)
        if (bullets >= RobotType.TANK.bulletCost + 200) {
            for (int i = 0; i < 8; i++) {
                Direction dir = new Direction(i * (float)Math.PI / 4);
                if (rc.canBuildRobot(RobotType.TANK, dir)) {
                    rc.buildRobot(RobotType.TANK, dir);
                    Comms.incrementCounter(9); // Tank counter
                    return;
                }
            }
        }
    }
    
    /**
     * Try to build a specific unit type.
     * @return true if successfully built
     */
    static boolean tryBuildUnit(RobotType type, int counterChannel) throws GameActionException {
        float bullets = rc.getTeamBullets();
        
        // Check if we have enough bullets
        if (bullets < type.bulletCost) {
            return false;
        }
        
        // Check specific caps
        boolean atCap = false;
        switch (type) {
            case SCOUT:
                atCap = numScouts >= MAX_SCOUTS;
                break;
            case SOLDIER:
                atCap = numSoldiers >= MAX_SOLDIERS;
                break;
            case LUMBERJACK:
                atCap = numLumberjacks >= MAX_LUMBERJACKS;
                break;
            default:
                atCap = false;
        }
        
        if (atCap) {
            return false;
        }
        
        // Try to build in multiple directions
        for (int i = 0; i < 8; i++) {
            Direction dir = new Direction(i * (float)Math.PI / 4);
            if (rc.canBuildRobot(type, dir)) {
                rc.buildRobot(type, dir);
                Comms.incrementCounter(counterChannel);
                return true;
            }
        }
        
        return false;
    }
    
    static void soldierSpend() throws GameActionException {
        // Soldiers don't directly spend bullets
        // They request support via comms if needed
    }
    
    static void lumberjackSpend() throws GameActionException {
        // Lumberjacks don't directly spend bullets
    }
    
    static void scoutSpend() throws GameActionException {
        // Scouts don't directly spend bullets
    }
    
    static void tankSpend() throws GameActionException {
        // Tanks don't directly spend bullets
    }
    
    static void updateCounters() throws GameActionException {
        numGardeners = Comms.getCount(5);
        numScouts = Comms.getCount(6);
        numSoldiers = Comms.getCount(7);
        numLumberjacks = Comms.getCount(8);
        numTanks = Comms.getCount(9);
    }
}
