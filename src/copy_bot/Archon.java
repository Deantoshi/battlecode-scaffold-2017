package copy_bot;
import battlecode.common.*;

public strictfp class Archon {
    static RobotController rc;

    public static void run(RobotController rc) throws GameActionException {
        Archon.rc = rc;
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
        Comms.broadcastLocation(0, 1, rc.getLocation());

        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemies.length > 0) {
            RobotInfo closest = Utils.findClosestEnemy(rc, enemies);
            Direction away = rc.getLocation().directionTo(closest.location).opposite();
            Nav.tryMove(away);
        }

        if (rc.getTeamBullets() >= 100) {
            tryHireGardener();
        }

        if (!rc.hasMoved()) {
            Nav.tryMove(Nav.randomDirection());
        }
    }

    static boolean tryHireGardener() throws GameActionException {
        for (int i = 0; i < 8; i++) {
            Direction dir = new Direction(i * (float)Math.PI / 4);
            if (rc.canHireGardener(dir)) {
                rc.hireGardener(dir);
                return true;
            }
        }
        return false;
    }
}
