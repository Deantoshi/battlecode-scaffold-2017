package hunter_alpha_champion_2;
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

                // Centralized spend policy (hire/build/donate)
                BulletSpending.spendPolicy();

                // Flee from enemies instead of random movement
                MapLocation myLocation = rc.getLocation();
                RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
                if (enemies.length > 0) {
                    // Move away from the nearest enemy
                    Direction awayFromEnemy = enemies[0].getLocation().directionTo(myLocation);
                    tryMove(awayFromEnemy);
                } else {
                    tryMove(randomDirection());
                }

                // Broadcast archon's location for other robots on the team to know
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

                // Read enemy composition intel for strategic positioning
                int intelFresh = rc.readBroadcast(BulletSpending.CH_INTEL_FRESH);

                // Centralized spend policy (plant/build/donate)
                BulletSpending.spendPolicy();

                // Move towards archon if enemies nearby, otherwise random
                MapLocation myLocation = rc.getLocation();
                RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
                if (enemies.length > 0) {
                    // Move back towards archon for protection
                    Direction toArchon = myLocation.directionTo(archonLoc);
                    tryMove(toArchon);
                } else {
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

                // If there are some...
                if (robots.length > 0) {
                    // Prioritize shooting scouts (low HP threats for intel denial)
                    RobotInfo target = robots[0];
                    for (RobotInfo r : robots) {
                        if (r.type == RobotType.SCOUT) {
                            target = r;
                            break;
                        }
                    }
                    // And we have enough bullets, and haven't attacked yet this turn...
                    if (rc.canFireSingleShot()) {
                        // ...Then fire a bullet in the direction of the enemy.
                        rc.fireSingleShot(rc.getLocation().directionTo(target.location));
                    }
                    // Move towards target
                    tryMove(myLocation.directionTo(target.location));
                } else {
                    // Move randomly
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

                // See if there are any enemy robots within striking range (distance 1 from lumberjack's radius)
                RobotInfo[] robots = rc.senseNearbyRobots(RobotType.LUMBERJACK.bodyRadius+GameConstants.LUMBERJACK_STRIKE_RADIUS, enemy);

                if(robots.length > 0 && !rc.hasAttacked()) {
                    // Use strike() to hit all nearby robots!
                    rc.strike();
                } else {
                    // No close robots, so search for robots within sight radius
                    robots = rc.senseNearbyRobots(-1,enemy);

                    // If there is a robot, move towards it
                    if(robots.length > 0) {
                        // Prioritize tanks (lumberjacks counter tanks)
                        RobotInfo target = robots[0];
                        for (RobotInfo r : robots) {
                            if (r.type == RobotType.TANK) {
                                target = r;
                                break;
                            }
                        }
                        MapLocation myLocation = rc.getLocation();
                        MapLocation enemyLocation = target.getLocation();
                        Direction toEnemy = myLocation.directionTo(enemyLocation);

                        tryMove(toEnemy);
                    } else {
                        // Move Randomly
                        tryMove(randomDirection());
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
     * Scout behavior: patrol and gather enemy composition intel.
     * Broadcasts enemy counts on channels 10-15 for the gardener to read.
     */
    static void runScout() throws GameActionException {
        System.out.println("I'm a scout!");
        Team enemy = rc.getTeam().opponent();
        int reportCounter = 0;

        while (true) {
            try {
                MapLocation myLocation = rc.getLocation();

                // Count enemy units by type
                RobotInfo[] enemies = rc.senseNearbyRobots(-1, enemy);
                int soldierCount = 0;
                int tankCount = 0;
                int lumberjackCount = 0;
                int scoutCount = 0;

                for (RobotInfo r : enemies) {
                    switch (r.type) {
                        case SOLDIER: soldierCount++; break;
                        case TANK: tankCount++; break;
                        case LUMBERJACK: lumberjackCount++; break;
                        case SCOUT: scoutCount++; break;
                        default: break;
                    }
                }

                // Report intel every 5 rounds to avoid excessive broadcasting
                reportCounter++;
                if (reportCounter >= 5) {
                    reportCounter = 0;

                    // Broadcast enemy composition (accumulate with other scouts)
                    rc.broadcast(BulletSpending.CH_ENEMY_SOLDIER_COUNT, soldierCount);
                    rc.broadcast(BulletSpending.CH_ENEMY_TANK_COUNT, tankCount);
                    rc.broadcast(BulletSpending.CH_ENEMY_LUMBERJACK_COUNT, lumberjackCount);
                    rc.broadcast(BulletSpending.CH_ENEMY_SCOUT_COUNT, scoutCount);
                    rc.broadcast(BulletSpending.CH_INTEL_FRESH, 1);
                }

                // Register as alive scout
                rc.broadcast(BulletSpending.CH_SCOUT_COUNT, 1);

                // If enemies nearby, stay at range and observe
                if (enemies.length > 0) {
                    // Kite away from nearest enemy while staying in sensor range
                    Direction awayFromNearest = enemies[0].getLocation().directionTo(myLocation);
                    tryMove(awayFromNearest);
                } else {
                    // Patrol: move towards enemy archon locations or randomly
                    MapLocation[] enemyArchons = rc.getInitialArchonLocations(enemy);
                    if (enemyArchons.length > 0) {
                        Direction toEnemy = myLocation.directionTo(enemyArchons[0]);
                        tryMove(toEnemy);
                    } else {
                        tryMove(randomDirection());
                    }
                }

                // Fire single shot if enemy in range
                if (enemies.length > 0 && rc.canFireSingleShot()) {
                    rc.fireSingleShot(rc.getLocation().directionTo(enemies[0].location));
                }

                Clock.yield();

            } catch (Exception e) {
                System.out.println("Scout Exception");
                e.printStackTrace();
            }
        }
    }

    /**
     * Tank behavior: heavy combat unit, targets enemies aggressively.
     */
    static void runTank() throws GameActionException {
        System.out.println("I'm a tank!");
        Team enemy = rc.getTeam().opponent();

        while (true) {
            try {
                MapLocation myLocation = rc.getLocation();
                RobotInfo[] enemies = rc.senseNearbyRobots(-1, enemy);

                if (enemies.length > 0) {
                    // Prioritize shooting at high-value targets
                    RobotInfo target = enemies[0];

                    // Fire at enemy
                    if (rc.canFireSingleShot()) {
                        rc.fireSingleShot(rc.getLocation().directionTo(target.location));
                    }

                    // Move towards enemy
                    tryMove(myLocation.directionTo(target.location));
                } else {
                    // Move towards enemy archon or random
                    MapLocation[] enemyArchons = rc.getInitialArchonLocations(enemy);
                    if (enemyArchons.length > 0) {
                        Direction toEnemy = myLocation.directionTo(enemyArchons[0]);
                        tryMove(toEnemy);
                    } else {
                        tryMove(randomDirection());
                    }
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
