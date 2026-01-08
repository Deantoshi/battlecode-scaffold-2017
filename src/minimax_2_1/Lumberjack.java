package minimax_2_1;
import battlecode.common.*;

public strictfp class Lumberjack {
    static RobotController rc;

    public static void run() throws GameActionException {
        System.out.println("I'm a lumberjack!");
        Team enemy = rc.getTeam().opponent();
        while (true) {
            try {
                RobotInfo[] nearbyEnemies = rc.senseNearbyRobots(3, enemy);
                if (nearbyEnemies.length > 0 && !rc.hasAttacked()) {
                    rc.strike();
                }

                TreeInfo[] neutralTrees = rc.senseNearbyTrees(-1, Team.NEUTRAL);
                if (neutralTrees.length > 0) {
                    TreeInfo closestTree = neutralTrees[0];
                    float minDist = Float.MAX_VALUE;
                    for (TreeInfo tree : neutralTrees) {
                        float dist = rc.getLocation().distanceTo(tree.location);
                        if (dist < minDist) {
                            minDist = dist;
                            closestTree = tree;
                        }
                    }
                    if (rc.canChop(closestTree.ID)) {
                        rc.chop(closestTree.ID);
                    } else {
                        Direction toTree = rc.getLocation().directionTo(closestTree.location);
                        Nav.tryMove(toTree);
                    }
                } else if (nearbyEnemies.length > 0) {
                    MapLocation enemyLoc = nearbyEnemies[0].location;
                    Direction toEnemy = rc.getLocation().directionTo(enemyLoc);
                    Nav.tryMove(toEnemy);
                } else {
                    MapLocation archonLoc = Comms.getArchonLocation();
                    if (archonLoc != null) {
                        Direction toArchon = rc.getLocation().directionTo(archonLoc);
                        Nav.tryMove(toArchon);
                    } else {
                        Nav.tryMove(Nav.randomDirection());
                    }
                }

                Clock.yield();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
