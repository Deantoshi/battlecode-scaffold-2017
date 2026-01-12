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
        if (tryShakeTree()) {
            return;
        }

        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        for (RobotInfo enemy : enemies) {
            reportEnemy(enemy);
        }

        if (target == null || rc.getLocation().distanceTo(target) < 5) {
            target = new MapLocation(rc.getLocation().x + 20 * (Math.random() < 0.5 ? -1 : 1), 
                                    rc.getLocation().y + 20 * (Math.random() < 0.5 ? -1 : 1));
        }

        if (!Nav.moveToward(target)) {
            Nav.tryMove(Nav.randomDirection());
        }
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
