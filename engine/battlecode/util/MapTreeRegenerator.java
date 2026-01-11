package battlecode.util;

import battlecode.common.*;
import battlecode.world.GameMapIO;
import battlecode.world.LiveMap;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility to regenerate a map with symmetrical, neatly spaced trees.
 * Creates a wedge pattern with trees on left and right sides.
 */
public class MapTreeRegenerator {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: MapTreeRegenerator <mapName> [outputMapName]");
            System.err.println("If outputMapName is not specified, overwrites the original map");
            System.exit(1);
        }

        String mapName = args[0];
        String outputMapName = args.length > 1 ? args[1] : mapName;

        try {
            LiveMap map = GameMapIO.loadMap(mapName, new File("maps"));
            System.out.println("Loaded map: " + mapName);

            LiveMap regeneratedMap = regenerateTrees(map);
            System.out.println("Regenerated map: " + countNeutralTrees(regeneratedMap) + " trees");

            GameMapIO.writeMap(regeneratedMap, new File("maps"));
            System.out.println("Saved regenerated map to: " + outputMapName + ".map17");
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static int countNeutralTrees(LiveMap map) {
        int count = 0;
        for (BodyInfo body : map.getInitialBodies()) {
            if (body.isTree()) {
                TreeInfo tree = (TreeInfo) body;
                if (tree.team == Team.NEUTRAL) {
                    count++;
                }
            }
        }
        return count;
    }

    private static LiveMap regenerateTrees(LiveMap map) {
        List<BodyInfo> newBodies = new ArrayList<>();

        // Don't add existing archons - we'll create new ones

        int nextRobotId = 0;

        // Generate symmetrical wedge pattern
        float mapWidth = map.getWidth();
        float mapHeight = map.getHeight();
        float treeRadius = 2.5f;
        float treeSpacing = treeRadius * 2.0f + 1.0f; // 6.0 - small gap between trees

        // Generate trees with wedge pattern
        // Wedge on left side and right side, mirrored top and bottom
        float centerLineY = mapHeight / 2.0f;
        float centerLineX = mapWidth / 2.0f;

        int nextTreeId = 10000;

        // Generate for top wedge (high y) and bottom wedge (low y)
        // Pattern: trees form ^ and upside down ^ pointing toward center
        float startY = treeRadius + 0.5f;
        float endY = centerLineY - 5.0f;

        for (float y = startY; y <= endY; y += treeSpacing) {
            // Calculate how wide the tree coverage is at this y position
            // Closer to top/bottom edges = wider coverage (more X positions)
            // Closer to center = narrower coverage (fewer X positions)

            float distFromEdge = y - startY;
            float maxDistFromEdge = endY - startY;
            float normalizedDist = distFromEdge / maxDistFromEdge; // 0 at edge, 1 at center

            // At edges: cover most of X, at center: narrow wedge
            float maxSpreadFromCenter = (mapWidth / 2.0f) * (1.0f - normalizedDist * 0.7f);

            // Generate X positions for left half (including center column)
            List<Float> xPositions = new ArrayList<>();

            float startX = treeRadius + 0.5f;

            // Generate for left half including center (x <= centerLineX)
            // Right half will be mirror
            for (float x = startX; x <= centerLineX + 0.5f; x += treeSpacing) {
                if (Math.abs(x - centerLineX) <= maxSpreadFromCenter) {
                    xPositions.add(x);
                }
            }

            // Add tree at top (left half)
            for (float x : xPositions) {
                newBodies.add(createTree(nextTreeId++, x, y, treeRadius));
                // Mirror at right (skip center column to avoid duplicate)
                if (Math.abs(x - centerLineX) > 0.1f) {
                    newBodies.add(createTree(nextTreeId++, mapWidth - x, y, treeRadius));
                }
            }

            // Mirror tree at bottom (left half)
            for (float x : xPositions) {
                newBodies.add(createTree(nextTreeId++, x, mapHeight - y, treeRadius));
                // Mirror at right (skip center column to avoid duplicate)
                if (Math.abs(x - centerLineX) > 0.1f) {
                    newBodies.add(createTree(nextTreeId++, mapWidth - x, mapHeight - y, treeRadius));
                }
            }
        }

        // Add 3 archons per team
        // Team A archons - positioned on left side, spread vertically
        newBodies.add(createArchon(nextRobotId++, Team.A, 12.0f, 35.0f));
        newBodies.add(createArchon(nextRobotId++, Team.A, 12.0f, 45.0f));
        newBodies.add(createArchon(nextRobotId++, Team.A, 12.0f, 55.0f));

        // Team B archons - positioned on right side, spread vertically
        newBodies.add(createArchon(nextRobotId++, Team.B, 78.0f, 35.0f));
        newBodies.add(createArchon(nextRobotId++, Team.B, 78.0f, 45.0f));
        newBodies.add(createArchon(nextRobotId++, Team.B, 78.0f, 55.0f));

        // Create new map with regenerated tree list
        BodyInfo[] newInitialBodies = newBodies.toArray(new BodyInfo[newBodies.size()]);
        MapLocation origin = map.getOrigin();

        return new LiveMap(
            map.getWidth(), map.getHeight(), origin, map.getSeed(),
            map.getRounds(), map.getMapName(), newInitialBodies
        );
    }

    private static TreeInfo createTree(int id, float x, float y, float radius) {
        return new TreeInfo(
            id,
            Team.NEUTRAL,
            new MapLocation(x, y),
            radius,
            radius * GameConstants.NEUTRAL_TREE_HEALTH_RATE,
            0,
            null
        );
    }

    private static RobotInfo createArchon(int id, Team team, float x, float y) {
        return new RobotInfo(
            id,
            team,
            RobotType.ARCHON,
            new MapLocation(x, y),
            RobotType.ARCHON.getStartingHealth(),
            0,
            0
        );
    }
}
