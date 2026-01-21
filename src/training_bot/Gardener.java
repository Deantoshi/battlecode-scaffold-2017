package training_bot;

import battlecode.common.*;

/**
 * Gardener - Economy and production unit.
 * Key responsibilities:
 * - Plant and water Bullet Trees for income
 * - Build combat units (Soldiers, Lumberjacks, Scouts, Tanks)
 * - Balance economy vs military production
 */
public strictfp class Gardener {
    private static RobotController rc;
    private static boolean settled = false;
    private static int treesPlanted = 0;
    private static final int MAX_TREES = 5;

    // Build order ratios
    private static int soldiersBuild = 0;
    private static int lumberjacksBuilt = 0;
    private static int scoutsBuilt = 0;
    private static int tanksBuilt = 0;

    public static void run(RobotController rc) throws GameActionException {
        Gardener.rc = rc;
        Navigation.init(rc);

        // Priority 1: Water nearby trees
        waterTrees();

        // Priority 2: If not settled, find a good spot
        if (!settled && treesPlanted == 0) {
            findSettleLocation();
        }

        // Priority 3: Plant trees if we have space
        if (treesPlanted < MAX_TREES) {
            tryPlantTree();
        }

        // Priority 4: Build combat units
        tryBuildUnits();
    }

    /**
     * Waters the lowest-health tree in range.
     */
    private static void waterTrees() throws GameActionException {
        TreeInfo[] trees = rc.senseNearbyTrees(2f, rc.getTeam());

        if (trees.length == 0) {
            return;
        }

        // Find tree with lowest health
        TreeInfo lowestHealthTree = null;
        float lowestHealth = Float.MAX_VALUE;

        for (TreeInfo tree : trees) {
            if (tree.health < lowestHealth && rc.canWater(tree.ID)) {
                lowestHealth = tree.health;
                lowestHealthTree = tree;
            }
        }

        if (lowestHealthTree != null) {
            rc.water(lowestHealthTree.ID);
        }
    }

    /**
     * Move to find a good location to settle and plant trees.
     */
    private static void findSettleLocation() throws GameActionException {
        MapLocation myLoc = rc.getLocation();

        // Check if current location is good (space for trees)
        TreeInfo[] nearbyTrees = rc.senseNearbyTrees(3f);
        RobotInfo[] nearbyRobots = rc.senseNearbyRobots(3f, rc.getTeam());

        // Settle if we have space
        if (nearbyTrees.length < 3 && nearbyRobots.length < 2) {
            settled = true;
            return;
        }

        // Move away from other units to find space
        if (nearbyRobots.length > 0) {
            float avgX = 0, avgY = 0;
            for (RobotInfo robot : nearbyRobots) {
                avgX += robot.location.x;
                avgY += robot.location.y;
            }
            avgX /= nearbyRobots.length;
            avgY /= nearbyRobots.length;

            Navigation.moveAwayFrom(new MapLocation(avgX, avgY));
        } else {
            Navigation.wander();
        }
    }

    /**
     * Attempts to plant a tree in an available direction.
     * Plants in a hexagonal pattern for efficiency.
     */
    private static void tryPlantTree() throws GameActionException {
        // Plant in 6 directions (hexagonal pattern), leaving 2 open for building
        // Using radians: 0 = East, PI/2 = North, PI = West, 3PI/2 = South
        float[] plantAngles = {
            0,                          // East
            (float) Math.PI / 3,        // 60 degrees
            (float) (2 * Math.PI / 3),  // 120 degrees
            (float) Math.PI,            // West
            (float) (4 * Math.PI / 3),  // 240 degrees
            (float) (5 * Math.PI / 3)   // 300 degrees
        };

        for (float angle : plantAngles) {
            Direction dir = new Direction(angle);
            if (rc.canPlantTree(dir)) {
                rc.plantTree(dir);
                treesPlanted++;
                settled = true;
                return;
            }
        }
    }

    /**
     * Builds combat units based on game state and build order.
     */
    private static void tryBuildUnits() throws GameActionException {
        // Determine what to build based on current counts and game state
        RobotType toBuild = determineBuildType();

        if (toBuild == null) {
            return;
        }

        // Try to build in available directions
        for (int i = 0; i < 8; i++) {
            Direction dir = new Direction(i * (float) Math.PI / 4);
            if (rc.canBuildRobot(toBuild, dir)) {
                rc.buildRobot(toBuild, dir);
                updateBuildCount(toBuild);
                return;
            }
        }
    }

    /**
     * Determines which unit type to build based on strategy.
     */
    private static RobotType determineBuildType() throws GameActionException {
        float bullets = rc.getTeamBullets();
        int round = rc.getRoundNum();

        // Need minimum bullets to build
        if (bullets < 100) {
            return null;
        }

        // Early game: Build lumberjacks to clear trees
        if (round < 100 && lumberjacksBuilt < 1) {
            return RobotType.LUMBERJACK;
        }

        // Early scouts for harass and vision
        if (round < 150 && scoutsBuilt < 1) {
            return RobotType.SCOUT;
        }

        // Main army composition: Soldiers
        if (soldiersBuild < 3) {
            return RobotType.SOLDIER;
        }

        // Late game: Add tanks if we have economy
        if (round > 500 && bullets > 400 && tanksBuilt < 2) {
            return RobotType.TANK;
        }

        // Maintain lumberjack presence
        if (lumberjacksBuilt < soldiersBuild / 3) {
            return RobotType.LUMBERJACK;
        }

        // Default to soldiers
        if (bullets > 150) {
            return RobotType.SOLDIER;
        }

        return null;
    }

    /**
     * Updates build counters.
     */
    private static void updateBuildCount(RobotType type) throws GameActionException {
        switch (type) {
            case SOLDIER:
                soldiersBuild++;
                Comms.incrementCounter(Comms.CHANNEL_SOLDIER_COUNT);
                break;
            case LUMBERJACK:
                lumberjacksBuilt++;
                Comms.incrementCounter(Comms.CHANNEL_LUMBERJACK_COUNT);
                break;
            case SCOUT:
                scoutsBuilt++;
                Comms.incrementCounter(Comms.CHANNEL_SCOUT_COUNT);
                break;
            case TANK:
                tanksBuilt++;
                Comms.incrementCounter(Comms.CHANNEL_TANK_COUNT);
                break;
            default:
                break;
        }
    }
}
