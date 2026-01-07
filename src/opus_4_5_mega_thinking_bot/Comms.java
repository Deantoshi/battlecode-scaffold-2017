package opus_4_5_mega_thinking_bot;

import battlecode.common.*;

/**
 * Broadcast communication protocol for team coordination.
 * Uses channels 0-99 of the 10,000 available broadcast channels.
 */
public strictfp class Comms {
    private static RobotController rc;

    // ===== CHANNEL LAYOUT =====
    // 0-9:     Archon status and locations
    // 10-19:   Gardener coordination
    // 20-49:   Enemy sightings (priority targets)
    // 50-59:   Focus fire target
    // 60-69:   Map analysis data
    // 70-99:   Unit counts and production queue

    // Archon channels
    public static final int ARCHON_COUNT = 0;
    public static final int ARCHON_X_BASE = 1;  // 1, 2, 3 for up to 3 archons
    public static final int ARCHON_Y_BASE = 4;  // 4, 5, 6

    // Gardener coordination
    public static final int GARDENER_COUNT = 10;
    public static final int GARDENER_SETTLED_COUNT = 11;

    // Enemy tracking (uses encoded location format)
    public static final int ENEMY_ARCHON_X = 20;
    public static final int ENEMY_ARCHON_Y = 21;
    public static final int ENEMY_ARCHON_ROUND = 22;
    public static final int ENEMY_GARDENER_X = 23;
    public static final int ENEMY_GARDENER_Y = 24;
    public static final int ENEMY_GARDENER_ROUND = 25;

    // Focus fire coordination
    public static final int FOCUS_TARGET_ID = 50;
    public static final int FOCUS_TARGET_X = 51;
    public static final int FOCUS_TARGET_Y = 52;
    public static final int FOCUS_TARGET_HP = 53;
    public static final int FOCUS_TARGET_ROUND = 54;

    // Map analysis
    public static final int MAP_TREE_DENSITY = 60;
    public static final int MAP_TYPE = 61;

    // Unit production counts
    public static final int SOLDIER_COUNT = 70;
    public static final int LUMBERJACK_COUNT = 71;
    public static final int SCOUT_COUNT = 72;
    public static final int TANK_COUNT = 73;

    /**
     * Initialize the communication system.
     */
    public static void init(RobotController robotController) {
        rc = robotController;
    }

    // ===== LOCATION ENCODING =====

    /**
     * Encode a MapLocation to an integer.
     * Uses 100x precision to preserve decimal places.
     */
    public static int encodeLocation(MapLocation loc) {
        return ((int)(loc.x * 100)) * 100000 + ((int)(loc.y * 100));
    }

    /**
     * Decode an integer back to MapLocation.
     */
    public static MapLocation decodeLocation(int encoded) {
        if (encoded == 0) return null;
        float x = (encoded / 100000) / 100f;
        float y = (encoded % 100000) / 100f;
        return new MapLocation(x, y);
    }

    // ===== ARCHON BROADCASTING =====

    /**
     * Broadcast archon location (called each turn by archon).
     */
    public static void broadcastArchonLocation(int archonIndex, MapLocation loc) throws GameActionException {
        rc.broadcast(ARCHON_X_BASE + archonIndex, (int)(loc.x * 100));
        rc.broadcast(ARCHON_Y_BASE + archonIndex, (int)(loc.y * 100));
    }

    /**
     * Read archon location.
     */
    public static MapLocation readArchonLocation(int archonIndex) throws GameActionException {
        int x = rc.readBroadcast(ARCHON_X_BASE + archonIndex);
        int y = rc.readBroadcast(ARCHON_Y_BASE + archonIndex);
        if (x == 0 && y == 0) return null;
        return new MapLocation(x / 100f, y / 100f);
    }

    /**
     * Set archon count.
     */
    public static void setArchonCount(int count) throws GameActionException {
        rc.broadcast(ARCHON_COUNT, count);
    }

    /**
     * Get archon count.
     */
    public static int getArchonCount() throws GameActionException {
        return rc.readBroadcast(ARCHON_COUNT);
    }

    // ===== GARDENER COORDINATION =====

    /**
     * Increment gardener count when hired.
     */
    public static void incrementGardenerCount() throws GameActionException {
        int count = rc.readBroadcast(GARDENER_COUNT);
        rc.broadcast(GARDENER_COUNT, count + 1);
    }

    /**
     * Get gardener count.
     */
    public static int getGardenerCount() throws GameActionException {
        return rc.readBroadcast(GARDENER_COUNT);
    }

    /**
     * Increment settled gardener count.
     */
    public static void incrementSettledGardeners() throws GameActionException {
        int count = rc.readBroadcast(GARDENER_SETTLED_COUNT);
        rc.broadcast(GARDENER_SETTLED_COUNT, count + 1);
    }

    /**
     * Get settled gardener count.
     */
    public static int getSettledGardenerCount() throws GameActionException {
        return rc.readBroadcast(GARDENER_SETTLED_COUNT);
    }

    // ===== ENEMY TRACKING =====

    /**
     * Report enemy archon sighting.
     */
    public static void reportEnemyArchon(MapLocation loc) throws GameActionException {
        rc.broadcast(ENEMY_ARCHON_X, (int)(loc.x * 100));
        rc.broadcast(ENEMY_ARCHON_Y, (int)(loc.y * 100));
        rc.broadcast(ENEMY_ARCHON_ROUND, Utils.roundNum);
    }

    /**
     * Get last known enemy archon location.
     */
    public static MapLocation getEnemyArchonLocation() throws GameActionException {
        int x = rc.readBroadcast(ENEMY_ARCHON_X);
        int y = rc.readBroadcast(ENEMY_ARCHON_Y);
        if (x == 0 && y == 0) return null;
        return new MapLocation(x / 100f, y / 100f);
    }

    /**
     * Check if enemy archon sighting is recent (within 50 rounds).
     */
    public static boolean isEnemyArchonRecent() throws GameActionException {
        int round = rc.readBroadcast(ENEMY_ARCHON_ROUND);
        return round > 0 && (Utils.roundNum - round) < 50;
    }

    /**
     * Report enemy gardener sighting.
     */
    public static void reportEnemyGardener(MapLocation loc) throws GameActionException {
        rc.broadcast(ENEMY_GARDENER_X, (int)(loc.x * 100));
        rc.broadcast(ENEMY_GARDENER_Y, (int)(loc.y * 100));
        rc.broadcast(ENEMY_GARDENER_ROUND, Utils.roundNum);
    }

    /**
     * Get last known enemy gardener location.
     */
    public static MapLocation getEnemyGardenerLocation() throws GameActionException {
        int x = rc.readBroadcast(ENEMY_GARDENER_X);
        int y = rc.readBroadcast(ENEMY_GARDENER_Y);
        if (x == 0 && y == 0) return null;
        return new MapLocation(x / 100f, y / 100f);
    }

    /**
     * Check if enemy gardener sighting is recent (within 30 rounds).
     */
    public static boolean isEnemyGardenerRecent() throws GameActionException {
        int round = rc.readBroadcast(ENEMY_GARDENER_ROUND);
        return round > 0 && (Utils.roundNum - round) < 30;
    }

    // ===== FOCUS FIRE COORDINATION =====

    /**
     * Set focus fire target for team coordination.
     */
    public static void setFocusTarget(RobotInfo target) throws GameActionException {
        rc.broadcast(FOCUS_TARGET_ID, target.ID);
        rc.broadcast(FOCUS_TARGET_X, (int)(target.location.x * 100));
        rc.broadcast(FOCUS_TARGET_Y, (int)(target.location.y * 100));
        rc.broadcast(FOCUS_TARGET_HP, (int)(target.health * 10));
        rc.broadcast(FOCUS_TARGET_ROUND, Utils.roundNum);
    }

    /**
     * Get focus target ID.
     */
    public static int getFocusTargetId() throws GameActionException {
        return rc.readBroadcast(FOCUS_TARGET_ID);
    }

    /**
     * Get focus target location.
     */
    public static MapLocation getFocusTargetLocation() throws GameActionException {
        int x = rc.readBroadcast(FOCUS_TARGET_X);
        int y = rc.readBroadcast(FOCUS_TARGET_Y);
        if (x == 0 && y == 0) return null;
        return new MapLocation(x / 100f, y / 100f);
    }

    /**
     * Check if focus target is stale (more than 5 rounds old).
     */
    public static boolean isFocusTargetStale() throws GameActionException {
        int round = rc.readBroadcast(FOCUS_TARGET_ROUND);
        return round == 0 || (Utils.roundNum - round) > 5;
    }

    /**
     * Clear focus target (when target is dead).
     */
    public static void clearFocusTarget() throws GameActionException {
        rc.broadcast(FOCUS_TARGET_ID, 0);
        rc.broadcast(FOCUS_TARGET_ROUND, 0);
    }

    // ===== MAP ANALYSIS =====

    /**
     * Set map type (determined by archon on round 1).
     */
    public static void setMapType(int mapType) throws GameActionException {
        rc.broadcast(MAP_TYPE, mapType);
    }

    /**
     * Get map type.
     */
    public static int getMapType() throws GameActionException {
        return rc.readBroadcast(MAP_TYPE);
    }

    /**
     * Set tree density (0-100 scale).
     */
    public static void setTreeDensity(int density) throws GameActionException {
        rc.broadcast(MAP_TREE_DENSITY, density);
    }

    /**
     * Get tree density.
     */
    public static int getTreeDensity() throws GameActionException {
        return rc.readBroadcast(MAP_TREE_DENSITY);
    }

    // ===== UNIT COUNTING =====

    /**
     * Increment unit count when built.
     */
    public static void incrementUnitCount(RobotType type) throws GameActionException {
        int channel = getUnitChannel(type);
        if (channel != -1) {
            int count = rc.readBroadcast(channel);
            rc.broadcast(channel, count + 1);
        }
    }

    /**
     * Get unit count for a type.
     */
    public static int getUnitCount(RobotType type) throws GameActionException {
        int channel = getUnitChannel(type);
        if (channel == -1) return 0;
        return rc.readBroadcast(channel);
    }

    /**
     * Get the broadcast channel for a unit type.
     */
    private static int getUnitChannel(RobotType type) {
        switch (type) {
            case SOLDIER:     return SOLDIER_COUNT;
            case LUMBERJACK:  return LUMBERJACK_COUNT;
            case SCOUT:       return SCOUT_COUNT;
            case TANK:        return TANK_COUNT;
            default:          return -1;
        }
    }
}
