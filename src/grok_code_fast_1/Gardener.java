package grok_code_fast_1;
import battlecode.common.*;

public class Gardener {
    static RobotController rc;
    static int lumberjacksBuilt = 0; // keep for tracking, but not limit
    static int buildCounter = 0;
    static RobotType[] buildOrder = {RobotType.LUMBERJACK, RobotType.SOLDIER, RobotType.TANK, RobotType.SCOUT};

    public static void init(RobotController rc) {
        Gardener.rc = rc;
    }

    public static void buildRobot() throws GameActionException {
        System.out.println("Gardener bullets: " + rc.getTeamBullets());
        System.out.println("Build counter: " + buildCounter);

        // Late Game Scaling: Delay military builds past round 1000, prioritize economy scaling with few insurance units
        int round = rc.getRoundNum();
        RobotType[] currentBuildOrder;
        if (round < 1000) {
            // Early: focus on tree economy, build minimal LUMBERJACK occasionally for protection
            if (buildCounter % 5 == 0) {  // Very rare, every 5th attempt
                currentBuildOrder = new RobotType[]{RobotType.LUMBERJACK};
            } else {
                // Focus entirely on trees
                return;
            }
        } else {
            // Late: build insurance units LUMBERJACK and TANK to protect scaling economy
            currentBuildOrder = new RobotType[]{RobotType.LUMBERJACK, RobotType.TANK};
        }

        int orderLength = currentBuildOrder.length;
        int typeIndex = buildCounter % orderLength;
        RobotType type = currentBuildOrder[typeIndex];
        int cost = type.bulletCost;

        if (rc.getTeamBullets() >= cost && rc.isBuildReady()) {
            System.out.println("Attempting to build " + type);
            for (int attempt = 0; attempt < 8; attempt++) {
                Direction dir = Direction.getNorth().rotateLeftDegrees(attempt * 45);
                if (rc.canBuildRobot(type, dir)) {
                    rc.buildRobot(type, dir);
                    if (type == RobotType.LUMBERJACK) {
                        lumberjacksBuilt++;
                    }
                    buildCounter++;
                    System.out.println("Built " + type + " successfully");
                    return;
                }
            }
            System.out.println("Failed to build " + type + " in all attempts");
        }
    }

    public static void plantTree() throws GameActionException {
        System.out.println("Trying to plant tree, bullets: " + rc.getTeamBullets());
        if (rc.getTeamBullets() >= 50) {
            Direction[] dirs = {
                Direction.getNorth(),
                Direction.getEast(),
                Direction.getSouth(),
                Direction.getWest(),
                Direction.getNorth().rotateRightDegrees(45), // NE
                Direction.getEast().rotateRightDegrees(45), // SE
                Direction.getSouth().rotateRightDegrees(45), // SW
                Direction.getWest().rotateRightDegrees(45) // NW
            };
            for (Direction dir : dirs) {
                if (rc.canPlantTree(dir)) {
                    rc.plantTree(dir);
                    System.out.println("Planted tree!");
                    return;
                }
            }
        }
    }

    public static void waterTree() throws GameActionException {
        TreeInfo[] nearbyTrees = rc.senseNearbyTrees(2, rc.getTeam());
        // For optimization, water the tree with lowest health if possible.
        TreeInfo bestTree = null;
        float lowestHealth = Float.MAX_VALUE;
        for (TreeInfo tree : nearbyTrees) {
            if (rc.canWater(tree.ID) && tree.health < lowestHealth) {
                lowestHealth = tree.health;
                bestTree = tree;
            }
        }
        if (bestTree != null) {
            rc.water(bestTree.ID);
        }
    }

    static Direction randomDirection() {
        return new Direction((float)Math.random() * 2 * (float)Math.PI);
    }
}