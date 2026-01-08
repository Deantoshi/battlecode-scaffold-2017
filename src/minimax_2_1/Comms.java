package minimax_2_1;
import battlecode.common.*;

public strictfp class Comms {
    static RobotController rc;

    public static final int ARCHON_X_CHANNEL = 0;
    public static final int ARCHON_Y_CHANNEL = 1;
    public static final int ENEMY_X_CHANNEL = 2;
    public static final int ENEMY_Y_CHANNEL = 3;
    public static final int GARDENER_COUNT_CHANNEL = 4;

    public static void broadcastArchonLocation(MapLocation loc) throws GameActionException {
        rc.broadcast(ARCHON_X_CHANNEL, (int)loc.x);
        rc.broadcast(ARCHON_Y_CHANNEL, (int)loc.y);
    }

    public static MapLocation getArchonLocation() throws GameActionException {
        int x = rc.readBroadcast(ARCHON_X_CHANNEL);
        int y = rc.readBroadcast(ARCHON_Y_CHANNEL);
        if (x == 0 && y == 0) {
            return null;
        }
        return new MapLocation(x, y);
    }

    public static void broadcastEnemyLocation(MapLocation loc) throws GameActionException {
        rc.broadcast(ENEMY_X_CHANNEL, (int)loc.x);
        rc.broadcast(ENEMY_Y_CHANNEL, (int)loc.y);
    }

    public static MapLocation getEnemyLocation() throws GameActionException {
        int x = rc.readBroadcast(ENEMY_X_CHANNEL);
        int y = rc.readBroadcast(ENEMY_Y_CHANNEL);
        if (x == 0 && y == 0) {
            return null;
        }
        return new MapLocation(x, y);
    }

    public static void broadcastGardenerCount(int count) throws GameActionException {
        rc.broadcast(GARDENER_COUNT_CHANNEL, count);
    }

    public static int getGardenerCount() throws GameActionException {
        return rc.readBroadcast(GARDENER_COUNT_CHANNEL);
    }
}
