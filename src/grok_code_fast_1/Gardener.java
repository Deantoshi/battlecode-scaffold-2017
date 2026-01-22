package grok_code_fast_1;
import battlecode.common.*;

public class Gardener {
    static RobotController rc;
    static int scoutsBuilt = 0;
    static int lumberjacksBuilt = 0;
    static int tanksBuilt = 0;
    static int factoriesBuilt = 0;

    public static void init(RobotController rc) {
        Gardener.rc = rc;
    }

    public static void buildRobot() throws GameActionException {
        System.out.println("Gardener bullets: " + rc.getTeamBullets());
        System.out.println("Scouts built: " + scoutsBuilt);
        System.out.println("Tanks built: " + tanksBuilt);

        // Prioritize building tanks when affordable, then scouts for protection
        if (tanksBuilt < 5 && rc.getTeamBullets() >= 300) {
            System.out.println("Bullets >=300, attempting to build tank");
            for (int attempt = 0; attempt < 8; attempt++) {
                Direction dir = Direction.getNorth().rotateLeftDegrees(attempt * 45);
                if (rc.canBuildRobot(RobotType.TANK, dir)) {
                    if (rc.isBuildReady()) {
                        rc.buildRobot(RobotType.TANK, dir);
                        tanksBuilt++;
                        System.out.println("Built tank successfully");
                        return;
                    } else {
                        System.out.println("Build not ready for tank");
                    }
                } else {
                    System.out.println("Cannot build tank in direction " + dir);
                }
            }
            System.out.println("Failed to build tank in all directions");
        } else if (scoutsBuilt < 15 && rc.getTeamBullets() >= 80) {
            System.out.println("Attempting to build scout");
            for (int attempt = 0; attempt < 8; attempt++) {
                Direction dir = Direction.getNorth().rotateLeftDegrees(attempt * 45);
                if (rc.canBuildRobot(RobotType.SCOUT, dir) && rc.isBuildReady()) {
                    rc.buildRobot(RobotType.SCOUT, dir);
                    scoutsBuilt++;
                    System.out.println("Built scout successfully");
                    return;
                }
            }
            System.out.println("Failed to build scout in all attempts");
        }
    }

    public static void plantTree() throws GameActionException {
        System.out.println("Trying to plant tree, bullets: " + rc.getTeamBullets());
        if (rc.getTeamBullets() >= 50 && rc.getTeamBullets() < 80) {
            for (int attempt = 0; attempt < 8; attempt++) {
                Direction dir = randomDirection();
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