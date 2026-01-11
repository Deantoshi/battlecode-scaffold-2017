package battlecode.util;

import battlecode.common.*;
import battlecode.world.GameMapIO;
import battlecode.world.LiveMap;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Utility to clean up overlapping trees in a map.
 * Removes trees that overlap with others while maintaining overall tree coverage.
 */
public class MapTreeCleaner {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: MapTreeCleaner <mapName> [outputMapName]");
            System.err.println("If outputMapName is not specified, overwrites the original map");
            System.exit(1);
        }

        String mapName = args[0];
        String outputMapName = args.length > 1 ? args[1] : mapName;

        try {
            LiveMap map = GameMapIO.loadMap(mapName, new File("maps"));
            System.out.println("Loaded map: " + mapName);

            LiveMap cleanedMap = cleanOverlappingTrees(map);
            System.out.println("Original trees: " + countNeutralTrees(map));
            System.out.println("Cleaned trees: " + countNeutralTrees(cleanedMap));

            GameMapIO.writeMap(cleanedMap, new File("maps"));
            System.out.println("Saved cleaned map to: " + outputMapName + ".map17");
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

    private static LiveMap cleanOverlappingTrees(LiveMap map) {
        List<BodyInfo> newBodies = new ArrayList<>();

        // Add all non-tree bodies
        List<TreeInfo> neutralTrees = new ArrayList<>();
        for (BodyInfo body : map.getInitialBodies()) {
            if (body.isTree()) {
                TreeInfo tree = (TreeInfo) body;
                if (tree.team == Team.NEUTRAL) {
                    neutralTrees.add(tree);
                } else {
                    newBodies.add(body);
                }
            } else {
                newBodies.add(body);
            }
        }

        // Sort trees: prefer trees with bullets and larger radius
        Collections.sort(neutralTrees, new Comparator<TreeInfo>() {
            @Override
            public int compare(TreeInfo a, TreeInfo b) {
                // First by bullets (descending)
                int bulletCompare = Integer.compare(b.containedBullets, a.containedBullets);
                if (bulletCompare != 0) return bulletCompare;

                // Then by radius (descending)
                int radiusCompare = Float.compare(b.radius, a.radius);
                if (radiusCompare != 0) return radiusCompare;

                // Finally by ID
                return Integer.compare(a.ID, b.ID);
            }
        });

        // Greedy selection: keep trees that don't overlap with already kept trees
        List<TreeInfo> keptTrees = new ArrayList<>();
        for (TreeInfo tree : neutralTrees) {
            boolean overlaps = false;
            for (TreeInfo kept : keptTrees) {
                if (treesOverlap(tree, kept)) {
                    overlaps = true;
                    break;
                }
            }
            if (!overlaps) {
                keptTrees.add(tree);
            }
        }

        System.out.println("Removed " + (neutralTrees.size() - keptTrees.size()) + " overlapping trees");

        // Add all kept trees
        newBodies.addAll(keptTrees);

        // Create new map with cleaned tree list
        BodyInfo[] newInitialBodies = newBodies.toArray(new BodyInfo[newBodies.size()]);

        // Keep the original origin
        MapLocation origin = map.getOrigin();

        return new LiveMap(
            map.getWidth(), map.getHeight(), origin, map.getSeed(),
            map.getRounds(), map.getMapName(), newInitialBodies
        );
    }

    private static boolean treesOverlap(TreeInfo a, TreeInfo b) {
        float dx = a.location.x - b.location.x;
        float dy = a.location.y - b.location.y;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);

        // Trees overlap if distance < sum of radii
        // Use small epsilon for floating point
        return distance < (a.radius + b.radius - 0.01f);
    }
}
