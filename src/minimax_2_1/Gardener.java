package minimax_2_1;
import battlecode.common.*;

public strictfp class Gardener {
    static RobotController rc;
    static int treesPlanted = 0;
    static int maxTrees = 3;
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
        int round = rc.getRoundNum();
        
        if (treesPlanted < 3) {
            if (tryPlantTree()) {
                return;
            }
        }
        
        RobotInfo[] nearbyEnemies = rc.senseNearbyRobots(10, rc.getTeam().opponent());
        boolean enemiesNearby = nearbyEnemies.length > 0;
        
        if (enemiesNearby) {
            if (tryBuildUnit()) {
                return;
            }
        }
        
        boolean shouldBuildUnits = true;
        
        if (shouldBuildUnits) {
            if (tryBuildUnit()) {
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
        
        if (round < 200) {
            toBuild = RobotType.SCOUT;
        } else if (round < 600) {
            double rand = Math.random();
            if (rand < 0.95) {
                toBuild = RobotType.SOLDIER;
            } else {
                toBuild = RobotType.TANK;
            }
        } else {
            double rand = Math.random();
            if (rand < 0.7) {
                toBuild = RobotType.SOLDIER;
            } else if (rand < 0.95) {
                toBuild = RobotType.TANK;
            } else {
                toBuild = RobotType.LUMBERJACK;
            }
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
