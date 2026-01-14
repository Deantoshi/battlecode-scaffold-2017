package grok_code_fast_1;
import battlecode.common.*;

public strictfp class Archon {
    static RobotController rc;
    static int turnCounter = 0;
    static int hireCounter = 0;
    static int gardenersHired = 0;
    static int gardenerCount = 0;

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
        turnCounter++;
        Comms.broadcastLocation(0, 1, rc.getLocation());

        // Check for nearby enemies - if any, move away from closest
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemies.length > 0 && !rc.hasMoved()) {
            RobotInfo closest = Utils.findClosestEnemy(rc, enemies);
            if (closest != null) {
                Direction away = rc.getLocation().directionTo(closest.location).opposite();
                Nav.tryMove(away);
            }
        }

        // Hire up to 4 gardeners early if bullets sufficient
        int maxGardeners = 4 + (turnCounter / 300);
        if (rc.getTeamBullets() >= 50 && gardenersHired < maxGardeners) {
            if (tryHireGardener()) {
                gardenersHired++;
                gardenerCount++;
            }
        }

        // Dynamic donation: only donate surplus after reserves
        int reserveBullets = 100 + (gardenerCount * 20); // Scale reserves with gardeners
        if (rc.getTeamBullets() > reserveBullets + 50) {
            int donateAmount = (int)(rc.getTeamBullets() - reserveBullets);
            rc.donate(donateAmount);
        }

        if (!rc.hasMoved()) {
            // Move towards map center for expansion, avoiding other archons
            MapLocation center = new MapLocation(50, 50);
            Direction toCenter = rc.getLocation().directionTo(center);
            Nav.tryMove(toCenter);
        }
    }

    static boolean tryHireGardener() throws GameActionException {
        Direction[] dirs = {new Direction(0), new Direction((float)Math.PI/4), new Direction((float)Math.PI/2), new Direction(3*(float)Math.PI/4), new Direction((float)Math.PI), new Direction(5*(float)Math.PI/4), new Direction(3*(float)Math.PI/2), new Direction(7*(float)Math.PI/4)};
        for (Direction dir : dirs) {
            MapLocation hireLoc = rc.getLocation().add(dir, 2.0f);
            TreeInfo[] trees = rc.senseNearbyTrees(hireLoc, 2.0f, Team.NEUTRAL);
            if (trees.length > 0 && rc.canHireGardener(dir)) {
                rc.hireGardener(dir);
                return true;
            }
        }
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
