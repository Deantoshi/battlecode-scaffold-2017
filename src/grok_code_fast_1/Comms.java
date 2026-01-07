package grok_code_fast_1;
import battlecode.common.*;

public strictfp class Comms {
    // Channel definitions
    public static final int ARCHON_COUNT = 0;
    public static final int ENEMY_ARCHON_START = 1; // up to 10 or so
    public static final int GARDENER_COUNT = 11;
    public static final int STRATEGY_CHANNEL = 12; // 0: early, 1: mid, 2: late
    public static final int ENEMY_SOLDIER_COUNT = 13;

    // Broadcast archon location
    public static void broadcastArchon(RobotController rc, MapLocation loc, int index) throws GameActionException {
        int packed = Utils.packLocation(loc);
        rc.broadcast(ENEMY_ARCHON_START + index * 2, packed >> 16);
        rc.broadcast(ENEMY_ARCHON_START + index * 2 + 1, packed & 0xFFFF);
    }

    // Read archon location
    public static MapLocation readArchon(RobotController rc, int index) throws GameActionException {
        int x = rc.readBroadcast(ENEMY_ARCHON_START + index * 2);
        int y = rc.readBroadcast(ENEMY_ARCHON_START + index * 2 + 1);
        return new MapLocation(x, y);
    }

    // Broadcast enemy position
    public static void broadcastEnemy(RobotController rc, MapLocation loc, int channel) throws GameActionException {
        rc.broadcast(channel, Utils.packLocation(loc));
    }

    // Read enemy position
    public static MapLocation readEnemy(RobotController rc, int channel) throws GameActionException {
        return Utils.unpackLocation(rc.readBroadcast(channel));
    }
}