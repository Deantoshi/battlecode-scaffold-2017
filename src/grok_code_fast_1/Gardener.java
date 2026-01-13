package grok_code_fast_1;
import battlecode.common.*;

public strictfp class Gardener {
    static RobotController rc;
    static int treesPlanted = 0;
    static Direction buildDirection = Direction.SOUTH;

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
        if (treesPlanted < 2) {
            tryPlantTree();
        } else {
            tryBuildUnit();
        }
        Nav.tryMove(Nav.randomDirection());
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
            if (Math.abs(dir.radians - buildDirection.radians) < 0.5f) continue;
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
        if (round < 50) {
            toBuild = RobotType.LUMBERJACK;
        } else if (round < 200) {
            toBuild = RobotType.SOLDIER;
        } else {
            toBuild = Math.random() < 0.7 ? RobotType.SOLDIER : RobotType.TANK;
        }
        if (rc.canBuildRobot(toBuild, buildDirection)) {
            rc.buildRobot(toBuild, buildDirection);
            return true;
        }
        return false;
    }
}
