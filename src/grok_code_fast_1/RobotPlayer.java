package grok_code_fast_1;
import battlecode.common.*;

/**
 * 
 * @author 
 */
public strictfp class RobotPlayer {
    static RobotController rc;

    /**
     * run() is the method that is called when a robot is instantiated in the Battlecode world.
     * If this method returns, the robot dies!
     **/
    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {

        // This is the RobotController object. You use it to perform actions from this robot,
        // and to get information on its current status.
        RobotPlayer.rc = rc;
        Navigation.init(rc);

        // Here, we've separated the controls into a different method for each RobotType.
        // You can add the missing ones or rewrite this into your own control structure.
        switch (rc.getType()) {
            case ARCHON:
                runArchon();
                break;
            case GARDENER:
                runGardener();
                break;
            case SOLDIER:
                runSoldier();
                break;
            case LUMBERJACK:
                runLumberjack();
                break;
        }
	}

    static void runArchon() throws GameActionException {
        System.out.println("I'm an archon!");
        Team enemy = rc.getTeam().opponent();
        MapLocation enemyArchonLoc = rc.getInitialArchonLocations(enemy)[0];

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {

                // Generate a random direction
                Direction dir = randomDirection();

                // Randomly attempt to build a gardener in this direction
                if (rc.canHireGardener(dir) && Math.random() < .15) {
                    rc.hireGardener(dir);
                }

                // Move randomly to prioritize survival
                Navigation.tryMove(randomDirection());

                // Broadcast archon's location for other robots on the team to know
                MapLocation myLocation = rc.getLocation();
                rc.broadcast(0,(int)myLocation.x);
                rc.broadcast(1,(int)myLocation.y);
                rc.broadcast(2,(int)enemyArchonLoc.x);
                rc.broadcast(3,(int)enemyArchonLoc.y);

                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println("Archon Exception");
                e.printStackTrace();
            }
        }
    }

	static void runGardener() throws GameActionException {
        System.out.println("I'm a gardener!");

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {

                // Listen for home archon's location
                int xPos = rc.readBroadcast(0);
                int yPos = rc.readBroadcast(1);
                MapLocation archonLoc = new MapLocation(xPos,yPos);

                // Generate a random direction
                Direction dir = randomDirection();

                // Randomly attempt to build a soldier or lumberjack in this direction
                float soldierProb;
                float lumberjackProb;
                if (rc.getRoundNum() < 500) {
                    lumberjackProb = 0.4f;
                    soldierProb = 0.1f;
                } else {
                    soldierProb = 0.4f;
                    lumberjackProb = 0.0f;
                }
                
                if (rc.canBuildRobot(RobotType.LUMBERJACK, dir) && Math.random() < lumberjackProb && rc.isBuildReady()) {
                    rc.buildRobot(RobotType.LUMBERJACK, dir);
                } else if (rc.canBuildRobot(RobotType.SOLDIER, dir) && Math.random() < soldierProb) {
                    rc.buildRobot(RobotType.SOLDIER, dir);
                }

                if (rc.canPlantTree(dir) && Math.random() < 0.0) {
                    rc.plantTree(dir);
                }

                // Check for nearby enemies
                RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
                if (enemies.length == 0) {
                    // No enemies nearby, move towards quadrant center if far away
                    int quadrant = rc.getID() % 4;
                    MapLocation quadrantCenter = getQuadrantCenter(quadrant);
                    if (rc.getLocation().distanceTo(quadrantCenter) > 5.0f) {
                        Direction toQuadrant = rc.getLocation().directionTo(quadrantCenter);
                        boolean moved = Navigation.tryMove(toQuadrant);
                        if (!moved) { Navigation.tryMove(randomDirection()); }
                    } else {
                        // Already close, move randomly
                        Navigation.tryMove(randomDirection());
                    }
                } else {
                    // Enemies nearby, move randomly
                    Navigation.tryMove(randomDirection());
                }

                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println("Gardener Exception");
                e.printStackTrace();
            }
        }
    }

    static void runSoldier() throws GameActionException {
        System.out.println("I'm an soldier!");
        Team enemy = rc.getTeam().opponent();

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {
                MapLocation myLocation = rc.getLocation();

                // See if there are any nearby enemy robots
                RobotInfo[] robots = rc.senseNearbyRobots(-1, enemy);

                Direction desiredDir;
                // If there are some...
                if (robots.length > 0) {
                    // Find closest archon
                    RobotInfo target = null;
                    for (RobotInfo r : robots) {
                        if (r.type == RobotType.ARCHON) {
                            target = r;
                            break; // since sorted by distance, first archon is closest
                        }
                    }
                    if (target == null) {
                        target = robots[0]; // closest enemy
                    }
                    // Fire at target
                    if (rc.canFirePentadShot()) {
                        rc.firePentadShot(rc.getLocation().directionTo(target.location));
                    } else if (rc.canFireTriadShot()) {
                        rc.fireTriadShot(rc.getLocation().directionTo(target.location));
                    } else if (rc.canFireSingleShot()) {
                        rc.fireSingleShot(rc.getLocation().directionTo(target.location));
                    }
                    // Set desired direction towards the target
                    desiredDir = myLocation.directionTo(target.location);
                } else {
                    int enemyX = rc.readBroadcast(2);
                    int enemyY = rc.readBroadcast(3);
                    MapLocation enemyArchon = new MapLocation((float)enemyX, (float)enemyY);
                    desiredDir = myLocation.directionTo(enemyArchon);
                }

                // Check for bullets to dodge
                BulletInfo[] bullets = rc.senseNearbyBullets();
                for (BulletInfo b : bullets) {
                    if (willCollideWithMe(b)) {
                        // Dodge by moving perpendicular to bullet direction
                        desiredDir = b.dir.rotateLeftDegrees(90);
                        break; // dodge the first colliding bullet
                    }
                }

                // Move in the desired direction
                Navigation.tryMove(desiredDir);

                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println("Soldier Exception");
                e.printStackTrace();
            }
        }
    }

    static void runLumberjack() throws GameActionException {
        System.out.println("I'm a lumberjack!");
        Team enemy = rc.getTeam().opponent();

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {
                int enemyX = rc.readBroadcast(2);
                int enemyY = rc.readBroadcast(3);
                MapLocation enemyArchon = new MapLocation((float)enemyX, (float)enemyY);

                // See if there are any enemy robots within striking range (distance 1 from lumberjack's radius)
                RobotInfo[] robots = rc.senseNearbyRobots(RobotType.LUMBERJACK.bodyRadius+GameConstants.LUMBERJACK_STRIKE_RADIUS, enemy);

                if(robots.length > 0 && !rc.hasAttacked()) {
                    // Use strike() to hit all nearby robots!
                    rc.strike();
                } else {
                    // No close robots, so search for robots within sight radius
                    robots = rc.senseNearbyRobots(-1,enemy);

                    Direction desiredDir;
                    MapLocation target;
                    if(robots.length > 0) {
                        target = robots[0].getLocation();
                        desiredDir = rc.getLocation().directionTo(target);
                    } else {
                        target = enemyArchon;
                        desiredDir = rc.getLocation().directionTo(target);
                    }

                    // Proactively clear blocking trees in desired direction
                    TreeInfo[] nearbyTrees = rc.senseNearbyTrees(3.0f, null);
                    for (TreeInfo tree : nearbyTrees) {
                        Direction toTree = rc.getLocation().directionTo(tree.location);
                        float angleDiff = Math.abs(desiredDir.radiansBetween(toTree));
                        if (angleDiff < Math.PI / 6) { // within 30 degrees
                            if (tree.containedRobot != null && rc.canShake(tree.ID)) {
                                rc.shake(tree.ID);
                            } else if (rc.canChop(tree.ID)) {
                                rc.chop(tree.ID);
                            }
                        }
                    }

                    // If there is a robot, move towards it
                    if(robots.length > 0) {
                        Navigation.tryMove(desiredDir);

                    } else {
                        // Sense nearby trees
                        TreeInfo[] trees = rc.senseNearbyTrees(-1, null);
                        if (trees.length > 0) {
                            // Find the tree with smallest angle to target direction within direct path
                            TreeInfo bestTree = null;
                            float minAngle = Float.MAX_VALUE;
                            float maxAngle = (float)Math.PI; // 180 degrees threshold
                            for (TreeInfo t : trees) {
                                if (t.containedRobot != null) {
                                    minAngle = 0;
                                    bestTree = t;
                                } else {
                                    Direction toTree = rc.getLocation().directionTo(t.location);
                                    float angleDiff = Math.abs(desiredDir.radiansBetween(toTree));
                                    if (angleDiff < maxAngle && angleDiff < minAngle) {
                                        minAngle = angleDiff;
                                        bestTree = t;
                                    }
                                }
                            }
                            // Chop the best tree if possible, otherwise move towards it
                            if (bestTree != null) {
                                if (rc.canChop(bestTree.ID)) {
                                    rc.chop(bestTree.ID);
                                } else {
                                    Direction toTree = rc.getLocation().directionTo(bestTree.location);
                                    Navigation.tryMove(toTree);
                                }
                            } else {
                                // No trees in direct path, move towards target if far away
                                if (rc.getLocation().distanceTo(target) > 5.0f) {
                                    boolean moved = Navigation.tryMove(desiredDir);
                                    if (!moved) { Navigation.tryMove(randomDirection()); }
                                } else {
                                    // Already close, move randomly
                                    Navigation.tryMove(randomDirection());
                                }
                            }
                        } else {
                            // No trees nearby, move towards target if far away
                            if (rc.getLocation().distanceTo(target) > 5.0f) {
                                boolean moved = Navigation.tryMove(desiredDir);
                                if (!moved) { Navigation.tryMove(randomDirection()); }
                            } else {
                                // Already close, move randomly
                                Navigation.tryMove(randomDirection());
                            }
                        }
                    }
                }

                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println("Lumberjack Exception");
                e.printStackTrace();
            }
        }
    }

    static MapLocation getQuadrantCenter(int quadrant) {
        MapLocation[] ownArchons = rc.getInitialArchonLocations(rc.getTeam());
        float avgX = 0, avgY = 0;
        for (MapLocation loc : ownArchons) {
            avgX += loc.x;
            avgY += loc.y;
        }
        avgX /= ownArchons.length;
        avgY /= ownArchons.length;
        MapLocation mapCenter = new MapLocation(avgX, avgY);
        float offsetX = 80.0f / 4.0f;
        float offsetY = 80.0f / 4.0f;
        switch(quadrant) {
            case 0: return new MapLocation(mapCenter.x - offsetX, mapCenter.y + offsetY); // NW
            case 1: return new MapLocation(mapCenter.x + offsetX, mapCenter.y + offsetY); // NE
            case 2: return new MapLocation(mapCenter.x + offsetX, mapCenter.y - offsetY); // SE
            case 3: return new MapLocation(mapCenter.x - offsetX, mapCenter.y - offsetY); // SW
            default: return rc.getLocation(); // shouldn't happen
        }
    }

    /**
     * Returns a random Direction
     * @return a random Direction
     */
    static Direction randomDirection() {
        return new Direction((float)Math.random() * 2 * (float)Math.PI);
    }

    /**
     * A slightly more complicated example function, this returns true if the given bullet is on a collision
     * course with the current robot. Doesn't take into account objects between the bullet and this robot.
     *
     * @param bullet The bullet in question
     * @return True if the line of the bullet's path intersects with this robot's current position.
     */
    static boolean willCollideWithMe(BulletInfo bullet) {
        MapLocation myLocation = rc.getLocation();

        // Get relevant bullet information
        Direction propagationDirection = bullet.dir;
        MapLocation bulletLocation = bullet.location;

        // Calculate bullet relations to this robot
        Direction directionToRobot = bulletLocation.directionTo(myLocation);
        float distToRobot = bulletLocation.distanceTo(myLocation);
        float theta = propagationDirection.radiansBetween(directionToRobot);

        // If theta > 90 degrees, then the bullet is traveling away from us and we can break early
        if (Math.abs(theta) > Math.PI/2) {
            return false;
        }

        // distToRobot is our hypotenuse, theta is our angle, and we want to know this length of the opposite leg.
        // This corresponds to the smallest radius circle centered at our location that would intersect with the
        // line that is the path of the bullet.
        float perpendicularDist = (float)Math.abs(distToRobot * Math.sin(theta)); // soh cah toa :)

        return (perpendicularDist <= rc.getType().bodyRadius);
    }
}