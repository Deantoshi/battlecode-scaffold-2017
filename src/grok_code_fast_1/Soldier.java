package grok_code_fast_1;
import battlecode.common.*;

public strictfp class Soldier {
    static RobotController rc;

    public static void run(RobotController rc) throws GameActionException {
        Soldier.rc = rc;
        Nav.init(rc);

        while (true) {
            try {
                // Dodge bullets
                BulletInfo[] bullets = rc.senseNearbyBullets();
                for (BulletInfo bullet : bullets) {
                    if (willCollideWithMe(bullet)) {
                        Direction away = bullet.dir.opposite();
                        Nav.tryMove(away);
                        break;
                    }
                }

                // Attack enemies
                RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
                RobotInfo target = null;
                for (RobotInfo enemy : enemies) {
                    if (target == null || enemy.health < target.health) {
                        target = enemy;
                    }
                }

                if (target != null) {
                    Direction dir = rc.getLocation().directionTo(target.location);
                    if (rc.canFireTriadShot()) {
                        rc.fireTriadShot(dir);
                    } else if (rc.canFireSingleShot()) {
                        rc.fireSingleShot(dir);
                    }
                    // Kite: move away if too close
                    if (rc.getLocation().distanceTo(target.location) < 3) {
                        Nav.tryMove(dir.opposite());
                    }
                } else {
                    // Patrol or move towards enemy archon
                    MapLocation enemyArchon = Comms.readArchon(rc, 0);
                    if (enemyArchon != null) {
                        Nav.tryMoveTowards(enemyArchon);
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
            Clock.yield();
        }
    }

    static boolean willCollideWithMe(BulletInfo bullet) {
        MapLocation myLocation = rc.getLocation();
        Direction propagationDirection = bullet.dir;
        MapLocation bulletLocation = bullet.location;
        Direction directionToRobot = bulletLocation.directionTo(myLocation);
        float distToRobot = bulletLocation.distanceTo(myLocation);
        float theta = propagationDirection.radiansBetween(directionToRobot);

        if (Math.abs(theta) > Math.PI/2) {
            return false;
        }

        float perpendicularDist = (float)Math.abs(distToRobot * Math.sin(theta));
        return (perpendicularDist <= rc.getType().bodyRadius);
    }
}