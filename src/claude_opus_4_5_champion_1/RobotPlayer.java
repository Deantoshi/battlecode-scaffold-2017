package claude_opus_4_5_champion_1;
import battlecode.common.*;

/**
 * Late Game Titan Strategy
 * 
 * Philosophy: Turtle with economy early, then transition to unstoppable tank army.
 * Maximize economy for ~500 rounds, then overwhelm with tanks while VP provides 
 * backup win condition.
 * 
 * Engagement Style: Passive early, overwhelming late. Tanks push as unstoppable deathball.
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
            case TANK:
                runTank();
                break;
        }
	}

    static void runArchon() throws GameActionException {
        System.out.println("I'm an archon! Building economy for tank army.");

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {

                // Centralized spend policy (hire/build/donate)
                BulletSpending.spendPolicy();

                // Archon stays relatively safe - avoid enemies
                RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
                if (enemies.length > 0) {
                    // Move away from nearest enemy
                    Direction away = enemies[0].location.directionTo(rc.getLocation());
                    tryMove(away);
                } else {
                    // Move slowly, stay defensive
                    if (Math.random() < 0.3) {
                        tryMove(randomDirection());
                    }
                }

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
        System.out.println("I'm a gardener! Building tree farm.");

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {

                // Listen for home archon's location
                int xPos = rc.readBroadcast(0);
                int yPos = rc.readBroadcast(1);
                MapLocation archonLoc = new MapLocation(xPos,yPos);

                // Water trees to maintain healthy economy - CRITICAL for tree income
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
                    // Water the tree if it needs it (critical for bullet income)
                    if (lowestHealthTree != null && lowestHealthPercent < 0.95f) {
                        rc.water(lowestHealthTree.ID);
                    }
                }

                // Centralized spend policy (plant/build/donate)
                BulletSpending.spendPolicy();

                // Gardener positioning: Stay near archon for protection, but spread out for tree farming
                MapLocation myLocation = rc.getLocation();
                float distToArchon = myLocation.distanceTo(archonLoc);
                
                // If too far from archon, move closer
                if (distToArchon > 12f) {
                    Direction toArchon = myLocation.directionTo(archonLoc);
                    tryMove(toArchon);
                } else if (distToArchon < 6f && trees.length < 5) {
                    // Move away from archon to spread out tree farms
                    Direction awayFromArchon = archonLoc.directionTo(myLocation);
                    tryMove(awayFromArchon);
                } else {
                    // Move randomly to find good planting spots
                    if (Math.random() < 0.2) {
                        tryMove(randomDirection());
                    }
                }

                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println("Gardener Exception");
                e.printStackTrace();
            }
        }
    }

    /**
     * Tank: The unstoppable deathball unit
     * - Move toward enemy as a group (deathball)
     * - High HP (200) allows sustained pushing
     * - Can body-damage trees for path clearing
     * - Fire pentad shots for maximum destruction
     */
    static void runTank() throws GameActionException {
        System.out.println("I'm a tank! Time to push!");
        Team enemy = rc.getTeam().opponent();
        Team myTeam = rc.getTeam();

        while (true) {
            try {
                MapLocation myLocation = rc.getLocation();
                
                // Find enemy targets
                RobotInfo[] enemies = rc.senseNearbyRobots(-1, enemy);
                
                // Priority targeting: Archon > Gardener > Combat units
                RobotInfo target = null;
                RobotInfo archonTarget = null;
                RobotInfo gardenerTarget = null;
                
                for (RobotInfo r : enemies) {
                    if (r.type == RobotType.ARCHON) {
                        archonTarget = r;
                    } else if (r.type == RobotType.GARDENER) {
                        if (gardenerTarget == null || 
                            myLocation.distanceTo(r.location) < myLocation.distanceTo(gardenerTarget.location)) {
                            gardenerTarget = r;
                        }
                    } else if (target == null) {
                        target = r;
                    }
                }
                
                // Select best target
                if (archonTarget != null) {
                    target = archonTarget;
                } else if (gardenerTarget != null) {
                    target = gardenerTarget;
                }
                
                // Fire at enemies if we have a target
                if (target != null) {
                    Direction toTarget = myLocation.directionTo(target.location);
                    float dist = myLocation.distanceTo(target.location);
                    
                    // Use pentad at close range for max damage, triad at medium range
                    if (dist < 5f && rc.canFirePentadShot()) {
                        // Check for friendly fire
                        RobotInfo[] allies = rc.senseNearbyRobots(dist - 0.5f, myTeam);
                        boolean clearShot = true;
                        for (RobotInfo ally : allies) {
                            Direction toAlly = myLocation.directionTo(ally.location);
                            float angleDiff = Math.abs(toTarget.degreesBetween(toAlly));
                            if (angleDiff < 20f && myLocation.distanceTo(ally.location) < dist) {
                                clearShot = false;
                                break;
                            }
                        }
                        if (clearShot) {
                            rc.firePentadShot(toTarget);
                        }
                    } else if (dist < 7f && rc.canFireTriadShot()) {
                        // Check for friendly fire
                        RobotInfo[] allies = rc.senseNearbyRobots(dist - 0.5f, myTeam);
                        boolean clearShot = true;
                        for (RobotInfo ally : allies) {
                            Direction toAlly = myLocation.directionTo(ally.location);
                            float angleDiff = Math.abs(toTarget.degreesBetween(toAlly));
                            if (angleDiff < 15f && myLocation.distanceTo(ally.location) < dist) {
                                clearShot = false;
                                break;
                            }
                        }
                        if (clearShot) {
                            rc.fireTriadShot(toTarget);
                        }
                    } else if (rc.canFireSingleShot()) {
                        rc.fireSingleShot(toTarget);
                    }
                    
                    // Move toward target (tanks can push through trees)
                    tryMove(toTarget);
                } else {
                    // No enemies visible - push toward enemy territory
                    MapLocation[] enemyArchons = rc.getInitialArchonLocations(enemy);
                    if (enemyArchons.length > 0) {
                        // Move toward closest enemy starting position
                        MapLocation closestEnemy = enemyArchons[0];
                        float closestDist = myLocation.distanceTo(closestEnemy);
                        for (MapLocation loc : enemyArchons) {
                            float dist = myLocation.distanceTo(loc);
                            if (dist < closestDist) {
                                closestDist = dist;
                                closestEnemy = loc;
                            }
                        }
                        Direction toEnemy = myLocation.directionTo(closestEnemy);
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

    static void runSoldier() throws GameActionException {
        System.out.println("I'm a soldier!");
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

    static void runScout() throws GameActionException {
        System.out.println("I'm a scout!");
        Team enemy = rc.getTeam().opponent();

        while (true) {
            try {
                MapLocation myLocation = rc.getLocation();

                // Collect bullets from neutral trees (shake)
                TreeInfo[] neutralTrees = rc.senseNearbyTrees(-1, Team.NEUTRAL);
                for (TreeInfo tree : neutralTrees) {
                    if (tree.containedBullets > 0 && rc.canShake(tree.ID)) {
                        rc.shake(tree.ID);
                        break;
                    }
                }

                // Find enemies
                RobotInfo[] enemies = rc.senseNearbyRobots(-1, enemy);
                
                if (enemies.length > 0) {
                    // Fire at enemy if we can
                    if (rc.canFireSingleShot()) {
                        rc.fireSingleShot(myLocation.directionTo(enemies[0].location));
                    }
                    // Kite away
                    Direction away = enemies[0].location.directionTo(myLocation);
                    tryMove(away);
                } else {
                    // Scout toward neutral trees or enemy
                    TreeInfo nearestWithBullets = null;
                    for (TreeInfo tree : neutralTrees) {
                        if (tree.containedBullets > 0) {
                            nearestWithBullets = tree;
                            break;
                        }
                    }
                    
                    if (nearestWithBullets != null) {
                        Direction toTree = myLocation.directionTo(nearestWithBullets.location);
                        tryMove(toTree);
                    } else {
                        MapLocation[] enemyArchons = rc.getInitialArchonLocations(enemy);
                        if (enemyArchons.length > 0) {
                            tryMove(myLocation.directionTo(enemyArchons[0]));
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
