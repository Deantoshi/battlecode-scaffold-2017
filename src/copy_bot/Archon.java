package copy_bot;
import battlecode.common.*;

public strictfp class Archon {
    static Direction[] buildDirections = new Direction[6];
    static {
        for (int i = 0; i < 6; i++) {
            buildDirections[i] = new Direction((float)(i * 60 * Math.PI / 180));
        }
    }

    public static void run(RobotController rc) throws GameActionException {
        while (true) {
            try {
                broadcastLocation(rc);
                
                if (rc.getRoundNum() == 1) {
                    tryHireGardener(rc);
                } else if (rc.getRoundNum() > 40 && rc.getTeamBullets() > 100) {
                    tryHireGardener(rc);
                } else if (rc.getRoundNum() > 100 && rc.getTeamBullets() > 80) {
                    tryHireGardener(rc);
                }
                
                MapLocation enemyLoc = Comms.readEnemyLocation(rc);
                if (enemyLoc != null) {
                    Nav.moveAway(rc, enemyLoc);
                } else {
                    Nav.tryMove(rc, Nav.randomDirection());
                }
                
                if (shouldDonate(rc)) {
                    rc.donate((float)(rc.getTeamBullets() * 0.9));
                }
                
                Clock.yield();
            } catch (Exception e) {
                System.out.println("Archon Exception");
                e.printStackTrace();
            }
        }
    }

    static void tryHireGardener(RobotController rc) throws GameActionException {
        if (rc.getTeamBullets() < 100) return;
        
        Direction dir = Nav.randomDirection();
        if (rc.canHireGardener(dir)) {
            rc.hireGardener(dir);
            return;
        }
        
        for (Direction d : buildDirections) {
            if (rc.canHireGardener(d)) {
                rc.hireGardener(d);
                return;
            }
        }
    }

    static void broadcastLocation(RobotController rc) throws GameActionException {
        Comms.broadcastLocation(rc, 0, 1, rc.getLocation());
    }

    static boolean shouldDonate(RobotController rc) throws GameActionException {
        int vp = rc.getTeamVictoryPoints();
        float bullets = rc.getTeamBullets();
        int round = rc.getRoundNum();
        int enemyVP = rc.getTeamVictoryPoints() - vp;
        return (bullets > 300) || (vp > enemyVP + 100 && bullets > 50) || (vp > 550 && bullets > 40) || (round > 700 && bullets > 50) || (round > 1200 && bullets > 30);
    }
}
