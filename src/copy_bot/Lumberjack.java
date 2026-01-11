package copy_bot;
import battlecode.common.*;

public strictfp class Lumberjack {
    static RobotController rc;

    public static void run(RobotController rc) throws GameActionException {
        Lumberjack.rc = rc;
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
        if (tryStrike()) {
            return;
        }

        if (tryChopTree()) {
            return;
        }

        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemies.length > 0) {
            RobotInfo closest = Utils.findClosestEnemy(rc, enemies);
            if (Nav.moveToward(closest.location)) {
                return;
            }
        }

        Nav.tryMove(Nav.randomDirection());
    }

    static boolean tryStrike() throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(GameConstants.LUMBERJACK_STRIKE_RADIUS, rc.getTeam().opponent());
        RobotInfo[] allies = rc.senseNearbyRobots(GameConstants.LUMBERJACK_STRIKE_RADIUS, rc.getTeam());
        if (enemies.length > 0 && allies.length == 0) {
            if (rc.canStrike()) {
                rc.strike();
                return true;
            }
        }
        return false;
    }

    static boolean tryChopTree() throws GameActionException {
        TreeInfo[] trees = rc.senseNearbyTrees(2.0f, Team.NEUTRAL);
        if (trees.length > 0) {
            if (rc.canChop(trees[0].ID)) {
                rc.chop(trees[0].ID);
                return true;
            }
        }
        return false;
    }
}
