package big_pickle;
import battlecode.common.*;

public class BulletSpending {
    static RobotController rc;

    public static void init(RobotController rc) {
        BulletSpending.rc = rc;
    }

    private static boolean shouldHireGardener(Direction dir) {
        // Hire gardeners more aggressively early game for expansion
        int round = rc.getRoundNum();
        double hireChance = 0.85;
        if (round < 100) hireChance = 0.95; // Very aggressive early expansion
        else if (round < 300) hireChance = 0.80; // Still good expansion
        else hireChance = 0.60; // Slow down later
        
        return rc.canHireGardener(dir) && Math.random() < hireChance;
    }

    private static boolean shouldBuildGardener(Direction dir) {
        // Meta-gardener for continued expansion, but limit them
        int gardeners = countAlliedRobots(RobotType.GARDENER);
        int maxGardeners = Math.min(8, 4 + rc.getRoundNum() / 300);
        
        return rc.canBuildRobot(RobotType.GARDENER, dir) && 
               Math.random() < 0.70 && 
               gardeners < maxGardeners;
    }

    private static boolean shouldBuildSoldier(Direction dir) {
        // Soldiers for mid-range defense and scout support
        int soldiers = countAlliedRobots(RobotType.SOLDIER);
        int tanks = countAlliedRobots(RobotType.TANK);
        
        // Build more soldiers if we lack mid-range units
        double soldierChance = 0.15;
        if (tanks > soldiers * 2) soldierChance = 0.35; // More soldiers if tank-heavy
        
        return rc.canBuildRobot(RobotType.SOLDIER, dir) && Math.random() < soldierChance;
    }

    private static boolean shouldBuildTank(Direction dir) {
        // Core of Tank Fortress - prioritize heavily
        int round = rc.getRoundNum();
        int tanks = countAlliedRobots(RobotType.TANK);
        int soldiers = countAlliedRobots(RobotType.SOLDIER);
        
        double tankChance = 0.55; // Base high chance for tanks
        
        // Even more aggressive early-mid game tank production
        if (round < 200) tankChance = 0.75;
        else if (round < 800) tankChance = 0.65;
        
        // Maintain good tank:soldier ratio
        if (tanks < soldiers * 1.5) tankChance = Math.min(0.85, tankChance + 0.15);
        
        return rc.canBuildRobot(RobotType.TANK, dir) && Math.random() < tankChance;
    }

    private static boolean shouldBuildScout(Direction dir) {
        // Limited scouts for vision and harassment
        int scouts = countAlliedRobots(RobotType.SCOUT);
        int maxScouts = 3;
        
        return rc.canBuildRobot(RobotType.SCOUT, dir) && 
               Math.random() < 0.25 && 
               scouts < maxScouts;
    }

    private static boolean shouldPlantTree(Direction dir) {
        // Very aggressive tree planting for economy
        int trees = countTeamTrees();
        int round = rc.getRoundNum();
        
        // Plant aggressively but manage space
        double treeChance = 0.90;
        if (trees > 25) treeChance = 0.70; // Slow down if many trees
        if (trees > 40) treeChance = 0.40; // Limit overcrowding
        
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
        // Priority: water trees that need it most and are most productive
        TreeInfo[] trees = rc.senseNearbyTrees(-1, rc.getTeam());
        if (trees.length > 0) {
            // Sort by health and radius (bigger, healthier trees are more valuable)
            TreeInfo bestTree = null;
            float bestScore = -1;
            
            for (TreeInfo tree : trees) {
                if (rc.canWater(tree.location)) {
                    // Prioritize lower health but also consider tree size
                    float healthRatio = tree.health / tree.maxHealth;
                    float score = (1 - healthRatio) * 10 + tree.radius * 2;
                    
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
        // Tank Fortress VP strategy: aggressive late-game donation
        float bullets = rc.getTeamBullets();
        float vpCost = rc.getVictoryPointCost();
        int round = rc.getRoundNum();

        // Early game: focus on expansion, minimal VP
        if (round < 300) {
            return 0f;
        }
        
        // Mid game: donate when we have significant excess
        if (round < 1000) {
            if (bullets > 400 && bullets >= vpCost * 3) {
                float donateAmount = (float)Math.floor((bullets - 200) / vpCost) * vpCost;
                if (donateAmount > 0) return donateAmount;
            }
        }
        // Late game: very aggressive VP rush
        else {
            if (bullets > 250 && bullets >= vpCost * 2) {
                float donateAmount = (float)Math.floor((bullets - 100) / vpCost) * vpCost;
                if (donateAmount > 0) return donateAmount;
            }
        }
        
        return 0f;
    }

    public static void spendPolicy() throws GameActionException {
        // Centralized spending policy for Tank Fortress strategy
        if (rc.getType() == RobotType.ARCHON) {
            // Archons focus heavily on gardener expansion early, then slow down
            Direction dir = randomDirection();
            
            // Early game: hire gardeners aggressively
            if (shouldHireGardener(dir)) {
                rc.hireGardener(dir);
            }
        } 
        else if (rc.getType() == RobotType.GARDENER) {
            // Tank Fortress gardener build priority
            Direction dir = randomDirection();
            
            // Phase 1: Expansion through meta-gardeners (early game priority)
            if (shouldBuildGardener(dir)) {
                rc.buildRobot(RobotType.GARDENER, dir);
            }
            // Phase 2: Core fortress units - tanks are highest priority
            else if (shouldBuildTank(dir)) {
                rc.buildRobot(RobotType.TANK, dir);
            }
            // Phase 3: Support units - soldiers for mid-range defense
            else if (shouldBuildSoldier(dir)) {
                rc.buildRobot(RobotType.SOLDIER, dir);
            }
            // Phase 4: Limited scouts for vision
            else if (shouldBuildScout(dir)) {
                rc.buildRobot(RobotType.SCOUT, dir);
            }
            
            // Always try to plant trees - economic foundation
            if (shouldPlantTree(dir)) {
                rc.plantTree(dir);
            }

            // Maintain tree farm health
            tryWaterTrees();
        }
        
        // All robots can donate for VP when strategically appropriate
        float donateAmount = getDonateAmount();
        if (donateAmount > 0f) {
            rc.donate(donateAmount);
        }
    }

    private static Direction randomDirection() {
        return new Direction((float)Math.random() * 2 * (float)Math.PI);
    }
}