package minimax_2_1;
import battlecode.common.*;

public strictfp class Gardener {
    static RobotController rc;
    static int treesPlanted = 0;
    static int maxTrees = 5;
    static int unitBuildRoundTrigger = 5;
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
        RobotInfo[] nearbyEnemies = rc.senseNearbyRobots(10, rc.getTeam().opponent());
        boolean enemiesNearby = nearbyEnemies.length > 0;
        
        boolean shouldBuildUnits = false;
        if (enemiesNearby) {
            shouldBuildUnits = true;
        } else if (rc.getTeamBullets() >= 120 && treesPlanted >= 1) {
            shouldBuildUnits = true;
        } else if (round > 80 && treesPlanted >= 1) {
            shouldBuildUnits = true;
        } else if (treesPlanted >= maxTrees) {
            shouldBuildUnits = true;
        } else if (round > unitBuildRoundTrigger) {
            shouldBuildUnits = true;
        }
        
        if (shouldBuildUnits) {
            if (tryBuildUnit()) {
                return;
            }
        }
        
        if (treesPlanted < maxTrees || (round < 100 && treesPlanted < maxTrees + 4)) {
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
        for (int i = 0; i < 8; i++) {
            Direction dir = new Direction(i * (float)Math.PI / 4);
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
        if (round < 300) {
            toBuild = RobotType.SOLDIER;
        } else if (round < 800) {
            toBuild = Math.random() < 0.7 ? RobotType.SOLDIER : RobotType.TANK;
        } else {
            toBuild = Math.random() < 0.5 ? RobotType.SOLDIER : RobotType.TANK;
        }
        
        for (int i = 0; i < 8; i++) {
            Direction dir = new Direction(i * (float)Math.PI / 4);
            if (rc.canBuildRobot(toBuild, dir)) {
                rc.buildRobot(toBuild, dir);
                buildDirection = dir;
                return true;
            }
        }
        return false;
    }
}
