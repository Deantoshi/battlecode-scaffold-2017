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

        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemies.length > 0) {
            RobotInfo closest = Utils.findClosestEnemy(rc, enemies);
            Direction away = rc.getLocation().directionTo(closest.location).opposite();
            Nav.tryMove(away);
            
            RobotInfo[] nearbyAllies = rc.senseNearbyRobots(10, rc.getTeam());
            int soldierCount = 0;
            for (RobotInfo ally : nearbyAllies) {
                if (ally.type == RobotType.SOLDIER || ally.type == RobotType.LUMBERJACK || ally.type == RobotType.TANK) {
                    soldierCount++;
                }
            }
            if (soldierCount < 2 && rc.canBuildRobot(RobotType.SOLDIER, away)) {
                rc.buildRobot(RobotType.SOLDIER, away);
            }
        }

        int gardenerCount = Comms.countFriendlyGardeners();
        if (rc.getTeamBullets() >= 100 && gardenerCount < 5) {
            tryHireGardener();
            tryHireGardener();
        }

        if (rc.getTeamBullets() >= 100 && gardenerCount < 8) {
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
