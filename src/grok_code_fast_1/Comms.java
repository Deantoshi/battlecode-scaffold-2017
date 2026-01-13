package grok_code_fast_1;
import battlecode.common.*;

public strictfp class Comms {
    static RobotController rc;

    public static void init(RobotController rc) {
        Comms.rc = rc;
    }

    public static void broadcastLocation(int channelX, int channelY, MapLocation loc) throws GameActionException {
        rc.broadcast(channelX, (int)(loc.x * 1000));
        rc.broadcast(channelY, (int)(loc.y * 1000));
    }

    public static MapLocation readLocation(int channelX, int channelY) throws GameActionException {
        int x = rc.readBroadcast(channelX);
        int y = rc.readBroadcast(channelY);
        if (x == 0 && y == 0) return null;
        return new MapLocation(x / 1000.0f, y / 1000.0f);
    }

    public static boolean isEnemySpotted() throws GameActionException {
        return rc.readBroadcast(4) == 1;
    }

    public static MapLocation getEnemyArchonLocation() throws GameActionException {
        return readLocation(2, 3);
    }
}
