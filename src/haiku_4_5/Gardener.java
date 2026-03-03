package haiku_4_5;
import battlecode.common.*;

public class Gardener {
    static RobotController rc;

    public static void run() throws GameActionException {
        rc = RobotPlayer.getRc();

        while (true) {
            try {
                BulletSpending.spendPolicy();

                // Move towards nearby trees to water them
                MapLocation myLoc = rc.getLocation();
                TreeInfo[] trees = rc.senseNearbyTrees(-1, rc.getTeam());

                if (trees.length > 0) {
                    // Move towards first team tree to water it
                    Direction toTree = myLoc.directionTo(trees[0].location);
                    Nav.tryMove(toTree);

                    // Try to water
                    if (rc.canWater(trees[0].ID)) {
                        rc.water(trees[0].ID);
                    }
                } else {
                    Nav.tryMove(Utils.randomDirection());
                }

                Clock.yield();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
