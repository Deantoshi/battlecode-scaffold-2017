package kimi_k2_5_remaster_champion_0;
import battlecode.common.*;

/**
 * Navigation utilities for robot movement.
 */
public strictfp class Nav {
    static RobotController rc;
    
    public static void init(RobotController rcIn) {
        rc = rcIn;
    }
    
    /**
     * Attempts to move in a direction, trying multiple rotations if needed.
     */
    public static boolean tryMove(Direction dir) throws GameActionException {
        return tryMove(dir, 20, 3);
    }
    
    /**
     * Attempts to move in a direction with specified degree and check offsets.
     */
    public static boolean tryMove(Direction dir, float degreeOffset, int checksPerSide) throws GameActionException {
        if (rc.hasMoved()) {
            return false;
        }
        
        int currentCheck = 1;
        float offset = degreeOffset * ((float)Math.PI / 180);
        
        // Try original direction
        if (rc.canMove(dir)) {
            rc.move(dir);
            return true;
        }
        
        // Try rotated directions
        while (currentCheck <= checksPerSide) {
            // Try offset to the right
            Direction rightDir = dir.rotateRightDegrees(degreeOffset * currentCheck);
            if (rc.canMove(rightDir)) {
                rc.move(rightDir);
                return true;
            }
            
            // Try offset to the left
            Direction leftDir = dir.rotateLeftDegrees(degreeOffset * currentCheck);
            if (rc.canMove(leftDir)) {
                rc.move(leftDir);
                return true;
            }
            
            currentCheck++;
        }
        
        return false;
    }
    
    /**
     * Move toward a target location.
     */
    public static boolean moveToward(MapLocation target) throws GameActionException {
        if (target == null) {
            return false;
        }
        
        Direction dir = rc.getLocation().directionTo(target);
        return tryMove(dir);
    }
    
    /**
     * Get a random direction.
     */
    public static Direction randomDirection() {
        return new Direction((float)Math.random() * 2 * (float)Math.PI);
    }
    
    /**
     * Attempt to dodge incoming bullets.
     * Returns true if dodged.
     */
    public static boolean tryDodgeBullets() throws GameActionException {
        BulletInfo[] bullets = rc.senseNearbyBullets(8.0f);
        if (bullets.length == 0) {
            return false;
        }
        
        MapLocation myLoc = rc.getLocation();
        Direction dodgeDir = null;
        float maxDanger = 0;
        
        for (BulletInfo bullet : bullets) {
            // Check if bullet is heading toward us
            MapLocation bulletLoc = bullet.location;
            Direction bulletDir = bullet.dir;
            
            // Project bullet path
            float distToBullet = myLoc.distanceTo(bulletLoc);
            float angleToBullet = myLoc.directionTo(bulletLoc).radiansBetween(bulletDir);
            
            // If bullet is generally heading toward us
            if (Math.abs(angleToBullet) < Math.PI / 2) {
                // Calculate closest point on bullet path
                MapLocation projectedLoc = bulletLoc.add(bulletDir, distToBullet);
                float distFromPath = myLoc.distanceTo(projectedLoc);
                
                // If we're in danger
                if (distFromPath < rc.getType().bodyRadius + 1.0f) {
                    float danger = bullet.damage / (distToBullet + 0.1f);
                    if (danger > maxDanger) {
                        maxDanger = danger;
                        // Dodge perpendicular to bullet direction
                        dodgeDir = bulletDir.rotateRightDegrees(90);
                        if (Math.random() < 0.5) {
                            dodgeDir = bulletDir.rotateLeftDegrees(90);
                        }
                    }
                }
            }
        }
        
        if (dodgeDir != null) {
            return tryMove(dodgeDir, 30, 3);
        }
        
        return false;
    }
    
    /**
     * Move away from a location.
     */
    public static boolean moveAwayFrom(MapLocation target) throws GameActionException {
        if (target == null) {
            return false;
        }
        
        Direction dir = target.directionTo(rc.getLocation());
        return tryMove(dir);
    }
    
    /**
     * Attempt to move to a specific location.
     */
    public static boolean tryMoveTo(MapLocation loc) throws GameActionException {
        if (rc.hasMoved()) {
            return false;
        }
        
        if (rc.canMove(loc)) {
            rc.move(loc);
            return true;
        }
        
        // Try to move toward the location
        Direction dir = rc.getLocation().directionTo(loc);
        return tryMove(dir);
    }
    
    /**
     * Check if a location is safe (no enemy units nearby).
     */
    public static boolean isSafe(MapLocation loc) {
        RobotInfo[] enemies = rc.senseNearbyRobots(loc, 7.0f, rc.getTeam().opponent());
        return enemies.length == 0;
    }
}
