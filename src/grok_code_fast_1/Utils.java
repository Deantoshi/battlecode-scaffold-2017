package grok_code_fast_1;
import battlecode.common.*;

public strictfp class Utils {
    // Constants
    public static final float EPSILON = 0.001f;
    public static final int MAX_MAP_SIZE = 100;

    // Bit packing helpers
    public static int packLocation(MapLocation loc) {
        return ((int)loc.x << 16) | ((int)loc.y & 0xFFFF);
    }

    public static MapLocation unpackLocation(int packed) {
        return new MapLocation((packed >> 16) & 0xFFFF, packed & 0xFFFF);
    }

    // Geometry helpers
    public static float distSq(MapLocation a, MapLocation b) {
        return a.distanceSquaredTo(b);
    }

    // Random direction
    public static Direction randomDirection() {
        return new Direction((float)Math.random() * 2 * (float)Math.PI);
    }


}