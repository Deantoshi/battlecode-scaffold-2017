package big_pickle;

import battlecode.common.*;

import java.util.Random;

public class Utils {
    public static Direction randomDirection(Random rand) {
        return new Direction((float) (rand.nextFloat() * 2f * Math.PI));
    }

    public static boolean willCollideWithLocation(BulletInfo bullet, MapLocation location, float bodyRadius) {
        Direction propagationDirection = bullet.dir;
        MapLocation bulletLocation = bullet.location;

        Direction directionToRobot = bulletLocation.directionTo(location);
        float distToRobot = bulletLocation.distanceTo(location);
        float theta = propagationDirection.radiansBetween(directionToRobot);

        if (Math.abs(theta) > Math.PI / 2) {
            return false;
        }

        float perpendicularDist = (float) Math.abs(distToRobot * Math.sin(theta));
        return perpendicularDist <= bodyRadius + 0.01f;
    }

    public static boolean rayIntersectsCircle(
            MapLocation origin,
            Direction rayDirection,
            MapLocation center,
            float radius,
            float maxDistance
    ) {
        Direction toCircle = origin.directionTo(center);
        float dist = origin.distanceTo(center);

        if (dist > maxDistance + radius + 0.05f) {
            return false;
        }

        float theta = rayDirection.radiansBetween(toCircle);
        if (Math.abs(theta) > Math.PI / 2) {
            return false;
        }

        float perpendicularDist = (float) Math.abs(dist * Math.sin(theta));
        return perpendicularDist <= radius + 0.03f;
    }

    public static RobotInfo pickBestEnemy(RobotInfo[] enemies, MapLocation from) {
        RobotInfo best = enemies[0];
        float bestScore = scoreEnemy(best, from);

        for (int i = 1; i < enemies.length; i++) {
            float score = scoreEnemy(enemies[i], from);
            if (score > bestScore) {
                bestScore = score;
                best = enemies[i];
            }
        }

        return best;
    }

    private static float scoreEnemy(RobotInfo enemy, MapLocation from) {
        float priority;
        switch (enemy.type) {
            case GARDENER:
                priority = 9f;
                break;
            case SOLDIER:
                priority = 8f;
                break;
            case TANK:
                priority = 8f;
                break;
            case LUMBERJACK:
                priority = 7f;
                break;
            case SCOUT:
                priority = 6f;
                break;
            case ARCHON:
                priority = 4f;
                break;
            default:
                priority = 1f;
        }

        float distPenalty = from.distanceTo(enemy.location) * 0.9f;
        float healthPenalty = enemy.health * 0.02f;
        return priority * 10f - distPenalty - healthPenalty;
    }
}
