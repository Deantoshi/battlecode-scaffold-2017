package grok_code_fast_1;
import battlecode.common.*;
import java.util.*;

public class Nav {
    static RobotController rc;
    static MapLocation enemyCenter;
    static MapLocation spawnLoc;
    static Map<Integer, Boolean> followingWall = new HashMap<>();
    static Map<Integer, Direction> bugDirection = new HashMap<>();

    public static void init(RobotController rc) {
        Nav.rc = rc;
        MapLocation[] enemyArchons = rc.getInitialArchonLocations(rc.getTeam().opponent());
        enemyCenter = new MapLocation(0,0);
        for (MapLocation loc : enemyArchons) {
            enemyCenter = new MapLocation(enemyCenter.x + loc.x, enemyCenter.y + loc.y);
        }
        enemyCenter = new MapLocation(enemyCenter.x / enemyArchons.length, enemyCenter.y / enemyArchons.length);
        spawnLoc = rc.getInitialArchonLocations(rc.getTeam())[0];
    }

    public static boolean isLanesMap() {
        return true;
    }

    public static boolean hasTreeBand(MapLocation from, MapLocation to) throws GameActionException {
        int steps = (int) Math.ceil(from.distanceTo(to));
        int sensorRadius = (int) rc.getType().sensorRadius;
        for (int i = 0; i <= steps && i <= sensorRadius; i++) {
            Direction dir = from.directionTo(to);
            MapLocation point = from.add(dir, i);
            if (rc.canSenseLocation(point)) {
                TreeInfo[] trees = rc.senseNearbyTrees(point, 1, null);
                if (trees.length > 5) return true;
            }
        }
        return false;
    }

    public static MapLocation findBandGap(MapLocation target) throws GameActionException {
        MapLocation current = rc.getLocation();
        int sensorRadius = (int) rc.getType().sensorRadius;
        if (isLanesMap()) {
            // vertical bands: hardcoded bands at x=438.1 and x=463.1
            List<Float> bandXs = Arrays.asList(438.1f, 463.1f);
            for (float bandX : bandXs) {
                if ((current.x < bandX && target.x > bandX) || (current.x > bandX && target.x < bandX)) {
                    List<Float> gaps = new ArrayList<>();
                    for (float y = current.y - 100; y <= current.y + 100; y += 1.0f) {
                        if (y >= 145 && y <= 245) {
                            MapLocation loc = new MapLocation(bandX, y);
                            if (current.distanceTo(loc) <= sensorRadius && rc.canSenseLocation(loc)) {
                                TreeInfo[] trees = rc.senseNearbyTrees(loc, 0, null);
                                if (trees.length == 0) gaps.add(y);
                            }
                        }
                    }
                    if (!gaps.isEmpty()) {
                        float closestGap = gaps.get(0);
                        float minDist = Math.abs(closestGap - target.y);
                        for (float gap : gaps) {
                            float dist = Math.abs(gap - target.y);
                            if (dist < minDist) {
                                minDist = dist;
                                closestGap = gap;
                            }
                        }
                        return new MapLocation(bandX, closestGap);
                    }
                }
            }
        } else {
            // horizontal logic for Boxed
            Map<Integer, Integer> treeCountPerY = new HashMap<>();
            for (int dy = -sensorRadius; dy <= sensorRadius; dy += 3) {
                int y = (int) current.y + dy;
                if (y < 474 || y >= 524) continue; // Boxed map bounds
                int count = 0;
                for (int dx = -sensorRadius; dx <= sensorRadius; dx += 1) {
                    int x = (int) current.x + dx;
                    if (x < 377 || x >= 427) continue; // Boxed map bounds
                    MapLocation loc = new MapLocation(x, y);
                    if (current.distanceTo(loc) <= sensorRadius && rc.canSenseLocation(loc)) {
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
                for (int x = 377; x < 427; x++) { // Boxed map bounds
                    MapLocation loc = new MapLocation(x, bandY);
                    if (current.distanceTo(loc) <= sensorRadius && rc.canSenseLocation(loc)) {
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
        }
        return null;
    }

    public static boolean tryMoveBug(MapLocation target) throws GameActionException {
        if (rc.getType() == RobotType.ARCHON) {
            // Stay near spawn
            if (rc.getLocation().distanceTo(spawnLoc) > 5) {
                return tryMove(rc.getLocation().directionTo(spawnLoc));
            } else {
                return false;
            }
        } else {
            // Original random movement for other units
            for (int i = 0; i < 8; i++) {
                Direction dir = new Direction((float) Math.random() * 2 * (float) Math.PI);
                if (rc.canMove(dir)) {
                    rc.move(dir);
                    return true;
                }
            }
            return false;
        }
    }

    public static boolean tryMove(Direction dir) throws GameActionException {
        if (rc.canMove(dir)) {
            rc.move(dir);
            return true;
        }
        // Try rotated directions
        for (int i = 1; i <= 3; i++) {
            Direction left = dir.rotateLeftDegrees(i * 45);
            if (rc.canMove(left)) {
                rc.move(left);
                return true;
            }
            Direction right = dir.rotateRightDegrees(i * 45);
            if (rc.canMove(right)) {
                rc.move(right);
                return true;
            }
        }
        return false;
    }
}