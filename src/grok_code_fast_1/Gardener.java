package grok_code_fast_1;
import battlecode.common.*;

public strictfp class Gardener {
    static RobotController rc;
    static int treesPlanted = 0;
    static Direction buildDirection = Direction.SOUTH;
    static int buildCount = 0;

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
        // Alternate: plant tree every other turn or when no units to build
        if (treesPlanted < 3 || rc.getTeamBullets() < 50) {
            if (tryPlantTree()) {
                treesPlanted++;
            }
        } else {
            tryBuildUnit();
        }
        if (!rc.hasMoved()) {
            // Move away from archon and other gardeners for spacing
            Direction away = rc.getLocation().directionTo(Comms.readLocation(0,1)).opposite();
            Nav.tryMove(away);
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

    static boolean tryBuildUnit() throws GameActionException {
        RobotType toBuild;
        int cycle = buildCount % 4;
        if (cycle == 0) toBuild = RobotType.SOLDIER;
        else if (cycle == 1) toBuild = RobotType.SCOUT;
        else if (cycle == 2) toBuild = RobotType.LUMBERJACK;
        else toBuild = RobotType.TANK;
        if (rc.canBuildRobot(toBuild, buildDirection)) {
            rc.buildRobot(toBuild, buildDirection);
            buildCount++;
            return true;
        }
        return false;
    }

    static boolean tryPlantTree() throws GameActionException {
        Direction[] dirs = Utils.getDirections();
        for (Direction dir : dirs) {
            if (rc.canPlantTree(dir)) {
                rc.plantTree(dir);
                return true;
            }
        }
        return false;
    }
}
