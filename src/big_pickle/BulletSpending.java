package big_pickle;
import battlecode.common.*;

public class BulletSpending {
    static RobotController rc;

    public static void init(RobotController rc) {
        BulletSpending.rc = rc;
    }

    private static boolean shouldHireGardener(Direction dir) {
        // Early Rush Horde: Maximum immediate gardener hiring for early swarm
        int gardeners = countAlliedRobots(RobotType.GARDENER);
        int round = rc.getRoundNum();
        
        // Hire maximum gardeners immediately - 6+ by round 100
        int maxGardeners = 12; // Very high max for early production
        if (round < 100) {
            maxGardeners = Math.min(12, 2 + round / 15); // Rapid early scaling
        } else if (round < 200) {
            maxGardeners = Math.min(15, 6 + (round - 100) / 20); // Continue expansion
        }
        
        // Very aggressive hiring for early game rush
        double hireChance = 0.90; // 90% chance - extremely aggressive
        if (gardeners < 4) hireChance = 0.95; // Near-certain early
        else if (gardeners < 8) hireChance = 0.85; // Still very aggressive
        else if (gardeners < 12) hireChance = 0.75; // High mid-game
        
        return rc.canHireGardener(dir) && 
               Math.random() < hireChance && 
               gardeners < maxGardeners;
    }

    private static boolean shouldBuildGardener(Direction dir) {
        // Early Rush Horde: Minimal meta-gardeners - focus on military
        int gardeners = countAlliedRobots(RobotType.GARDENER);
        int maxGardeners = 3; // Very limited meta-gardeners
        
        return rc.canBuildRobot(RobotType.GARDENER, dir) && 
               Math.random() < 0.05 && // 5% chance - focus on rush units
               gardeners < maxGardeners;
    }

    private static boolean shouldBuildSoldier(Direction dir) {
        // Early Rush Horde: Soldiers 35% early game - primary rush units
        int soldiers = countAlliedRobots(RobotType.SOLDIER);
        int round = rc.getRoundNum();
        int maxSoldiers = 40; // Large army for rush
        
        // Early game priority scaling
        double soldierChance = 0.35; // 35% base priority
        if (round < 200) {
            soldierChance = 0.40; // 40% in very early game
        } else if (round < 500) {
            soldierChance = 0.35; // Maintain rush pressure
        } else {
            soldierChance = 0.25; // Scale back later
        }
        
        return rc.canBuildRobot(RobotType.SOLDIER, dir) && 
               Math.random() < soldierChance && 
               soldiers < maxSoldiers;
    }

    private static boolean shouldBuildTank(Direction dir) {
        // Early Rush Horde: No tanks early - focus on cheap rush units
        int round = rc.getRoundNum();
        
        // Completely disable tanks in early game rush
        if (round < 600) return false;
        
        int tanks = countAlliedRobots(RobotType.TANK);
        int maxTanks = 2; // Minimal late game support
        
        return rc.canBuildRobot(RobotType.TANK, dir) && 
               Math.random() < 0.03 && // 3% - very low priority
               tanks < maxTanks;
    }

    private static boolean shouldBuildScout(Direction dir) {
        // Early Rush Horde: Scouts 30% early game - secondary rush units
        int scouts = countAlliedRobots(RobotType.SCOUT);
        int round = rc.getRoundNum();
        int maxScouts = 35; // Large scout force for early pressure
        
        // Early game priority scaling
        double scoutChance = 0.30; // 30% base priority
        if (round < 200) {
            scoutChance = 0.35; // 35% in very early game
        } else if (round < 500) {
            scoutChance = 0.30; // Maintain rush
        } else {
            scoutChance = 0.20; // Scale back later
        }
        
        return rc.canBuildRobot(RobotType.SCOUT, dir) && 
               Math.random() < scoutChance && 
               scouts < maxScouts;
    }

    private static boolean shouldBuildLumberjack(Direction dir) {
        // Early Rush Horde: Minimal lumberjacks - focus on rush not economy
        int round = rc.getRoundNum();
        
        // Very limited lumberjacks in early game
        if (round < 300) return false; // None early
        
        int lumberjacks = countAlliedRobots(RobotType.LUMBERJACK);
        int maxLumberjacks = 5; // Minimal force
        
        return rc.canBuildRobot(RobotType.LUMBERJACK, dir) && 
               Math.random() < 0.08 && // 8% - low priority
               lumberjacks < maxLumberjacks;
    }

    private static boolean shouldPlantTree(Direction dir) {
        // Early Rush Horde: NO tree planting in first 500 rounds
        int round = rc.getRoundNum();
        
        // Completely disabled early to preserve bullets for rush
        if (round < 500) return false;
        
        // Very limited tree planting after round 500
        int trees = countTeamTrees();
        if (trees > 10) return false; // Minimal trees
        
        return rc.canPlantTree(dir) && 
               Math.random() < 0.10; // 10% chance - very low
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
        // Early Rush Horde: Minimal tree watering - focus on rush
        int round = rc.getRoundNum();
        
        // No tree watering early game
        if (round < 600) return;
        
        TreeInfo[] trees = rc.senseNearbyTrees(-1, rc.getTeam());
        if (trees.length > 0) {
            // Only water if we have excess bullets
            if (rc.getTeamBullets() > 200) {
                TreeInfo bestTree = null;
                float lowestHealth = Float.MAX_VALUE;
                
                for (TreeInfo tree : trees) {
                    if (rc.canWater(tree.location) && tree.health < lowestHealth) {
                        lowestHealth = tree.health;
                        bestTree = tree;
                    }
                }
                
                if (bestTree != null) {
                    rc.water(bestTree.location);
                }
            }
        }
    }

    private static float getDonateAmount() throws GameActionException {
        // Early Rush Horde: NO VP donations until round 800
        int round = rc.getRoundNum();
        
        // Completely disabled early to preserve all bullets for rush
        if (round < 800) return 0f;
        
        // Late game only VP pressure
        float bullets = rc.getTeamBullets();
        float vpCost = rc.getVictoryPointCost();
        
        // Only donate if we have massive bullet excess
        if (bullets > 300 && bullets >= vpCost * 3) {
            float donateAmount = (float)Math.floor((bullets - 100) / vpCost) * vpCost;
            if (donateAmount > 0) return donateAmount;
        }
        
        return 0f;
    }

    public static void spendPolicy() throws GameActionException {
        // Early Rush Horde: All-out early military pressure, no economy
        
        if (rc.getType() == RobotType.ARCHON) {
            // Archons hire maximum gardeners immediately for rush
            Direction dir = randomDirection();
            
            if (shouldHireGardener(dir)) {
                rc.hireGardener(dir);
            }
        } 
        else if (rc.getType() == RobotType.GARDENER) {
            // Early Rush Horde: Pure rush unit production
            
            // Priority 1: Minimal tree watering only late game
            if (rc.getRoundNum() >= 600 && Math.random() < 0.20) { // 20% chance late
                tryWaterTrees();
                if (rc.hasMoved()) return;
            }
            
            // Priority 2: Build rush units - soldiers and scouts dominate
            Direction dir = randomDirection();
            int round = rc.getRoundNum();
            
            // Early game: pure rush composition
            if (round < 500) {
                // Soldier 35% - primary rush force
                if (Math.random() < 0.35 && shouldBuildSoldier(dir)) {
                    rc.buildRobot(RobotType.SOLDIER, dir);
                }
                // Scout 30% - secondary rush force  
                else if (Math.random() < 0.30 && shouldBuildScout(dir)) {
                    rc.buildRobot(RobotType.SCOUT, dir);
                }
                // Minimal lumberjacks only after round 300
                else if (round >= 300 && Math.random() < 0.08 && shouldBuildLumberjack(dir)) {
                    rc.buildRobot(RobotType.LUMBERJACK, dir);
                }
                // No tree planting before round 500
            }
            // Later game: still rush-focused but some economy
            else {
                // Still prioritize rush units
                if (Math.random() < 0.25 && shouldBuildSoldier(dir)) {
                    rc.buildRobot(RobotType.SOLDIER, dir);
                }
                else if (Math.random() < 0.20 && shouldBuildScout(dir)) {
                    rc.buildRobot(RobotType.SCOUT, dir);
                }
                // Limited trees after round 500
                else if (Math.random() < 0.10 && shouldPlantTree(dir)) {
                    rc.plantTree(dir);
                }
                // Very limited tanks late game
                else if (round >= 600 && Math.random() < 0.03 && shouldBuildTank(dir)) {
                    rc.buildRobot(RobotType.TANK, dir);
                }
            }
            
            // Minimal meta-gardener building
            dir = randomDirection();
            if (shouldBuildGardener(dir)) {
                rc.buildRobot(RobotType.GARDENER, dir);
            }
        }
        
        // Early Rush Horde: VP pressure only very late game
        float donateAmount = getDonateAmount();
        if (donateAmount > 0f) {
            rc.donate(donateAmount);
        }
    }

    private static Direction randomDirection() {
        return new Direction((float)Math.random() * 2 * (float)Math.PI);
    }
}