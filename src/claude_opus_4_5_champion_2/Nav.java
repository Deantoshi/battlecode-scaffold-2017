package claude_opus_4_5_champion_2;
import battlecode.common.*;

public strictfp class Nav {
    static RobotController rc;
    
    public static void init(RobotController rc) {
        Nav.rc = rc;
    }
    
    /**
     * Generate a random direction.
     */
    public static Direction randomDirection() {
        return new Direction((float)Math.random() * 2 * (float)Math.PI);
    }
    
    /**
     * Try to move in a direction, rotating left/right if blocked.
     * Returns true if movement succeeded.
     */
    public static boolean tryMove(Direction dir) throws GameActionException {
        return tryMove(dir, 20, 5);
    }
    
    /**
     * Try to move in a direction with configurable rotation.
     * @param dir The intended direction
     * @param degreeOffset How many degrees to rotate each try
     * @param checksPerSide How many rotations to try on each side
     */
    public static boolean tryMove(Direction dir, float degreeOffset, int checksPerSide) throws GameActionException {
        if (rc.canMove(dir)) {
            rc.move(dir);
            return true;
        }
        
        // Try rotating left and right
        int currentCheck = 1;
        while (currentCheck <= checksPerSide) {
            // Try right
            Direction rightDir = dir.rotateRightDegrees(degreeOffset * currentCheck);
            if (rc.canMove(rightDir)) {
                rc.move(rightDir);
                return true;
            }
            // Try left
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
        MapLocation myLoc = rc.getLocation();
        Direction dir = myLoc.directionTo(target);
        return tryMove(dir);
    }
    
    /**
     * Move away from a location.
     */
    public static boolean moveAway(MapLocation threat) throws GameActionException {
        if (threat == null) {
            return false;
        }
        MapLocation myLoc = rc.getLocation();
        Direction awayDir = threat.directionTo(myLoc);
        return tryMove(awayDir);
    }
    
    /**
     * Try to dodge incoming bullets.
     * Returns true if we moved to dodge.
     */
    public static boolean dodgeBullets() throws GameActionException {
        BulletInfo[] bullets = rc.senseNearbyBullets(5.0f);
        if (bullets.length == 0) {
            return false;
        }
        
        MapLocation myLoc = rc.getLocation();
        float myRadius = rc.getType().bodyRadius;
        
        for (BulletInfo bullet : bullets) {
            // Check if bullet is heading toward us
            Direction bulletDir = bullet.dir;
            MapLocation bulletLoc = bullet.location;
            
            // Vector from bullet to us
            Direction toMe = bulletLoc.directionTo(myLoc);
            float angle = bulletDir.radiansBetween(toMe);
            
            // If bullet is heading somewhat toward us
            if (Math.abs(angle) < Math.PI / 4) {
                float dist = bulletLoc.distanceTo(myLoc);
                
                // If bullet is close, try to dodge perpendicular
                if (dist < 4.0f + myRadius) {
                    Direction dodgeDir;
                    if (angle > 0) {
                        dodgeDir = bulletDir.rotateRightDegrees(90);
                    } else {
                        dodgeDir = bulletDir.rotateLeftDegrees(90);
                    }
                    if (tryMove(dodgeDir, 10, 3)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
    
    /**
     * Check if we can move in a direction.
     */
    public static boolean canMove(Direction dir) {
        return rc.canMove(dir);
    }
    
    /**
     * Get distance to location.
     */
    public static float distanceTo(MapLocation loc) {
        return rc.getLocation().distanceTo(loc);
    }
}
