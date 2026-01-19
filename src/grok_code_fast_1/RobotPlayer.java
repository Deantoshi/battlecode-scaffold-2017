package grok_code_fast_1;
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

        // Here, we've separated the controls into a different method for each RobotType.
        // You can add the missing ones or rewrite this into your own control structure.
        switch (rc.getType()) {
            case ARCHON:
                Archon.run(rc);
                break;
            case GARDENER:
                Gardener.run(rc);
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



    static void runSoldier() throws GameActionException {
        System.out.println("I'm a soldier!");
        Team enemy = rc.getTeam().opponent();

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {
                MapLocation myLocation = rc.getLocation();

                // Track stationary rounds for stuck detection
                int myID = rc.getID();
                int base = myID * 100;
                int lastX = rc.readBroadcast(base + 1);
                int lastY = rc.readBroadcast(base + 2);
                MapLocation lastPos = new MapLocation(lastX / 100f, lastY / 100f);
                int stationary = rc.readBroadcast(base + 3);
                if (myLocation.equals(lastPos) && lastX != 0) {
                    stationary++;
                } else {
                    stationary = 0;
                }
                rc.broadcast(base + 1, (int)(myLocation.x * 100));
                rc.broadcast(base + 2, (int)(myLocation.y * 100));
                rc.broadcast(base + 3, stationary);

                // See if there are any nearby enemy robots
                RobotInfo[] robots = rc.senseNearbyRobots(-1, enemy);

                // If there are some...
                if (robots.length > 0) {
                    // Find the closest enemy
                    RobotInfo closestEnemy = robots[0];
                    float minDist = myLocation.distanceTo(closestEnemy.location);
                    for (RobotInfo r : robots) {
                        float dist = myLocation.distanceTo(r.location);
                        if (dist < minDist) {
                            minDist = dist;
                            closestEnemy = r;
                        }
                    }
                    // And we have enough bullets, and haven't attacked yet this turn...
                    if (rc.canFireSingleShot()) {
                        // ...Then fire a bullet in the direction of the enemy.
                        rc.fireSingleShot(rc.getLocation().directionTo(closestEnemy.location));
                    }
                    // Move toward enemy
                    Direction toEnemy = rc.getLocation().directionTo(closestEnemy.location);
                    tryMove(toEnemy);
                } else {
                    // Movement logic with stuck override
                    if (stationary > 100) {
                        // Force move toward enemy location to break out of stuck position
                        int ex = rc.readBroadcast(2);
                        int ey = rc.readBroadcast(3);
                        if (ex != 0 || ey != 0) {
                            MapLocation enemyArchon = new MapLocation(ex, ey);
                            Direction toEnemy = myLocation.directionTo(enemyArchon);
                            tryMove(toEnemy);
                        } else {
                            tryMove(randomDirection());
                        }
                    } else {
                        // Move towards enemy archon if known
                        int ex = rc.readBroadcast(2);
                        int ey = rc.readBroadcast(3);
                        if (ex != 0 || ey != 0) {
                            MapLocation enemyArchon = new MapLocation(ex, ey);
                            Direction toEnemyArchon = myLocation.directionTo(enemyArchon);
                            tryMove(toEnemyArchon);
                        } else {
                            // Move randomly for exploration
                            tryMove(randomDirection());
                        }
                    }
                }

                // Dodge bullets
                BulletInfo[] bullets = rc.senseNearbyBullets();
                for (BulletInfo b : bullets) {
                    if (willCollideWithMe(b)) {
                        Direction away = b.dir.opposite();
                        tryMove(away);
                        break;
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

    static void runLumberjack() throws GameActionException {
        System.out.println("I'm a lumberjack!");
        Team enemy = rc.getTeam().opponent();

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {
                MapLocation myLocation = rc.getLocation();

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
                        MapLocation enemyLocation = robots[0].getLocation();
                        Direction toEnemy = myLocation.directionTo(enemyLocation);

                        tryMove(toEnemy);
                    } else {
                        // No enemies, check for neutral trees to chop
                        TreeInfo[] trees = rc.senseNearbyTrees(-1);
                        TreeInfo targetTree = null;
                        for (TreeInfo t : trees) {
                            if (t.team == Team.NEUTRAL) {
                                targetTree = t;
                                break;
                            }
                        }
                        if (targetTree != null) {
                            MapLocation treeLoc = targetTree.location;
                            Direction toTree = myLocation.directionTo(treeLoc);
                            if (rc.canChop(treeLoc)) {
                                rc.chop(treeLoc);
                            } else {
                                tryMove(toTree);
                            }
                        } else {
                            // Move towards enemy archon if known
                            int ex = rc.readBroadcast(2);
                            int ey = rc.readBroadcast(3);
                            if (ex != 0 || ey != 0) {
                                MapLocation enemyArchon = new MapLocation(ex, ey);
                                Direction toEnemyArchon = myLocation.directionTo(enemyArchon);
                                tryMove(toEnemyArchon);
                            } else {
                                // Move Randomly
                                tryMove(randomDirection());
                            }
                        }
                    }
                }

                // Dodge bullets
                BulletInfo[] bullets = rc.senseNearbyBullets();
                for (BulletInfo b : bullets) {
                    if (willCollideWithMe(b)) {
                        Direction away = b.dir.opposite();
                        tryMove(away);
                        break;
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

                // See if there are any nearby enemy robots
                RobotInfo[] robots = rc.senseNearbyRobots(-1, enemy);

                // If there are some...
                if (robots.length > 0) {
                    // Find the closest enemy
                    RobotInfo closestEnemy = robots[0];
                    float minDist = myLocation.distanceTo(closestEnemy.location);
                    for (RobotInfo r : robots) {
                        float dist = myLocation.distanceTo(r.location);
                        if (dist < minDist) {
                            minDist = dist;
                            closestEnemy = r;
                        }
                    }
                    // And we have enough bullets, and haven't attacked yet this turn...
                    if (rc.canFireSingleShot()) {
                        // ...Then fire a bullet in the direction of the enemy.
                        rc.fireSingleShot(rc.getLocation().directionTo(closestEnemy.location));
                    }
                    // Move toward enemy
                    Direction toEnemy = rc.getLocation().directionTo(closestEnemy.location);
                    tryMove(toEnemy);
                } else {
                    // Move towards enemy archon if known
                    int ex = rc.readBroadcast(2);
                    int ey = rc.readBroadcast(3);
                    if (ex != 0 || ey != 0) {
                        MapLocation enemyArchon = new MapLocation(ex, ey);
                        Direction toEnemyArchon = myLocation.directionTo(enemyArchon);
                        tryMove(toEnemyArchon);
                    } else {
                        // Move randomly for exploration
                        tryMove(randomDirection());
                    }
                }

                // Dodge bullets
                BulletInfo[] bullets = rc.senseNearbyBullets();
                for (BulletInfo b : bullets) {
                    if (willCollideWithMe(b)) {
                        Direction away = b.dir.opposite();
                        tryMove(away);
                        break;
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
        float distToRobot = bulletLocation.distanceTo(myLocation);
        float theta = propagationDirection.radiansBetween(directionToRobot);

        // If theta > 90 degrees, then the bullet is traveling away from us and we can break early
        if (Math.abs(theta) > Math.PI/2) {
            return false;
        }

        // distToRobot is our hypotenuse, theta is our angle, and we want to know this length of the opposite leg.
        // This corresponds to the line that is the path of the bullet.
        float perpendicularDist = (float)Math.abs(distToRobot * Math.sin(theta)); // soh cah toa :)

        return (perpendicularDist <= rc.getType().bodyRadius);
    }
}