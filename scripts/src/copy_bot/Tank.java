package copy_bot;
import battlecode.common.*;

public class Tank {
    static RobotController rc;

    public static void init(RobotController rc) {
        Tank.rc = rc;
    }

    public static void fire() throws GameActionException {
        Team enemy = rc.getTeam().opponent();
        // See if there are any nearby enemy robots
        RobotInfo[] robots = rc.senseNearbyRobots(-1, enemy);

        // If there are some...
        if (robots.length > 0) {
            // Prioritize archons
            RobotInfo target = null;
            for (RobotInfo robot : robots) {
                if (robot.type == RobotType.ARCHON) {
                    target = robot;
                    break;
                }
            }
            if (target == null) {
                target = robots[0];
            }
            MapLocation enemyLoc = target.location;
            float dist = rc.getLocation().distanceTo(enemyLoc);
            Direction dir = rc.getLocation().directionTo(enemyLoc);

            // Use area shots when close for efficiency, adapted for tank range
            if (dist <= 2 && rc.canFirePentadShot()) {
                rc.firePentadShot(dir);
            } else if (dist <= 3 && rc.canFireTriadShot()) {
                rc.fireTriadShot(dir);
            } else if (rc.canFireSingleShot()) {
                rc.fireSingleShot(dir);
            }
        }
    }
}