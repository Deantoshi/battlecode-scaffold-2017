package minimax_2_1;
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

        RobotInfo[] nearbyAllies = rc.senseNearbyRobots(-1, rc.getTeam());
        int soldierCount = 0;
        for (RobotInfo ally : nearbyAllies) {
            if (ally.type == RobotType.SOLDIER || ally.type == RobotType.LUMBERJACK || ally.type == RobotType.TANK || ally.type == RobotType.SCOUT) {
                soldierCount++;
            }
        }
        
        int round = rc.getRoundNum();
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemies.length > 0) {
            RobotInfo closest = Utils.findClosestEnemy(rc, enemies);
            Direction away = rc.getLocation().directionTo(closest.location).opposite();
            Nav.tryMove(away);
        }
        
        // PRIORITY: Build soldiers aggressively in early game
        // This is critical - we need combat units to survive!
        for (int i = 0; i < 3; i++) {  // Try up to 3 times per turn
            Direction dir = new Direction(i * (float)Math.PI / 4);
            if (rc.canBuildRobot(RobotType.SOLDIER, dir)) {
                rc.buildRobot(RobotType.SOLDIER, dir);
            }
        }
        
        // After round 300, start hiring gardeners (but not before!)
        int gardenerCount = Comms.countFriendlyGardeners();
        
        // Only hire gardeners after round 100 and we have some soldiers
        // Soldiers are critical for survival in early game
        if (round > 100 && gardenerCount < 2 && rc.getTeamBullets() >= 100) {
            tryHireGardener();
        }

        if (rc.getTeamBullets() >= 100 && round > 200 && gardenerCount < 4) {
            tryHireGardener();
        }

        if (rc.getTeamBullets() >= 100 && round > 400 && gardenerCount < 6) {
            tryHireGardener();
        }

        if (!rc.hasMoved()) {
            Nav.tryMove(Nav.randomDirection());
        }
    }

    static int countNearbyGardeners() throws GameActionException {
        RobotInfo[] robots = rc.senseNearbyRobots(20, rc.getTeam());
        int count = 0;
        for (RobotInfo robot : robots) {
            if (robot.type == RobotType.GARDENER) {
                count++;
            }
        }
        return count;
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
