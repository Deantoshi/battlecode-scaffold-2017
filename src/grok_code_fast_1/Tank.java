package grok_code_fast_1;
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
        if (enemies.length > 0) {
            RobotInfo target = Utils.findLowestHealthTarget(enemies);
            if (target != null) {
                Direction dir = rc.getLocation().directionTo(target.location);
                // Check for friendly fire
                RobotInfo[] allies = rc.senseNearbyRobots(rc.getType().sensorRadius, rc.getTeam());
                boolean safe = true;
                for (RobotInfo ally : allies) {
                    Direction toAlly = rc.getLocation().directionTo(ally.location);
                    if (Math.abs(dir.degreesBetween(toAlly)) < 20) {
                        safe = false;
                        break;
                    }
                }
                if (safe) {
                    if (enemies.length >= 3 && rc.canFireTriadShot()) {
                        rc.fireTriadShot(dir);
                    } else if (rc.canFireSingleShot()) {
                        rc.fireSingleShot(dir);
                    }
                }
            }
        }
        if (!rc.hasMoved()) {
            MapLocation enemyLoc = Comms.getEnemyArchonLocation();
            if (enemyLoc != null) {
                Nav.moveToward(enemyLoc);
            } else {
                Nav.tryMove(Nav.randomDirection());
            }
        }
    }
}
