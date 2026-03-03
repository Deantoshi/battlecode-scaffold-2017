package haiku_4_5;
import battlecode.common.*;

public class Tank {
    static RobotController rc;

    public static void run() throws GameActionException {
        rc = RobotPlayer.getRc();
        Team enemy = rc.getTeam().opponent();

        while (true) {
            try {
                MapLocation myLoc = rc.getLocation();
                RobotInfo[] robots = rc.senseNearbyRobots(-1, enemy);

                if (robots.length > 0) {
                    RobotInfo target = robots[0];
                    Direction toTarget = myLoc.directionTo(target.location);

                    // Fire pentad if possible, else triad, else single
                    if (rc.canFirePentadShot()) {
                        rc.fireTriadShot(toTarget);
                    } else if (rc.canFireTriadShot()) {
                        rc.fireTriadShot(toTarget);
                    } else if (rc.canFireSingleShot()) {
                        rc.fireSingleShot(toTarget);
                    }

                    // Move towards target
                    Nav.tryMove(toTarget);
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
