package grok_code_fast_1;
import battlecode.common.*;

public strictfp class Scout {
    static RobotController rc;

    public static void run(RobotController rc) throws GameActionException {
        Scout.rc = rc;
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
        tryShakeTree();
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        for (RobotInfo enemy : enemies) {
            reportEnemy(enemy);
            if (enemy.type == RobotType.GARDENER) {
                Nav.moveToward(enemy.location);
                return;
            }
        }
        Nav.tryMove(Nav.randomDirection());
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
            rc.broadcast(4, 1); // Enemy spotted flag
        }
    }
}
