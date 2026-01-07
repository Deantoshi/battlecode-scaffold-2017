package opus_4_5_mega_thinking_bot;

import battlecode.common.*;

/**
 * Navigation utilities including pathfinding, obstacle avoidance, and bullet dodging.
 */
public strictfp class Nav {
    private static RobotController rc;

    // Bug navigation state
    private static boolean isBugging = false;
    private static Direction bugDirection = null;
    private static MapLocation bugTarget = null;
    private static float bugStartDist = 0;
    private static int bugStartRound = 0;

    /**
     * Initialize the navigation system.
     */
    public static void init(RobotController robotController) {
        rc = robotController;
    }

    // ===== BASIC MOVEMENT =====

    /**
     * Attempt to move in a direction with obstacle avoidance.
     * Tries the primary direction, then rotates left/right by 20 degrees up to 3 times.
     */
    public static boolean tryMove(Direction dir) throws GameActionException {
        return tryMove(dir, 20, 3);
    }

    /**
     * Attempt to move with configurable angle offset and checks.
     */
    public static boolean tryMove(Direction dir, float degreeOffset, int checksPerSide) throws GameActionException {
        if (!rc.hasMoved()) {
            // First try intended direction
            if (rc.canMove(dir)) {
                rc.move(dir);
                return true;
            }

            // Try rotating left and right at increasing angles
            int currentCheck = 1;
            while (currentCheck <= checksPerSide) {
                Direction leftDir = dir.rotateLeftDegrees(degreeOffset * currentCheck);
                if (rc.canMove(leftDir)) {
                    rc.move(leftDir);
                    return true;
                }
                Direction rightDir = dir.rotateRightDegrees(degreeOffset * currentCheck);
                if (rc.canMove(rightDir)) {
                    rc.move(rightDir);
                    return true;
                }
                currentCheck++;
            }
        }
        return false;
    }

    /**
     * Move toward a target location using bug navigation.
     */
    public static boolean moveTo(MapLocation target) throws GameActionException {
        if (rc.hasMoved()) return false;
        if (target == null) return false;

        float dist = Utils.myLoc.distanceTo(target);
        if (dist < 0.5f) return false;

        Direction toTarget = Utils.myLoc.directionTo(target);

        // If not bugging and can move directly, do so
        if (!isBugging && rc.canMove(toTarget)) {
            rc.move(toTarget);
            return true;
        }

        // Start bug navigation if direct path blocked
        if (!isBugging) {
            isBugging = true;
            bugDirection = toTarget;
            bugTarget = target;
            bugStartDist = dist;
            bugStartRound = Utils.roundNum;
        }

        // Reset bugging if we're stuck too long or got closer
        if (Utils.roundNum - bugStartRound > 20) {
            isBugging = false;
            return tryMove(toTarget);
        }

        if (Utils.myLoc.distanceTo(bugTarget) < bugStartDist - 2) {
            isBugging = false;
            return tryMove(toTarget);
        }

        // Trace obstacle boundary by trying to rotate
        for (int i = 0; i < 8; i++) {
            if (rc.canMove(bugDirection)) {
                rc.move(bugDirection);
                // Try to straighten path
                bugDirection = bugDirection.rotateRightDegrees(45);
                return true;
            }
            bugDirection = bugDirection.rotateLeftDegrees(45);
        }

        isBugging = false;
        return false;
    }

    /**
     * Move away from a location.
     */
    public static boolean moveAway(MapLocation threat) throws GameActionException {
        if (threat == null) return false;
        Direction away = threat.directionTo(Utils.myLoc);
        return tryMove(away);
    }

    // ===== BULLET DODGING =====

    /**
     * Check if a bullet will collide with the robot at its current position.
     */
    public static boolean willCollideWithMe(BulletInfo bullet) {
        return willCollideAtLocation(bullet, Utils.myLoc);
    }

    /**
     * Check if a bullet will collide at a given location.
     */
    public static boolean willCollideAtLocation(BulletInfo bullet, MapLocation loc) {
        Direction bulletDir = bullet.dir;
        MapLocation bulletLoc = bullet.location;

        Direction toRobot = bulletLoc.directionTo(loc);
        float distToRobot = bulletLoc.distanceTo(loc);
        float theta = bulletDir.radiansBetween(toRobot);

        // If bullet is traveling away from us
        if (Math.abs(theta) > Math.PI / 2) {
            return false;
        }

        // Calculate perpendicular distance from bullet trajectory to robot center
        float perpendicularDist = (float) Math.abs(distToRobot * Math.sin(theta));
        float bodyRadius = Utils.myType.bodyRadius;

        return perpendicularDist <= bodyRadius;
    }

    /**
     * Get the danger score for a position given incoming bullets.
     */
    public static float getDangerScore(MapLocation loc, BulletInfo[] bullets) {
        float danger = 0;
        for (BulletInfo bullet : bullets) {
            if (willCollideAtLocation(bullet, loc)) {
                danger += bullet.damage * 10;
            } else {
                // Add some danger for nearby bullets
                float dist = distToTrajectory(loc, bullet);
                if (dist < 3) {
                    danger += bullet.damage / (dist + 0.1f);
                }
            }
        }
        return danger;
    }

    /**
     * Calculate distance from a point to a bullet's trajectory line.
     */
    private static float distToTrajectory(MapLocation loc, BulletInfo bullet) {
        MapLocation bulletLoc = bullet.location;
        Direction bulletDir = bullet.dir;

        Direction toPoint = bulletLoc.directionTo(loc);
        float distToPoint = bulletLoc.distanceTo(loc);
        float theta = bulletDir.radiansBetween(toPoint);

        // If point is behind bullet direction, use direct distance
        if (Math.abs(theta) > Math.PI / 2) {
            return distToPoint;
        }

        return (float) Math.abs(distToPoint * Math.sin(theta));
    }

    /**
     * Get the safest direction to move given incoming bullets.
     */
    public static Direction getSafestDirection(BulletInfo[] bullets) {
        Direction safest = null;
        float lowestDanger = Float.MAX_VALUE;

        // Check staying in place
        float currentDanger = getDangerScore(Utils.myLoc, bullets);
        if (currentDanger == 0) {
            return null; // Safe to stay
        }

        // Check all 8 cardinal directions
        for (int i = 0; i < 8; i++) {
            Direction testDir = Utils.CARDINAL_DIRS[i];
            if (!rc.canMove(testDir)) continue;

            MapLocation futurePos = Utils.myLoc.add(testDir, Utils.myType.strideRadius);
            float danger = getDangerScore(futurePos, bullets);

            if (danger < lowestDanger) {
                lowestDanger = danger;
                safest = testDir;
            }
        }

        // Only return if it's safer than staying
        if (lowestDanger < currentDanger) {
            return safest;
        }
        return null;
    }

    /**
     * Dodge incoming bullets if needed.
     * Returns true if we moved to dodge.
     */
    public static boolean dodgeBullets() throws GameActionException {
        if (rc.hasMoved()) return false;

        BulletInfo[] bullets = rc.senseNearbyBullets(10f);
        if (bullets.length == 0) return false;

        Direction safeDir = getSafestDirection(bullets);
        if (safeDir != null) {
            return tryMove(safeDir);
        }
        return false;
    }

    /**
     * Move toward a target while dodging bullets.
     */
    public static boolean safeMoveToward(MapLocation target) throws GameActionException {
        if (rc.hasMoved()) return false;

        BulletInfo[] bullets = rc.senseNearbyBullets(10f);

        if (bullets.length == 0) {
            return moveTo(target);
        }

        // Check if direct path is safe
        Direction toTarget = Utils.myLoc.directionTo(target);
        if (rc.canMove(toTarget)) {
            MapLocation futurePos = Utils.myLoc.add(toTarget, Utils.myType.strideRadius);
            if (getDangerScore(futurePos, bullets) == 0) {
                rc.move(toTarget);
                return true;
            }
        }

        // Try to find a safe direction that's close to target direction
        Direction safest = null;
        float lowestDanger = Float.MAX_VALUE;
        float bestAngle = Float.MAX_VALUE;

        for (int i = 0; i < 8; i++) {
            Direction testDir = Utils.CARDINAL_DIRS[i];
            if (!rc.canMove(testDir)) continue;

            MapLocation futurePos = Utils.myLoc.add(testDir, Utils.myType.strideRadius);
            float danger = getDangerScore(futurePos, bullets);
            float angle = Math.abs(testDir.radiansBetween(toTarget));

            if (danger < lowestDanger || (danger == lowestDanger && angle < bestAngle)) {
                lowestDanger = danger;
                bestAngle = angle;
                safest = testDir;
            }
        }

        if (safest != null && lowestDanger < getDangerScore(Utils.myLoc, bullets)) {
            return tryMove(safest);
        }

        return false;
    }

    // ===== COMBAT MOVEMENT =====

    /**
     * Kite away from an enemy while staying in range.
     */
    public static boolean kiteFrom(MapLocation enemyLoc, float optimalDist) throws GameActionException {
        if (rc.hasMoved()) return false;

        float dist = Utils.myLoc.distanceTo(enemyLoc);
        Direction toEnemy = Utils.myLoc.directionTo(enemyLoc);

        if (dist < optimalDist - 1) {
            // Too close, back up
            return tryMove(toEnemy.opposite());
        } else if (dist > optimalDist + 1) {
            // Too far, close in
            return tryMove(toEnemy);
        } else {
            // Optimal range, strafe
            return strafe(toEnemy);
        }
    }

    /**
     * Strafe perpendicular to enemy direction.
     */
    public static boolean strafe(Direction toEnemy) throws GameActionException {
        if (rc.hasMoved()) return false;

        Direction left = toEnemy.rotateLeftDegrees(90);
        Direction right = toEnemy.rotateRightDegrees(90);

        // Check for bullets and choose safer strafe direction
        BulletInfo[] bullets = rc.senseNearbyBullets(5f);
        if (bullets.length > 0) {
            MapLocation leftPos = Utils.myLoc.add(left, Utils.myType.strideRadius);
            MapLocation rightPos = Utils.myLoc.add(right, Utils.myType.strideRadius);

            float leftDanger = getDangerScore(leftPos, bullets);
            float rightDanger = getDangerScore(rightPos, bullets);

            if (leftDanger < rightDanger && rc.canMove(left)) {
                rc.move(left);
                return true;
            } else if (rc.canMove(right)) {
                rc.move(right);
                return true;
            }
        }

        // No bullets, just pick one
        if (rc.canMove(left)) {
            rc.move(left);
            return true;
        } else if (rc.canMove(right)) {
            rc.move(right);
            return true;
        }
        return false;
    }

    // ===== SPACE FINDING =====

    /**
     * Count open directions around the robot.
     */
    public static int countOpenDirections() throws GameActionException {
        int count = 0;
        float checkDist = 2.5f; // Distance to check for trees/obstacles

        for (Direction dir : Utils.HEX_DIRS) {
            MapLocation checkLoc = Utils.myLoc.add(dir, checkDist);
            if (rc.canSenseLocation(checkLoc) && !rc.isLocationOccupiedByTree(checkLoc)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Check if there's enough space for a gardener farm.
     * Requires at least 4 open directions.
     */
    public static boolean hasSpaceForFarm() throws GameActionException {
        return countOpenDirections() >= 4;
    }

    /**
     * Find a direction to move to find better settling space.
     */
    public static Direction findSettlingDirection() throws GameActionException {
        // Move away from other gardeners and archons
        RobotInfo[] allies = rc.senseNearbyRobots(7f, Utils.myTeam);

        MapLocation awayFrom = null;
        for (RobotInfo ally : allies) {
            if (ally.type == RobotType.GARDENER || ally.type == RobotType.ARCHON) {
                if (awayFrom == null) {
                    awayFrom = ally.location;
                } else {
                    // Average direction away from multiple allies
                    awayFrom = new MapLocation(
                            (awayFrom.x + ally.location.x) / 2,
                            (awayFrom.y + ally.location.y) / 2
                    );
                }
            }
        }

        if (awayFrom != null) {
            return awayFrom.directionTo(Utils.myLoc);
        }

        // No allies nearby, move toward enemy to expand territory
        MapLocation[] enemyArchons = Utils.getEnemyInitialArchonLocs();
        if (enemyArchons.length > 0) {
            return Utils.myLoc.directionTo(enemyArchons[0]);
        }

        return Utils.randomDirection();
    }
}
