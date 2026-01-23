package grok_code_fast_1;
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
        if (gardenersHired < 20 && rc.canHireGardener(dir)) {
            rc.hireGardener(dir);
            gardenersHired++;
        }
    }

    public static void donateForVP() throws GameActionException {
        // VP donation logic: start earlier for accumulation, focus on scaling VP late game
        float bullets = rc.getTeamBullets();
        int round = rc.getRoundNum();
        if (round >= 300 && bullets > 200) {
            // Early: start donating to accumulate VP sooner
            float donateAmount = bullets - 100;
            float cost = rc.getVictoryPointCost();
            if (donateAmount >= cost) {
                int pointsToBuy = (int)(donateAmount / cost);
                float actualDonate = pointsToBuy * cost;
                if (actualDonate >= cost) {
                    rc.donate(actualDonate);
                }
            }
        } else if (round > 1800) {
            // Late: aggressive donation for VP rush if economy is scaled
            if (bullets > 50) {
                float donateAmount = bullets - 0;
                float cost = rc.getVictoryPointCost();
                if (donateAmount >= cost) {
                    rc.donate(donateAmount);
                }
            }
        }
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
}
