package grok_code_fast_1;
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
        // Broadcast current position using Comms.broadcastLocation(0, 1, rc.getLocation())
        Comms.broadcastLocation(0, 1, rc.getLocation());
        
        // Check for nearby enemies - if any, move away from closest
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemies.length > 0) {
            RobotInfo closest = Utils.findClosestEnemy(rc, enemies);
            if (closest != null) {
                Direction away = rc.getLocation().directionTo(closest.location).opposite();
                Nav.tryMove(away);
            }
        }
        
        // If rc.getTeamBullets() >= 100, call tryHireGardener()
        if (rc.getTeamBullets() >= 100) {
            tryHireGardener();
        }
        
        // If no action taken, move randomly using Nav.tryMove(Nav.randomDirection())
        Nav.tryMove(Nav.randomDirection());
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
