package grok_code_fast_1;
import battlecode.common.*;

public strictfp class Scout {
    static RobotController rc;

    public static void run(RobotController rc) throws GameActionException {
        Scout.rc = rc;
        Nav.init(rc);

        while (true) {
            try {
                // Shake neutral trees
                TreeInfo[] trees = rc.senseNearbyTrees(-1, Team.NEUTRAL);
                for (TreeInfo tree : trees) {
                    if (rc.canShake(tree.ID)) {
                        rc.shake(tree.ID);
                        break;
                    }
                }

                // Broadcast enemy positions
                RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
                if (enemies.length > 0) {
                    Comms.broadcastEnemy(rc, enemies[0].location, Comms.ENEMY_SOLDIER_COUNT);
                }

                // Move randomly for scouting
                Nav.tryMove(Utils.randomDirection());

            } catch (Exception e) {
                e.printStackTrace();
            }
            Clock.yield();
        }
    }
}