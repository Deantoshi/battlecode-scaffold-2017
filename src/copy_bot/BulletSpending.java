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
 */
public strictfp class BulletSpending {
    static RobotController rc;
    
    // Game constants
    static final int MAX_GARDENERS = 6;
    static final int MAX_SCOUTS = 2;
    static final float VP_DONATE_THRESHOLD = 1000f;
    
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
     */
    static void gardenerSpend() throws GameActionException {
        float bullets = rc.getTeamBullets();
        int round = rc.getRoundNum();
        
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
        
        // Build units based on game state
        if (numScouts < MAX_SCOUTS && bullets >= RobotType.SCOUT.bulletCost) {
            for (int i = 0; i < 8; i++) {
                Direction dir = new Direction(i * (float)Math.PI / 4);
                if (rc.canBuildRobot(RobotType.SCOUT, dir)) {
                    rc.buildRobot(RobotType.SCOUT, dir);
                    Comms.incrementCounter(6); // Scout counter
                    return;
                }
            }
        }
        
        // Build Soldiers as main combat unit
        if (bullets >= RobotType.SOLDIER.bulletCost && round < 1500) {
            for (int i = 0; i < 8; i++) {
                Direction dir = new Direction(i * (float)Math.PI / 4);
                if (rc.canBuildRobot(RobotType.SOLDIER, dir)) {
                    rc.buildRobot(RobotType.SOLDIER, dir);
                    Comms.incrementCounter(7); // Soldier counter
                    return;
                }
            }
        }
        
        // Build Lumberjacks early for tree clearing
        if (numLumberjacks < 2 && bullets >= RobotType.LUMBERJACK.bulletCost && round < 500) {
            for (int i = 0; i < 8; i++) {
                Direction dir = new Direction(i * (float)Math.PI / 4);
                if (rc.canBuildRobot(RobotType.LUMBERJACK, dir)) {
                    rc.buildRobot(RobotType.LUMBERJACK, dir);
                    Comms.incrementCounter(8); // Lumberjack counter
                    return;
                }
            }
        }
        
        // Build Tanks late game
        if (bullets >= RobotType.TANK.bulletCost && round > 1500) {
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
