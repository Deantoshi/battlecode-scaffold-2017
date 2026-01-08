package minimax_2_1;
import battlecode.common.*;

public strictfp class Archon {
    static RobotController rc;

    public static void run() throws GameActionException {
        System.out.println("I'm an archon!");
        while (true) {
            try {
                MapLocation myLocation = rc.getLocation();
                Comms.broadcastArchonLocation(myLocation);

                if (rc.getTeamBullets() > 200) {
                    if (Math.random() < 0.01) {
                        rc.donate(10);
                    }
                }

                Direction dir = Nav.randomDirection();
                if (rc.canHireGardener(dir) && rc.getTeamBullets() >= RobotType.GARDENER.bulletCost) {
                    if (Comms.getGardenerCount() < 3) {
                        rc.hireGardener(dir);
                    }
                }

                boolean moved = Nav.tryMove(Nav.randomDirection());

                Clock.yield();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
