package copy_bot;
import battlecode.common.*;

public strictfp class Scout {
    static Direction spiralDir = Direction.EAST;
    static int spiralSteps = 0;
    static int spiralTurns = 0;
    static boolean moved = false;

    public static void run(RobotController rc) throws GameActionException {
        while (true) {
            moved = false;
            try {
                shakeNearestTree(rc);
                scoutAndReport(rc);

                RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
                if (enemies.length > 0) {
                    for (RobotInfo enemy : enemies) {
                        if (enemy.type == RobotType.GARDENER) {
                            harassGardener(rc, enemy);
                            break;
                        }
                    }
                }

                if (!moved) {
                    moveInSpiral(rc);
                }

                Clock.yield();
            } catch (Exception e) {
                System.out.println("Scout Exception");
                e.printStackTrace();
            }
        }
    }

    static void shakeNearestTree(RobotController rc) throws GameActionException {
        TreeInfo[] trees = rc.senseNearbyTrees();
        for (TreeInfo tree : trees) {
            if (tree.getContainedBullets() > 0 && rc.canShake(tree.ID)) {
                rc.shake(tree.ID);
                return;
            }
        }
    }

    static void scoutAndReport(RobotController rc) throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        for (RobotInfo enemy : enemies) {
            if (enemy.type == RobotType.ARCHON || enemy.type == RobotType.GARDENER) {
                Comms.broadcastEnemySpotted(rc, enemy.location, rc.getRoundNum());
                if (enemy.type == RobotType.ARCHON) {
                    Comms.broadcastLocation(rc, 2, 3, enemy.location);
                }
                return;
            }
        }
    }

    static void harassGardener(RobotController rc, RobotInfo gardener) throws GameActionException {
        float dist = rc.getLocation().distanceTo(gardener.location);
        if (dist > 4) {
            if (Nav.moveToward(rc, gardener.location)) {
                moved = true;
            }
        } else if (dist < 3) {
            if (Nav.moveAway(rc, gardener.location)) {
                moved = true;
            }
        }

        if (rc.canFireSingleShot() && rc.getTeamBullets() > 5) {
            if (rc.canFireSingleShot()) {
                rc.fireSingleShot(rc.getLocation().directionTo(gardener.location));
            }
        }
    }

    static void moveInSpiral(RobotController rc) throws GameActionException {
        MapLocation archonLoc = Comms.readLocation(rc, 0, 1);
        if (archonLoc != null && rc.getLocation().distanceTo(archonLoc) < 10) {
            if (Nav.moveToward(rc, archonLoc)) {
                return;
            }
        }
        if (spiralSteps >= 2 + spiralTurns / 2) {
            spiralDir = spiralDir.rotateLeftDegrees(90);
            spiralSteps = 0;
            spiralTurns++;
        }
        if (Nav.tryMove(rc, spiralDir)) {
            spiralSteps++;
        }
    }
}
