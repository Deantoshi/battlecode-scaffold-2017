package minimax_2_1_champion_3;
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
        }
	}

    static void runArchon() throws GameActionException {
        System.out.println("I'm an archon!");

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {

                // Centralized spend policy (hire/build/donate)
                BulletSpending.spendPolicy();

                // Move randomly
                tryMove(randomDirection());

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

                // Centralized spend policy (plant/build/donate) - Archon Hunter plants no trees
                BulletSpending.spendPolicy();

                // Move randomly
                tryMove(randomDirection());

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

                // Archon Hunter: First check if we know enemy archon location
                int enemyArchonX = rc.readBroadcast(2);
                int enemyArchonY = rc.readBroadcast(3);
                MapLocation enemyArchonLoc = null;
                if (enemyArchonX != 0 || enemyArchonY != 0) {
                    enemyArchonLoc = new MapLocation(enemyArchonX, enemyArchonY);
                }

                // See if there are any nearby enemy robots
                RobotInfo[] robots = rc.senseNearbyRobots(-1, enemy);

                // Archon Hunter: Target priority - enemy archon first, then soldiers/tanks, then others
                RobotInfo target = null;
                
                // If we know enemy archon location and it's in sensor range, target it
                if (enemyArchonLoc != null && myLocation.distanceTo(enemyArchonLoc) <= rc.getType().sensorRadius) {
                    // Check if archon is still alive
                    RobotInfo[] nearbyRobots = rc.senseNearbyRobots(myLocation, rc.getType().sensorRadius, enemy);
                    for (RobotInfo r : nearbyRobots) {
                        if (r.type == RobotType.ARCHON) {
                            target = r;
                            break;
                        }
                    }
                }

                // If no archon found, target soldiers/tanks first
                if (target == null) {
                    for (RobotInfo r : robots) {
                        if (r.type == RobotType.SOLDIER || r.type == RobotType.TANK) {
                            target = r;
                            break;
                        }
                    }
                }

                // If still no target, target first enemy found
                if (target == null && robots.length > 0) {
                    target = robots[0];
                }

                // If there are targets, fire at them
                if (target != null && rc.canFireSingleShot()) {
                    rc.fireSingleShot(myLocation.directionTo(target.location));
                }

                // Archon Hunter: Move toward enemy archon if known, otherwise hunt enemies
                if (enemyArchonLoc != null) {
                    Direction toArchon = myLocation.directionTo(enemyArchonLoc);
                    tryMove(toArchon);
                } else if (robots.length > 0) {
                    // Move toward enemy robots to find archon
                    Direction toEnemy = myLocation.directionTo(robots[0].location);
                    tryMove(toEnemy);
                } else {
                    // Move randomly to search
                    tryMove(randomDirection());
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

                // Archon Hunter: Read enemy archon location from broadcast channels 2 and 3
                int enemyArchonX = rc.readBroadcast(2);
                int enemyArchonY = rc.readBroadcast(3);
                MapLocation enemyArchonLoc = null;
                if (enemyArchonX != 0 || enemyArchonY != 0) {
                    enemyArchonLoc = new MapLocation(enemyArchonX, enemyArchonY);
                }

                // See if there are any enemy robots within striking range
                RobotInfo[] robots = rc.senseNearbyRobots(RobotType.LUMBERJACK.bodyRadius+GameConstants.LUMBERJACK_STRIKE_RADIUS, enemy);

                // Archon Hunter: Priority targeting - target archon first if in range
                RobotInfo target = null;
                MapLocation myLocation = rc.getLocation();
                
                // Check if enemy archon is in striking range
                if (enemyArchonLoc != null && myLocation.distanceTo(enemyArchonLoc) <= RobotType.LUMBERJACK.bodyRadius+GameConstants.LUMBERJACK_STRIKE_RADIUS) {
                    RobotInfo[] nearbyRobots = rc.senseNearbyRobots(myLocation, RobotType.LUMBERJACK.bodyRadius+GameConstants.LUMBERJACK_STRIKE_RADIUS, enemy);
                    for (RobotInfo r : nearbyRobots) {
                        if (r.type == RobotType.ARCHON) {
                            target = r;
                            break;
                        }
                    }
                }

                // If no archon in range, prioritize soldiers/tanks then other targets
                if (target == null) {
                    for (RobotInfo r : robots) {
                        if (r.type == RobotType.SOLDIER || r.type == RobotType.TANK) {
                            target = r;
                            break;
                        }
                    }
                    if (target == null && robots.length > 0) {
                        target = robots[0];
                    }
                }

                if (robots.length > 0 && !rc.hasAttacked()) {
                    // Use strike() to hit all nearby robots!
                    rc.strike();
                } else {
                    // Archon Hunter: Move toward enemy archon if known
                    if (enemyArchonLoc != null) {
                        Direction toEnemyArchon = myLocation.directionTo(enemyArchonLoc);
                        tryMove(toEnemyArchon);
                    } else {
                        // No enemy archon info yet, search for enemy robots
                        robots = rc.senseNearbyRobots(-1, enemy);

                        // If there is a robot, move towards it
                        if (robots.length > 0) {
                            MapLocation enemyLocation = robots[0].getLocation();
                            Direction toEnemy = myLocation.directionTo(enemyLocation);

                            tryMove(toEnemy);
                        } else {
                            // Move Randomly to search
                            tryMove(randomDirection());
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

    static void runScout() throws GameActionException {
        System.out.println("I'm a scout!");
        Team enemy = rc.getTeam().opponent();

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {
                MapLocation myLocation = rc.getLocation();

                // Archon Hunter Scout: Search for enemy archon in first 50 rounds
                RobotInfo[] robots = rc.senseNearbyRobots(-1, enemy);
                
                // Check if we found enemy archon
                for (RobotInfo r : robots) {
                    if (r.type == RobotType.ARCHON) {
                        // Broadcast enemy archon location
                        rc.broadcast(2, (int)r.location.x);
                        rc.broadcast(3, (int)r.location.y);
                        System.out.println("Found enemy archon at: " + r.location.x + ", " + r.location.y);
                        break;
                    }
                }

                // Archon Hunter: If we know enemy archon location, move toward it
                int enemyArchonX = rc.readBroadcast(2);
                int enemyArchonY = rc.readBroadcast(3);
                if (enemyArchonX != 0 || enemyArchonY != 0) {
                    MapLocation enemyArchonLoc = new MapLocation(enemyArchonX, enemyArchonY);
                    Direction toArchon = myLocation.directionTo(enemyArchonLoc);
                    tryMove(toArchon);
                } else {
                    // No enemy archon location yet, explore aggressively
                    if (robots.length > 0) {
                        // Move toward enemy robots to find archon
                        Direction toEnemy = myLocation.directionTo(robots[0].location);
                        tryMove(toEnemy);
                    } else {
                        // Move randomly to search the map
                        tryMove(randomDirection());
                    }
                }

                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println("Scout Exception");
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
     *
     * @param dir The intended direction of movement
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryMove(Direction dir) throws GameActionException {
        return tryMove(dir,20,3);
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
        float distToRobot = bulletLocation.distanceTo(bulletLocation);
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
