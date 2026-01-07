package glm_4_7;
import battlecode.common.*;

public strictfp class Comms {
    public static void broadcastLocation(RobotController rc, int channelX, int channelY, MapLocation loc) throws GameActionException {
        rc.broadcast(channelX, (int)(loc.x * 1000));
        rc.broadcast(channelY, (int)(loc.y * 1000));
    }

    public static MapLocation readLocation(RobotController rc, int channelX, int channelY) throws GameActionException {
        int x = rc.readBroadcast(channelX);
        int y = rc.readBroadcast(channelY);
        if (x == 0 && y == 0) return null;
        return new MapLocation(x / 1000.0f, y / 1000.0f);
    }

    public static void broadcastEnemySpotted(RobotController rc, MapLocation loc, int roundNum) throws GameActionException {
        broadcastLocation(rc, 5, 6, loc);
        rc.broadcast(7, roundNum);
        rc.broadcast(4, 1);
    }

    public static MapLocation readEnemyLocation(RobotController rc) throws GameActionException {
        if (rc.readBroadcast(4) == 0) return null;
        int lastUpdate = rc.readBroadcast(7);
        if (rc.getRoundNum() - lastUpdate > 20) return null;
        return readLocation(rc, 5, 6);
    }
}
