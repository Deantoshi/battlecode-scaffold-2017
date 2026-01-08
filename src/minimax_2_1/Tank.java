package minimax_2_1;
import battlecode.common.*;

public strictfp class Tank {
    static RobotController rc;

    public static void run() throws GameActionException {
        System.out.println("I'm a tank!");
        Team enemy = rc.getTeam().opponent();
        while (true) {
            try {
                BulletInfo[] bullets = rc.senseNearbyBullets(-1);
                for (BulletInfo bullet : bullets) {
                    if (Nav.willCollideWithMe(bullet)) {
                        Direction dodgeDir = bullet.dir.rotateLeftDegrees(90);
                        if (!rc.canMove(dodgeDir)) {
                            dodgeDir = bullet.dir.rotateRightDegrees(90);
                        }
                        if (rc.canMove(dodgeDir)) {
                            rc.move(dodgeDir);
                        }
                    }
                }

                RobotInfo[] enemies = rc.senseNearbyRobots(-1, enemy);
                RobotInfo target = null;
                float minHealth = Float.MAX_VALUE;
                for (RobotInfo enemyRobot : enemies) {
                    if (enemyRobot.health < minHealth) {
                        minHealth = enemyRobot.health;
                        target = enemyRobot;
                    }
                }

                if (target != null && rc.canFireSingleShot()) {
                    Direction toEnemy = rc.getLocation().directionTo(target.location);
                    rc.fireSingleShot(toEnemy);
                }

                if (enemies.length > 0) {
                    MapLocation enemyLoc = enemies[0].location;
                    Direction toEnemy = rc.getLocation().directionTo(enemyLoc);
                    Nav.tryMove(toEnemy);
                } else {
                    MapLocation archonLoc = Comms.getArchonLocation();
                    if (archonLoc != null) {
                        Direction toArchon = rc.getLocation().directionTo(archonLoc);
                        Nav.tryMove(toArchon);
                    } else {
                        Nav.tryMove(Nav.randomDirection());
                    }
                }

                Clock.yield();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
