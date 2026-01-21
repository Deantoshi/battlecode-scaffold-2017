package grok_code_fast_1;
import battlecode.common.*;

public class Soldier {
    static RobotController rc;

    public static void init(RobotController rc) {
        Soldier.rc = rc;
    }

    public static void fireSingleShot() throws GameActionException {
        Team enemy = rc.getTeam().opponent();
        // See if there are any nearby enemy robots
        RobotInfo[] robots = rc.senseNearbyRobots(-1, enemy);

        // If there are some...
        if (robots.length > 0) {
            RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
            MapLocation enemyLoc = robots[0].location;
            float distToEnemy = rc.getLocation().distanceTo(enemyLoc);
            boolean safe = true;
            for(RobotInfo ally : allies){
                float distToAlly = rc.getLocation().distanceTo(ally.location);
                if(distToAlly < distToEnemy && ally.location.distanceTo(enemyLoc) < distToEnemy){
                    safe = false;
                    break;
                }
            }
            // And we have enough bullets, and haven't attacked yet this turn
            if (safe && rc.canFireSingleShot()) {
                // Fire a bullet in the direction of the enemy
                rc.fireSingleShot(rc.getLocation().directionTo(enemyLoc));
            }
        }
    }
}