package grok_code_fast_1;
import battlecode.common.*;

public class Gardener {
    static RobotController rc;
    static int lumberjacksBuilt = 0;

    public static void init(RobotController rc) {
        Gardener.rc = rc;
    }

    public static void buildRobot() throws GameActionException {
        System.out.println("Gardener bullets: " + rc.getTeamBullets());
        System.out.println("Lumberjacks built: " + lumberjacksBuilt);

        // Build lumberjacks for defense
        if (lumberjacksBuilt < 50 && rc.getTeamBullets() >= 100) {
            System.out.println("Attempting to build lumberjack");
            for (int attempt = 0; attempt < 8; attempt++) {
                Direction dir = Direction.getNorth().rotateLeftDegrees(attempt * 45);
                if (rc.canBuildRobot(RobotType.LUMBERJACK, dir) && rc.isBuildReady()) {
                    rc.buildRobot(RobotType.LUMBERJACK, dir);
                    lumberjacksBuilt++;
                    System.out.println("Built lumberjack successfully");
                    return;
                }
            }
            System.out.println("Failed to build lumberjack in all attempts");
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