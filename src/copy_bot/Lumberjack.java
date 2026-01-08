package copy_bot;
import battlecode.common.*;

public strictfp class Lumberjack {
    static boolean moved = false;

    public static void run(RobotController rc) throws GameActionException {
        while (true) {
            moved = false;
            try {
                RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
                TreeInfo[] trees = rc.senseNearbyTrees();

                if (enemies.length > 0) {
                    strikeEnemies(rc, enemies);
                }

                if (rc.canStrike()) {
                    boolean struck = false;
                    for (RobotInfo enemy : enemies) {
                        if (rc.getLocation().distanceTo(enemy.location) <= rc.getType().bodyRadius + 2) {
                            if (!hasAllyInRange(rc)) {
                                rc.strike();
                                struck = true;
                                break;
                            }
                        }
                    }
                    if (!struck) {
                        chopNearestTree(rc, trees);
                    }
                } else {
                    chopNearestTree(rc, trees);
                }

                if (!moved) {
                    MapLocation enemyLoc = Comms.readEnemyLocation(rc);
                    if (enemies.length == 0 && enemyLoc != null) {
                        Nav.moveToward(rc, enemyLoc);
                    } else {
                        Nav.tryMove(rc, Nav.randomDirection());
                    }
                }

                Clock.yield();
            } catch (Exception e) {
                System.out.println("Lumberjack Exception");
                e.printStackTrace();
            }
        }
    }

    static void strikeEnemies(RobotController rc, RobotInfo[] enemies) throws GameActionException {
        RobotInfo lowest = Utils.findLowestHealthTarget(enemies);
        if (Nav.moveToward(rc, lowest.location)) {
            moved = true;
        }
    }

    static void chopNearestTree(RobotController rc, TreeInfo[] trees) throws GameActionException {
        TreeInfo nearest = null;
        float minDist = Float.MAX_VALUE;

        for (TreeInfo tree : trees) {
            float dist = rc.getLocation().distanceTo(tree.location);
            if (dist < minDist && dist <= rc.getType().strideRadius) {
                if (tree.team != rc.getTeam() || tree.health < 50) {
                    minDist = dist;
                    nearest = tree;
                }
            }
        }

        if (nearest != null && rc.canChop(nearest.ID)) {
            rc.chop(nearest.ID);
        }
    }

    static boolean hasAllyInRange(RobotController rc) throws GameActionException {
        RobotInfo[] allies = rc.senseNearbyRobots(2, rc.getTeam());
        for (RobotInfo ally : allies) {
            if (ally.type != RobotType.LUMBERJACK) {
                return true;
            }
        }
        return false;
    }
}
