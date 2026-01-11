package copy_bot;
import battlecode.common.*;

public strictfp class Tank {
    static RobotController rc;

    public static void run(RobotController rc) throws GameActionException {
        Tank.rc = rc;
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
        RobotInfo target = null;
        if (enemies.length > 0) {
            target = Utils.findLowestHealthTarget(enemies);
            if (target != null) {
                Direction dir = rc.getLocation().directionTo(target.location);
                RobotInfo[] allies = rc.senseNearbyRobots(rc.getType().sensorRadius, rc.getTeam());
                boolean safe = true;
                for (RobotInfo ally : allies) {
                    Direction toAlly = rc.getLocation().directionTo(ally.location);
                    float dist = rc.getLocation().distanceTo(ally.location);
                    float distToTarget = rc.getLocation().distanceTo(target.location);
                    if (dist < distToTarget && Math.abs(dir.degreesBetween(toAlly)) < 20) {
                        safe = false;
                        break;
                    }
                }
                if (safe) {
                    int enemyCount = 0;
                    for (RobotInfo e : enemies) {
                        if (rc.getLocation().distanceTo(e.location) <= 5) {
                            enemyCount++;
                        }
                    }
                    if (enemyCount >= 3 && rc.canFireTriadShot()) {
                        rc.fireTriadShot(dir);
                    } else if (rc.canFireSingleShot()) {
                        rc.fireSingleShot(dir);
                    }
                }
            }
        }

        MapLocation enemyArchon = Comms.getEnemyArchonLocation();
        if (enemyArchon != null) {
            if (Nav.moveToward(enemyArchon)) {
                return;
            }
        }

        if (!rc.hasMoved()) {
            Nav.tryMove(Nav.randomDirection());
        }
    }
}
