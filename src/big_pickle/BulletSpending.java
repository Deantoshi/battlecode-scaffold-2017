package big_pickle;
import battlecode.common.*;

public class BulletSpending {
    static RobotController rc;

    public static void init(RobotController rc) {
        BulletSpending.rc = rc;
    }

    private static boolean shouldHireGardener(Direction dir) {
        // VP Tyrant: Hire maximum gardeners for massive tree farm coverage
        int gardeners = countAlliedRobots(RobotType.GARDENER);
        int maxGardeners = Math.min(15, 8 + rc.getRoundNum() / 200); // Scale up to 15 max
        
        double hireChance = 0.90; // Very high chance to hire gardeners
        if (gardeners < 8) hireChance = 0.95; // Extremely aggressive early
        else if (gardeners < 12) hireChance = 0.85; // Still high for medium expansion
        
        return rc.canHireGardener(dir) && 
               Math.random() < hireChance && 
               gardeners < maxGardeners;
    }

    private static boolean shouldBuildGardener(Direction dir) {
        // VP Tyrant: Continue building meta-gardeners for expansion
        int gardeners = countAlliedRobots(RobotType.GARDENER);
        int maxGardeners = Math.min(15, 8 + rc.getRoundNum() / 200);
        
        return rc.canBuildRobot(RobotType.GARDENER, dir) && 
               Math.random() < 0.10 && // Very low chance - prioritize trees
               gardeners < maxGardeners;
    }

    private static boolean shouldBuildSoldier(Direction dir) {
        // VP Tyrant: Minimal defense only - 5% chance
        int soldiers = countAlliedRobots(RobotType.SOLDIER);
        int maxSoldiers = 3; // Very limited defensive force
        
        return rc.canBuildRobot(RobotType.SOLDIER, dir) && 
               Math.random() < 0.05 && // 5% minimum for defense
               soldiers < maxSoldiers;
    }

    private static boolean shouldBuildTank(Direction dir) {
        // VP Tyrant: Minimal defense only - 5% chance
        int tanks = countAlliedRobots(RobotType.TANK);
        int maxTanks = 2; // Very limited defensive force
        
        return rc.canBuildRobot(RobotType.TANK, dir) && 
               Math.random() < 0.05 && // 5% minimum for defense
               tanks < maxTanks;
    }

    private static boolean shouldBuildScout(Direction dir) {
        // VP Tyrant: Minimal scouts for vision - 5% chance
        int scouts = countAlliedRobots(RobotType.SCOUT);
        int maxScouts = 2; // Minimal scouting
        
        return rc.canBuildRobot(RobotType.SCOUT, dir) && 
               Math.random() < 0.05 && // 5% minimum for defense/scouting
               scouts < maxScouts;
    }

    private static boolean shouldPlantTree(Direction dir) {
        // VP Tyrant: 98% tree planting chance - massive tree farms
        int trees = countTeamTrees();
        int round = rc.getRoundNum();
        
        // Always try to plant trees with 98% chance
        double treeChance = 0.98;
        
        // Only slow down if we have an absolute massive number of trees
        if (trees > 60) treeChance = 0.95;
        if (trees > 80) treeChance = 0.90;
        
        return rc.canPlantTree(dir) && Math.random() < treeChance;
    }

    private static int countAlliedRobots(RobotType type) {
        RobotInfo[] robots = rc.senseNearbyRobots(-1, rc.getTeam());
        int count = 0;
        for (RobotInfo robot : robots) {
            if (robot.type == type) count++;
        }
        return count;
    }

    private static int countTeamTrees() {
        TreeInfo[] trees = rc.senseNearbyTrees(-1, rc.getTeam());
        return trees.length;
    }

    private static void tryWaterTrees() throws GameActionException {
        // VP Tyrant: 100% priority for tree watering over all other actions
        TreeInfo[] trees = rc.senseNearbyTrees(-1, rc.getTeam());
        if (trees.length > 0) {
            // Prioritize trees that need water most urgently
            TreeInfo bestTree = null;
            float bestScore = -1;
            
            for (TreeInfo tree : trees) {
                if (rc.canWater(tree.location)) {
                    // Prioritize lowest health trees to keep them alive and producing
                    float healthRatio = tree.health / tree.maxHealth;
                    float score = (1 - healthRatio) * 20; // Heavy weight on low health
                    
                    // Slight preference for larger trees
                    score += tree.radius;
                    
                    if (score > bestScore) {
                        bestScore = score;
                        bestTree = tree;
                    }
                }
            }
            
            if (bestTree != null) {
                rc.water(bestTree.location);
            }
        }
    }

    private static float getDonateAmount() throws GameActionException {
        // VP Tyrant: Very aggressive VP donation starting at 200 bullets
        float bullets = rc.getTeamBullets();
        float vpCost = rc.getVictoryPointCost();
        int round = rc.getRoundNum();

        // Start VP donation much earlier and more aggressively
        if (round < 200) {
            // Even early game: donate if we have excess over 200 bullets
            if (bullets > 200 && bullets >= vpCost * 2) {
                float donateAmount = (float)Math.floor((bullets - 100) / vpCost) * vpCost;
                if (donateAmount > 0) return donateAmount;
            }
        }
        else if (round < 600) {
            // Mid game: very aggressive donation
            if (bullets > 150 && bullets >= vpCost * 1.5) {
                float donateAmount = (float)Math.floor((bullets - 50) / vpCost) * vpCost;
                if (donateAmount > 0) return donateAmount;
            }
        }
        // Late game: extremely aggressive VP rush
        else {
            if (bullets > 100 && bullets >= vpCost) {
                float donateAmount = (float)Math.floor(bullets / vpCost) * vpCost;
                if (donateAmount > 0) return donateAmount;
            }
        }
        
        return 0f;
    }

    public static void spendPolicy() throws GameActionException {
        // VP Tyrant: Economic powerhouse strategy focused on trees and VP
        
        if (rc.getType() == RobotType.ARCHON) {
            // Archons prioritize hiring maximum gardeners for tree farms
            Direction dir = randomDirection();
            
            if (shouldHireGardener(dir)) {
                rc.hireGardener(dir);
            }
        } 
        else if (rc.getType() == RobotType.GARDENER) {
            // VP Tyrant gardener priority: Trees > Everything else
            
            // Priority 1: Tree watering - 100% priority
            tryWaterTrees();
            if (rc.hasMoved()) return; // Don't do anything else if we watered
            
            // Priority 2: Plant massive tree farms - 98% chance
            Direction dir = randomDirection();
            if (shouldPlantTree(dir)) {
                rc.plantTree(dir);
            }
            // Only build minimal defensive units if we can't plant
            else if (Math.random() < 0.05) { // 5% chance for minimal defense
                if (shouldBuildSoldier(dir)) {
                    rc.buildRobot(RobotType.SOLDIER, dir);
                } else if (shouldBuildTank(dir)) {
                    rc.buildRobot(RobotType.TANK, dir);
                } else if (shouldBuildScout(dir)) {
                    rc.buildRobot(RobotType.SCOUT, dir);
                }
            }
            
            // Continue building meta-gardeners occasionally for expansion
            dir = randomDirection();
            if (shouldBuildGardener(dir)) {
                rc.buildRobot(RobotType.GARDENER, dir);
            }
        }
        
        // VP Tyrant: Very aggressive donation strategy
        float donateAmount = getDonateAmount();
        if (donateAmount > 0f) {
            rc.donate(donateAmount);
        }
    }

    private static Direction randomDirection() {
        return new Direction((float)Math.random() * 2 * (float)Math.PI);
    }
}