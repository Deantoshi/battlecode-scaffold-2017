package grok_code_fast_1;
import battlecode.common.*;
import java.util.*;

public class Nav {
    static RobotController rc;
    static Map<Integer, Boolean> followingWall = new HashMap<>();
    static Map<Integer, Direction> bugDirection = new HashMap<>();

    public static void init(RobotController rc) {
        Nav.rc = rc;
    }

    public static boolean tryMove(MapLocation target) throws GameActionException {
        MapLocation current = rc.getLocation();
        if (current.distanceTo(target) < 1) return false;

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
                bugDirection.put(id, currentBugDir.rotateLeftDegrees(45));
            }
        }

        return false;
    }
}