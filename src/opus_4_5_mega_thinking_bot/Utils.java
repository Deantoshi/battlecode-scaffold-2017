package opus_4_5_mega_thinking_bot;

import battlecode.common.*;

/**
 * Shared utilities, constants, and cached state used by all robot types.
 */
public strictfp class Utils {
    // Core references
    public static RobotController rc;
    public static Team myTeam;
    public static Team enemyTeam;
    public static RobotType myType;

    // Per-turn cached values
    public static MapLocation myLoc;
    public static int roundNum;
    public static float teamBullets;
    public static float myHealth;

    // Game phase thresholds
    public static final int EARLY_GAME_END = 150;
    public static final int MID_GAME_END = 600;

    // Pre-computed directions (60 degree spacing for hexagonal patterns)
    public static final Direction[] HEX_DIRS = new Direction[6];

    // Cardinal directions (45 degree spacing)
    public static final Direction[] CARDINAL_DIRS = new Direction[8];

    // Map type constants
    public static final int MAP_OPEN = 0;
    public static final int MAP_MEDIUM = 1;
    public static final int MAP_DENSE = 2;

    static {
        // Initialize hexagonal directions (60 degrees apart)
        for (int i = 0; i < 6; i++) {
            HEX_DIRS[i] = new Direction((float)(i * Math.PI / 3));
        }
        // Initialize cardinal directions (45 degrees apart)
        for (int i = 0; i < 8; i++) {
            CARDINAL_DIRS[i] = new Direction((float)(i * Math.PI / 4));
        }
    }

    /**
     * Initialize shared state. Call once at robot spawn.
     */
    public static void init(RobotController robotController) {
        rc = robotController;
        myTeam = rc.getTeam();
        enemyTeam = myTeam.opponent();
        myType = rc.getType();
    }

    /**
     * Update per-turn cached values. Call at start of each turn.
     */
    public static void updatePerTurn() {
        myLoc = rc.getLocation();
        roundNum = rc.getRoundNum();
        teamBullets = rc.getTeamBullets();
        myHealth = rc.getHealth();
    }

    /**
     * Generate a random direction.
     */
    public static Direction randomDirection() {
        return new Direction((float)(Math.random() * 2 * Math.PI));
    }

    /**
     * Check if we're in early game (rounds 0-150).
     */
    public static boolean isEarlyGame() {
        return roundNum <= EARLY_GAME_END;
    }

    /**
     * Check if we're in mid game (rounds 151-600).
     */
    public static boolean isMidGame() {
        return roundNum > EARLY_GAME_END && roundNum <= MID_GAME_END;
    }

    /**
     * Check if we're in late game (rounds 601+).
     */
    public static boolean isLateGame() {
        return roundNum > MID_GAME_END;
    }

    /**
     * Get the VP cost at a given round.
     */
    public static float vpCostAtRound(int round) {
        return 7.5f + (round * 12.5f / 3000f);
    }

    /**
     * Get current VP cost.
     */
    public static float currentVPCost() {
        return rc.getVictoryPointCost();
    }

    /**
     * Get enemy's initial archon locations.
     */
    public static MapLocation[] getEnemyInitialArchonLocs() {
        return rc.getInitialArchonLocations(enemyTeam);
    }

    /**
     * Get our initial archon locations.
     */
    public static MapLocation[] getMyInitialArchonLocs() {
        return rc.getInitialArchonLocations(myTeam);
    }

    /**
     * Check if we can win instantly by donating bullets.
     */
    public static boolean canWinByDonation() throws GameActionException {
        int myVP = rc.getTeamVictoryPoints();
        int vpNeeded = 1000 - myVP;
        float vpCost = rc.getVictoryPointCost();
        float costForWin = vpNeeded * vpCost;
        return teamBullets >= costForWin;
    }

    /**
     * Donate bullets for instant win if possible.
     */
    public static boolean tryWinByDonation() throws GameActionException {
        int myVP = rc.getTeamVictoryPoints();
        int vpNeeded = 1000 - myVP;
        float vpCost = rc.getVictoryPointCost();
        float costForWin = vpNeeded * vpCost;

        if (teamBullets >= costForWin) {
            rc.donate(costForWin);
            return true;
        }
        return false;
    }

    /**
     * Strategic donation in late game or when opponent is close to winning.
     */
    public static void strategicDonate() throws GameActionException {
        int oppVP = rc.getOpponentVictoryPoints();
        float vpCost = rc.getVictoryPointCost();

        // Opponent close to winning - defensive donation
        if (oppVP > 900 && teamBullets > 100) {
            float toDonate = Math.min(teamBullets - 50, 100);
            if (toDonate >= vpCost) {
                // Round down to whole VP
                float donateAmount = ((int)(toDonate / vpCost)) * vpCost;
                if (donateAmount >= vpCost) {
                    rc.donate(donateAmount);
                }
            }
        }

        // Late game - convert excess bullets
        if (roundNum > 2500 && teamBullets > 300) {
            float excess = teamBullets - 200;
            float donateAmount = ((int)(excess / vpCost)) * vpCost;
            if (donateAmount >= vpCost) {
                rc.donate(donateAmount);
            }
        }
    }

    /**
     * Count robots of a specific type in an array.
     */
    public static int countType(RobotInfo[] robots, RobotType type) {
        int count = 0;
        for (RobotInfo robot : robots) {
            if (robot.type == type) {
                count++;
            }
        }
        return count;
    }

    /**
     * Find the closest robot of a specific type.
     */
    public static RobotInfo findClosest(RobotInfo[] robots, RobotType type) {
        RobotInfo closest = null;
        float minDist = Float.MAX_VALUE;
        for (RobotInfo robot : robots) {
            if (robot.type == type) {
                float dist = myLoc.distanceTo(robot.location);
                if (dist < minDist) {
                    minDist = dist;
                    closest = robot;
                }
            }
        }
        return closest;
    }

    /**
     * Find the robot with lowest health.
     */
    public static RobotInfo findLowestHealth(RobotInfo[] robots) {
        RobotInfo lowest = null;
        float minHealth = Float.MAX_VALUE;
        for (RobotInfo robot : robots) {
            if (robot.health < minHealth) {
                minHealth = robot.health;
                lowest = robot;
            }
        }
        return lowest;
    }
}
