package big_pickle;

import battlecode.common.*;

public class Comms {
    private static RobotController rc;

    private static final int ENEMY_X = 0;
    private static final int ENEMY_Y = 1;
    private static final int ENEMY_ROUND = 2;
    private static final int ENEMY_PRIORITY = 3;

    private static final int COUNT_BASE = 10;
    private static final int COUNT_STRIDE = 4;
    // Per type channels:
    // +0 = current round stamp
    // +1 = current round count
    // +2 = previous round stamp
    // +3 = previous round count

    public static void init(RobotController controller) {
        rc = controller;
    }

    public static void reportUnitCount(RobotType type) throws GameActionException {
        int idx = typeIndex(type);
        if (idx < 0) {
            return;
        }

        int base = COUNT_BASE + idx * COUNT_STRIDE;
        int curRoundCh = base;
        int curCountCh = base + 1;
        int prevRoundCh = base + 2;
        int prevCountCh = base + 3;

        int round = rc.getRoundNum();
        int curRound = rc.readBroadcast(curRoundCh);

        if (curRound == round) {
            rc.broadcast(curCountCh, rc.readBroadcast(curCountCh) + 1);
            return;
        }

        if (curRound > 0) {
            rc.broadcast(prevRoundCh, curRound);
            rc.broadcast(prevCountCh, rc.readBroadcast(curCountCh));
        }

        rc.broadcast(curRoundCh, round);
        rc.broadcast(curCountCh, 1);
    }

    public static int getUnitCount(RobotType type) throws GameActionException {
        int idx = typeIndex(type);
        if (idx < 0) {
            return 0;
        }

        int base = COUNT_BASE + idx * COUNT_STRIDE;
        int curRound = rc.readBroadcast(base);
        int curCount = rc.readBroadcast(base + 1);
        int prevRound = rc.readBroadcast(base + 2);
        int prevCount = rc.readBroadcast(base + 3);

        int round = rc.getRoundNum();

        if (prevRound == round - 1) {
            return prevCount;
        }
        if (curRound == round) {
            return curCount;
        }
        if (prevRound > 0) {
            return prevCount;
        }
        return curCount;
    }

    public static void reportEnemy(RobotInfo enemy) throws GameActionException {
        if (enemy == null) {
            return;
        }

        int round = rc.getRoundNum();
        int seenRound = rc.readBroadcast(ENEMY_ROUND);
        int oldPriority = rc.readBroadcast(ENEMY_PRIORITY);
        int newPriority = enemyPriority(enemy.type);

        if (round >= seenRound || newPriority >= oldPriority) {
            rc.broadcast(ENEMY_X, Float.floatToRawIntBits(enemy.location.x));
            rc.broadcast(ENEMY_Y, Float.floatToRawIntBits(enemy.location.y));
            rc.broadcast(ENEMY_ROUND, round);
            rc.broadcast(ENEMY_PRIORITY, newPriority);
        }
    }

    public static MapLocation getEnemyLocation() throws GameActionException {
        int round = rc.readBroadcast(ENEMY_ROUND);
        if (round <= 0 || rc.getRoundNum() - round > 60) {
            return null;
        }
        float x = Float.intBitsToFloat(rc.readBroadcast(ENEMY_X));
        float y = Float.intBitsToFloat(rc.readBroadcast(ENEMY_Y));
        return new MapLocation(x, y);
    }

    private static int typeIndex(RobotType type) {
        switch (type) {
            case ARCHON:
                return 0;
            case GARDENER:
                return 1;
            case LUMBERJACK:
                return 2;
            case SOLDIER:
                return 3;
            case SCOUT:
                return 4;
            case TANK:
                return 5;
            default:
                return -1;
        }
    }

    private static int enemyPriority(RobotType type) {
        switch (type) {
            case GARDENER:
                return 6;
            case SOLDIER:
                return 5;
            case TANK:
                return 5;
            case LUMBERJACK:
                return 4;
            case SCOUT:
                return 3;
            case ARCHON:
                return 1;
            default:
                return 1;
        }
    }
}
