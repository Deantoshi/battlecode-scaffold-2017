package copy_bot;
import battlecode.common.*;

public class BulletSpending {
    static RobotController rc;
    static int gardenersHired = 0;
    static int lumberjacksBuilt = 0;
    static int buildCounter = 0;

    public static void init(RobotController rc) {
        BulletSpending.rc = rc;
    }

    public static int getGardenersHired() {
        return gardenersHired;
    }

    public static void tryHireGardener(Direction dir) throws GameActionException {
        if (rc.canHireGardener(dir)) {
            rc.hireGardener(dir);
            gardenersHired++;
        }
    }

    public static void donateForVP() throws GameActionException {
        // VP donation logic: aggressive once bullet income is high
        float bullets = rc.getTeamBullets();
        int round = rc.getRoundNum();
        float cost = rc.getVictoryPointCost();
        // Aggressive donation when bullets are abundant
        if (bullets > 200 && bullets >= cost) {
            // Donate all bullets above a small reserve
            float donateAmount = bullets - 50;
            int pointsToBuy = (int)(donateAmount / cost);
            float actualDonate = pointsToBuy * cost;
            if (actualDonate >= cost) {
                rc.donate(actualDonate);
            }
        }
    }

    public static void buildRobot() throws GameActionException {
        System.out.println("Gardener bullets: " + rc.getTeamBullets());
        System.out.println("Build counter: " + buildCounter);

        // Build units only very late and sparingly for protection, prioritizing LUMBERJACK
        int round = rc.getRoundNum();
        if (round < 1500) {
            // No units before very late game
            return;
        }
        // Very seldom, every 15th attempt, build LUMBERJACK for protection
        if (buildCounter % 15 != 0) {
            return;
        }
        RobotType type = RobotType.LUMBERJACK;
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
}