package minimax_2_1;
import battlecode.common.*;

public strictfp class Gardener {
    static RobotController rc;
    static int treesPlanted = 0;
    static int maxTrees = 15;
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
        
        waterLowestHealthTree();
        
        RobotInfo[] nearbyEnemies = rc.senseNearbyRobots(10, rc.getTeam().opponent());
        boolean enemiesNearby = nearbyEnemies.length > 0;
        
        // PRIORITY 1: Build combat units if enemies are nearby
        if (enemiesNearby) {
            if (tryBuildUnit()) {
                return;
            }
        }
        
        // PRIORITY 2: Build soldiers throughout the game (not just trees!)
        // Only plant trees after we've built some soldiers
        if (tryBuildUnit()) {
            return;
        }
        
        // PRIORITY 3: Plant trees (but don't overdo it - soldiers win games)
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
        
        // Build diverse army based on game phase
        if (round < 300) {
            // Early game: Scouts for harassment, Soldiers for defense
            double rand = Math.random();
            if (rand < 0.6) {
                toBuild = RobotType.SCOUT;  // Fast, good for early harassment
            } else {
                toBuild = RobotType.SOLDIER;
            }
        } else if (round < 1000) {
            // Mid game: Soldiers + Lumberjacks for melee pressure
            double rand = Math.random();
            if (rand < 0.7) {
                toBuild = RobotType.SOLDIER;
            } else if (rand < 0.9) {
                toBuild = RobotType.LUMBERJACK;
            } else {
                toBuild = RobotType.TANK;
            }
        } else {
            // Late game: Heavy hitters
            double rand = Math.random();
            if (rand < 0.5) {
                toBuild = RobotType.SOLDIER;
            } else if (rand < 0.8) {
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
