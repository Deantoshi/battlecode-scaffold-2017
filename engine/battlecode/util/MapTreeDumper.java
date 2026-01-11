package battlecode.util;

import battlecode.common.*;
import battlecode.world.GameMapIO;
import battlecode.world.LiveMap;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MapTreeDumper {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: MapTreeDumper <mapName>");
            System.exit(1);
        }

        String mapName = args[0];

        try {
            LiveMap map = GameMapIO.loadMap(mapName, new File("maps"));

            List<TreeInfo> neutralTrees = new ArrayList<>();
            for (BodyInfo body : map.getInitialBodies()) {
                if (body.isTree()) {
                    TreeInfo tree = (TreeInfo) body;
                    if (tree.team == Team.NEUTRAL) {
                        neutralTrees.add(tree);
                    }
                }
            }

            Collections.sort(neutralTrees, (a, b) -> {
                int xCompare = Float.compare(a.location.x, b.location.x);
                if (xCompare != 0) return xCompare;
                return Float.compare(a.location.y, b.location.y);
            });

            System.out.println("=== TREES (" + neutralTrees.size() + ") ===");
            for (TreeInfo tree : neutralTrees) {
                System.out.println(String.format("  (%.1f, %.1f)", tree.location.x, tree.location.y));
            }

            float width = map.getWidth();
            float height = map.getHeight();

            System.out.println("\n=== SYMMETRY CHECK ===");
            boolean symmetric = true;
            for (TreeInfo tree : neutralTrees) {
                float mirrorX = width - tree.location.x;
                float mirrorY = height - tree.location.y;

                boolean hasMirrorX = false;
                boolean hasMirrorY = false;
                for (TreeInfo other : neutralTrees) {
                    if (Math.abs(other.location.x - mirrorX) < 0.1 && Math.abs(other.location.y - tree.location.y) < 0.1) {
                        hasMirrorX = true;
                    }
                    if (Math.abs(other.location.x - tree.location.x) < 0.1 && Math.abs(other.location.y - mirrorY) < 0.1) {
                        hasMirrorY = true;
                    }
                }

                if (!hasMirrorX || !hasMirrorY) {
                    System.out.println("  Missing mirror for: (" + tree.location.x + ", " + tree.location.y + ")");
                    System.out.println("    X mirror: " + hasMirrorX + ", Y mirror: " + hasMirrorY);
                    symmetric = false;
                }
            }

            if (symmetric) {
                System.out.println("  ✓ All trees have proper mirrors");
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
