package haiku_4_5;
import battlecode.common.*;

public class Comms {
    static RobotController rc;

    static final int ARCHON_X = 0;
    static final int ARCHON_Y = 1;
    static final int ENEMY_X = 2;
    static final int ENEMY_Y = 3;

    public static void init(RobotController rc) {
        Comms.rc = rc;
    }

    public static void broadcastArchonLocation(MapLocation loc) throws GameActionException {
        rc.broadcast(ARCHON_X, (int)loc.x);
        rc.broadcast(ARCHON_Y, (int)loc.y);
    }

    public static void broadcastEnemyLocation(MapLocation loc) throws GameActionException {
        rc.broadcast(ENEMY_X, (int)loc.x);
        rc.broadcast(ENEMY_Y, (int)loc.y);
    }

    public static MapLocation readArchonLocation() throws GameActionException {
        int x = rc.readBroadcast(ARCHON_X);
        int y = rc.readBroadcast(ARCHON_Y);
        return new MapLocation(x, y);
    }

    public static MapLocation readEnemyLocation() throws GameActionException {
        int x = rc.readBroadcast(ENEMY_X);
        int y = rc.readBroadcast(ENEMY_Y);
        return new MapLocation(x, y);
    }
}
