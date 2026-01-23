package big_pickle;
import battlecode.common.*;

public class BulletSpending {
    static RobotController rc;

    public static void init(RobotController rc) {
        BulletSpending.rc = rc;
    }

    private static boolean shouldHireGardener(Direction dir) {
        return rc.canHireGardener(dir) && Math.random() < 0.75; // 75% for meta-gardeners
    }

    private static boolean shouldBuildGardener(Direction dir) {
        return rc.canBuildRobot(RobotType.GARDENER, dir) && Math.random() < 0.75; // 75% for meta-gardeners
    }

    private static boolean shouldBuildSoldier(Direction dir) {
        return rc.canBuildRobot(RobotType.SOLDIER, dir) && Math.random() < 0.1; // 10% for soldiers
    }

    private static boolean shouldBuildTank(Direction dir) {
        return rc.canBuildRobot(RobotType.TANK, dir) && Math.random() < 0.4; // 40% for tanks
    }

    private static boolean shouldPlantTree(Direction dir) {
        return rc.canPlantTree(dir) && Math.random() < 0.85; // 85% for trees
    }

    private static void tryWaterTrees() throws GameActionException {
        // Priority: water trees that need it most
        TreeInfo[] trees = rc.senseNearbyTrees(-1, rc.getTeam());
        if (trees.length > 0) {
            // Find the tree with lowest health
            TreeInfo lowestHealthTree = trees[0];
            for (TreeInfo tree : trees) {
                if (tree.health < lowestHealthTree.health) {
                    lowestHealthTree = tree;
                }
            }
            if (rc.canWater(lowestHealthTree.location)) {
                rc.water(lowestHealthTree.location);
            }
        }
    }

    private static float getDonateAmount() throws GameActionException {
        // Tank Fortress VP strategy: donate when we have larger excess for bigger chunks
        float bullets = rc.getTeamBullets();
        float vpCost = rc.getVictoryPointCost();

        // Donate when we have enough bullets for multiple VPs or when bullets are high (300 threshold)
        if (bullets > 300 && bullets >= vpCost) {
            float donateAmount = (float)Math.floor((bullets - 150) / vpCost) * vpCost;
            if (donateAmount > 0) {
                return donateAmount;
            }
        }
        return 0f;
    }

    public static void spendPolicy() throws GameActionException {
        // Centralized spending policy based on robot type for Tank Fortress
        if (rc.getType() == RobotType.ARCHON) {
            // Archons focus on hiring gardeners for rapid expansion
            Direction dir = randomDirection();
            if (shouldHireGardener(dir)) {
                rc.hireGardener(dir);
            }
        } else if (rc.getType() == RobotType.GARDENER) {
            // Tank Fortress: prioritize tree planting (85%), then tanks (40%), then soldiers (10%)
            Direction dir = randomDirection();
            
            // First try to build meta-gardeners for expansion
            if (shouldBuildGardener(dir)) {
                rc.buildRobot(RobotType.GARDENER, dir);
            } 
            // Then build tanks for defensive fortress
            else if (shouldBuildTank(dir)) {
                rc.buildRobot(RobotType.TANK, dir);
            }
            // Then build some soldiers for basic defense
            else if (shouldBuildSoldier(dir)) {
                rc.buildRobot(RobotType.SOLDIER, dir);
            }
            
            // High priority on tree planting for massive farm
            if (shouldPlantTree(dir)) {
                rc.plantTree(dir);
            }

            // Water existing trees to maintain farm
            tryWaterTrees();
        }
        
        // All robots can donate for VP when we have excess
        float donateAmount = getDonateAmount();
        if (donateAmount > 0f) {
            rc.donate(donateAmount);
        }
    }

    private static Direction randomDirection() {
        return new Direction((float)Math.random() * 2 * (float)Math.PI);
    }
}