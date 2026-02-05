package claude_opus_4_5_champion_0;
import battlecode.common.*;

/**
 * Scout Swarm Harass Strategy
 * 
 * Philosophy: Mass cheap scouts to harass enemy gardeners and economy
 * while collecting bullets from neutral trees. Win through economic
 * disruption and opportunistic VP pushes.
 * 
 * Scouts use 1.25 stride to outmaneuver enemies, target gardeners,
 * and flee from soldiers. Collect neutral tree bullets.
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
        BulletSpending.init(rc);

        // Here, we've separated the controls into a different method for each RobotType.
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

                // Water trees to maintain healthy economy
                TreeInfo[] trees = rc.senseNearbyTrees(-1, rc.getTeam());
                if (trees.length > 0) {
                    // Find the tree with lowest health percentage
                    TreeInfo lowestHealthTree = null;
                    float lowestHealthPercent = 1.0f;
                    for (TreeInfo tree : trees) {
                        float healthPercent = tree.health / tree.maxHealth;
                        if (healthPercent < lowestHealthPercent && rc.canWater(tree.ID)) {
                            lowestHealthTree = tree;
                            lowestHealthPercent = healthPercent;
                        }
                    }
                    // Water the tree if it needs it
                    if (lowestHealthTree != null && lowestHealthPercent < 0.8f) {
                        rc.water(lowestHealthTree.ID);
                    }
                }

                // Centralized spend policy (plant/build/donate)
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
                    // And we have enough bullets, and haven't attacked yet this turn...
                    if (rc.canFireSingleShot()) {
                        // ...Then fire a bullet in the direction of the enemy.
                        rc.fireSingleShot(rc.getLocation().directionTo(robots[0].location));
                    }
                }

                // Move randomly
                tryMove(randomDirection());

                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println("Soldier Exception");
                e.printStackTrace();
            }
        }
    }

    /**
     * Scout: Hit-and-run harassment specialist
     * - Target enemy gardeners first (economic disruption)
     * - Collect bullets from neutral trees (shake)
     * - Flee from soldiers and tanks
     * - Use 1.25 stride advantage to outmaneuver enemies
     */
    static void runScout() throws GameActionException {
        System.out.println("I'm a scout! Time to harass!");
        Team enemy = rc.getTeam().opponent();
        Team myTeam = rc.getTeam();

        while (true) {
            try {
                MapLocation myLocation = rc.getLocation();

                // Priority 1: Collect bullets from neutral trees (shake)
                TreeInfo[] neutralTrees = rc.senseNearbyTrees(-1, Team.NEUTRAL);
                for (TreeInfo tree : neutralTrees) {
                    if (tree.containedBullets > 0 && rc.canShake(tree.ID)) {
                        rc.shake(tree.ID);
                        break;  // Can only shake once per turn
                    }
                }

                // Priority 2: Find enemies, especially gardeners
                RobotInfo[] enemies = rc.senseNearbyRobots(-1, enemy);
                RobotInfo targetGardener = null;
                RobotInfo targetArchon = null;
                RobotInfo dangerousEnemy = null;
                
                for (RobotInfo r : enemies) {
                    if (r.type == RobotType.GARDENER) {
                        targetGardener = r;
                    } else if (r.type == RobotType.ARCHON) {
                        targetArchon = r;
                    } else if (r.type == RobotType.SOLDIER || r.type == RobotType.TANK || r.type == RobotType.LUMBERJACK) {
                        float dist = myLocation.distanceTo(r.location);
                        if (dist < 5f) {  // Close dangerous enemy
                            dangerousEnemy = r;
                        }
                    }
                }

                // Priority 3: Decide movement - flee from danger or chase targets
                boolean shouldFlee = dangerousEnemy != null;
                RobotInfo primaryTarget = targetGardener != null ? targetGardener : targetArchon;
                
                if (shouldFlee && dangerousEnemy != null) {
                    // Flee from dangerous enemy (use 1.25 stride to escape)
                    Direction awayFromDanger = dangerousEnemy.location.directionTo(myLocation);
                    tryMove(awayFromDanger);
                } else if (primaryTarget != null) {
                    // Move toward gardener/archon for harassment
                    Direction toTarget = myLocation.directionTo(primaryTarget.location);
                    float distToTarget = myLocation.distanceTo(primaryTarget.location);
                    
                    // If close enough, try to fire at them
                    if (distToTarget < 4f && rc.canFireSingleShot()) {
                        rc.fireSingleShot(toTarget);
                    }
                    
                    // Kite: stay at medium range, not too close
                    if (distToTarget > 3f) {
                        tryMove(toTarget);
                    } else if (distToTarget < 2f) {
                        // Too close, back off slightly
                        tryMove(toTarget.opposite());
                    }
                } else if (enemies.length > 0) {
                    // Fire at any enemy if we can
                    if (rc.canFireSingleShot()) {
                        rc.fireSingleShot(myLocation.directionTo(enemies[0].location));
                    }
                    // Kite away after firing
                    Direction away = enemies[0].location.directionTo(myLocation);
                    tryMove(away);
                } else {
                    // No enemies visible - explore toward neutral trees or enemy archon
                    TreeInfo nearestNeutralWithBullets = null;
                    for (TreeInfo tree : neutralTrees) {
                        if (tree.containedBullets > 0) {
                            nearestNeutralWithBullets = tree;
                            break;
                        }
                    }
                    
                    if (nearestNeutralWithBullets != null) {
                        // Move toward neutral tree with bullets
                        Direction toTree = myLocation.directionTo(nearestNeutralWithBullets.location);
                        tryMove(toTree);
                    } else {
                        // Scout toward enemy territory (use initial archon locations)
                        MapLocation[] enemyArchons = rc.getInitialArchonLocations(enemy);
                        if (enemyArchons.length > 0) {
                            Direction toEnemy = myLocation.directionTo(enemyArchons[0]);
                            tryMove(toEnemy);
                        } else {
                            tryMove(randomDirection());
                        }
                    }
                }

                Clock.yield();

            } catch (Exception e) {
                System.out.println("Scout Exception");
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
                        MapLocation myLocation = rc.getLocation();
                        MapLocation enemyLocation = robots[0].getLocation();
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
