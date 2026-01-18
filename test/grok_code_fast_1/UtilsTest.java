package grok_code_fast_1;

import battlecode.common.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class UtilsTest {

    @Test
    public void testCalculateCentroid() {
        // Create mock RobotInfo
        RobotInfo r1 = new RobotInfo(1, Team.A, RobotType.SOLDIER, new MapLocation(0, 0), 100, 0, 0);
        RobotInfo r2 = new RobotInfo(2, Team.A, RobotType.SOLDIER, new MapLocation(2, 2), 100, 0, 0);
        RobotInfo r3 = new RobotInfo(3, Team.A, RobotType.SOLDIER, new MapLocation(4, 4), 100, 0, 0);

        RobotInfo[] robots = {r1, r2, r3};

        MapLocation centroid = Utils.calculateCentroid(robots);

        assertNotNull(centroid);
        assertEquals(2.0f, centroid.x, 0.01f);
        assertEquals(2.0f, centroid.y, 0.01f);
    }

    @Test
    public void testCalculateCentroidEmpty() {
        RobotInfo[] robots = {};
        MapLocation centroid = Utils.calculateCentroid(robots);
        assertNull(centroid);
    }

    @Test
    public void testGetDirections() {
        Direction[] directions = Utils.getDirections();
        assertEquals(8, directions.length);
        assertEquals(Direction.NORTH, directions[0]);
    }
}