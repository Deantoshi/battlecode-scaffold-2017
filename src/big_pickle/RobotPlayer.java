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

                // Use centralized spending policy for gardener hiring
                BulletSpending.spendPolicy();

                // Tank Fortress: archons stay relatively central but move strategically
                if (rc.getRoundNum() < 200) {
                    // Early game: move randomly but carefully to find good positioning
                    tryMoveCareful(randomDirection());
                } else {
                    // Later game: stay near center of our controlled area
                    moveToFortressCenter();
                }

                // Broadcast archon's location for fortress coordination
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

                // Use centralized spending policy for building meta-gardeners, tanks, and trees
                BulletSpending.spendPolicy();

                // Tank Fortress: gardeners position themselves strategically around archon
                maintainGardenerPosition(archonLoc);

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

                // Get archon location for fortress coordination
                MapLocation fortressCenter = getFortressCenter();

                // See if there are any nearby enemy robots
                RobotInfo[] robots = rc.senseNearbyRobots(-1, enemy);

                // Tank Fortress: soldiers provide defensive support with better targeting
                if (robots.length > 0) {
                    RobotInfo target = selectBestTarget(robots);
                    
                    float distToTarget = myLocation.distanceTo(target.location);
                    
                    // Attack if in range and prioritized
                    if (distToTarget <= 8 && rc.canFireSingleShot()) {
                        // Use triad shot against groups or high-value targets
                        if (shouldUseTriad(target, robots) && rc.canFireTriadShot()) {
                            rc.fireTriadShot(myLocation.directionTo(target.location));
                        } else {
                            rc.fireSingleShot(myLocation.directionTo(target.location));
                        }
                    }
                    
                    // Smart defensive movement
                    if (distToTarget > 10) {
                        // Move toward target but stay in defensive range
                        Direction toTarget = myLocation.directionTo(target.location);
                        if (myLocation.distanceTo(fortressCenter) < 12) {
                            tryMoveCareful(toTarget);
                        }
                    } else if (distToTarget < 4) {
                        // Too close - back up to maintain optimal range
                        Direction awayFromTarget = target.location.directionTo(myLocation);
                        tryMoveCareful(awayFromTarget);
                    } else {
                        // Strafe around target for better positioning
                        tryStrafe(target.location);
                    }
                } else {
                    // No enemies nearby - patrol defensively around fortress
                    patrolDefensiveArea(fortressCenter);
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

                // See if there are any enemy robots within striking range
                RobotInfo[] robots = rc.senseNearbyRobots(RobotType.LUMBERJACK.bodyRadius+GameConstants.LUMBERJACK_STRIKE_RADIUS, enemy);

                if(robots.length > 0 && !rc.hasAttacked()) {
                    // Use strike() to hit all nearby robots!
                    rc.strike();
                } else {
                    // Tank Fortress: lumberjacks focus on clearing trees near our fortress
                    MapLocation fortressCenter = getFortressCenter();
                    TreeInfo[] trees = rc.senseNearbyTrees(-1, Team.NEUTRAL);
                    
                    if (trees.length > 0 && !rc.hasAttacked()) {
                        // Prioritize trees near our fortress
                        TreeInfo closestTree = null;
                        float bestScore = Float.MAX_VALUE;
                        
                        for (TreeInfo tree : trees) {
                            float distToTree = rc.getLocation().distanceTo(tree.location);
                            float distToFortress = tree.location.distanceTo(fortressCenter);
                            float score = distToTree + distToFortress * 0.5f; // Weight toward fortress
                            
                            if (score < bestScore) {
                                bestScore = score;
                                closestTree = tree;
                            }
                        }
                        
                        if (closestTree != null) {
                            float distToTree = rc.getLocation().distanceTo(closestTree.location);
                            if (distToTree <= RobotType.LUMBERJACK.bodyRadius + 1.0f) {
                                rc.chop(closestTree.location);
                            } else {
                                Direction toTree = rc.getLocation().directionTo(closestTree.location);
                                tryMoveCareful(toTree);
                            }
                        }
                    } else {
                        // No close robots or trees, patrol near fortress
                        patrolDefensiveArea(fortressCenter);
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

                // Tank Fortress: scouts focus on vision and harassment, not front-line combat
                RobotInfo[] robots = rc.senseNearbyRobots(-1, enemy);
                if (robots.length > 0) {
                    // Target high-value or isolated enemies
                    RobotInfo target = selectBestScoutTarget(robots);
                    float distToTarget = myLocation.distanceTo(target.location);
                    
                    // Only engage very weak targets or at range
                    if (distToTarget <= 5 && (target.type == RobotType.ARCHON || target.type == RobotType.GARDENER)) {
                        if (rc.canFireSingleShot()) {
                            rc.fireSingleShot(myLocation.directionTo(target.location));
                        }
                    }
                    
                    // Generally avoid combat unless we have advantage
                    if (target.type == RobotType.ARCHON || target.type == RobotType.GARDENER) {
                        // Harass high-value targets cautiously
                        tryHarass(target.location);
                    } else {
                        // Run away from combat units
                        Direction awayFromEnemy = target.location.directionTo(myLocation);
                        tryMoveCareful(awayFromEnemy);
                    }
                } else {
                    // Explore the map looking for opportunities
                    exploreStrategically();
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
                MapLocation fortressCenter = getFortressCenter();

                // See if there are any nearby enemy robots
                RobotInfo[] robots = rc.senseNearbyRobots(-1, enemy);

                if (robots.length > 0) {
                    RobotInfo target = selectBestTarget(robots);
                    float distToTarget = myLocation.distanceTo(target.location);
                    
                    // Tank Fortress: tanks engage at medium range, protect fortress
                    if (distToTarget <= 6) {
                        // Use triad against multiple enemies or high-value targets
                        if (shouldUseTriad(target, robots) && rc.canFireTriadShot()) {
                            rc.fireTriadShot(myLocation.directionTo(target.location));
                        } else if (rc.canFireSingleShot()) {
                            rc.fireSingleShot(myLocation.directionTo(target.location));
                        }
                    }
                    
                    // Smart positioning - stay defensive but intercept threats
                    if (distToTarget > 8) {
                        // Move toward target but don't stray too far from fortress
                        if (myLocation.distanceTo(fortressCenter) < 10) {
                            Direction toTarget = myLocation.directionTo(target.location);
                            tryMoveCareful(toTarget);
                        } else {
                            // Move back toward fortress
                            Direction toFortress = myLocation.directionTo(fortressCenter);
                            tryMoveCareful(toFortress);
                        }
                    } else if (distToTarget < 3) {
                        // Too close - back up to optimal range
                        Direction awayFromTarget = target.location.directionTo(myLocation);
                        tryMoveCareful(awayFromTarget);
                    } else {
                        // Maintain optimal range by circling
                        tryCircle(target.location);
                    }
                } else {
                    // No enemies nearby - form defensive perimeter
                    maintainDefensivePerimeter(fortressCenter);
                }

                Clock.yield();

            } catch (Exception e) {
                System.out.println("Tank Exception");
                e.printStackTrace();
            }
        }
    }

    // Helper methods for improved Tank Fortress coordination
    
    private static MapLocation getFortressCenter() {
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

    private static RobotInfo selectBestTarget(RobotInfo[] enemies) {
        RobotInfo bestTarget = enemies[0];
        float bestScore = -1;
        
        for (RobotInfo enemy : enemies) {
            float score = 0;
            float dist = rc.getLocation().distanceTo(enemy.location);
            
            // Prioritize high-value targets
            if (enemy.type == RobotType.ARCHON) score += 1000;
            else if (enemy.type == RobotType.GARDENER) score += 500;
            else if (enemy.type == RobotType.TANK) score += 300;
            else if (enemy.type == RobotType.SOLDIER) score += 200;
            else if (enemy.type == RobotType.LUMBERJACK) score += 150;
            else if (enemy.type == RobotType.SCOUT) score += 50;
            
            // Factor in distance (closer is generally better)
            score += (10 - Math.min(10, dist)) * 10;
            
            // Factor in low health targets
            if (enemy.health < enemy.type.maxHealth * 0.3f) score += 100;
            
            if (score > bestScore) {
                bestScore = score;
                bestTarget = enemy;
            }
        }
        
        return bestTarget;
    }

    private static RobotInfo selectBestScoutTarget(RobotInfo[] enemies) {
        // Scouts prioritize economic targets they can harass safely
        RobotInfo bestTarget = enemies[0];
        float bestScore = -1;
        
        for (RobotInfo enemy : enemies) {
            float score = 0;
            float dist = rc.getLocation().distanceTo(enemy.location);
            
            // Prioritize isolated economic targets
            if (enemy.type == RobotType.GARDENER) score += 300;
            else if (enemy.type == RobotType.ARCHON) score += 500;
            
            // Only consider targets we can actually engage
            if (dist > 8) score -= 100;
            
            // Prefer isolated targets
            RobotInfo[] nearbyEnemies = rc.senseNearbyRobots(6, enemy.getTeam());
            score -= nearbyEnemies.length * 50;
            
            if (score > bestScore) {
                bestScore = score;
                bestTarget = enemy;
            }
        }
        
        return bestTarget;
    }

    private static boolean shouldUseTriad(RobotInfo primaryTarget, RobotInfo[] allEnemies) {
        // Use triad if multiple enemies are in range
        int enemiesInRange = 0;
        MapLocation myLocation = rc.getLocation();
        
        for (RobotInfo enemy : allEnemies) {
            float dist = myLocation.distanceTo(enemy.location);
            if (dist <= 8) enemiesInRange++;
        }
        
        return enemiesInRange >= 2 || primaryTarget.type == RobotType.ARCHON || primaryTarget.type == RobotType.TANK;
    }

    private static void moveToFortressCenter() throws GameActionException {
        MapLocation fortressCenter = getFortressCenter();
        Direction toCenter = rc.getLocation().directionTo(fortressCenter);
        tryMoveCareful(toCenter);
    }

    private static void maintainGardenerPosition(MapLocation archonLoc) throws GameActionException {
        float distToArchon = rc.getLocation().distanceTo(archonLoc);
        
        if (distToArchon > 8) {
            // Move closer to archon
            Direction toArchon = rc.getLocation().directionTo(archonLoc);
            tryMoveCareful(toArchon);
        } else if (distToArchon < 4) {
            // Move away from archon to avoid crowding
            Direction awayFromArchon = archonLoc.directionTo(rc.getLocation());
            tryMoveCareful(awayFromArchon);
        } else {
            // Maintain distance, move perpendicular for spreading out
            tryMoveCareful(randomDirection());
        }
    }

    private static void patrolDefensiveArea(MapLocation center) throws GameActionException {
        float distToCenter = rc.getLocation().distanceTo(center);
        
        if (distToCenter > 15) {
            // Move back toward center
            Direction toCenter = rc.getLocation().directionTo(center);
            tryMoveCareful(toCenter);
        } else if (distToCenter < 8) {
            // Move outward to expand patrol area
            Direction awayFromCenter = center.directionTo(rc.getLocation());
            tryMoveCareful(awayFromCenter);
        } else {
            // Patrol in current area
            tryMoveCareful(randomDirection());
        }
    }

    private static void maintainDefensivePerimeter(MapLocation center) throws GameActionException {
        float idealDist = 10;
        float distToCenter = rc.getLocation().distanceTo(center);
        
        if (distToCenter > idealDist + 2) {
            // Move toward center
            Direction toCenter = rc.getLocation().directionTo(center);
            tryMoveCareful(toCenter);
        } else if (distToCenter < idealDist - 2) {
            // Move away from center
            Direction awayFromCenter = center.directionTo(rc.getLocation());
            tryMoveCareful(awayFromCenter);
        } else {
            // Circle around center
            tryCircle(center);
        }
    }

    private static void tryCircle(MapLocation target) throws GameActionException {
        // Move perpendicular to target direction to circle
        Direction toTarget = rc.getLocation().directionTo(target);
        Direction circleDir = toTarget.rotateLeftDegrees(90);
        tryMoveCareful(circleDir);
    }

    private static void tryStrafe(MapLocation target) throws GameActionException {
        // Move perpendicular to target direction for strafing
        Direction toTarget = rc.getLocation().directionTo(target);
        Direction strafeDir = Math.random() < 0.5 ? 
            toTarget.rotateLeftDegrees(45) : toTarget.rotateRightDegrees(45);
        tryMoveCareful(strafeDir);
    }

    private static void tryHarass(MapLocation target) throws GameActionException {
        // Move toward target but keep distance
        float dist = rc.getLocation().distanceTo(target);
        if (dist > 6) {
            tryMoveCareful(rc.getLocation().directionTo(target));
        } else if (dist < 3) {
            tryMoveCareful(target.directionTo(rc.getLocation()));
        } else {
            // Circle around target
            tryCircle(target);
        }
    }

    private static void exploreStrategically() throws GameActionException {
        // Move toward unexplored areas while maintaining some awareness of fortress
        MapLocation fortressCenter = getFortressCenter();
        float distToCenter = rc.getLocation().distanceTo(fortressCenter);
        
        if (distToCenter > 20) {
            // Don't stray too far from fortress
            tryMoveCareful(rc.getLocation().directionTo(fortressCenter));
        } else {
            // Explore in current direction
            tryMoveCareful(randomDirection());
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