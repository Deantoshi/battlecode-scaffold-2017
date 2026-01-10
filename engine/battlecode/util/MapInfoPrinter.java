package battlecode.util;

import battlecode.common.*;
import battlecode.world.GameMapIO;
import battlecode.world.LiveMap;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility to print detailed map information for analysis.
 */
public class MapInfoPrinter {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: MapInfoPrinter <mapName>");
            System.exit(1);
        }

        String mapName = args[0];

        try {
            // Try to load from maps/ directory first, then fall back to resources
            LiveMap map = GameMapIO.loadMap(mapName, new File("maps"));
            printMapInfo(map);
        } catch (Exception e) {
            System.err.println("Error loading map: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void printMapInfo(LiveMap map) {
        System.out.println("=== MAP INFO START ===");
        System.out.println("NAME: " + map.getMapName());
        System.out.println("WIDTH: " + map.getWidth());
        System.out.println("HEIGHT: " + map.getHeight());
        System.out.println("AREA: " + (map.getWidth() * map.getHeight()));
        System.out.println("ORIGIN: " + map.getOrigin().x + "," + map.getOrigin().y);
        System.out.println("SEED: " + map.getSeed());

        // Categorize initial bodies
        List<BodyInfo> teamAArchons = new ArrayList<>();
        List<BodyInfo> teamBArchons = new ArrayList<>();
        List<BodyInfo> neutralTrees = new ArrayList<>();
        List<BodyInfo> teamATrees = new ArrayList<>();
        List<BodyInfo> teamBTrees = new ArrayList<>();
        List<BodyInfo> otherBodies = new ArrayList<>();

        for (BodyInfo body : map.getInitialBodies()) {
            if (body.isRobot()) {
                RobotInfo robot = (RobotInfo) body;
                if (robot.type == RobotType.ARCHON) {
                    if (robot.team == Team.A) {
                        teamAArchons.add(body);
                    } else {
                        teamBArchons.add(body);
                    }
                } else {
                    otherBodies.add(body);
                }
            } else if (body.isTree()) {
                TreeInfo tree = (TreeInfo) body;
                if (tree.team == Team.NEUTRAL) {
                    neutralTrees.add(body);
                } else if (tree.team == Team.A) {
                    teamATrees.add(body);
                } else {
                    teamBTrees.add(body);
                }
            }
        }

        // Print archon info
        System.out.println("TEAM_A_ARCHONS: " + teamAArchons.size());
        for (BodyInfo archon : teamAArchons) {
            RobotInfo robot = (RobotInfo) archon;
            System.out.println("  ARCHON_A: " + robot.location.x + "," + robot.location.y);
        }

        System.out.println("TEAM_B_ARCHONS: " + teamBArchons.size());
        for (BodyInfo archon : teamBArchons) {
            RobotInfo robot = (RobotInfo) archon;
            System.out.println("  ARCHON_B: " + robot.location.x + "," + robot.location.y);
        }

        // Calculate archon distance (if 1 archon per team)
        if (teamAArchons.size() >= 1 && teamBArchons.size() >= 1) {
            RobotInfo a = (RobotInfo) teamAArchons.get(0);
            RobotInfo b = (RobotInfo) teamBArchons.get(0);
            float dx = a.location.x - b.location.x;
            float dy = a.location.y - b.location.y;
            float distance = (float) Math.sqrt(dx * dx + dy * dy);
            System.out.println("ARCHON_DISTANCE: " + distance);
        }

        // Print tree info
        System.out.println("NEUTRAL_TREES: " + neutralTrees.size());

        // Calculate tree density
        float mapArea = map.getWidth() * map.getHeight();
        float treeDensity = neutralTrees.size() / mapArea * 100;
        System.out.println("TREE_DENSITY: " + String.format("%.2f", treeDensity) + "%");

        // Calculate total tree coverage area
        float totalTreeArea = 0;
        float minTreeX = Float.MAX_VALUE, maxTreeX = Float.MIN_VALUE;
        float minTreeY = Float.MAX_VALUE, maxTreeY = Float.MIN_VALUE;

        for (BodyInfo treeBody : neutralTrees) {
            TreeInfo tree = (TreeInfo) treeBody;
            totalTreeArea += Math.PI * tree.radius * tree.radius;
            if (tree.location.x < minTreeX) minTreeX = tree.location.x;
            if (tree.location.x > maxTreeX) maxTreeX = tree.location.x;
            if (tree.location.y < minTreeY) minTreeY = tree.location.y;
            if (tree.location.y > maxTreeY) maxTreeY = tree.location.y;
        }

        float coveragePercent = (totalTreeArea / mapArea) * 100;
        System.out.println("TREE_COVERAGE: " + String.format("%.2f", coveragePercent) + "%");

        // Print tree distribution info
        if (!neutralTrees.isEmpty()) {
            System.out.println("TREE_SPREAD_X: " + String.format("%.1f", maxTreeX - minTreeX));
            System.out.println("TREE_SPREAD_Y: " + String.format("%.1f", maxTreeY - minTreeY));
        }

        // Calculate terrain level (low/medium/high based on tree coverage)
        String terrainLevel;
        if (coveragePercent < 5) {
            terrainLevel = "LOW";
        } else if (coveragePercent < 15) {
            terrainLevel = "MEDIUM";
        } else {
            terrainLevel = "HIGH";
        }
        System.out.println("TERRAIN_LEVEL: " + terrainLevel);

        // Classify map size
        String sizeClass;
        if (mapArea < 1500) {
            sizeClass = "SMALL";
        } else if (mapArea < 4000) {
            sizeClass = "MEDIUM";
        } else {
            sizeClass = "LARGE";
        }
        System.out.println("SIZE_CLASS: " + sizeClass);

        // Print some tree details (first 10 for large maps)
        int treeCount = 0;
        for (BodyInfo treeBody : neutralTrees) {
            if (treeCount >= 10 && neutralTrees.size() > 15) {
                System.out.println("  ... and " + (neutralTrees.size() - 10) + " more trees");
                break;
            }
            TreeInfo tree = (TreeInfo) treeBody;
            System.out.println("  TREE: " + String.format("%.1f,%.1f r=%.1f bullets=%d",
                tree.location.x, tree.location.y, tree.radius, tree.containedBullets));
            treeCount++;
        }

        System.out.println("=== MAP INFO END ===");
    }
}
