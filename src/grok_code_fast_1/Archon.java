package grok_code_fast_1;
import battlecode.common.*;

public strictfp class Archon {
    static RobotController rc;
    static int gardenerCount = 0;

    public static void run(RobotController rc) throws GameActionException {
        Archon.rc = rc;
        Nav.init(rc);

        while (true) {
            try {
                // Broadcast location
                Comms.broadcastArchon(rc, rc.getLocation(), 0); // Assume first archon

                // Hire gardener if possible
                if (gardenerCount < 3 && rc.canHireGardener(Direction.getEast())) {
                    rc.hireGardener(Direction.getEast());
                    gardenerCount++;
                }

                // Move away from other archons
                RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
                float sumX = rc.getLocation().x;
                float sumY = rc.getLocation().y;
                int archonCount = 1;
                for (RobotInfo ally : allies) {
                    if (ally.type == RobotType.ARCHON) {
                        sumX += ally.location.x;
                        sumY += ally.location.y;
                        archonCount++;
                    }
                }
                if (archonCount > 1) {
                    MapLocation avgAllyLoc = new MapLocation(sumX / archonCount, sumY / archonCount);
                    Direction away = rc.getLocation().directionTo(avgAllyLoc).opposite();
                    Nav.tryMove(away);
                }

                // Donate to VP if late game
                if (rc.getRoundNum() > 2000 && rc.getTeamBullets() > 1000) {
                    float donation = rc.getTeamBullets() - 100;
                    rc.donate(donation);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
            Clock.yield();
        }
    }
}