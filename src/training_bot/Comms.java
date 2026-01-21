package training_bot;

import battlecode.common.*;

/**
 * Communication utilities using broadcast channels.
 * Channels 0-9999 are available for team communication.
 */
public strictfp class Comms {
    private static RobotController rc;

    // Channel assignments
    public static final int CHANNEL_ARCHON_COUNT = 0;
    public static final int CHANNEL_GARDENER_COUNT = 1;
    public static final int CHANNEL_SOLDIER_COUNT = 2;
    public static final int CHANNEL_LUMBERJACK_COUNT = 3;
    public static final int CHANNEL_SCOUT_COUNT = 4;
    public static final int CHANNEL_TANK_COUNT = 5;

    public static final int CHANNEL_ENEMY_ARCHON_X = 10;
    public static final int CHANNEL_ENEMY_ARCHON_Y = 11;
    public static final int CHANNEL_ENEMY_SPOTTED = 12;

    public static final int CHANNEL_TREE_COUNT = 20;

    // Unit count channels for build decisions
    public static final int CHANNEL_ROUND_RESET = 100;

    public static void init(RobotController rc) {
        Comms.rc = rc;
    }

    /**
     * Broadcasts a location to the team.
     */
    public static void broadcastLocation(int channelX, int channelY, MapLocation loc) throws GameActionException {
        rc.broadcast(channelX, (int) (loc.x * 100));
        rc.broadcast(channelY, (int) (loc.y * 100));
    }

    /**
     * Reads a location from broadcast channels.
     */
    public static MapLocation readLocation(int channelX, int channelY) throws GameActionException {
        int x = rc.readBroadcast(channelX);
        int y = rc.readBroadcast(channelY);
        if (x == 0 && y == 0) {
            return null;
        }
        return new MapLocation(x / 100f, y / 100f);
    }

    /**
     * Increments a unit counter.
     */
    public static void incrementCounter(int channel) throws GameActionException {
        int current = rc.readBroadcast(channel);
        rc.broadcast(channel, current + 1);
    }

    /**
     * Reads a counter value.
     */
    public static int readCounter(int channel) throws GameActionException {
        return rc.readBroadcast(channel);
    }

    /**
     * Broadcasts enemy Archon location when spotted.
     */
    public static void reportEnemyArchon(MapLocation loc) throws GameActionException {
        broadcastLocation(CHANNEL_ENEMY_ARCHON_X, CHANNEL_ENEMY_ARCHON_Y, loc);
        rc.broadcast(CHANNEL_ENEMY_SPOTTED, rc.getRoundNum());
    }

    /**
     * Gets the last reported enemy Archon location.
     * Returns null if no recent report (older than 50 rounds).
     */
    public static MapLocation getEnemyArchonLocation() throws GameActionException {
        int lastSpotted = rc.readBroadcast(CHANNEL_ENEMY_SPOTTED);
        if (rc.getRoundNum() - lastSpotted > 50) {
            return null;
        }
        return readLocation(CHANNEL_ENEMY_ARCHON_X, CHANNEL_ENEMY_ARCHON_Y);
    }

    /**
     * Reports the count of trees we own.
     */
    public static void reportTreeCount(int count) throws GameActionException {
        rc.broadcast(CHANNEL_TREE_COUNT, count);
    }

    /**
     * Reads our tree count.
     */
    public static int getTreeCount() throws GameActionException {
        return rc.readBroadcast(CHANNEL_TREE_COUNT);
    }
}
