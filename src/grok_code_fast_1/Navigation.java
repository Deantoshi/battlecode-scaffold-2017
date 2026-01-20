package grok_code_fast_1;

import battlecode.common.*;

public class Navigation {

    static RobotController rc;

    public static void init(RobotController rc) {
        Navigation.rc = rc;
    }

    /**
     * Attempts to move in a given direction, while avoiding small obstacles directly in the path.
     *
     * @param dir The intended direction of movement
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryMove(Direction dir) throws GameActionException {
        return tryMove(dir,20,10);
    }

    /**
     * Attempts to move in a given direction, while avoiding small obstacles direction in the path.
     *
     * @param dir The intended direction of movement
     * @param degreeOffset Spacing between checked directions (degrees)
     * @param checksPerSide Number of extra directions checked on each side, if intended direction was unavailable
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryMove(Direction dir, float degreeOffset, int checksPerSide) throws GameActionException {

        // Calculate quadrant densities
        float[] quadrantDensities = getQuadrantTreeDensities();

        // First, try intended direction
        if (rc.canMove(dir) && isDirectionGood(dir, quadrantDensities)) {
            rc.move(dir);
            return true;
        } else if (rc.getType() == RobotType.LUMBERJACK && chopBlockingTrees(dir, 2.0f)) {
            return true;
        }

        // Now try a bunch of similar angles
        boolean moved = false;
        int currentCheck = 1;

        while(currentCheck<=checksPerSide) {
            // Try the offset of the left side
            Direction leftDir = dir.rotateLeftDegrees(degreeOffset*currentCheck);
            if(rc.canMove(leftDir) && isDirectionGood(leftDir, quadrantDensities)) {
                rc.move(leftDir);
                return true;
            } else if (rc.getType() == RobotType.LUMBERJACK && chopBlockingTrees(leftDir, 2.0f)) {
                return true;
            }
            // Try the offset on the right side
            Direction rightDir = dir.rotateRightDegrees(degreeOffset*currentCheck);
            if(rc.canMove(rightDir) && isDirectionGood(rightDir, quadrantDensities)) {
                rc.move(rightDir);
                return true;
            } else if (rc.getType() == RobotType.LUMBERJACK && chopBlockingTrees(rightDir, 2.0f)) {
                return true;
            }
            // No move performed, try slightly further
            currentCheck++;
        }

        // A move never happened, so return false.
        return false;
    }

    static boolean chopBlockingTrees(Direction dir, float range) throws GameActionException {
        if (rc.getType() != RobotType.LUMBERJACK) return false;
        TreeInfo[] trees = rc.senseNearbyTrees(range, null);
        TreeInfo shakeTree = null;
        TreeInfo chopTree = null;
        int maxBullets = -1;
        for (TreeInfo t : trees) {
            Direction toTree = rc.getLocation().directionTo(t.location);
            if (Math.abs(dir.radiansBetween(toTree)) <= Math.PI / 3) { // within 60 degrees
                if (t.containedBullets > 0 && rc.canShake(t.ID)) {
                    if (shakeTree == null || t.containedBullets > shakeTree.containedBullets) {
                        shakeTree = t;
                    }
                } else if (rc.canChop(t.ID)) {
                    if (chopTree == null || t.containedBullets > maxBullets) {
                        chopTree = t;
                        maxBullets = t.containedBullets;
                    }
                }
            }
        }
        if (shakeTree != null) {
            rc.shake(shakeTree.ID);
            return true;
        } else if (chopTree != null) {
            rc.chop(chopTree.ID);
            return true;
        }
        return false;
    }

    static boolean isDirectionGood(Direction dir, float[] quadrantDensities) throws GameActionException {
        // Count trees in direction
        int treeCount = countTreesInDirection(dir, 3.0f, (float)Math.PI/2);
        if (treeCount > 5) return false;

        // Get quadrant the direction leads to
        MapLocation projectedLoc = rc.getLocation().add(dir, 5.0f); // project 5 units ahead
        int quadrant = getQuadrant(projectedLoc);

        // Avoid SW quadrant on MagicWood map
        if (rc.getMap().getMapName().equals("MagicWood") && quadrant == 3) return false;

        // Get density of target quadrant
        float density = quadrantDensities[quadrant];

        // Find minimum density across quadrants
        float minDensity = Float.MAX_VALUE;
        for (float d : quadrantDensities) {
            minDensity = Math.min(minDensity, d);
        }

        // Prioritize less dense areas: avoid quadrants significantly denser than the minimum
        if (density > minDensity + 2) return false;

        // Strictly avoid SW and SE quadrants unless they have the minimum density
        if ((quadrant == 2 || quadrant == 3) && density > minDensity) return false;

        // Force movement to assigned quadrant
        int targetQuad = getTargetQuadrant();
        int currentQuad = getQuadrant(rc.getLocation());
        if (targetQuad != -1 && currentQuad != targetQuad) {
            if (quadrant != targetQuad) return false;
        }

        return true;
    }

    static float[] getQuadrantTreeDensities() throws GameActionException {
        float[] densities = new float[4];
        TreeInfo[] trees = rc.senseNearbyTrees(-1, null);
        for (TreeInfo t : trees) {
            int q = getQuadrant(t.location);
            densities[q] += 1; // simple count
        }
        return densities;
    }

    static int getQuadrant(MapLocation loc) {
        MapLocation[] ownArchons = rc.getInitialArchonLocations(rc.getTeam());
        float avgX = 0, avgY = 0;
        for (MapLocation l : ownArchons) {
            avgX += l.x;
            avgY += l.y;
        }
        avgX /= ownArchons.length;
        avgY /= ownArchons.length;
        MapLocation center = new MapLocation(avgX, avgY);
        boolean north = loc.y > center.y;
        boolean east = loc.x > center.x;
        if (north && east) return 1; // NE
        if (north && !east) return 0; // NW
        if (!north && east) return 2; // SE
        return 3; // SW
    }

    static int countTreesInDirection(Direction dir, float radius, float angleRad) throws GameActionException {
        TreeInfo[] trees = rc.senseNearbyTrees(radius, null);
        int count = 0;
        for (TreeInfo t : trees) {
            Direction toTree = rc.getLocation().directionTo(t.location);
            if (Math.abs(dir.radiansBetween(toTree)) <= angleRad / 2) {
                count++;
            }
        }
        return count;
    }

    static int getTargetQuadrant() {
        if (rc.getMap().getMapName().equals("MagicWood")) {
            return (rc.getID() % 2 == 0) ? 0 : 1; // NW or NE
        }
        RobotType type = rc.getType();
        if (type == RobotType.GARDENER) {
            return (rc.getID() % 2 == 0) ? 1 : 2; // NE or SE
        } else if (type == RobotType.SOLDIER || type == RobotType.SCOUT) {
            return (rc.getID() % 2 == 0) ? 0 : 1; // NW or NE
        }
        return -1;
    }

    static Direction getDirectionToQuadrant(int quad) {
        MapLocation center = getQuadrantCenter(quad);
        return rc.getLocation().directionTo(center);
    }

    static MapLocation getQuadrantCenter(int quad) {
        MapLocation[] ownArchons = rc.getInitialArchonLocations(rc.getTeam());
        float avgX = 0, avgY = 0;
        for (MapLocation l : ownArchons) {
            avgX += l.x;
            avgY += l.y;
        }
        avgX /= ownArchons.length;
        avgY /= ownArchons.length;
        MapLocation mapCenter = new MapLocation(avgX, avgY);
        float offsetX = 15.0f;
        float offsetY = 15.0f;
        switch(quad) {
            case 0: return new MapLocation(mapCenter.x - offsetX, mapCenter.y + offsetY); // NW
            case 1: return new MapLocation(mapCenter.x + offsetX, mapCenter.y + offsetY); // NE
            case 2: return new MapLocation(mapCenter.x + offsetX, mapCenter.y - offsetY); // SE
            case 3: return new MapLocation(mapCenter.x - offsetX, mapCenter.y - offsetY); // SW
            default: return mapCenter;
        }
    }
}