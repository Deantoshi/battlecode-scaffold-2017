package claude_opus_4_5;
import battlecode.common.*;

public strictfp class Comms {
    static RobotController rc;
    
    // Channel constants
    public static final int ARCHON_X = 0;
    public static final int ARCHON_Y = 1;
    public static final int ENEMY_ARCHON_X = 2;
    public static final int ENEMY_ARCHON_Y = 3;
    public static final int GARDENER_COUNT = 4;
    public static final int SOLDIER_COUNT = 5;
    public static final int LUMBERJACK_COUNT = 6;
    public static final int SCOUT_COUNT = 7;
    public static final int TANK_COUNT = 8;
    public static final int TREE_COUNT = 9;
    
    // Encoding multiplier for locations
    private static final int LOC_MULTIPLIER = 1000;
    
    public static void init(RobotController rc) {
        Comms.rc = rc;
    }
    
    /**
     * Broadcast a location to two channels (X and Y).
     */
    public static void broadcastLocation(int channelX, int channelY, MapLocation loc) throws GameActionException {
        if (loc == null) return;
        int x = (int)(loc.x * LOC_MULTIPLIER);
        int y = (int)(loc.y * LOC_MULTIPLIER);
        rc.broadcast(channelX, x);
        rc.broadcast(channelY, y);
    }
    
    /**
     * Read a location from two channels.
     * Returns null if no location has been broadcast.
     */
    public static MapLocation readLocation(int channelX, int channelY) throws GameActionException {
        int x = rc.readBroadcast(channelX);
        int y = rc.readBroadcast(channelY);
        if (x == 0 && y == 0) {
            return null;
        }
        return new MapLocation((float)x / LOC_MULTIPLIER, (float)y / LOC_MULTIPLIER);
    }
    
    /**
     * Get our Archon's last broadcast location.
     */
    public static MapLocation getArchonLocation() throws GameActionException {
        return readLocation(ARCHON_X, ARCHON_Y);
    }
    
    /**
     * Get enemy Archon location if known.
     */
    public static MapLocation getEnemyArchonLocation() throws GameActionException {
        return readLocation(ENEMY_ARCHON_X, ENEMY_ARCHON_Y);
    }
    
    /**
     * Broadcast enemy Archon location.
     */
    public static void broadcastEnemyArchon(MapLocation loc) throws GameActionException {
        broadcastLocation(ENEMY_ARCHON_X, ENEMY_ARCHON_Y, loc);
    }
    
    /**
     * Increment a count on a channel.
     */
    public static void incrementCount(int channel) throws GameActionException {
        int count = rc.readBroadcast(channel);
        rc.broadcast(channel, count + 1);
    }
    
    /**
     * Get count from a channel.
     */
    public static int getCount(int channel) throws GameActionException {
        return rc.readBroadcast(channel);
    }
    
    /**
     * Set count on a channel.
     */
    public static void setCount(int channel, int value) throws GameActionException {
        rc.broadcast(channel, value);
    }
    
    /**
     * Reset all counts (called by Archon at start of round).
     */
    public static void resetCounts() throws GameActionException {
        rc.broadcast(GARDENER_COUNT, 0);
        rc.broadcast(SOLDIER_COUNT, 0);
        rc.broadcast(LUMBERJACK_COUNT, 0);
        rc.broadcast(SCOUT_COUNT, 0);
        rc.broadcast(TANK_COUNT, 0);
    }
    
    /**
     * Report existence of this robot type.
     */
    public static void reportAlive() throws GameActionException {
        RobotType type = rc.getType();
        switch (type) {
            case GARDENER:   incrementCount(GARDENER_COUNT);   break;
            case SOLDIER:    incrementCount(SOLDIER_COUNT);    break;
            case LUMBERJACK: incrementCount(LUMBERJACK_COUNT); break;
            case SCOUT:      incrementCount(SCOUT_COUNT);      break;
            case TANK:       incrementCount(TANK_COUNT);       break;
            default: break;
        }
    }
}
