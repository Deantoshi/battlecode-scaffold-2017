package minimax_2_1;
import battlecode.common.*;

public strictfp class Nav {
    static RobotController rc;
    static MapLocation lastLocation = null;
    static int stuckCounter = 0;

    public static void init(RobotController rc) {
        Nav.rc = rc;
    }

    public static boolean tryMove(Direction dir) throws GameActionException {
        // Track if we're stuck
        MapLocation currentLoc = rc.getLocation();
        if (lastLocation != null && currentLoc.distanceTo(lastLocation) < 0.5) {
            stuckCounter++;
        } else {
            stuckCounter = 0;
        }
        lastLocation = currentLoc;
        
        // If can move directly, do it
        if (rc.canMove(dir)) {
            rc.move(dir);
            return true;
        }
        
        RobotInfo[] nearbyRobots = rc.senseNearbyRobots(5, rc.getTeam());
        TreeInfo[] nearbyTrees = rc.senseNearbyTrees(5, Team.NEUTRAL);
        boolean[] blocked = new boolean[8];
        
        // Block cardinal directions (N, S, E, W) for Archons and Gardeners
        for (RobotInfo r : nearbyRobots) {
            if (r.type != RobotType.ARCHON && r.type != RobotType.GARDENER) continue;
            Direction d = rc.getLocation().directionTo(r.location);
            int idx = ((int)Math.round(d.radians / (Math.PI / 4)) % 8 + 8) % 8;
            blocked[idx] = true;
        }
        
        // Block for nearby trees (trees are passable but we try to go around)
        for (TreeInfo t : nearbyTrees) {
            Direction d = rc.getLocation().directionTo(t.location);
            int idx = ((int)Math.round(d.radians / (Math.PI / 4)) % 8 + 8) % 8;
            blocked[idx] = true;
        }
        
        // If stuck, ignore all blocking and try any direction
        if (stuckCounter >= 2) {
            // STUCK MODE: Try all 8 directions aggressively
            for (int i = 0; i < 8; i++) {
                Direction testDir = new Direction((float)(i * Math.PI / 4));
                if (rc.canMove(testDir)) {
                    rc.move(testDir);
                    stuckCounter = 0;
                    return true;
                }
            }
            // Try wider angles
            for (int i = 0; i < 16; i++) {
                Direction testDir = new Direction((float)(i * Math.PI / 8));
                if (rc.canMove(testDir)) {
                    rc.move(testDir);
                    stuckCounter = 0;
                    return true;
                }
            }
            // Reset stuck counter after exhaustive search
            stuckCounter = 0;
            return false;
        }
        
        // Normal mode: try rotations around target direction
        for (int i = 1; i <= 6; i++) {
            Direction left = dir.rotateLeftDegrees(15 * i);
            int leftIdx = ((int)Math.round(left.radians / (Math.PI / 4)) % 8 + 8) % 8;
            if (!blocked[leftIdx] && rc.canMove(left)) {
                rc.move(left);
                return true;
            }
            Direction right = dir.rotateRightDegrees(15 * i);
            int rightIdx = ((int)Math.round(right.radians / (Math.PI / 4)) % 8 + 8) % 8;
            if (!blocked[rightIdx] && rc.canMove(right)) {
                rc.move(right);
                return true;
            }
        }
        
        // Fallback: try any open direction
        for (int i = 0; i < 8; i++) {
            Direction randomDir = new Direction((float)(i * Math.PI / 4));
            if (rc.canMove(randomDir)) {
                rc.move(randomDir);
                return true;
            }
        }
        return false;
    }

    public static boolean moveToward(MapLocation target) throws GameActionException {
        if (target == null) return false;
        Direction dir = rc.getLocation().directionTo(target);
        return tryMove(dir);
    }

    public static Direction randomDirection() {
        return new Direction((float)(Math.random() * 2 * Math.PI));
    }
}
