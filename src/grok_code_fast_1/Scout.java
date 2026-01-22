package grok_code_fast_1;
import battlecode.common.*;

public class Scout {
    static RobotController rc;

    public static void init(RobotController rc) {
        Scout.rc = rc;
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

            // Scouts can only fire single shot
            if (rc.canFireSingleShot()) {
                rc.fireSingleShot(dir);
            }
        }
    }
}