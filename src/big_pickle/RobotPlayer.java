package big_pickle;
import battlecode.common.*;

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

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {

                // Use centralized spending policy for gardener hiring
                BulletSpending.spendPolicy();

                // Move randomly but carefully (Tank Fortress - avoid unnecessary risk)
                tryMoveCareful(randomDirection());

                // Broadcast archon's location for other robots on the team to know
                MapLocation myLocation = rc.getLocation();
                rc.broadcast(0,(int)myLocation.x);
                rc.broadcast(1,(int)myLocation.y);

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

                // Use centralized spending policy for building meta-gardeners, tanks, and trees
                BulletSpending.spendPolicy();

                // Tank Fortress - stay relatively close to archon to protect tree farm
                if (rc.getLocation().distanceTo(archonLoc) > 15) {
                    Direction toArchon = rc.getLocation().directionTo(archonLoc);
                    tryMoveCareful(toArchon);
                } else {
                    // Move randomly but carefully when near archon
                    tryMoveCareful(randomDirection());
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

                // Tank Fortress: soldiers provide defensive support
                if (robots.length > 0) {
                    RobotInfo nearestEnemy = robots[0];
                    float closestDist = myLocation.distanceTo(nearestEnemy.location);
                    
                    // Find the closest enemy
                    for (RobotInfo robot : robots) {
                        float dist = myLocation.distanceTo(robot.location);
                        if (dist < closestDist) {
                            closestDist = dist;
                            nearestEnemy = robot;
                        }
                    }

                    // Only attack if enemy is relatively close (defensive)
                    if (closestDist <= 8 && rc.canFireSingleShot()) {
                        rc.fireSingleShot(myLocation.directionTo(nearestEnemy.location));
                    }
                    
                    // Move away from distant enemies, approach close ones
                    if (closestDist > 10) {
                        // Stay defensive - move away
                        Direction awayFromEnemy = nearestEnemy.location.directionTo(myLocation);
                        tryMoveCareful(awayFromEnemy);
                    } else if (closestDist > 4) {
                        // Move closer to engage if they're not too close
                        Direction toEnemy = myLocation.directionTo(nearestEnemy.location);
                        tryMoveCareful(toEnemy);
                    }
                } else {
                    // No enemies nearby - patrol defensively around tree farms
                    tryMoveCareful(randomDirection());
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

                // See if there are any enemy robots within striking range (distance 1 from lumberjack's radius)
                RobotInfo[] robots = rc.senseNearbyRobots(RobotType.LUMBERJACK.bodyRadius+GameConstants.LUMBERJACK_STRIKE_RADIUS, enemy);

                if(robots.length > 0 && !rc.hasAttacked()) {
                    // Use strike() to hit all nearby robots!
                    rc.strike();
                } else {
                    // Look for neutral trees to chop for resources
                    TreeInfo[] trees = rc.senseNearbyTrees(-1, Team.NEUTRAL);
                    if (trees.length > 0 && !rc.hasAttacked()) {
                        TreeInfo closestTree = trees[0];
                        float closestDist = rc.getLocation().distanceTo(closestTree.location);
                        
                        for (TreeInfo tree : trees) {
                            float dist = rc.getLocation().distanceTo(tree.location);
                            if (dist < closestDist) {
                                closestDist = dist;
                                closestTree = tree;
                            }
                        }
                        
                        if (closestDist <= RobotType.LUMBERJACK.bodyRadius + 1.0f) {
                            rc.chop(closestTree.location);
                        } else {
                            Direction toTree = rc.getLocation().directionTo(closestTree.location);
                            tryMoveCareful(toTree);
                        }
                    } else {
                        // No close robots or trees, move randomly
                        tryMoveCareful(randomDirection());
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

    static void runScout() throws GameActionException {
        System.out.println("I'm a scout!");
        Team enemy = rc.getTeam().opponent();

        while (true) {
            try {
                MapLocation myLocation = rc.getLocation();

                // Tank Fortress - scouts focus on economic support, not aggressive combat
                RobotInfo[] robots = rc.senseNearbyRobots(-1, enemy);
                if (robots.length > 0) {
                    // Only shoot at very close range, mostly just run away
                    RobotInfo nearestEnemy = robots[0];
                    float closestDist = myLocation.distanceTo(nearestEnemy.location);
                    
                    for (RobotInfo robot : robots) {
                        float dist = myLocation.distanceTo(robot.location);
                        if (dist < closestDist) {
                            closestDist = dist;
                            nearestEnemy = robot;
                        }
                    }

                    if (closestDist <= 3 && rc.canFireSingleShot()) {
                        rc.fireSingleShot(myLocation.directionTo(nearestEnemy.location));
                    }
                    
                    // Run away from enemies
                    Direction awayFromEnemy = nearestEnemy.location.directionTo(myLocation);
                    tryMoveCareful(awayFromEnemy);
                } else {
                    // Explore the map
                    tryMoveCareful(randomDirection());
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

                // See if there are any nearby enemy robots
                RobotInfo[] robots = rc.senseNearbyRobots(-1, enemy);

                if (robots.length > 0) {
                    RobotInfo nearestEnemy = robots[0];
                    float closestDist = myLocation.distanceTo(nearestEnemy.location);
                    
                    for (RobotInfo robot : robots) {
                        float dist = myLocation.distanceTo(robot.location);
                        if (dist < closestDist) {
                            closestDist = dist;
                            nearestEnemy = robot;
                        }
                    }

                    // Tank Fortress defensive behavior - only engage very close enemies (4 units max)
                    if (closestDist <= 4) {
                        if (rc.canFireSingleShot()) {
                            rc.fireSingleShot(myLocation.directionTo(nearestEnemy.location));
                        }
                    }
                    
                    // Move defensively - stay in protective perimeter around tree farms
                    if (closestDist > 6) {
                        // Stay defensive - move away from distant threats
                        Direction awayFromEnemy = nearestEnemy.location.directionTo(myLocation);
                        tryMoveCareful(awayFromEnemy);
                    } else if (closestDist > 2) {
                        // Move closer to engage if they're very close
                        Direction toEnemy = myLocation.directionTo(nearestEnemy.location);
                        tryMoveCareful(toEnemy);
                    }
                } else {
                    // No enemies nearby - patrol defensively around tree farm area
                    tryMoveCareful(randomDirection());
                }

                Clock.yield();

            } catch (Exception e) {
                System.out.println("Tank Exception");
                e.printStackTrace();
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
     * Attempts to move in a given direction, while avoiding small obstacles directly in the path.
     * More conservative movement for Tank Fortress defensive strategy.
     *
     * @param dir The intended direction of movement
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryMoveCareful(Direction dir) throws GameActionException {
        return tryMove(dir,30,6); // More careful movement with wider angle checks
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