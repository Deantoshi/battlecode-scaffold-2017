package glm_4_7;
import battlecode.common.*;

public strictfp class Gardener {
    static int treesPlanted = 0;
    static Direction buildDir = Direction.EAST;
    static boolean moved = false;

    public static void run(RobotController rc) throws GameActionException {
        while (true) {
            moved = false;
            try {
                findOpenSpace(rc);
                waterLowestHealthTree(rc);
                
                if (shouldBuildUnit(rc)) {
                    if (rc.getRoundNum() < 100 && !hasBuiltScout()) {
                        tryBuild(rc, RobotType.SCOUT, buildDir);
                    } else if (rc.getRoundNum() > 50) {
                        if (rc.getRoundNum() % 6 == 0 && rc.getTreeCount() > 3) {
                            tryBuild(rc, RobotType.LUMBERJACK, buildDir);
                        } else {
                            tryBuild(rc, RobotType.SOLDIER, buildDir);
                        }
                    }
                }
                
                if (rc.getTeamBullets() > 20) {
                    tryPlantTree(rc);
                }
                
                Clock.yield();
            } catch (Exception e) {
                System.out.println("Gardener Exception");
                e.printStackTrace();
            }
        }
    }

    static void findOpenSpace(RobotController rc) throws GameActionException {
        MapLocation archonLoc = Comms.readLocation(rc, 0, 1);
        if (archonLoc != null && rc.getLocation().distanceTo(archonLoc) < 5 && !moved) {
            Direction away = rc.getLocation().directionTo(archonLoc).opposite();
            if (Nav.tryMove(rc, away)) {
                moved = true;
            }
        }
        
        if (!moved) {
            RobotInfo[] gardeners = rc.senseNearbyRobots(-1, rc.getTeam());
            for (RobotInfo g : gardeners) {
                if (g.type == RobotType.GARDENER && g.location.distanceTo(rc.getLocation()) < 5) {
                    Direction away = rc.getLocation().directionTo(g.location).opposite();
                    if (Nav.tryMove(rc, away)) {
                        moved = true;
                    }
                    break;
                }
            }
        }
    }

    static void waterLowestHealthTree(RobotController rc) throws GameActionException {
        TreeInfo[] trees = rc.senseNearbyTrees(RobotType.GARDENER.sensorRadius, rc.getTeam());
        if (trees.length == 0) return;
        
        TreeInfo lowest = Utils.findLowestHealthTree(trees);
        if (rc.canWater(lowest.ID)) {
            rc.water(lowest.ID);
        }
    }

    static boolean shouldBuildUnit(RobotController rc) {
        return rc.getTeamBullets() > 55 || (rc.getRoundNum() > 600 && rc.getTeamBullets() > 45);
    }

    static void tryBuild(RobotController rc, RobotType type, Direction dir) throws GameActionException {
        if (rc.canBuildRobot(type, dir)) {
            rc.buildRobot(type, dir);
        } else {
            for (int i = 0; i < 6; i++) {
                if (rc.canBuildRobot(type, dir.rotateLeftDegrees(60 * i))) {
                    rc.buildRobot(type, dir.rotateLeftDegrees(60 * i));
                    return;
                }
            }
        }
    }

    static void tryPlantTree(RobotController rc) throws GameActionException {
        Direction[] dirs = new Direction[6];
        for (int i = 0; i < 6; i++) {
            dirs[i] = new Direction((float)(i * 60 * Math.PI / 180));
        }
        
        for (Direction d : dirs) {
            if (d.equals(buildDir)) continue;
            if (rc.canPlantTree(d)) {
                rc.plantTree(d);
                treesPlanted++;
                return;
            }
        }
    }

    static boolean hasBuiltScout() {
        return treesPlanted > 0;
    }
}
