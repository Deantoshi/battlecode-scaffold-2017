package minimax_2_1;
import battlecode.common.*;

public strictfp class Utils {
    static RobotController rc;

    public static float distanceSquared(MapLocation a, MapLocation b) {
        float dx = a.x - b.x;
        float dy = a.y - b.y;
        return dx * dx + dy * dy;
    }

    public static MapLocation[] getNeighbors(MapLocation loc, float radius) {
        return new MapLocation[]{
            loc.add(Direction.NORTH),
            loc.add(Direction.EAST),
            loc.add(Direction.SOUTH),
            loc.add(Direction.WEST)
        };
    }
}
