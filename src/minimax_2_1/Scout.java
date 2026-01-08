package minimax_2_1;
import battlecode.common.*;

public strictfp class Scout {
    static RobotController rc;

    public static void run() throws GameActionException {
        System.out.println("I'm a scout!");
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

                TreeInfo[] neutralTrees = rc.senseNearbyTrees(-1, Team.NEUTRAL);
                for (TreeInfo tree : neutralTrees) {
                    if (tree.containedBullets > 0 && rc.canShake(tree.ID)) {
                        rc.shake(tree.ID);
                        break;
                    }
                }

                RobotInfo[] enemies = rc.senseNearbyRobots(-1, enemy);
                for (RobotInfo enemyRobot : enemies) {
                    if (enemyRobot.type == RobotType.GARDENER || enemyRobot.type == RobotType.ARCHON) {
                        MapLocation enemyLoc = enemyRobot.location;
                        Comms.broadcastEnemyLocation(enemyLoc);
                        if (rc.canFireSingleShot()) {
                            Direction toEnemy = rc.getLocation().directionTo(enemyLoc);
                            rc.fireSingleShot(toEnemy);
                        }
                    }
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
