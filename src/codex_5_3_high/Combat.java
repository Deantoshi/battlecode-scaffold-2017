package codex_5_3_high;

import battlecode.common.*;

public strictfp class Combat {
    static RobotController rc;
    static Team us;

    static void init(RobotController controller) {
        rc = controller;
        us = rc.getTeam();
    }

    static RobotInfo pickTarget(RobotInfo[] enemies) {
        MapLocation my = rc.getLocation();
        RobotInfo best = enemies[0];
        float bestScore = scoreTarget(best, my);
        for (int i = 1; i < enemies.length; i++) {
            float score = scoreTarget(enemies[i], my);
            if (score > bestScore) {
                bestScore = score;
                best = enemies[i];
            }
        }
        return best;
    }

    static RobotInfo pickScoutTarget(RobotInfo[] enemies) {
        MapLocation my = rc.getLocation();
        RobotInfo best = enemies[0];
        float bestScore = scoreScoutTarget(best, my);
        for (int i = 1; i < enemies.length; i++) {
            float score = scoreScoutTarget(enemies[i], my);
            if (score > bestScore) {
                bestScore = score;
                best = enemies[i];
            }
        }
        return best;
    }

    static float scoreTarget(RobotInfo enemy, MapLocation my) {
        float priority;
        switch (enemy.type) {
            case GARDENER:
                priority = 9f;
                break;
            case SOLDIER:
                priority = 8f;
                break;
            case TANK:
                priority = 7f;
                break;
            case LUMBERJACK:
                priority = 7.5f;
                break;
            case SCOUT:
                priority = 6f;
                break;
            case ARCHON:
                priority = 2f;
                break;
            default:
                priority = 4f;
        }

        float dist = my.distanceTo(enemy.location);
        return 100f * priority - 1.8f * enemy.health - 4.2f * dist;
    }

    static float scoreScoutTarget(RobotInfo enemy, MapLocation my) {
        float priority;
        switch (enemy.type) {
            case GARDENER:
                priority = 12f;
                break;
            case SCOUT:
                priority = 10f;
                break;
            case SOLDIER:
                priority = 6f;
                break;
            case LUMBERJACK:
                priority = 5f;
                break;
            case ARCHON:
                priority = 3f;
                break;
            default:
                priority = 4f;
        }
        float dist = my.distanceTo(enemy.location);
        return 100f * priority - 2.2f * enemy.health - 4.5f * dist;
    }

    static void tryShoot(RobotInfo target) throws GameActionException {
        if (target == null || rc.hasAttacked()) {
            return;
        }

        Direction dir = rc.getLocation().directionTo(target.location);
        float dist = rc.getLocation().distanceTo(target.location);

        if (!isSafeLineFire(dir, dist, target.ID)) {
            return;
        }

        RobotType type = rc.getType();

        if (type == RobotType.SOLDIER || type == RobotType.TANK) {
            if (rc.canFirePentadShot() && dist < 3.1f && isWideShotSafe(dir, dist, 15f, 2)) {
                rc.firePentadShot(dir);
                return;
            }
            if (rc.canFireTriadShot() && dist < 5.2f && isWideShotSafe(dir, dist, 20f, 1)) {
                rc.fireTriadShot(dir);
                return;
            }
            if (rc.canFireSingleShot()) {
                rc.fireSingleShot(dir);
            }
        } else if (type == RobotType.SCOUT) {
            if (rc.canFireSingleShot()) {
                rc.fireSingleShot(dir);
            }
        }
    }

    static boolean isWideShotSafe(Direction dir, float dist, float spreadDeg, int sideShots) {
        for (int i = 1; i <= sideShots; i++) {
            Direction left = dir.rotateLeftDegrees(spreadDeg * i);
            Direction right = dir.rotateRightDegrees(spreadDeg * i);
            if (!isSafeLineFire(left, dist, -1) || !isSafeLineFire(right, dist, -1)) {
                return false;
            }
        }
        return true;
    }

    static boolean isSafeLineFire(Direction dir, float dist, int targetId) {
        MapLocation my = rc.getLocation();
        float ux = dir.getDeltaX(1f);
        float uy = dir.getDeltaY(1f);

        try {
            RobotInfo[] allies = rc.senseNearbyRobots(dist + 0.5f, us);
            for (int i = 0; i < allies.length; i++) {
                if (allies[i].ID == rc.getID()) {
                    continue;
                }
                if (lineHitsCircle(my, ux, uy, dist, allies[i].location, allies[i].type.bodyRadius + 0.05f)) {
                    return false;
                }
            }

            TreeInfo[] trees = rc.senseNearbyTrees(dist + 0.5f);
            for (int i = 0; i < trees.length; i++) {
                if (trees[i].team == us) {
                    if (lineHitsCircle(my, ux, uy, dist, trees[i].location, trees[i].radius + 0.02f)) {
                        return false;
                    }
                }
                if (trees[i].team == Team.NEUTRAL) {
                    if (lineHitsCircle(my, ux, uy, dist, trees[i].location, trees[i].radius + 0.02f)) {
                        return false;
                    }
                }
            }

            RobotInfo[] enemiesOnLine = rc.senseNearbyRobots(dist + 0.5f, us.opponent());
            for (int i = 0; i < enemiesOnLine.length; i++) {
                if (targetId >= 0 && enemiesOnLine[i].ID == targetId) {
                    continue;
                }
                float along = projectionAlongRay(my, ux, uy, enemiesOnLine[i].location);
                if (along > 0.2f && along < dist * 0.5f &&
                        perpendicularDistanceSquared(my, ux, uy, enemiesOnLine[i].location) <
                                (enemiesOnLine[i].type.bodyRadius * enemiesOnLine[i].type.bodyRadius)) {
                    return false;
                }
            }

        } catch (Exception ignored) {
            return false;
        }

        return true;
    }

    static boolean lineHitsCircle(MapLocation origin,
                                  float ux,
                                  float uy,
                                  float maxDist,
                                  MapLocation center,
                                  float radius) {
        float relX = center.x - origin.x;
        float relY = center.y - origin.y;

        float along = relX * ux + relY * uy;
        if (along <= 0f || along >= maxDist) {
            return false;
        }

        float perpSq = relX * relX + relY * relY - along * along;
        return perpSq <= radius * radius;
    }

    static float projectionAlongRay(MapLocation origin, float ux, float uy, MapLocation point) {
        float relX = point.x - origin.x;
        float relY = point.y - origin.y;
        return relX * ux + relY * uy;
    }

    static float perpendicularDistanceSquared(MapLocation origin, float ux, float uy, MapLocation point) {
        float relX = point.x - origin.x;
        float relY = point.y - origin.y;
        float along = relX * ux + relY * uy;
        return relX * relX + relY * relY - along * along;
    }
}
