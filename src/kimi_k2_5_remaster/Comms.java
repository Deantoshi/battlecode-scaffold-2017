package kimi_k2_5_remaster;
import battlecode.common.*;

/**
 * Communication system using broadcast channels.
 * Channel map:
 * 0-1: Archon position
 * 2-3: Enemy Archon position
 * 4: Enemy spotted flag
 * 5-9: Unit counters (Gardener, Scout, Soldier, Lumberjack, Tank)
 * 10-11: Enemy Gardener position
 * 12: Emergency flag
 * 13-14: Rally point
 */
public strictfp class Comms {
    static RobotController rc;
    static final float ENCODING_SCALE = 1000.0f;
    
    // Channel constants
    static final int ARCHON_X = 0;
    static final int ARCHON_Y = 1;
    static final int ENEMY_ARCHON_X = 2;
    static final int ENEMY_ARCHON_Y = 3;
    static final int ENEMY_SPOTTED = 4;
    static final int COUNTER_GARDENER = 5;
    static final int COUNTER_SCOUT = 6;
    static final int COUNTER_SOLDIER = 7;
    static final int COUNTER_LUMBERJACK = 8;
    static final int COUNTER_TANK = 9;
    static final int ENEMY_GARDENER_X = 10;
    static final int ENEMY_GARDENER_Y = 11;
    static final int EMERGENCY = 12;
    static final int RALLY_X = 13;
    static final int RALLY_Y = 14;
    
    public static void init(RobotController rcIn) {
        rc = rcIn;
    }
    
    /**
     * Broadcast a location on two channels (x and y).
     */
    public static void broadcastLocation(int channelX, int channelY, MapLocation loc) throws GameActionException {
        if (loc == null) return;
        rc.broadcast(channelX, (int)(loc.x * ENCODING_SCALE));
        rc.broadcast(channelY, (int)(loc.y * ENCODING_SCALE));
    }
    
    /**
     * Read a location from two channels.
     */
    public static MapLocation readLocation(int channelX, int channelY) throws GameActionException {
        int x = rc.readBroadcast(channelX);
        int y = rc.readBroadcast(channelY);
        if (x == 0 && y == 0) return null;
        return new MapLocation(x / ENCODING_SCALE, y / ENCODING_SCALE);
    }
    
    /**
     * Check if an enemy has been spotted.
     */
    public static boolean isEnemySpotted() throws GameActionException {
        return rc.readBroadcast(ENEMY_SPOTTED) > 0;
    }
    
    /**
     * Set enemy spotted flag.
     */
    public static void setEnemySpotted(boolean spotted) throws GameActionException {
        rc.broadcast(ENEMY_SPOTTED, spotted ? 1 : 0);
    }
    
    /**
     * Report enemy Archon location.
     */
    public static void reportEnemyArchon(MapLocation loc) throws GameActionException {
        broadcastLocation(ENEMY_ARCHON_X, ENEMY_ARCHON_Y, loc);
    }
    
    /**
     * Get enemy Archon location.
     */
    public static MapLocation getEnemyArchonLocation() throws GameActionException {
        return readLocation(ENEMY_ARCHON_X, ENEMY_ARCHON_Y);
    }
    
    /**
     * Report enemy Gardener location.
     */
    public static void reportEnemyGardener(MapLocation loc) throws GameActionException {
        broadcastLocation(ENEMY_GARDENER_X, ENEMY_GARDENER_Y, loc);
    }
    
    /**
     * Get enemy Gardener location.
     */
    public static MapLocation getEnemyGardenerLocation() throws GameActionException {
        return readLocation(ENEMY_GARDENER_X, ENEMY_GARDENER_Y);
    }
    
    /**
     * Broadcast our Archon position.
     */
    public static void broadcastArchonLocation(MapLocation loc) throws GameActionException {
        broadcastLocation(ARCHON_X, ARCHON_Y, loc);
    }
    
    /**
     * Read our Archon position.
     */
    public static MapLocation getArchonLocation() throws GameActionException {
        return readLocation(ARCHON_X, ARCHON_Y);
    }
    
    /**
     * Set rally point.
     */
    public static void setRallyPoint(MapLocation loc) throws GameActionException {
        broadcastLocation(RALLY_X, RALLY_Y, loc);
    }
    
    /**
     * Get rally point.
     */
    public static MapLocation getRallyPoint() throws GameActionException {
        return readLocation(RALLY_X, RALLY_Y);
    }
    
    /**
     * Set emergency flag.
     */
    public static void setEmergency(boolean emergency) throws GameActionException {
        rc.broadcast(EMERGENCY, emergency ? 1 : 0);
    }
    
    /**
     * Check emergency flag.
     */
    public static boolean isEmergency() throws GameActionException {
        return rc.readBroadcast(EMERGENCY) > 0;
    }
    
    /**
     * Increment a counter channel.
     */
    public static void incrementCounter(int channel) throws GameActionException {
        int current = rc.readBroadcast(channel);
        rc.broadcast(channel, current + 1);
    }
    
    /**
     * Decrement a counter channel.
     */
    public static void decrementCounter(int channel) throws GameActionException {
        int current = rc.readBroadcast(channel);
        if (current > 0) {
            rc.broadcast(channel, current - 1);
        }
    }
    
    /**
     * Get counter value.
     */
    public static int getCount(int channel) throws GameActionException {
        return rc.readBroadcast(channel);
    }
    
    /**
     * Reset counter.
     */
    public static void resetCounter(int channel) throws GameActionException {
        rc.broadcast(channel, 0);
    }
    
    /**
     * Report any enemy seen.
     */
    public static void reportEnemy(RobotInfo enemy) throws GameActionException {
        setEnemySpotted(true);
        
        if (enemy.type == RobotType.ARCHON) {
            reportEnemyArchon(enemy.location);
        } else if (enemy.type == RobotType.GARDENER) {
            reportEnemyGardener(enemy.location);
        }
    }
}
