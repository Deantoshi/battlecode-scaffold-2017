package minimax_2_1;
import battlecode.common.*;

public strictfp class Gardener {
    static RobotController rc;
    static int gardenersBuilt = 0;

    public static void run() throws GameActionException {
        System.out.println("I'm a gardener!");
        MapLocation archonLoc = Comms.getArchonLocation();
        if (archonLoc != null && rc.getLocation().distanceTo(archonLoc) < 5) {
            Direction awayFromArchon = rc.getLocation().directionTo(archonLoc).opposite();
            Nav.tryMove(awayFromArchon);
        }
        while (true) {
            try {
                TreeInfo[] nearbyTrees = rc.senseNearbyTrees(-1, rc.getTeam());
                TreeInfo lowestHealthTree = null;
                float minHealth = Float.MAX_VALUE;
                for (TreeInfo tree : nearbyTrees) {
                    if (tree.health < minHealth && tree.health < 100) {
                        minHealth = tree.health;
                        lowestHealthTree = tree;
                    }
                }
                if (lowestHealthTree != null && rc.canWater(lowestHealthTree.ID)) {
                    rc.water(lowestHealthTree.ID);
                }

                Direction plantDir = Nav.randomDirection();
                if (rc.canPlantTree(plantDir)) {
                    rc.plantTree(plantDir);
                }

                if (rc.isBuildReady() && rc.getTeamBullets() > 100) {
                    Direction buildDir = Nav.randomDirection();
                    if (rc.canBuildRobot(RobotType.SCOUT, buildDir)) {
                        rc.buildRobot(RobotType.SCOUT, buildDir);
                    } else if (rc.canBuildRobot(RobotType.LUMBERJACK, buildDir)) {
                        rc.buildRobot(RobotType.LUMBERJACK, buildDir);
                    } else if (rc.canBuildRobot(RobotType.SOLDIER, buildDir)) {
                        rc.buildRobot(RobotType.SOLDIER, buildDir);
                    }
                }

                Nav.tryMove(Nav.randomDirection());

                Clock.yield();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
