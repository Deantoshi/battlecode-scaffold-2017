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

        // Dynamic build order based on round for hybrid strategy
        int round = rc.getRoundNum();
        RobotType[] currentBuildOrder;
        if (round < 500) {
            // Early defensive: prioritize LUMBERJACK and TANK
            currentBuildOrder = new RobotType[]{RobotType.LUMBERJACK, RobotType.TANK};
        } else if (round < 1000) {
            // Mid game: mixed defense and attack
            currentBuildOrder = new RobotType[]{RobotType.LUMBERJACK, RobotType.TANK, RobotType.SOLDIER, RobotType.SCOUT};
        } else {
            // Late game: focus on army push
            currentBuildOrder = new RobotType[]{RobotType.SOLDIER, RobotType.TANK, RobotType.SOLDIER, RobotType.SCOUT};
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
        for (TreeInfo tree : nearbyTrees) {
            if (rc.canWater(tree.ID)) {
                rc.water(tree.ID);
                return;
            }
        }
    }

    static Direction randomDirection() {
        return new Direction((float)Math.random() * 2 * (float)Math.PI);
    }
}