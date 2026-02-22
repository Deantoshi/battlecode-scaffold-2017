package codex_5_3_high;

import battlecode.common.*;

public strictfp class Comms {
    static RobotController rc;
    static MapLocation[] initialEnemyArchons;

    static final int CH_ROUND_MARK = 0;

    static final int CH_ENEMY_ROUND = 1;
    static final int CH_ENEMY_X = 2;
    static final int CH_ENEMY_Y = 3;
    static final int CH_ENEMY_TYPE = 4;

    static final int CH_COUNT_ARCHON = 20;
    static final int CH_COUNT_GARDENER = 22;
    static final int CH_COUNT_SOLDIER = 24;
    static final int CH_COUNT_LUMBER = 26;
    static final int CH_COUNT_SCOUT = 28;
    static final int CH_COUNT_TANK = 30;

    static void init(RobotController controller, MapLocation[] enemyArchons) {
        rc = controller;
        initialEnemyArchons = enemyArchons;
    }

    static void beginRound() throws GameActionException {
        int round = rc.getRoundNum();
        if (rc.readBroadcast(CH_ROUND_MARK) != round) {
            int parity = round & 1;
            rc.broadcast(CH_COUNT_ARCHON + parity, 0);
            rc.broadcast(CH_COUNT_GARDENER + parity, 0);
            rc.broadcast(CH_COUNT_SOLDIER + parity, 0);
            rc.broadcast(CH_COUNT_LUMBER + parity, 0);
            rc.broadcast(CH_COUNT_SCOUT + parity, 0);
            rc.broadcast(CH_COUNT_TANK + parity, 0);
            rc.broadcast(CH_ROUND_MARK, round);
        }
    }

    static void reportAlive(RobotType type) throws GameActionException {
        int ch = channelForType(type, rc.getRoundNum() & 1);
        int value = rc.readBroadcast(ch);
        rc.broadcast(ch, value + 1);
    }

    static int getCount(RobotType type) throws GameActionException {
        int previousParity = (rc.getRoundNum() + 1) & 1;
        return rc.readBroadcast(channelForType(type, previousParity));
    }

    static void reportEnemy(RobotInfo enemy) throws GameActionException {
        int now = rc.getRoundNum();
        int seenRound = rc.readBroadcast(CH_ENEMY_ROUND);

        if (now >= seenRound) {
            rc.broadcast(CH_ENEMY_ROUND, now);
            rc.broadcast(CH_ENEMY_X, Float.floatToRawIntBits(enemy.location.x));
            rc.broadcast(CH_ENEMY_Y, Float.floatToRawIntBits(enemy.location.y));
            rc.broadcast(CH_ENEMY_TYPE, enemy.type.ordinal());
        }
    }

    static MapLocation getObjective(MapLocation from) {
        try {
            int seenRound = rc.readBroadcast(CH_ENEMY_ROUND);
            if (rc.getRoundNum() - seenRound <= 20 && seenRound > 0) {
                float x = Float.intBitsToFloat(rc.readBroadcast(CH_ENEMY_X));
                float y = Float.intBitsToFloat(rc.readBroadcast(CH_ENEMY_Y));
                return new MapLocation(x, y);
            }
        } catch (Exception ignored) {
        }

        if (initialEnemyArchons != null && initialEnemyArchons.length > 0) {
            MapLocation best = initialEnemyArchons[0];
            float bestDist = from.distanceSquaredTo(best);
            for (int i = 1; i < initialEnemyArchons.length; i++) {
                float d = from.distanceSquaredTo(initialEnemyArchons[i]);
                if (d < bestDist) {
                    bestDist = d;
                    best = initialEnemyArchons[i];
                }
            }
            return best;
        }
        return from;
    }

    static int channelForType(RobotType type, int parity) {
        switch (type) {
            case ARCHON:
                return CH_COUNT_ARCHON + parity;
            case GARDENER:
                return CH_COUNT_GARDENER + parity;
            case SOLDIER:
                return CH_COUNT_SOLDIER + parity;
            case LUMBERJACK:
                return CH_COUNT_LUMBER + parity;
            case SCOUT:
                return CH_COUNT_SCOUT + parity;
            case TANK:
                return CH_COUNT_TANK + parity;
            default:
                return CH_COUNT_SOLDIER + parity;
        }
    }
}
