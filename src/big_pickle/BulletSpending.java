package big_pickle;
import battlecode.common.*;

public class BulletSpending {
    static RobotController rc;

    public static void init(RobotController rc) {
        BulletSpending.rc = rc;
    }

    private static boolean shouldHireGardener(Direction dir) {
        // Hybrid Assassin: Moderate gardener hiring up to 10 for sustained production
        int gardeners = countAlliedRobots(RobotType.GARDENER);
        int maxGardeners = Math.min(10, 4 + rc.getRoundNum() / 250); // Scale up to 10 max
        
        double hireChance = 0.70; // Moderate chance to hire gardeners
        if (gardeners < 5) hireChance = 0.80; // More aggressive early
        else if (gardeners < 8) hireChance = 0.60; // Moderate for mid expansion
        
        return rc.canHireGardener(dir) && 
               Math.random() < hireChance && 
               gardeners < maxGardeners;
    }

    private static boolean shouldBuildGardener(Direction dir) {
        // Hybrid Assassin: Continue building meta-gardeners for expansion
        int gardeners = countAlliedRobots(RobotType.GARDENER);
        int maxGardeners = Math.min(10, 4 + rc.getRoundNum() / 250);
        
        return rc.canBuildRobot(RobotType.GARDENER, dir) && 
               Math.random() < 0.15 && // Low chance - prioritize military
               gardeners < maxGardeners;
    }

    private static boolean shouldBuildSoldier(Direction dir) {
        // Hybrid Assassin: Soldiers 20% - medium attack force
        int soldiers = countAlliedRobots(RobotType.SOLDIER);
        int maxSoldiers = 15; // Support economic warfare
        
        return rc.canBuildRobot(RobotType.SOLDIER, dir) && 
               Math.random() < 0.20 && // 20% for balanced attack force
               soldiers < maxSoldiers;
    }

    private static boolean shouldBuildTank(Direction dir) {
        // Hybrid Assassin: Minimal tanks - focus on speed for economic raids
        int tanks = countAlliedRobots(RobotType.TANK);
        int maxTanks = 3; // Limited heavy support
        
        return rc.canBuildRobot(RobotType.TANK, dir) && 
               Math.random() < 0.05 && // 5% minimal heavy support
               tanks < maxTanks;
    }

    private static boolean shouldBuildScout(Direction dir) {
        // Hybrid Assassin: Scouts 35% - primary economic warfare units
        int scouts = countAlliedRobots(RobotType.SCOUT);
        int maxScouts = 25; // Large scout force for hunting gardeners
        
        return rc.canBuildRobot(RobotType.SCOUT, dir) && 
               Math.random() < 0.35 && // 35% high priority for economic hunting
               scouts < maxScouts;
    }

    private static boolean shouldBuildLumberjack(Direction dir) {
        // Hybrid Assassin: Lumberjacks 25% - tree clearing and economic disruption
        int lumberjacks = countAlliedRobots(RobotType.LUMBERJACK);
        int maxLumberjacks = 20; // Significant force for economic warfare
        
        return rc.canBuildRobot(RobotType.LUMBERJACK, dir) && 
               Math.random() < 0.25 && // 25% for tree clearing and disruption
               lumberjacks < maxLumberjacks;
    }

    private static boolean shouldPlantTree(Direction dir) {
        // Hybrid Assassin: Moderate tree farming at 50% for steady VP pressure
        int trees = countTeamTrees();
        int round = rc.getRoundNum();
        
        // Moderate tree planting for VP pressure while focusing on military
        double treeChance = 0.50; // 50% chance - balanced approach
        
        // Slow down if we have sufficient trees for VP pressure
        if (trees > 30) treeChance = 0.40;
        if (trees > 45) treeChance = 0.30;
        
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
        // Hybrid Assassin: Moderate priority for tree watering
        TreeInfo[] trees = rc.senseNearbyTrees(-1, rc.getTeam());
        if (trees.length > 0) {
            // Prioritize trees that need water most urgently
            TreeInfo bestTree = null;
            float bestScore = -1;
            
            for (TreeInfo tree : trees) {
                if (rc.canWater(tree.location)) {
                    // Prioritize lowest health trees to keep them producing VP
                    float healthRatio = tree.health / tree.maxHealth;
                    float score = (1 - healthRatio) * 10; // Moderate weight on low health
                    
                    // Slight preference for larger trees
                    score += tree.radius * 0.5;
                    
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
        // Hybrid Assassin: VP donations at 180 bullets to maintain constant pressure
        float bullets = rc.getTeamBullets();
        float vpCost = rc.getVictoryPointCost();
        int round = rc.getRoundNum();

        // Start VP donation at 180 bullets threshold for constant pressure
        if (round < 300) {
            // Early game: donate if we have 180+ bullets for pressure
            if (bullets > 180 && bullets >= vpCost * 2) {
                float donateAmount = (float)Math.floor((bullets - 80) / vpCost) * vpCost;
                if (donateAmount > 0) return donateAmount;
            }
        }
        else if (round < 800) {
            // Mid game: maintain pressure while building military
            if (bullets > 150 && bullets >= vpCost * 1.8) {
                float donateAmount = (float)Math.floor((bullets - 50) / vpCost) * vpCost;
                if (donateAmount > 0) return donateAmount;
            }
        }
        // Late game: aggressive VP to finish while military raids economy
        else {
            if (bullets > 120 && bullets >= vpCost * 1.5) {
                float donateAmount = (float)Math.floor((bullets - 30) / vpCost) * vpCost;
                if (donateAmount > 0) return donateAmount;
            }
        }
        
        return 0f;
    }

    public static void spendPolicy() throws GameActionException {
        // Hybrid Assassin: Economic warfare with mixed military and VP pressure
        
        if (rc.getType() == RobotType.ARCHON) {
            // Archons hire moderate gardeners for sustained production
            Direction dir = randomDirection();
            
            if (shouldHireGardener(dir)) {
                rc.hireGardener(dir);
            }
        } 
        else if (rc.getType() == RobotType.GARDENER) {
            // Hybrid Assassin gardener priority: Economic warfare units > Trees
            
            // Priority 1: Moderate tree watering for VP pressure
            if (Math.random() < 0.60) { // 60% chance to water
                tryWaterTrees();
                if (rc.hasMoved()) return; // Don't do anything else if we watered
            }
            
            // Priority 2: Build economic warfare military units
            Direction dir = randomDirection();
            
            // Scout 35% - primary economic assassins
            if (Math.random() < 0.35 && shouldBuildScout(dir)) {
                rc.buildRobot(RobotType.SCOUT, dir);
            }
            // Lumberjack 25% - economic disruptors  
            else if (Math.random() < 0.25 && shouldBuildLumberjack(dir)) {
                rc.buildRobot(RobotType.LUMBERJACK, dir);
            }
            // Soldier 20% - support force
            else if (Math.random() < 0.20 && shouldBuildSoldier(dir)) {
                rc.buildRobot(RobotType.SOLDIER, dir);
            }
            // Plant trees 50% - moderate VP pressure
            else if (Math.random() < 0.50 && shouldPlantTree(dir)) {
                rc.plantTree(dir);
            }
            // Minimal tanks for heavy support
            else if (Math.random() < 0.05 && shouldBuildTank(dir)) {
                rc.buildRobot(RobotType.TANK, dir);
            }
            
            // Continue building meta-gardeners occasionally for expansion
            dir = randomDirection();
            if (shouldBuildGardener(dir)) {
                rc.buildRobot(RobotType.GARDENER, dir);
            }
        }
        
        // Hybrid Assassin: VP pressure to force enemy mistakes
        float donateAmount = getDonateAmount();
        if (donateAmount > 0f) {
            rc.donate(donateAmount);
        }
    }

    private static Direction randomDirection() {
        return new Direction((float)Math.random() * 2 * (float)Math.PI);
    }
}