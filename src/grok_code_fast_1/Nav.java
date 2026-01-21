package grok_code_fast_1;
import battlecode.common.*;
import java.util.*;

public class Nav {
    static RobotController rc;
    static Map<Integer, Boolean> followingWall = new HashMap<>();
    static Map<Integer, Direction> bugDirection = new HashMap<>();
    static MapLocation enemyCenter;

    public static void init(RobotController rc) {
        Nav.rc = rc;
        MapLocation[] enemyArchons = rc.getInitialArchonLocations(rc.getTeam().opponent());
        enemyCenter = new MapLocation(0,0);
        for (MapLocation loc : enemyArchons) {
            enemyCenter = new MapLocation(enemyCenter.x + loc.x, enemyCenter.y + loc.y);
        }
        enemyCenter = new MapLocation(enemyCenter.x / enemyArchons.length, enemyCenter.y / enemyArchons.length);
    }

    public static boolean tryMove(MapLocation target) throws GameActionException {
        return tryMoveBug(target);
    }

    public static boolean hasTreeBand(MapLocation from, MapLocation to) throws GameActionException {
        int steps = (int) Math.ceil(from.distanceTo(to));
        for (int i = 0; i <= steps; i++) {
            Direction dir = from.directionTo(to);
            MapLocation point = from.add(dir, i);
            if (rc.canSenseLocation(point)) {
                TreeInfo[] trees = rc.senseNearbyTrees(point, 1, null);
                if (trees.length > 5) return true;
            }
        }
        return false;
    }

    public static boolean isBoxedMap() {
        return true; // since map is Boxed
    }

    public static MapLocation findBandGap(MapLocation target) throws GameActionException {
        MapLocation current = rc.getLocation();
        int sensorRadius = (int) rc.getType().sensorRadius;
        Map<Integer, Integer> treeCountPerY = new HashMap<>();
        for (int dy = -sensorRadius; dy <= sensorRadius; dy += 3) {
            int y = (int) current.y + dy;
            if (y < 0 || y >= 100) continue; // assume max 100
            int count = 0;
            for (int dx = -sensorRadius; dx <= sensorRadius; dx += 1) {
                int x = (int) current.x + dx;
                if (x < 0 || x >= 100) continue; // assume max 100
                MapLocation loc = new MapLocation(x, y);
                if (rc.canSenseLocation(loc)) {
                    TreeInfo[] trees = rc.senseNearbyTrees(loc, 0, null);
                    if (trees.length > 0) count++;
                }
            }
            treeCountPerY.put(y, count);
        }
        List<Integer> bands = new ArrayList<>();
        int maxPossible = (sensorRadius * 2) / 3;
        for (Map.Entry<Integer, Integer> entry : treeCountPerY.entrySet()) {
            if (entry.getValue() > maxPossible) bands.add(entry.getKey());
        }
        Map<Integer, List<Integer>> gapsPerBand = new HashMap<>();
        for (int bandY : bands) {
            List<Integer> gaps = new ArrayList<>();
            for (int x = 0; x < 100; x++) { // assume max 100
                MapLocation loc = new MapLocation(x, bandY);
                if (rc.canSenseLocation(loc)) {
                    TreeInfo[] trees = rc.senseNearbyTrees(loc, 0, null);
                    if (trees.length == 0) gaps.add(x);
                }
            }
            gapsPerBand.put(bandY, gaps);
        }
        for (int bandY : bands) {
            if ((current.y < bandY && target.y > bandY) || (current.y > bandY && target.y < bandY)) {
                List<Integer> gaps = gapsPerBand.get(bandY);
                if (gaps.isEmpty()) continue;
                int closestGap = gaps.get(0);
                int minDist = Math.abs(closestGap - (int) target.x);
                for (int gap : gaps) {
                    int dist = Math.abs(gap - (int) target.x);
                    if (dist < minDist) {
                        minDist = dist;
                        closestGap = gap;
                    }
                }
                return new MapLocation(closestGap, bandY);
            }
        }
        return null;
    }

    public static boolean tryMoveBug(MapLocation target) throws GameActionException {
        MapLocation current = rc.getLocation();
        if (current.distanceTo(target) < 1) return false; // close enough

        int id = rc.getID();
        boolean isFollowingWall = followingWall.getOrDefault(id, false);
        Direction currentBugDir = bugDirection.get(id);

        Direction toTarget = current.directionTo(target);
        if (!isFollowingWall) {
            if (isBoxedMap() && current.x < 25 && target.x > 25 && hasTreeBand(current, target)) {
                if (rc.canMove(Direction.getSouth())) {
                    rc.move(Direction.getSouth());
                    return true;
                }
                if (rc.canMove(Direction.getNorth())) {
                    rc.move(Direction.getNorth());
                    return true;
                }
            } else if (isBoxedMap() && hasTreeBand(current, target)) {
                MapLocation gap = findBandGap(target);
                if (gap != null) {
                    Direction toGap = current.directionTo(gap);
                    if (rc.canMove(toGap)) {
                        rc.move(toGap);
                        return true;
                    }
                    return tryMove(gap);
                } else {
                    if (rc.canMove(Direction.getSouth())) {
                        rc.move(Direction.getSouth());
                        return true;
                    }
                    if (rc.canMove(Direction.getNorth())) {
                        rc.move(Direction.getNorth());
                        return true;
                    }
                }
            } else if (hasTreeBand(current, target)) {
                if (rc.canMove(Direction.getSouth())) {
                    rc.move(Direction.getSouth());
                    return true;
                }
                if (rc.canMove(Direction.getNorth())) {
                    rc.move(Direction.getNorth());
                    return true;
                }
            }
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