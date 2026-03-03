package haiku_4_5;
import battlecode.common.*;

public class Lumberjack {
    static RobotController rc;

    public static void run() throws GameActionException {
        rc = RobotPlayer.getRc();
        Team enemy = rc.getTeam().opponent();

        while (true) {
            try {
                MapLocation myLoc = rc.getLocation();

                // Look for nearby enemies to strike
                RobotInfo[] robots = rc.senseNearbyRobots(
                    RobotType.LUMBERJACK.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS, enemy);

                if (robots.length > 0 && rc.canStrike()) {
                    rc.strike();
                } else {
                    // Look for distant enemies
                    robots = rc.senseNearbyRobots(-1, enemy);

                    if (robots.length > 0) {
                        Direction toEnemy = myLoc.directionTo(robots[0].location);
                        Nav.tryMove(toEnemy);
                    } else {
                        Nav.tryMove(Utils.randomDirection());
                    }
                }

                Clock.yield();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
