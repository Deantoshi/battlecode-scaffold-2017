package haiku_4_5;
import battlecode.common.*;

public class Archon {
    static RobotController rc;

    public static void run() throws GameActionException {
        rc = RobotPlayer.getRc();

        while (true) {
            try {
                BulletSpending.spendPolicy();

                // Move towards center or enemy
                MapLocation myLoc = rc.getLocation();
                RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());

                if (enemies.length > 0) {
                    Direction toEnemy = myLoc.directionTo(enemies[0].location);
                    Nav.tryMove(toEnemy);
                } else {
                    Nav.tryMove(Utils.randomDirection());
                }

                Clock.yield();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
