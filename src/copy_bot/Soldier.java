package copy_bot;
import battlecode.common.*;

public strictfp class Soldier {
    static RobotController rc;

    public static void run(RobotController rc) throws GameActionException {
        Soldier.rc = rc;
        Nav.init(rc);
        Comms.init(rc);

        while (true) {
            try {
                doTurn();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                Clock.yield();
            }
        }
    }

    static void doTurn() throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemies.length > 0) {
            RobotInfo target = findTarget();
            if (tryShoot(target)) {
                return;
            }
        }

        MapLocation enemyArchon = Comms.getEnemyArchonLocation();
        if (enemyArchon != null) {
            if (Nav.moveToward(enemyArchon)) {
                return;
            }
        }

        Nav.tryMove(Nav.randomDirection());
    }

    static RobotInfo findTarget() throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        return Utils.findLowestHealthTarget(enemies);
    }

    static boolean tryShoot(RobotInfo target) throws GameActionException {
        if (target == null) return false;
        Direction dir = rc.getLocation().directionTo(target.location);
        RobotInfo[] allies = rc.senseNearbyRobots(rc.getType().sensorRadius, rc.getTeam());
        for (RobotInfo ally : allies) {
            Direction toAlly = rc.getLocation().directionTo(ally.location);
            float dist = rc.getLocation().distanceTo(ally.location);
            float distToTarget = rc.getLocation().distanceTo(target.location);
            if (dist < distToTarget && Math.abs(dir.degreesBetween(toAlly)) < 15) {
                return false;
            }
        }
        if (rc.canFireSingleShot()) {
            rc.fireSingleShot(dir);
            return true;
        }
        return false;
    }
}
