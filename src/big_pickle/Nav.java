package big_pickle;

import battlecode.common.*;

import java.util.Random;

public class Nav {
    private static RobotController rc;
    private static Random rand;

    private static final float[] MOVE_OFFSETS = {0f, 15f, -15f, 30f, -30f, 45f, -45f, 70f, -70f, 110f, -110f, 180f};

    public static void init(RobotController controller, Random random) {
        rc = controller;
        rand = random;
    }

    public static boolean moveToward(MapLocation target, boolean dodgeBullets) throws GameActionException {
        if (rc.hasMoved() || target == null) {
            return false;
        }
        MapLocation myLoc = rc.getLocation();
        if (myLoc.distanceTo(target) < 0.01f) {
            return false;
        }
        return moveInDirection(myLoc.directionTo(target), dodgeBullets);
    }

    public static boolean moveInDirection(Direction baseDirection, boolean dodgeBullets) throws GameActionException {
        if (rc.hasMoved() || baseDirection == null) {
            return false;
        }

        BulletInfo[] bullets = dodgeBullets ? rc.senseNearbyBullets(6f) : null;

        Direction bestDirection = null;
        float bestScore = -99999f;

        for (float offset : MOVE_OFFSETS) {
            Direction dir = rotate(baseDirection, offset);
            if (!rc.canMove(dir)) {
                continue;
            }

            MapLocation candidate = rc.getLocation().add(dir, rc.getType().strideRadius);
            float score = 100f - Math.abs(offset);

            if (bullets != null && bullets.length > 0) {
                score -= bulletDanger(candidate, bullets) * 30f;
            }

            score += rand.nextFloat() * 0.5f;

            if (score > bestScore) {
                bestScore = score;
                bestDirection = dir;
            }
        }

        if (bestDirection != null) {
            rc.move(bestDirection);
            return true;
        }

        return false;
    }

    public static boolean wander(Direction preferred) throws GameActionException {
        if (rc.hasMoved()) {
            return false;
        }

        Direction base = preferred;
        if (base == null) {
            base = Utils.randomDirection(rand);
        }

        if (moveInDirection(base, true)) {
            return true;
        }

        for (int i = 0; i < 5; i++) {
            if (moveInDirection(Utils.randomDirection(rand), true)) {
                return true;
            }
        }

        return false;
    }

    private static float bulletDanger(MapLocation location, BulletInfo[] bullets) {
        float danger = 0f;
        float bodyRadius = rc.getType().bodyRadius;

        for (BulletInfo bullet : bullets) {
            if (Utils.willCollideWithLocation(bullet, location, bodyRadius)) {
                danger += bullet.damage;
            }
        }

        return danger;
    }

    private static Direction rotate(Direction base, float degrees) {
        if (degrees > 0f) {
            return base.rotateLeftDegrees(degrees);
        }
        if (degrees < 0f) {
            return base.rotateRightDegrees(-degrees);
        }
        return base;
    }
}
