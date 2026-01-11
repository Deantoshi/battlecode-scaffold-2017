package minimax_2_1;
import battlecode.common.*;

public strictfp class Gardener {
    static RobotController rc;
    static int treesPlanted = 0;
    static int maxTrees = 5;
    static int unitBuildRoundTrigger = 50;
    static Direction treeDirection = Direction.SOUTH;
    static Direction buildDirection = Direction.NORTH;

    public static void run(RobotController rc) throws GameActionException {
        Gardener.rc = rc;
        Nav.init(rc);
        Comms.init(rc);

        while (true) {
            try {
                doTurn();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                Clock.yield();
            }
        }
    }

    static void doTurn() throws GameActionException {
        waterLowestHealthTree();

        int round = rc.getRoundNum();
        boolean shouldBuildUnits = (round > unitBuildRoundTrigger && treesPlanted >= 2) || treesPlanted >= maxTrees;
        
        if (shouldBuildUnits) {
            if (tryBuildUnit()) {
                return;
            }
        }
        
        if (treesPlanted < maxTrees) {
            if (tryPlantTree()) {
                return;
            }
        }

        if (!rc.hasMoved()) {
            Nav.tryMove(Nav.randomDirection());
        }
    }

    static void waterLowestHealthTree() throws GameActionException {
        TreeInfo[] trees = rc.senseNearbyTrees(2.0f, rc.getTeam());
        TreeInfo lowestTree = null;
        float lowestHealth = Float.MAX_VALUE;
        for (TreeInfo tree : trees) {
            if (rc.canWater(tree.ID) && tree.health < lowestHealth) {
                lowestHealth = tree.health;
                lowestTree = tree;
            }
        }
        if (lowestTree != null) {
            rc.water(lowestTree.ID);
        }
    }

    static boolean tryPlantTree() throws GameActionException {
        for (int i = 0; i < 6; i++) {
            Direction dir = new Direction(i * (float)Math.PI / 3);
            if (Math.abs(dir.radians - treeDirection.radians) < 0.5f) continue;
            if (rc.canPlantTree(dir)) {
                rc.plantTree(dir);
                treesPlanted++;
                return true;
            }
        }
        return false;
    }

    static boolean tryBuildUnit() throws GameActionException {
        int round = rc.getRoundNum();
        RobotType toBuild;
        if (round < 200) {
            toBuild = RobotType.SOLDIER;
        } else if (round < 500) {
            toBuild = Math.random() < 0.8 ? RobotType.SOLDIER : RobotType.TANK;
        } else {
            toBuild = Math.random() < 0.6 ? RobotType.SOLDIER : RobotType.TANK;
        }
        
        for (int i = 0; i < 6; i++) {
            Direction dir = new Direction(i * (float)Math.PI / 3);
            if (rc.canBuildRobot(toBuild, dir)) {
                rc.buildRobot(toBuild, dir);
                buildDirection = dir;
                return true;
            }
        }
        return false;
    }
}
