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
        }

        RobotInfo[] nearbyAllies = rc.senseNearbyRobots(-1, rc.getTeam());
        int soldierCount = 0;
        for (RobotInfo ally : nearbyAllies) {
            if (ally.type == RobotType.SOLDIER || ally.type == RobotType.LUMBERJACK || ally.type == RobotType.TANK) {
                soldierCount++;
            }
        }
        
        int round = rc.getRoundNum();
        if (enemies.length > 0) {
            if (rc.canBuildRobot(RobotType.SOLDIER, Nav.randomDirection())) {
                rc.buildRobot(RobotType.SOLDIER, Nav.randomDirection());
            }
            if (rc.canBuildRobot(RobotType.SOLDIER, Nav.randomDirection())) {
                rc.buildRobot(RobotType.SOLDIER, Nav.randomDirection());
            }
        } else if (soldierCount < 10 && round < 400) {
            if (rc.canBuildRobot(RobotType.SOLDIER, Nav.randomDirection())) {
                rc.buildRobot(RobotType.SOLDIER, Nav.randomDirection());
            }
        } else if (soldierCount < 20 && round < 800) {
            if (rc.canBuildRobot(RobotType.SOLDIER, Nav.randomDirection())) {
                rc.buildRobot(RobotType.SOLDIER, Nav.randomDirection());
            }
        } else if (rc.getTeamBullets() > 150 && soldierCount < 30) {
            if (rc.canBuildRobot(RobotType.SOLDIER, Nav.randomDirection())) {
                rc.buildRobot(RobotType.SOLDIER, Nav.randomDirection());
            }
        }

        int gardenerCount = Comms.countFriendlyGardeners();
        if (rc.getTeamBullets() >= 100 && gardenerCount < 3) {
            tryHireGardener();
        }

        if (rc.getTeamBullets() >= 150 && gardenerCount < 4) {
            tryHireGardener();
        }

        if (rc.getTeamBullets() >= 100 && round > 300 && gardenerCount < 5) {
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
