package minimax_2_1;
import battlecode.common.*;

public strictfp class Gardener {
    static RobotController rc;
    static int treesPlanted = 0;
    static int maxTrees = 15;
    static Direction treeDirection = Direction.SOUTH;
    static Direction buildDirection = Direction.NORTH;
    static boolean treeDenseMap = false;
    
    // Strategic thresholds
    static final int TREE_DENSITY_DETECTION_THRESHOLD = 10;  // Trees nearby to detect dense map

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
        
        // FIX: Detect tree-dense map (Shrine, Bullseye, Blitzkrieg)
        if (!treeDenseMap && round < 50) {
            TreeInfo[] nearbyTrees = rc.senseNearbyTrees(15.0f, Team.NEUTRAL);
            if (nearbyTrees.length > TREE_DENSITY_DETECTION_THRESHOLD) {
                treeDenseMap = true;
                maxTrees = 8;  // FIX: Reduce max trees on dense maps (was 15)
            }
        }
        
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
        // FIX: Prioritize army building - soldiers win games
        if (tryBuildUnit()) {
            return;
        }
        
        // PRIORITY 3: Plant trees (but reduce on tree-dense maps)
        // FIX: On Shrine/Bullseye/Blitzkrieg, plant even fewer trees
        int effectiveMaxTrees = treeDenseMap ? 6 : maxTrees;
        
        if (treesPlanted < effectiveMaxTrees) {
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
        // Plant trees only in diagonal directions (every other direction)
        // This reserves cardinal directions (N, S, E, W) as spawn lanes for Archons
        for (int i = 0; i < 8; i += 2) {
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
            // Early game: Scouts for early intel (80%), soldiers for defense (20%)
            double rand = Math.random();
            if (rand < 0.8) {
                toBuild = RobotType.SCOUT;  // Priority: find enemy archon by R150
            } else {
                toBuild = RobotType.SOLDIER;
            }
        } else if (round < 1000) {
            // Mid game: Soldiers + Tanks, NO LUMBERJACKS (80% death rate = feeding)
            double rand = Math.random();
            if (rand < 0.8) {
                toBuild = RobotType.SOLDIER;
            } else {
                toBuild = RobotType.TANK;  // Late game tanks instead of lumberjacks
            }
        } else {
            // Late game: Heavy hitters, NO LUMBERJACKS
            double rand = Math.random();
            if (rand < 0.6) {
                toBuild = RobotType.SOLDIER;
            } else {
                toBuild = RobotType.TANK;
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
