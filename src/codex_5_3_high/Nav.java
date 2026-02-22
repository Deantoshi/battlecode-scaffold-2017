package codex_5_3_high;

import battlecode.common.*;

public strictfp class Nav {
    static RobotController rc;
    static java.util.Random rng;

    static final float DEGREE_STEP = 15f;
    static final int CHECKS_PER_SIDE = 8;

    static void init(RobotController controller, java.util.Random random) {
        rc = controller;
        rng = random;
    }

    static Direction randomDirection() {
        return new Direction((float) (rng.nextFloat() * Math.PI * 2.0));
    }

    static boolean tryMoveToward(MapLocation loc) throws GameActionException {
        if (loc == null) {
            return false;
        }
        return tryMove(rc.getLocation().directionTo(loc));
    }

    static boolean tryMove(Direction dir) throws GameActionException {
        if (dir == null) {
            return false;
        }
        if (rc.canMove(dir)) {
            rc.move(dir);
            return true;
        }

        for (int i = 1; i <= CHECKS_PER_SIDE; i++) {
            Direction left = dir.rotateLeftDegrees(i * DEGREE_STEP);
            if (rc.canMove(left)) {
                rc.move(left);
                return true;
            }
            Direction right = dir.rotateRightDegrees(i * DEGREE_STEP);
            if (rc.canMove(right)) {
                rc.move(right);
                return true;
            }
        }
        return false;
    }

    static Direction directionFromVector(float vx, float vy, Direction fallback) {
        if (Math.abs(vx) < 0.0001f && Math.abs(vy) < 0.0001f) {
            return fallback;
        }
        return new Direction(vx, vy);
    }

    static void addBulletDodgeVector(BulletInfo[] bullets,
                                     MapLocation my,
                                     float bodyRadius,
                                     float stride,
                                     RobotPlayer.VectorHolder holder) {
        holder.x = 0f;
        holder.y = 0f;

        if (bullets == null || bullets.length == 0) {
            return;
        }

        float totalX = 0f;
        float totalY = 0f;

        for (int i = 0; i < bullets.length; i++) {
            BulletInfo b = bullets[i];
            Direction toMe = b.location.directionTo(my);
            float distToMe = b.location.distanceTo(my);
            float theta = b.dir.radiansBetween(toMe);

            if (Math.abs(theta) > Math.PI / 2f) {
                continue;
            }

            float perpendicular = (float) Math.abs(distToMe * Math.sin(theta));
            float safety = bodyRadius + 0.1f;
            if (perpendicular > safety + stride) {
                continue;
            }

            float eta = distToMe / Math.max(0.01f, b.speed);
            float weight = 1.2f / (0.2f + eta);

            Direction dodge;
            if (theta > 0) {
                dodge = b.dir.rotateRightDegrees(90f);
            } else {
                dodge = b.dir.rotateLeftDegrees(90f);
            }

            totalX += dodge.getDeltaX(weight);
            totalY += dodge.getDeltaY(weight);
        }

        holder.x = totalX;
        holder.y = totalY;
    }
}
