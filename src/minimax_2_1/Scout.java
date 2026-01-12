package minimax_2_1;
import battlecode.common.*;

public strictfp class Scout {
    static RobotController rc;
    static MapLocation target;

    public static void run(RobotController rc) throws GameActionException {
        Scout.rc = rc;
        Nav.init(rc);
        Comms.init(rc);
        target = null;

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
        TreeInfo[] nearbyTrees = rc.senseNearbyTrees(2.0f, Team.NEUTRAL);
        for (TreeInfo tree : nearbyTrees) {
            if (tree.containedBullets > 0 && rc.canShake(tree.ID)) {
                rc.shake(tree.ID);
                return;
            }
        }

        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        for (RobotInfo enemy : enemies) {
            reportEnemy(enemy);
        }

        if (enemies.length > 0) {
            RobotInfo closest = Utils.findClosestEnemy(rc, enemies);
            if (closest != null) {
                MapLocation enemyLoc = closest.location;
                if (rc.getLocation().distanceTo(enemyLoc) > 8) {
                    Nav.moveToward(enemyLoc);
                    return;
                }
                if (rc.canFireSingleShot()) {
                    Direction dir = rc.getLocation().directionTo(enemyLoc);
                    rc.fireSingleShot(dir);
                    return;
                }
            }
        }

        MapLocation enemyArchon = Comms.getEnemyArchonLocation();
        if (enemyArchon != null) {
            Nav.moveToward(enemyArchon);
            return;
        }

        if (target == null || rc.getLocation().distanceTo(target) < 10) {
            target = rc.getLocation().add(new Direction((float)(Math.random() * 2 * Math.PI)), 30);
        }

        Nav.moveToward(target);
    }

    static boolean tryShakeTree() throws GameActionException {
        TreeInfo[] trees = rc.senseNearbyTrees(2.0f, Team.NEUTRAL);
        for (TreeInfo tree : trees) {
            if (tree.containedBullets > 0 && rc.canShake(tree.ID)) {
                rc.shake(tree.ID);
                return true;
            }
        }
        return false;
    }

    static void reportEnemy(RobotInfo enemy) throws GameActionException {
        if (enemy.type == RobotType.ARCHON) {
            Comms.broadcastLocation(2, 3, enemy.location);
            rc.broadcast(4, 1);
        }
    }
}
