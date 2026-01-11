package battlecode.util;

import battlecode.common.*;
import battlecode.world.GameMapIO;
import battlecode.world.LiveMap;

import java.io.File;
import java.util.*;

public class MapVisualizer {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: MapVisualizer <mapName>");
            System.exit(1);
        }

        String mapName = args[0];

        try {
            LiveMap map = GameMapIO.loadMap(mapName, new File("maps"));

            Set<String> treePositions = new HashSet<>();
            for (BodyInfo body : map.getInitialBodies()) {
                if (body.isTree()) {
                    TreeInfo tree = (TreeInfo) body;
                    if (tree.team == Team.NEUTRAL) {
                        int gridX = (int) (tree.location.x / 3.0);
                        int gridY = (int) (tree.location.y / 3.0);
                        treePositions.add(gridX + "," + gridY);
                    }
                }
            }

            // Get archon positions
            MapLocation archonA = null, archonB = null;
            for (BodyInfo body : map.getInitialBodies()) {
                if (body.isRobot()) {
                    RobotInfo robot = (RobotInfo) body;
                    if (robot.type == RobotType.ARCHON) {
                        if (robot.team == Team.A) {
                            archonA = robot.location;
                        } else {
                            archonB = robot.location;
                        }
                    }
                }
            }

            int width = (int) (map.getWidth() / 3.0);
            int height = (int) (map.getHeight() / 3.0);

            // Get all archon positions
            List<MapLocation> archonALocations = new ArrayList<>();
            List<MapLocation> archonBLocations = new ArrayList<>();
            for (BodyInfo body : map.getInitialBodies()) {
                if (body.isRobot()) {
                    RobotInfo robot = (RobotInfo) body;
                    if (robot.type == RobotType.ARCHON) {
                        if (robot.team == Team.A) {
                            archonALocations.add(robot.location);
                        } else {
                            archonBLocations.add(robot.location);
                        }
                    }
                }
            }

            System.out.println("Map: " + mapName + " (" + width + "x" + height + " grid)");
            System.out.println("Legend: # = tree, 1/2/3 = Archon A #, a/b/c = Archon B #, . = empty");
            System.out.println();

            for (int y = height - 1; y >= 0; y--) {
                for (int x = 0; x < width; x++) {
                    // Check for archons
                    boolean foundArchon = false;
                    for (int i = 0; i < archonALocations.size(); i++) {
                        MapLocation loc = archonALocations.get(i);
                        if ((int)(loc.x / 3.0) == x && (int)(loc.y / 3.0) == y) {
                            System.out.print((i + 1));
                            foundArchon = true;
                            break;
                        }
                    }
                    if (!foundArchon) {
                        for (int i = 0; i < archonBLocations.size(); i++) {
                            MapLocation loc = archonBLocations.get(i);
                            if ((int)(loc.x / 3.0) == x && (int)(loc.y / 3.0) == y) {
                                System.out.print((char)('a' + i));
                                foundArchon = true;
                                break;
                            }
                        }
                    }

                    if (!foundArchon) {
                        if (treePositions.contains(x + "," + y)) {
                            System.out.print("#");
                        } else {
                            System.out.print(".");
                        }
                    }
                }
                System.out.println();
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
