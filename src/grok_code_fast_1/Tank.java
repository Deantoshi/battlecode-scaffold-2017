package grok_code_fast_1;
import battlecode.common.*;

public strictfp class Tank {
    static RobotController rc;

    public static void run(RobotController rc) throws GameActionException {
        Tank.rc = rc;
        Nav.init(rc);

        while (true) {
            try {
                // Attack enemies
                RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
                if (enemies.length > 0) {
                    RobotInfo target = enemies[0];
                    Direction dir = rc.getLocation().directionTo(target.location);
                    if (rc.canFirePentadShot()) {
                        rc.firePentadShot(dir);
                    }
                    Nav.tryMoveTowards(target.location);
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