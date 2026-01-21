package training_bot;

import battlecode.common.*;

/**
 * Navigation utilities for movement and pathfinding.
 * Implements obstacle avoidance and bullet dodging.
 */
public strictfp class Navigation {
    private static RobotController rc;

    // Directions for rotation attempts
    private static final float[] ROTATION_ANGLES = {0, 15, -15, 30, -30, 45, -45, 60, -60, 90, -90};

    public static void init(RobotController rc) {
        Navigation.rc = rc;
    }

    /**
     * Attempts to move in the given direction with obstacle avoidance.
     * Tries rotating left and right if the direct path is blocked.
     *
     * @param dir The desired direction
     * @return true if movement succeeded
     */
    public static boolean tryMove(Direction dir) throws GameActionException {
        return tryMove(dir, 90, 15);
    }

    /**
     * Attempts to move in the given direction with customizable rotation.
     *
     * @param dir The desired direction
     * @param maxAngle Maximum angle to rotate (degrees)
     * @param angleStep Step size for rotation attempts (degrees)
     * @return true if movement succeeded
     */
    public static boolean tryMove(Direction dir, float maxAngle, float angleStep) throws GameActionException {
        if (rc.hasMoved()) {
            return false;
        }

        // Try the direct direction first
        if (rc.canMove(dir)) {
            rc.move(dir);
            return true;
        }

        // Try rotating left and right
        float currentAngle = angleStep;
        while (currentAngle <= maxAngle) {
            Direction leftDir = dir.rotateLeftDegrees(currentAngle);
            if (rc.canMove(leftDir)) {
                rc.move(leftDir);
                return true;
            }

            Direction rightDir = dir.rotateRightDegrees(currentAngle);
            if (rc.canMove(rightDir)) {
                rc.move(rightDir);
                return true;
            }

            currentAngle += angleStep;
        }

        return false;
    }

    /**
     * Move toward a target location with obstacle avoidance.
     *
     * @param target The target MapLocation
     * @return true if movement succeeded
     */
    public static boolean moveToward(MapLocation target) throws GameActionException {
        if (target == null) {
            return false;
        }
        Direction dir = rc.getLocation().directionTo(target);
        return tryMove(dir);
    }

    /**
     * Move away from a location (flee behavior).
     *
     * @param threat The location to flee from
     * @return true if movement succeeded
     */
    public static boolean moveAwayFrom(MapLocation threat) throws GameActionException {
        if (threat == null) {
            return false;
        }
        Direction awayDir = threat.directionTo(rc.getLocation());
        return tryMove(awayDir);
    }

    /**
     * Attempts to dodge incoming bullets.
     * Analyzes nearby bullets and moves perpendicular to their path.
     *
     * @return true if a dodge was attempted
     */
    public static boolean tryDodgeBullets() throws GameActionException {
        BulletInfo[] bullets = rc.senseNearbyBullets(5f);

        if (bullets.length == 0) {
            return false;
        }

        MapLocation myLoc = rc.getLocation();
        float bodyRadius = rc.getType().bodyRadius;

        for (BulletInfo bullet : bullets) {
            // Check if bullet will hit us
            if (willBulletHit(myLoc, bodyRadius, bullet)) {
                // Move perpendicular to bullet direction
                Direction bulletDir = bullet.dir;
                Direction dodgeDir = bulletDir.rotateLeftDegrees(90);

                if (rc.canMove(dodgeDir)) {
                    rc.move(dodgeDir);
                    return true;
                } else if (rc.canMove(dodgeDir.opposite())) {
                    rc.move(dodgeDir.opposite());
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Checks if a bullet will hit a robot at the given location.
     */
    private static boolean willBulletHit(MapLocation robotLoc, float bodyRadius, BulletInfo bullet) {
        MapLocation bulletLoc = bullet.location;
        Direction bulletDir = bullet.dir;
        float bulletSpeed = bullet.speed;

        // Calculate where bullet will be next turn
        MapLocation nextBulletLoc = bulletLoc.add(bulletDir, bulletSpeed);

        // Check if robot is in bullet's path
        // Using simple distance check to bullet's trajectory
        float distToLine = distanceToLine(robotLoc, bulletLoc, nextBulletLoc);

        return distToLine < bodyRadius + 0.1f;
    }

    /**
     * Calculates distance from a point to a line segment.
     */
    private static float distanceToLine(MapLocation point, MapLocation lineStart, MapLocation lineEnd) {
        float dx = lineEnd.x - lineStart.x;
        float dy = lineEnd.y - lineStart.y;
        float lengthSq = dx * dx + dy * dy;

        if (lengthSq == 0) {
            return point.distanceTo(lineStart);
        }

        float t = Math.max(0, Math.min(1,
            ((point.x - lineStart.x) * dx + (point.y - lineStart.y) * dy) / lengthSq));

        MapLocation projection = new MapLocation(
            lineStart.x + t * dx,
            lineStart.y + t * dy
        );

        return point.distanceTo(projection);
    }

    /**
     * Wander randomly with obstacle avoidance.
     *
     * @return true if movement succeeded
     */
    public static boolean wander() throws GameActionException {
        Direction dir = randomDirection();
        return tryMove(dir);
    }

    /**
     * Returns a random direction.
     */
    public static Direction randomDirection() {
        return new Direction((float) Math.random() * 2 * (float) Math.PI);
    }

    /**
     * Returns the direction to the nearest enemy Archon (initial positions).
     */
    public static Direction towardEnemyArchons() throws GameActionException {
        MapLocation[] enemyArchons = rc.getInitialArchonLocations(rc.getTeam().opponent());
        if (enemyArchons.length > 0) {
            return rc.getLocation().directionTo(enemyArchons[0]);
        }
        return randomDirection();
    }
}
