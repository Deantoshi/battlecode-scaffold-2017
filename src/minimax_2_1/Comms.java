package minimax_2_1;
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
    
    public static void broadcastEnemySpotted(MapLocation loc) throws GameActionException {
        rc.broadcast(4, 1);
        rc.broadcast(5, (int)(loc.x * 1000));
        rc.broadcast(6, (int)(loc.y * 1000));
    }
    
    public static MapLocation getLastEnemySighting() throws GameActionException {
        if (rc.readBroadcast(4) != 1) return null;
        int x = rc.readBroadcast(5);
        int y = rc.readBroadcast(6);
        if (x == 0 && y == 0) return null;
        return new MapLocation(x / 1000.0f, y / 1000.0f);
    }

    public static int countFriendlyGardeners() throws GameActionException {
        RobotInfo[] robots = rc.senseNearbyRobots(-1, rc.getTeam());
        int count = 0;
        for (RobotInfo robot : robots) {
            if (robot.type == RobotType.GARDENER) {
                count++;
            }
        }
        return count;
    }

    public static MapLocation getFriendlyArchonLocation() throws GameActionException {
        return readLocation(0, 1);
    }
}
