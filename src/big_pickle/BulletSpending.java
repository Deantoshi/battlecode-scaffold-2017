package big_pickle;
import battlecode.common.*;

public class BulletSpending {
    static RobotController rc;

    public static void init(RobotController rc) {
        BulletSpending.rc = rc;
    }

    private static boolean shouldHireGardener(Direction dir) {
        return rc.canHireGardener(dir) && Math.random() < 0.8;
    }

    private static boolean shouldBuildGardener(Direction dir) {
        return rc.canBuildRobot(RobotType.GARDENER, dir) && Math.random() < 0.6;
    }

    private static boolean shouldBuildSoldier(Direction dir) {
        return rc.canBuildRobot(RobotType.SOLDIER, dir) && Math.random() < 0.1;
    }

    private static boolean shouldPlantTree(Direction dir) {
        return rc.canPlantTree(dir) && Math.random() < 0.7;
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
        // VP rush strategy: donate bullets when we have excess
        float bullets = rc.getTeamBullets();
        float vpCost = rc.getVictoryPointCost();

        // Donate when we have enough bullets for multiple VPs or when bullets are high
        if (bullets > 200 && bullets >= vpCost) {
            float donateAmount = (float)Math.floor((bullets - 100) / vpCost) * vpCost;
            if (donateAmount > 0) {
                return donateAmount;
            }
        }
        return 0f;
    }

    public static void spendPolicy() throws GameActionException {
        // Centralized spending policy based on robot type
        if (rc.getType() == RobotType.ARCHON) {
            // Archons focus on hiring gardeners
            Direction dir = randomDirection();
            if (shouldHireGardener(dir)) {
                rc.hireGardener(dir);
            }
        } else if (rc.getType() == RobotType.GARDENER) {
            // Gardeners prioritize building meta-gardeners, then trees
            Direction dir = randomDirection();
            if (shouldBuildGardener(dir)) {
                rc.buildRobot(RobotType.GARDENER, dir);
            } else if (shouldBuildSoldier(dir)) {
                rc.buildRobot(RobotType.SOLDIER, dir);
            }
            if (shouldPlantTree(dir)) {
                rc.plantTree(dir);
            }

            // Water existing trees
            tryWaterTrees();
        }
        
        // All robots can donate for VP
        float donateAmount = getDonateAmount();
        if (donateAmount > 0f) {
            rc.donate(donateAmount);
        }
    }

    private static Direction randomDirection() {
        return new Direction((float)Math.random() * 2 * (float)Math.PI);
    }
}
