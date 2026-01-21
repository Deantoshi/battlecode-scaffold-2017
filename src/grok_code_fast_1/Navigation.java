package grok_code_fast_1;
import battlecode.common.*;
import java.util.*;

public class Navigation {
    static RobotController rc;
    static MapLocation enemyCenter;
    static Map<Integer, Boolean> followingWall = new HashMap<>();
    static Map<Integer, Direction> bugDirection = new HashMap<>();

    public static void init(RobotController rc) {
        Navigation.rc = rc;
    }

    public static boolean tryMove(Direction dir) throws GameActionException {
        return tryMove(dir, 20, 3);
    }

    public static boolean tryMove(Direction dir, float degreeOffset, int checksPerSide) throws GameActionException {
        MapLocation[] archons = rc.getInitialArchonLocations(rc.getTeam());
        float sumX = 0, sumY = 0;
        for (MapLocation loc : archons) {
            sumX += loc.x;
            sumY += loc.y;
        }
        MapLocation center = new MapLocation(25,25); // Assuming 50x50 map
        boolean isMagicWood = true; // hardcoded for MagicWood map

        MapLocation current = rc.getLocation();

        // Calculate enemy center if not already
        if (enemyCenter == null) {
            MapLocation[] enemyArchons = rc.getInitialArchonLocations(rc.getTeam().opponent());
            sumX = 0; sumY = 0;
            for (MapLocation loc : enemyArchons) {
                sumX += loc.x;
                sumY += loc.y;
            }
            enemyCenter = new MapLocation(sumX / enemyArchons.length, sumY / enemyArchons.length);
        }

        // Collect directions to try
        List<Direction> directionsToTry = new ArrayList<>();
        directionsToTry.add(dir);
        for (int i = 1; i <= checksPerSide; i++) {
            directionsToTry.add(dir.rotateLeftDegrees(degreeOffset * i));
            directionsToTry.add(dir.rotateRightDegrees(degreeOffset * i));
        }

        // Sort directions by quadrant preference (favor SE), then by distance to enemy center
        directionsToTry.sort(Comparator.comparing((Direction d) -> {
            MapLocation target = current.add(d);
            int quadrantScore = (target.x >= center.x ? 1 : 0) + (target.y >= center.y ? 1 : 0);
            return -quadrantScore; // descending
        }).thenComparing(d -> {
            MapLocation target = current.add(d);
            return target.distanceTo(enemyCenter);
        }));

        // Try directions in sorted order
        for (Direction checkDir : directionsToTry) {
            MapLocation target = current.add(checkDir);
            if ((!isMagicWood || !(target.x < center.x && target.y < center.y)) && rc.canMove(checkDir)) {
                rc.move(checkDir);
                return true;
            } else if (rc.getType() == RobotType.LUMBERJACK) {
                // For lumberjacks, prioritize clearing trees along path to enemy quadrant
                TreeInfo[] nearbyTrees = rc.senseNearbyTrees();
                TreeInfo bestTree = null;
                float bestDist = Float.MAX_VALUE;
                for (TreeInfo tree : nearbyTrees) {
                    if (tree.team == Team.NEUTRAL && rc.canChop(tree.ID) && tree.location.distanceTo(enemyCenter) < current.distanceTo(enemyCenter)) {
                        float dist = current.distanceTo(tree.location);
                        if (dist < bestDist) {
                            bestDist = dist;
                            bestTree = tree;
                        }
                    }
                }
                if (bestTree != null) {
                    rc.chop(bestTree.ID);
                    return true;
                }
                // Fallback: try to chop blocking trees at target
                TreeInfo tree = rc.senseTreeAtLocation(target);
                if (tree != null && tree.team == Team.NEUTRAL && rc.canChop(tree.ID)) {
                    rc.chop(tree.ID);
                    return true; // Action taken (chopped), even if not moved
                }
            }
        }

        // A move never happened, so return false.
        return false;
    }

    public static boolean tryMoveBug(MapLocation target) throws GameActionException {
        MapLocation current = rc.getLocation();
        if (current.distanceTo(target) < 1) return false; // close enough

        int id = rc.getID();
        boolean isFollowingWall = followingWall.getOrDefault(id, false);
        Direction currentBugDir = bugDirection.get(id);

        Direction toTarget = current.directionTo(target);
        if (!isFollowingWall) {
            if (rc.canMove(toTarget)) {
                rc.move(toTarget);
                return true;
            } else {
                followingWall.put(id, true);
                bugDirection.put(id, toTarget.rotateLeftDegrees(90)); // follow left
            }
        }

        if (isFollowingWall) {
            if (rc.canMove(currentBugDir)) {
                rc.move(currentBugDir);
                // check if can now move towards target
                MapLocation newLoc = current.add(currentBugDir);
                Direction newToTarget = newLoc.directionTo(target);
                if (rc.canMove(newToTarget)) {
                    followingWall.put(id, false);
                    bugDirection.remove(id);
                }
                return true;
            } else {
                // rotate
                bugDirection.put(id, currentBugDir.rotateLeftDegrees(10));
            }
        }

        return false;
    }
}