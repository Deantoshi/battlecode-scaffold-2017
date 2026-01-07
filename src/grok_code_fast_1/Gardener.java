package grok_code_fast_1;
import battlecode.common.*;

public strictfp class Gardener {
    static RobotController rc;
    static int unitCount = 0;

    public static void run(RobotController rc) throws GameActionException {
        Gardener.rc = rc;
        Nav.init(rc);

        while (true) {
            try {
                // Water trees
                TreeInfo[] trees = rc.senseNearbyTrees(-1, rc.getTeam());
                TreeInfo lowestHealth = null;
                for (TreeInfo tree : trees) {
                    if (rc.canWater(tree.ID) && (lowestHealth == null || tree.health < lowestHealth.health)) {
                        lowestHealth = tree;
                    }
                }
                if (lowestHealth != null) {
                    rc.water(lowestHealth.ID);
                }

                // Plant trees if possible
                if (trees.length < 6 && rc.canPlantTree(rc.getLocation().directionTo(rc.getLocation().add(0, 2)))) {
                    rc.plantTree(rc.getLocation().directionTo(rc.getLocation().add(0, 2)));
                }

                // Build units
                RobotType toBuild = null;
                if (unitCount == 0) toBuild = RobotType.SCOUT;
                else if (unitCount < 3) toBuild = RobotType.LUMBERJACK;
                else toBuild = RobotType.SOLDIER;

                if (toBuild != null && rc.canBuildRobot(toBuild, Direction.getNorth())) {
                    rc.buildRobot(toBuild, Direction.getNorth());
                    unitCount++;
                }

                // Move if crowded
                if (rc.senseNearbyRobots(2, rc.getTeam()).length > 2) {
                    Nav.tryMove(Utils.randomDirection());
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
            Clock.yield();
        }
    }
}