package grok_code_fast_1;
import battlecode.common.*;

public strictfp class Lumberjack {
    static RobotController rc;

    public static void run(RobotController rc) throws GameActionException {
        Lumberjack.rc = rc;
        Nav.init(rc);

        while (true) {
            try {
                // Strike if enemies nearby
                RobotInfo[] enemies = rc.senseNearbyRobots(2, rc.getTeam().opponent());
                if (enemies.length > 0 && rc.canStrike()) {
                    rc.strike();
                }

                // Chop neutral trees
                TreeInfo[] trees = rc.senseNearbyTrees(2, Team.NEUTRAL);
                if (trees.length > 0 && rc.canChop(trees[0].ID)) {
                    rc.chop(trees[0].ID);
                }

                // Move towards enemies or trees
                if (enemies.length > 0) {
                    Nav.tryMoveTowards(enemies[0].location);
                } else if (trees.length > 0) {
                    Nav.tryMoveTowards(trees[0].location);
                } else {
                    Nav.tryMove(Utils.randomDirection());
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
            Clock.yield();
        }
    }
}