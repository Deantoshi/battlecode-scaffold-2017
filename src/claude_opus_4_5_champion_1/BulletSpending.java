package claude_opus_4_5_champion_1;
import battlecode.common.*;

/**
 * Late Game Titan Strategy - Centralized Bullet Spending
 * 
 * Philosophy: Turtle with economy early, then transition to unstoppable tank army.
 * Maximize economy for ~500 rounds, then overwhelm with tanks while VP provides 
 * backup win condition.
 */
public class BulletSpending {
    static RobotController rc;
    
    // Economy thresholds
    static final int EARLY_GAME_END = 500;       // Transition point from economy to tanks
    static final int MAX_GARDENERS = 5;          // Cap at 5 gardeners for massive economy
    static final int TREES_PER_GARDENER = 10;    // Target 10 trees per gardener
    static final float TANK_BUILD_THRESHOLD = 400f;  // Build tanks when bullets > 400
    static final float VP_DONATION_THRESHOLD = 800f; // Donate excess above 800

    public static void init(RobotController rc) {
        BulletSpending.rc = rc;
    }

    public static void spendPolicy() throws GameActionException {
        int round = rc.getRoundNum();
        float bullets = rc.getTeamBullets();
        
        // Centralized spend order varies by game phase
        if (rc.getType() == RobotType.ARCHON) {
            // VP donation when bullets exceed threshold (backup win condition)
            float donateAmount = getDonateAmount();
            if (donateAmount > 0f) {
                rc.donate(donateAmount);
            }
            
            // Hire gardeners - essential for economy
            Direction dir = findBuildDirection();
            if (shouldHireGardener(dir)) {
                rc.hireGardener(dir);
            }
            return;
        }
        
        if (rc.getType() == RobotType.GARDENER) {
            int round2 = rc.getRoundNum();
            float bullets2 = rc.getTeamBullets();
            
            // Early game (round < 500): Focus on economy - only trees
            if (round2 < EARLY_GAME_END) {
                // Plant trees for economy
                Direction dir = findPlantDirection();
                if (shouldPlantTree(dir)) {
                    rc.plantTree(dir);
                }
                return;
            }
            
            // Mid-late game (round >= 500): Build tanks when bullets > 400
            Direction buildDir = findBuildDirection();
            if (shouldBuildTank(buildDir)) {
                rc.buildRobot(RobotType.TANK, buildDir);
                return;
            }
            
            // Still plant trees if we haven't reached target
            Direction plantDir = findPlantDirection();
            if (shouldPlantTree(plantDir)) {
                rc.plantTree(plantDir);
            }
            
            // VP donation when bullets exceed threshold
            float donateAmount = getDonateAmount();
            if (donateAmount > 0f) {
                rc.donate(donateAmount);
            }
        }
    }

    private static boolean shouldHireGardener(Direction dir) throws GameActionException {
        if (!rc.canHireGardener(dir)) {
            return false;
        }
        
        // Count gardeners on team
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        int gardenerCount = 0;
        for (RobotInfo ally : allies) {
            if (ally.type == RobotType.GARDENER) {
                gardenerCount++;
            }
        }
        
        // Cap at 5 gardeners for massive economy
        if (gardenerCount >= MAX_GARDENERS) {
            return false;
        }
        
        float bullets = rc.getTeamBullets();
        int round = rc.getRoundNum();
        
        // Early game: Aggressive gardener hiring
        if (round < EARLY_GAME_END) {
            // Hire gardeners quickly early on
            if (gardenerCount < 3) {
                return bullets > 120f;  // Low threshold for first 3
            } else {
                return bullets > 200f;  // Higher threshold for 4-5
            }
        }
        
        // Late game: Only hire replacement gardeners if we have plenty of bullets
        return gardenerCount < 3 && bullets > 300f;
    }

    private static boolean shouldPlantTree(Direction dir) throws GameActionException {
        if (!rc.canPlantTree(dir)) {
            return false;
        }
        
        // Count nearby team trees (this gardener's contribution)
        TreeInfo[] trees = rc.senseNearbyTrees(-1, rc.getTeam());
        int treeCount = trees.length;
        
        // Target 10 trees per gardener
        if (treeCount < TREES_PER_GARDENER) {
            float bullets = rc.getTeamBullets();
            // Plant if we have enough bullets and not too many trees yet
            return bullets > 80f || treeCount < 3;
        }
        return false;
    }

    /**
     * Build tanks as primary military unit for late game push
     * Tanks cost 300 bullets, have 200 HP, and deal 5 damage
     */
    private static boolean shouldBuildTank(Direction dir) throws GameActionException {
        if (!rc.canBuildRobot(RobotType.TANK, dir)) {
            return false;
        }
        
        int round = rc.getRoundNum();
        float bullets = rc.getTeamBullets();
        
        // Only build tanks after early game transition (round >= 500)
        if (round < EARLY_GAME_END) {
            return false;
        }
        
        // Build tanks when bullets > 400
        if (bullets > TANK_BUILD_THRESHOLD) {
            // Higher probability when we have more bullets
            if (bullets > 600f) {
                return Math.random() < 0.8;  // Very likely when rich
            }
            return Math.random() < 0.5;  // Moderate chance at threshold
        }
        
        return false;
    }

    private static float getDonateAmount() throws GameActionException {
        float bullets = rc.getTeamBullets();
        float cost = rc.getVictoryPointCost();
        
        // Donate excess bullets above 800 for VP backup win condition
        if (bullets > VP_DONATION_THRESHOLD) {
            float donateAmount = bullets - VP_DONATION_THRESHOLD;
            if (donateAmount >= cost) {
                int pointsToBuy = (int)(donateAmount / cost);
                return pointsToBuy * cost;
            }
        }
        
        // End game: If we can win with VP, go for it
        int currentVP = rc.getTeamVictoryPoints();
        int vpToWin = 1000 - currentVP;
        float costToWin = vpToWin * cost;
        if (bullets >= costToWin && vpToWin <= 100) {
            // We can win, donate everything needed
            return costToWin;
        }
        
        return 0f;
    }

    /**
     * Find a direction to build robots
     */
    private static Direction findBuildDirection() throws GameActionException {
        Direction dir = new Direction((float)Math.random() * 2 * (float)Math.PI);
        // Try 8 directions
        for (int i = 0; i < 8; i++) {
            Direction tryDir = dir.rotateLeftDegrees(45 * i);
            if (rc.canBuildRobot(RobotType.TANK, tryDir) || rc.canHireGardener(tryDir)) {
                return tryDir;
            }
        }
        return dir;
    }
    
    /**
     * Find a direction to plant trees - prefer spacing them out
     */
    private static Direction findPlantDirection() throws GameActionException {
        Direction dir = new Direction((float)Math.random() * 2 * (float)Math.PI);
        // Try 6 directions (hexagonal pattern for optimal tree packing)
        for (int i = 0; i < 6; i++) {
            Direction tryDir = dir.rotateLeftDegrees(60 * i);
            if (rc.canPlantTree(tryDir)) {
                return tryDir;
            }
        }
        return dir;
    }
}
