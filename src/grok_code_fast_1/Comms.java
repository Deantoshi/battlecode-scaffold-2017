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

    public static void broadcastEnemyLocation(MapLocation loc) throws GameActionException {
        rc.broadcast(2, (int)(loc.x * 10));
        rc.broadcast(3, (int)(loc.y * 10));
    }

    public static MapLocation getEnemyLocation() throws GameActionException {
        float x = rc.readBroadcast(2) / 10.0f;
        float y = rc.readBroadcast(3) / 10.0f;
        if (x == 0 && y == 0) return null;
        return new MapLocation(x, y);
    }

    public static void broadcastSafeArea(MapLocation loc) throws GameActionException {
        broadcastLocation(11, 12, loc);
    }

    public static MapLocation getSafeArea() throws GameActionException {
        return readLocation(11, 12);
    }

    public static void broadcastFocusTarget(MapLocation loc) throws GameActionException {
        broadcastLocation(13, 14, loc);
    }

    public static MapLocation getFocusTarget() throws GameActionException {
        return readLocation(13, 14);
    }

    public static MapLocation getTreeCluster() throws GameActionException {
        return readLocation(5, 6);
    }

    public static MapLocation getMapEdge() throws GameActionException {
        return readLocation(7, 8);
    }

    public static void broadcastChokepoint(MapLocation loc) throws GameActionException {
        broadcastLocation(15, 16, loc);
    }

    public static MapLocation getChokepoint() throws GameActionException {
        return readLocation(15, 16);
    }

    public static void broadcastProductionPriority(int priority) throws GameActionException { // 0=lumberjack, 1=soldier
        rc.broadcast(17, priority);
    }

    public static int getProductionPriority() throws GameActionException {
        return rc.readBroadcast(17);
    }

    public static void broadcastQuadrantEnemies(int quadrant, int count) throws GameActionException { // Quadrant 0-3
        rc.broadcast(18 + quadrant, count);
    }

    public static int getQuadrantEnemies(int quadrant) throws GameActionException {
        return rc.readBroadcast(18 + quadrant);
    }

    public static void broadcastTreeDensity(int density) throws GameActionException {
        rc.broadcast(22, density);
    }

    public static void broadcastEnemyThreats(int count) throws GameActionException {
        rc.broadcast(23, count);
    }

    public static void broadcastUnitCount(int count) throws GameActionException {
        rc.broadcast(24, count);
    }

    public static int getUnitCount() throws GameActionException {
        return rc.readBroadcast(24);
    }

    public static void broadcastRallyPoint(MapLocation loc) throws GameActionException {
        broadcastLocation(25, 26, loc);
    }

    public static MapLocation getRallyPoint() throws GameActionException {
        return readLocation(25, 26);
    }

    public static void broadcastTreePlantingThreshold(int threshold) throws GameActionException {
        rc.broadcast(27, threshold);
    }
    public static int getTreePlantingThreshold() throws GameActionException {
        return rc.readBroadcast(27);
    }
}