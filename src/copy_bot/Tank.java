package copy_bot;
import battlecode.common.*;

public strictfp class Tank {
    public static void run(RobotController rc) throws GameActionException {
        while (true) {
            try {
                RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
                
                if (enemies.length > 0) {
                    fireAtTarget(rc, enemies);
                }
                
                bulldozeTrees(rc);
                
                MapLocation enemyLoc = Comms.readEnemyLocation(rc);
                if (enemyLoc != null) {
                    Nav.moveToward(rc, enemyLoc);
                } else {
                    Nav.tryMove(rc, Nav.randomDirection());
                }
                
                Clock.yield();
            } catch (Exception e) {
                System.out.println("Tank Exception");
                e.printStackTrace();
            }
        }
    }

    static void fireAtTarget(RobotController rc, RobotInfo[] enemies) throws GameActionException {
        RobotInfo target = Utils.findLowestHealthTarget(enemies);
        
        if (rc.canFirePentadShot() && rc.getTeamBullets() > 20) {
            rc.firePentadShot(rc.getLocation().directionTo(target.location));
        }
    }

    static void bulldozeTrees(RobotController rc) throws GameActionException {
        Nav.tryMove(rc, Nav.randomDirection());
    }
}
