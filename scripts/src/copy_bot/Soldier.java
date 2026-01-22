package copy_bot;
import battlecode.common.*;

public class Soldier {
    static RobotController rc;

    public static void init(RobotController rc) {
        Soldier.rc = rc;
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

            // Check for friendly robots near the enemy location to avoid friendly fire
            RobotInfo[] friendlies = rc.senseNearbyRobots(enemyLoc, 3, rc.getTeam());
            boolean hasFriendliesNear = friendlies.length > 0;

            // Use area shots when close for efficiency, but only if no friendlies near
            if (dist < 5 && rc.canFirePentadShot() && !hasFriendliesNear) {
                rc.firePentadShot(dir);
            } else if (dist < 7 && rc.canFireTriadShot() && !hasFriendliesNear) {
                rc.fireTriadShot(dir);
            } else if (rc.canFireSingleShot()) {
                rc.fireSingleShot(dir);
            }
        }
    }
}