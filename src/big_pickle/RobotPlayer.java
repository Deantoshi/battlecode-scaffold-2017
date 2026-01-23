package big_pickle;
import battlecode.common.*;

public strictfp class RobotPlayer {
    static RobotController rc;
    static MapLocation archonLocation = null;
    static boolean archonLocationSet = false;

    /**
     * run() is the method that is called when a robot is instantiated in the Battlecode world.
     * If this method returns, the robot dies!
    **/
    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {

        // This is the RobotController object. You use it to perform actions from this robot,
        // and to get information on its current status.
        RobotPlayer.rc = rc;
        BulletSpending.init(rc);

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
            case SCOUT:
                runScout();
                break;
            case TANK:
                runTank();
                break;
        }
	}

    static void runArchon() throws GameActionException {
        System.out.println("I'm an archon!");

        // Store archon location for fortress coordination
        archonLocation = rc.getLocation();
        archonLocationSet = true;

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {

                // VP Tyrant: Use centralized spending policy for gardener hiring
                BulletSpending.spendPolicy();

                // VP Tyrant: Archons stay central and avoid combat - focus on economy
                if (rc.getRoundNum() < 100) {
                    // Early game: move carefully to find good positioning for tree farms
                    tryMoveTurtle(randomDirection());
                } else {
                    // Later game: stay near center to protect tree economy
                    stayNearEconomicCenter();
                }

                // Broadcast archon's location for economic coordination
                if (archonLocation != null) {
                    rc.broadcast(0,(int)archonLocation.x);
                    rc.broadcast(1,(int)archonLocation.y);
                }

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
                if (!archonLocationSet) {
                    archonLocation = archonLoc;
                    archonLocationSet = true;
                }

                // VP Tyrant: Use centralized spending policy - trees and economy are priority
                BulletSpending.spendPolicy();

                // VP Tyrant: Gardeners position themselves to maximize tree farming
                maintainTreeFarmPosition(archonLoc);

                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println("Gardener Exception");
                e.printStackTrace();
            }
        }
    }

    static void runSoldier() throws GameActionException {
        System.out.println("I'm a soldier!");
        Team enemy = rc.getTeam().opponent();

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {
                MapLocation myLocation = rc.getLocation();

                // Get archon location for economic center protection
                MapLocation economicCenter = getEconomicCenter();

                // VP Tyrant: Soldiers are defensive only - avoid combat unless absolutely necessary
                RobotInfo[] robots = rc.senseNearbyRobots(-1, enemy);

                if (robots.length > 0) {
                    RobotInfo closestEnemy = getClosestEnemy(robots);
                    float distToEnemy = myLocation.distanceTo(closestEnemy.location);
                    
                    // VP Tyrant: Only engage if enemies are very close to our economy
                    float distToEconomy = economicCenter.distanceTo(closestEnemy.location);
                    
                    if (distToEconomy < 8 && distToEnemy <= 4) {
                        // Defend economic center with minimal force
                        if (rc.canFireSingleShot()) {
                            rc.fireSingleShot(myLocation.directionTo(closestEnemy.location));
                        }
                    }
                    
                    // Always prioritize avoiding combat - move away from enemies
                    if (distToEnemy < 10) {
                        Direction awayFromEnemy = closestEnemy.location.directionTo(myLocation);
                        tryMoveTurtle(awayFromEnemy);
                    }
                } else {
                    // No enemies nearby - patrol defensively near economic center
                    patrolNearEconomy(economicCenter);
                }

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

                // VP Tyrant: Lumberjacks focus on clearing trees ONLY if blocking our tree farms
                MapLocation economicCenter = getEconomicCenter();
                TreeInfo[] trees = rc.senseNearbyTrees(-1, Team.NEUTRAL);
                
                if (trees.length > 0) {
                    // Only chop trees that are very close to our economic center and blocking space
                    TreeInfo blockingTree = null;
                    float bestScore = Float.MAX_VALUE;
                    
                    for (TreeInfo tree : trees) {
                        float distToEconomy = tree.location.distanceTo(economicCenter);
                        float distToMe = rc.getLocation().distanceTo(tree.location);
                        
                        // Only consider trees within economic zone
                        if (distToEconomy < 12) {
                            float score = distToMe; // Priority to closest trees
                            if (score < bestScore) {
                                bestScore = score;
                                blockingTree = tree;
                            }
                        }
                    }
                    
                    if (blockingTree != null && bestScore < 3) {
                        float distToTree = rc.getLocation().distanceTo(blockingTree.location);
                        if (distToTree <= RobotType.LUMBERJACK.bodyRadius + 1.0f) {
                            rc.chop(blockingTree.location);
                        } else {
                            Direction toTree = rc.getLocation().directionTo(blockingTree.location);
                            tryMoveTurtle(toTree);
                        }
                    } else {
                        // No blocking trees - patrol near economy
                        patrolNearEconomy(economicCenter);
                    }
                } else {
                    // No trees nearby - patrol near economy
                    patrolNearEconomy(economicCenter);
                }

                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println("Lumberjack Exception");
                e.printStackTrace();
            }
        }
    }

    static void runScout() throws GameActionException {
        System.out.println("I'm a scout!");
        Team enemy = rc.getTeam().opponent();

        while (true) {
            try {
                MapLocation myLocation = rc.getLocation();

                // VP Tyrant: Scouts avoid combat entirely - focus on vision for economy protection
                RobotInfo[] robots = rc.senseNearbyRobots(-1, enemy);
                if (robots.length > 0) {
                    // Run away from all enemies - avoid combat at all costs
                    RobotInfo closestEnemy = getClosestEnemy(robots);
                    Direction awayFromEnemy = closestEnemy.location.directionTo(myLocation);
                    tryMoveTurtle(awayFromEnemy);
                } else {
                    // Explore but stay relatively close to economic center
                    exploreForEconomyProtection();
                }

                Clock.yield();

            } catch (Exception e) {
                System.out.println("Scout Exception");
                e.printStackTrace();
            }
        }
    }

    static void runTank() throws GameActionException {
        System.out.println("I'm a tank!");
        Team enemy = rc.getTeam().opponent();

        while (true) {
            try {
                MapLocation myLocation = rc.getLocation();
                MapLocation economicCenter = getEconomicCenter();

                // VP Tyrant: Tanks are defensive only - avoid combat unless economy is threatened
                RobotInfo[] robots = rc.senseNearbyRobots(-1, enemy);

                if (robots.length > 0) {
                    RobotInfo closestEnemy = getClosestEnemy(robots);
                    float distToEnemy = myLocation.distanceTo(closestEnemy.location);
                    float distToEconomy = economicCenter.distanceTo(closestEnemy.location);
                    
                    // Only engage if enemies are directly threatening our economy
                    if (distToEconomy < 6 && distToEnemy <= 5) {
                        if (rc.canFireSingleShot()) {
                            rc.fireSingleShot(myLocation.directionTo(closestEnemy.location));
                        }
                    }
                    
                    // Always prioritize avoiding combat
                    if (distToEnemy < 12) {
                        Direction awayFromEnemy = closestEnemy.location.directionTo(myLocation);
                        tryMoveTurtle(awayFromEnemy);
                    }
                } else {
                    // No enemies nearby - defend economic center
                    defendEconomicCenter(economicCenter);
                }

                Clock.yield();

            } catch (Exception e) {
                System.out.println("Tank Exception");
                e.printStackTrace();
            }
        }
    }

    // Helper methods for VP Tyrant defensive turtle strategy
    
    private static MapLocation getEconomicCenter() {
        if (archonLocation != null) {
            return archonLocation;
        }
        // Fallback: try to read from broadcast
        try {
            int xPos = rc.readBroadcast(0);
            int yPos = rc.readBroadcast(1);
            return new MapLocation(xPos, yPos);
        } catch (Exception e) {
            return rc.getLocation(); // Last resort
        }
    }

    private static RobotInfo getClosestEnemy(RobotInfo[] enemies) {
        RobotInfo closest = enemies[0];
        float bestDist = rc.getLocation().distanceTo(closest.location);
        
        for (RobotInfo enemy : enemies) {
            float dist = rc.getLocation().distanceTo(enemy.location);
            if (dist < bestDist) {
                bestDist = dist;
                closest = enemy;
            }
        }
        
        return closest;
    }

    private static void stayNearEconomicCenter() throws GameActionException {
        MapLocation center = getEconomicCenter();
        float distToCenter = rc.getLocation().distanceTo(center);
        
        if (distToCenter > 6) {
            // Move back toward economic center
            Direction toCenter = rc.getLocation().directionTo(center);
            tryMoveTurtle(toCenter);
        } else {
            // Stay in place or move randomly within safe zone
            if (Math.random() < 0.3) {
                tryMoveTurtle(randomDirection());
            }
        }
    }

    private static void maintainTreeFarmPosition(MapLocation archonLoc) throws GameActionException {
        float distToArchon = rc.getLocation().distanceTo(archonLoc);
        
        // Position for optimal tree farming - spread out but not too far
        if (distToArchon > 10) {
            // Move closer to archon for tree farm coordination
            Direction toArchon = rc.getLocation().directionTo(archonLoc);
            tryMoveTurtle(toArchon);
        } else if (distToArchon < 3) {
            // Move away from archon to avoid crowding
            Direction awayFromArchon = archonLoc.directionTo(rc.getLocation());
            tryMoveTurtle(awayFromArchon);
        } else {
            // Stay in current position for tree farming
            // Only move randomly occasionally
            if (Math.random() < 0.1) {
                tryMoveTurtle(randomDirection());
            }
        }
    }

    private static void patrolNearEconomy(MapLocation center) throws GameActionException {
        float distToCenter = rc.getLocation().distanceTo(center);
        
        if (distToCenter > 12) {
            // Move back toward economic center
            Direction toCenter = rc.getLocation().directionTo(center);
            tryMoveTurtle(toCenter);
        } else if (distToCenter < 6) {
            // Move outward slightly for defensive perimeter
            Direction awayFromCenter = center.directionTo(rc.getLocation());
            tryMoveTurtle(awayFromCenter);
        } else {
            // Maintain defensive position - minimal movement
            if (Math.random() < 0.2) {
                tryMoveTurtle(randomDirection());
            }
        }
    }

    private static void defendEconomicCenter(MapLocation center) throws GameActionException {
        float idealDist = 8; // Defensive perimeter distance
        float distToCenter = rc.getLocation().distanceTo(center);
        
        if (distToCenter > idealDist + 3) {
            // Move toward center
            Direction toCenter = rc.getLocation().directionTo(center);
            tryMoveTurtle(toCenter);
        } else if (distToCenter < idealDist - 3) {
            // Move away from center
            Direction awayFromCenter = center.directionTo(rc.getLocation());
            tryMoveTurtle(awayFromCenter);
        } else {
            // Hold position - minimal movement
            if (Math.random() < 0.1) {
                tryMoveTurtle(randomDirection());
            }
        }
    }

    private static void exploreForEconomyProtection() throws GameActionException {
        MapLocation economicCenter = getEconomicCenter();
        float distToCenter = rc.getLocation().distanceTo(economicCenter);
        
        // Don't stray too far from economic center
        if (distToCenter > 25) {
            tryMoveTurtle(rc.getLocation().directionTo(economicCenter));
        } else {
            // Limited exploration for intelligence
            if (Math.random() < 0.4) {
                tryMoveTurtle(randomDirection());
            }
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
     * VP Tyrant: Very careful movement - avoid all contact with enemies
     * More conservative movement for defensive turtle strategy.
     *
     * @param dir The intended direction of movement
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryMoveTurtle(Direction dir) throws GameActionException {
        return tryMove(dir,45,8); // Even more careful movement with wider angle checks
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

        // First, try intended direction
        if (rc.canMove(dir)) {
            rc.move(dir);
            return true;
        }

        // Now try a bunch of similar angles
        boolean moved = false;
        int currentCheck = 1;

        while(currentCheck<=checksPerSide) {
            // Try the offset of the left side
            if(rc.canMove(dir.rotateLeftDegrees(degreeOffset*currentCheck))) {
                rc.move(dir.rotateLeftDegrees(degreeOffset*currentCheck));
                return true;
            }
            // Try the offset on the right side
            if(rc.canMove(dir.rotateRightDegrees(degreeOffset*currentCheck))) {
                rc.move(dir.rotateRightDegrees(degreeOffset*currentCheck));
                return true;
            }
            // No move performed, try slightly further
            currentCheck++;
        }

        // A move never happened, so return false.
        return false;
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
        // This is the distance of a line that goes from myLocation and intersects perpendicularly with propagationDirection.
        // This corresponds to the smallest radius circle centered at our location that would intersect with the
        // line that is the path of the bullet.
        float perpendicularDist = (float)Math.abs(distToRobot * Math.sin(theta)); // soh cah toa :)

        return (perpendicularDist <= rc.getType().bodyRadius);
    }
}