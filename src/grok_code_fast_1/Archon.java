package grok_code_fast_1;
import battlecode.common.*;

public strictfp class Archon {
    static RobotController rc;
    static int turnCounter = 0;
    static int hireCounter = 0;

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
        // Broadcast current position using Comms.broadcastLocation(0, 1, rc.getLocation())
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
        
        int hireInterval = 3;
        if (turnCounter % hireInterval == 0 && rc.getTeamBullets() >= 50) {
            if (hireCounter == 0) {
                tryHireScout();
            } else {
                tryHireGardener();
            }
            hireCounter++;
        }

        // Donate excess bullets starting from round 100
        if (rc.getRoundNum() >= 100 && rc.getTeamBullets() > 50) {
            int donateAmount = Math.min((int)(rc.getTeamBullets() - 50), 50);
            if (donateAmount > 0) {
                rc.donate(donateAmount);
            }
        }
        
        // If no action taken, move randomly using Nav.tryMove(Nav.randomDirection())
        if (!rc.hasMoved()) {
            Nav.tryMove(Nav.randomDirection());
        }
    }

    static boolean tryHireGardener() throws GameActionException {
        Direction[] dirs = {new Direction(0), new Direction((float)Math.PI/4), new Direction((float)Math.PI/2), new Direction(3*(float)Math.PI/4), new Direction((float)Math.PI), new Direction(5*(float)Math.PI/4), new Direction(3*(float)Math.PI/2), new Direction(7*(float)Math.PI/4)};
        for (Direction dir : dirs) {
            MapLocation hireLoc = rc.getLocation().add(dir, 2.0f); // Approximate gardener spawn distance
            TreeInfo[] trees = rc.senseNearbyTrees(hireLoc, 2.0f, Team.NEUTRAL);
            if (trees.length > 0 && rc.canHireGardener(dir)) {
                rc.hireGardener(dir);
                return true;
            }
        }
        // Fallback to original logic
        for (int i = 0; i < 8; i++) {
            Direction dir = new Direction(i * (float)Math.PI / 4);
            if (rc.canHireGardener(dir)) {
                rc.hireGardener(dir);
                return true;
            }
        }
        return false;
    }

    static boolean tryHireScout() throws GameActionException {
        for (int i = 0; i < 8; i++) {
            Direction dir = new Direction(i * (float)Math.PI / 4);
            if (rc.canBuildRobot(RobotType.SCOUT, dir)) {
                rc.buildRobot(RobotType.SCOUT, dir);
                return true;
            }
        }
        return false;
    }
}
