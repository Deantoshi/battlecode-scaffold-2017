package kimi_k2_5_champion_3;
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
        System.out.println("I'm an archon! Bullet Starvation mode!");

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {

                // Centralized spend policy (hire/build/donate)
                BulletSpending.spendPolicy();

                // Move randomly - archons should stay safe
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
        System.out.println("I'm a gardener! Bullet Starvation mode!");
        int round = rc.getRoundNum();

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {
                // Update round for checks
                round = rc.getRoundNum();

                // Listen for home archon's location
                int xPos = rc.readBroadcast(0);
                int yPos = rc.readBroadcast(1);
                MapLocation archonLoc = new MapLocation(xPos,yPos);

                // Centralized spend policy (plant/build/donate)
                BulletSpending.spendPolicy();

                // Water trees if possible (gardeners maintain economy)
                waterTrees();

                // Defensive movement: hide from enemies
                RobotInfo[] nearbyEnemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
                if (nearbyEnemies.length > 0) {
                    Direction awayFromEnemy = nearbyEnemies[0].location.directionTo(rc.getLocation());
                    tryMove(awayFromEnemy);
                } else {
                    // Move randomly otherwise
                    tryMove(randomDirection());
                }

                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println("Gardener Exception");
                e.printStackTrace();
            }
        }
    }

    // Helper method for gardeners to water trees
    static void waterTrees() throws GameActionException {
        TreeInfo[] nearbyTrees = rc.senseNearbyTrees(rc.getType().sensorRadius, rc.getTeam());
        for (TreeInfo tree : nearbyTrees) {
            if (rc.canWater(tree.ID)) {
                rc.water(tree.ID);
                return; // Water one tree per turn
            }
        }
    }

    static void runScout() throws GameActionException {
        System.out.println("I'm a scout! Bullet Starvation mode!");
        Team enemy = rc.getTeam().opponent();
        Team myTeam = rc.getTeam();
        int round = rc.getRoundNum();

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {
                MapLocation myLocation = rc.getLocation();
                round = rc.getRoundNum();

                // BULLET STARVATION PRIORITY: Shake all trees for bullets first
                TreeInfo[] neutralTrees = rc.senseNearbyTrees(-1, Team.NEUTRAL);
                boolean shookTree = false;
                for (TreeInfo tree : neutralTrees) {
                    if (tree.containedBullets > 0 && rc.canShake(tree.ID)) {
                        rc.shake(tree.ID);
                        shookTree = true;
                        break; // Shake one tree per turn
                    }
                }

                // Find neutral trees with bullets to move toward
                TreeInfo targetTree = null;
                int maxBullets = 0;
                for (TreeInfo tree : neutralTrees) {
                    if (tree.containedBullets > maxBullets) {
                        maxBullets = tree.containedBullets;
                        targetTree = tree;
                    }
                }

                // Scouts have superior vision - find enemy robots
                RobotInfo[] robots = rc.senseNearbyRobots(-1, enemy);

                // Priority targeting: GARDENER > ARCHON > other (starve their economy)
                RobotInfo target = findPriorityTarget(robots);

                if (target != null) {
                    Direction toTarget = myLocation.directionTo(target.location);
                    
                    // Fire at target if in range
                    if (rc.canFireSingleShot()) {
                        rc.fireSingleShot(toTarget);
                    }
                    
                    // Bullet Starvation: Aggressive toward production units
                    if (target.type == RobotType.GARDENER || target.type == RobotType.ARCHON) {
                        // Push toward production targets to deny economy
                        tryMove(toTarget);
                    } else {
                        // Circle at safe distance for reconnaissance
                        Direction circlingDir = toTarget.rotateLeftDegrees(60);
                        tryMove(circlingDir);
                    }
                    
                    // Broadcast enemy production location for team
                    if (target.type == RobotType.ARCHON || target.type == RobotType.GARDENER) {
                        rc.broadcast(2, (int)target.location.x);
                        rc.broadcast(3, (int)target.location.y);
                        rc.broadcast(4, target.type.ordinal()); // Type indicator
                    }
                } else if (targetTree != null && !shookTree) {
                    // Move toward trees with bullets to shake them
                    Direction toTree = myLocation.directionTo(targetTree.location);
                    tryMove(toTree);
                } else {
                    // No priority targets - explore toward enemy base or random
                    MapLocation enemyLoc = guessEnemyLocation();
                    if (enemyLoc != null) {
                        Direction toEnemy = myLocation.directionTo(enemyLoc);
                        tryMove(toEnemy);
                    } else {
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

    static void runSoldier() throws GameActionException {
        System.out.println("I'm a soldier! Bullet Starvation mode!");
        Team enemy = rc.getTeam().opponent();
        int round = rc.getRoundNum();

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {
                MapLocation myLocation = rc.getLocation();
                round = rc.getRoundNum();

                // See if there are any nearby enemy robots
                RobotInfo[] robots = rc.senseNearbyRobots(-1, enemy);

                // Priority targeting: GARDENER > ARCHON > other (starve their economy)
                RobotInfo target = findPriorityTarget(robots);

                if (target != null) {
                    Direction toTarget = myLocation.directionTo(target.location);
                    float distToTarget = myLocation.distanceTo(target.location);
                    
                    // Fire at priority target
                    if (rc.canFireSingleShot()) {
                        rc.fireSingleShot(toTarget);
                    }
                    
                    // Bullet Starvation: Secure controlled areas
                    // Push when targeting production, defend positions otherwise
                    if (target.type == RobotType.GARDENER || target.type == RobotType.ARCHON) {
                        // Aggressive push toward production units
                        tryMove(toTarget);
                    } else if (distToTarget > 8f) {
                        // Enemy combat unit at range - advance to control area
                        Direction cautiousAdvance = toTarget.rotateLeftDegrees(10);
                        tryMove(cautiousAdvance);
                    } else {
                        // Close combat - kite at optimal range
                        Direction kiteDir = target.location.directionTo(myLocation);
                        tryMove(kiteDir);
                    }
                } else {
                    // No enemies visible - check broadcast for enemy production location
                    int targetX = rc.readBroadcast(2);
                    int targetY = rc.readBroadcast(3);
                    if (targetX != 0 || targetY != 0) {
                        MapLocation targetLoc = new MapLocation(targetX, targetY);
                        Direction toTarget = myLocation.directionTo(targetLoc);
                        tryMove(toTarget);
                    } else {
                        // No intel - move randomly or toward enemy start
                        MapLocation enemyLoc = guessEnemyLocation();
                        if (enemyLoc != null) {
                            tryMove(myLocation.directionTo(enemyLoc));
                        } else {
                            tryMove(randomDirection());
                        }
                    }
                }

                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println("Soldier Exception");
                e.printStackTrace();
            }
        }
    }

    // Helper method to find priority target: GARDENER > ARCHON > other (economy targeting)
    static RobotInfo findPriorityTarget(RobotInfo[] robots) {
        RobotInfo gardenerTarget = null;
        RobotInfo archonTarget = null;
        RobotInfo anyTarget = null;
        double gardenerDist = Double.MAX_VALUE;
        double archonDist = Double.MAX_VALUE;
        double anyDist = Double.MAX_VALUE;
        
        for (RobotInfo robot : robots) {
            double dist = rc.getLocation().distanceSquaredTo(robot.location);
            
            if (robot.type == RobotType.GARDENER) {
                if (dist < gardenerDist) {
                    gardenerTarget = robot;
                    gardenerDist = dist;
                }
            } else if (robot.type == RobotType.ARCHON) {
                if (dist < archonDist) {
                    archonTarget = robot;
                    archonDist = dist;
                }
            } else {
                if (dist < anyDist) {
                    anyTarget = robot;
                    anyDist = dist;
                }
            }
        }
        
        // Return in priority order: GARDENER > ARCHON > any (starve economy)
        if (gardenerTarget != null) return gardenerTarget;
        if (archonTarget != null) return archonTarget;
        return anyTarget;
    }

    // Helper to guess enemy location based on map symmetry
    static MapLocation guessEnemyLocation() {
        MapLocation[] myArchons = rc.getInitialArchonLocations(rc.getTeam());
        MapLocation[] enemyArchons = rc.getInitialArchonLocations(rc.getTeam().opponent());
        
        if (enemyArchons.length > 0 && myArchons.length > 0) {
            // Return average enemy archon location
            float x = 0, y = 0;
            for (MapLocation loc : enemyArchons) {
                x += loc.x;
                y += loc.y;
            }
            return new MapLocation(x / enemyArchons.length, y / enemyArchons.length);
        }
        return null;
    }

    static void runLumberjack() throws GameActionException {
        System.out.println("I'm a lumberjack! Bullet Starvation mode!");
        Team enemy = rc.getTeam().opponent();
        int round = rc.getRoundNum();

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {
                round = rc.getRoundNum();
                MapLocation myLocation = rc.getLocation();

                // BULLET STARVATION PRIORITY: Chop neutral trees to deny enemy bullets
                TreeInfo[] neutralTrees = rc.senseNearbyTrees(RobotType.LUMBERJACK.sensorRadius, Team.NEUTRAL);
                TreeInfo bestTree = null;
                int maxBullets = 0;
                
                for (TreeInfo tree : neutralTrees) {
                    // Priority: Trees with robots > trees with bullets > other trees
                    if (tree.containedRobot != null) {
                        bestTree = tree;
                        break; // Highest priority - deny enemy free units
                    } else if (tree.containedBullets > maxBullets) {
                        maxBullets = tree.containedBullets;
                        bestTree = tree;
                    }
                }
                
                // Chop the best tree if we can
                if (bestTree != null && rc.canChop(bestTree.ID)) {
                    rc.chop(bestTree.ID);
                } else {
                    // No good trees to chop - check for enemies to strike
                    RobotInfo[] robots = rc.senseNearbyRobots(RobotType.LUMBERJACK.bodyRadius+GameConstants.LUMBERJACK_STRIKE_RADIUS, enemy);

                    if(robots.length > 0 && !rc.hasAttacked()) {
                        // Use strike() to hit all nearby robots!
                        rc.strike();
                    } else {
                        // No close robots, so search for robots within sight radius
                        robots = rc.senseNearbyRobots(-1,enemy);

                        // If there is a robot, move toward it
                        if(robots.length > 0) {
                            // Priority targeting for lumberjack: GARDENER > ARCHON > other
                            RobotInfo target = findPriorityTarget(robots);
                            
                            if (target != null) {
                                MapLocation enemyLocation = target.getLocation();
                                Direction toEnemy = myLocation.directionTo(enemyLocation);
                                tryMove(toEnemy);
                            }
                        } else if (bestTree != null) {
                            // Move toward best tree to chop it
                            Direction toTree = myLocation.directionTo(bestTree.location);
                            tryMove(toTree);
                        } else {
                            // Move Randomly
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
