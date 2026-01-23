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

        // Store archon location for rush coordination
        archonLocation = rc.getLocation();
        archonLocationSet = true;

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {

                // Early Rush Horde: Use centralized spending policy for maximum early military production
                BulletSpending.spendPolicy();

                // Early Rush Horde: Archons support rush with aggressive positioning
                if (rc.getRoundNum() < 200) {
                    // Early game: position to maximize rush effectiveness
                    tryMoveAggressive(randomDirection());
                } else {
                    // Later game: maintain position to continue supporting rush
                    maintainRushPosition();
                }

                // Broadcast archon's location for rush coordination
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

                // Early Rush Horde: Use centralized spending policy - pure rush focus
                BulletSpending.spendPolicy();

                // Early Rush Horde: Gardeners position to maximize rush unit production
                maintainRushProductionPosition(archonLoc);

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

                // Early Rush Horde: Soldiers attack closest enemy regardless of type
                RobotInfo[] robots = rc.senseNearbyRobots(-1, enemy);

                if (robots.length > 0) {
                    RobotInfo target = selectClosestTarget(robots);
                    float distToTarget = myLocation.distanceTo(target.location);
                    
                    // Early Rush Horde: Attack closest target aggressively
                    if (distToTarget <= 6) {
                        if (rc.canFireSingleShot()) {
                            rc.fireSingleShot(myLocation.directionTo(target.location));
                        }
                        // Use triad shots if very close regardless of target type
                        if (distToTarget <= 4 && rc.canFireTriadShot()) {
                            rc.fireTriadShot(myLocation.directionTo(target.location));
                        }
                    }
                    
                    // Early Rush Horde: Pursue closest target relentlessly
                    if (distToTarget > 2) {
                        Direction toTarget = myLocation.directionTo(target.location);
                        tryMoveAggressive(toTarget);
                    }
                } else {
                    // No targets - rush toward enemy base
                    rushEnemyBase();
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

                // Early Rush Horde: Lumberjacks attack closest enemy and support rush
                MapLocation myLocation = rc.getLocation();
                
                // Priority 1: Strike closest enemy if in range
                RobotInfo[] enemyRobots = rc.senseNearbyRobots(2, enemy);
                
                if (enemyRobots.length > 0 && rc.canStrike()) {
                    // Strike regardless of target type for maximum pressure
                    rc.strike();
                } else {
                    // Priority 2: Chop trees that block rush paths
                    TreeInfo[] trees = rc.senseNearbyTrees(-1);
                    TreeInfo blockingTree = selectBlockingTree(trees);
                    
                    if (blockingTree != null) {
                        float distToTree = myLocation.distanceTo(blockingTree.location);
                        if (distToTree <= RobotType.LUMBERJACK.bodyRadius + 1.0f) {
                            rc.chop(blockingTree.location);
                        } else {
                            Direction toTree = myLocation.directionTo(blockingTree.location);
                            tryMoveAggressive(toTree);
                        }
                    } else {
                        // Priority 3: Rush toward enemy base
                        rushEnemyBase();
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

                // Early Rush Horde: Scouts attack closest enemy regardless of type
                RobotInfo[] robots = rc.senseNearbyRobots(-1, enemy);
                
                RobotInfo closestTarget = null;
                float minDistance = Float.MAX_VALUE;
                
                // Find closest enemy regardless of type
                for (RobotInfo robot : robots) {
                    float dist = myLocation.distanceTo(robot.location);
                    if (dist < minDistance) {
                        minDistance = dist;
                        closestTarget = robot;
                    }
                }
                
                if (closestTarget != null) {
                    // Early Rush Horde: Attack closest target relentlessly
                    if (minDistance <= 5) {
                        if (rc.canFireSingleShot()) {
                            rc.fireSingleShot(myLocation.directionTo(closestTarget.location));
                        }
                    }
                    
                    // Early Rush Horde: Pursue closest target aggressively
                    if (minDistance > 1) {
                        Direction toTarget = myLocation.directionTo(closestTarget.location);
                        tryMoveAggressive(toTarget);
                    } else {
                        // Close range - circle to maintain pressure
                        tryMoveAggressive(randomDirection());
                    }
                } else {
                    // No targets - rush toward enemy base
                    rushEnemyBase();
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

                // Early Rush Horde: Tanks attack closest enemy regardless of type
                RobotInfo[] robots = rc.senseNearbyRobots(-1, enemy);

                if (robots.length > 0) {
                    RobotInfo closestTarget = selectClosestTarget(robots);
                    float distToTarget = myLocation.distanceTo(closestTarget.location);
                    
                    // Early Rush Horde: Attack closest target with heavy firepower
                    if (distToTarget <= 8) {
                        if (rc.canFireSingleShot()) {
                            rc.fireSingleShot(myLocation.directionTo(closestTarget.location));
                        }
                        // Use pentad shots on close targets regardless of type
                        if (distToTarget <= 6 && rc.canFirePentadShot()) {
                            rc.firePentadShot(myLocation.directionTo(closestTarget.location));
                        }
                    }
                    
                    // Early Rush Horde: Move toward closest target
                    if (distToTarget > 4) {
                        Direction toTarget = myLocation.directionTo(closestTarget.location);
                        tryMoveAggressive(toTarget);
                    }
                } else {
                    // No targets - support rush
                    supportRush();
                }

                Clock.yield();

            } catch (Exception e) {
                System.out.println("Tank Exception");
                e.printStackTrace();
            }
        }
    }

    // Helper methods for Early Rush Horde strategy
    
    private static RobotInfo selectClosestTarget(RobotInfo[] enemies) {
        RobotInfo closestTarget = null;
        float minDistance = Float.MAX_VALUE;
        
        for (RobotInfo enemy : enemies) {
            float dist = rc.getLocation().distanceTo(enemy.location);
            if (dist < minDistance) {
                minDistance = dist;
                closestTarget = enemy;
            }
        }
        
        return closestTarget;
    }

    private static TreeInfo selectBlockingTree(TreeInfo[] trees) {
        TreeInfo blockingTree = null;
        float bestScore = Float.MIN_VALUE;
        
        for (TreeInfo tree : trees) {
            float score = 0;
            float dist = rc.getLocation().distanceTo(tree.location);
            
            // Priority for trees that block rush paths to enemy
            MapLocation myLoc = rc.getLocation();
            MapLocation[] enemyArchons = rc.getInitialArchonLocations(rc.getTeam().opponent());
            
            float minDistToEnemyArchons = Float.MAX_VALUE;
            for (MapLocation archon : enemyArchons) {
                float archonDist = tree.location.distanceTo(archon);
                minDistToEnemyArchons = Math.min(minDistToEnemyArchons, archonDist);
            }
            
            if (tree.team == Team.NEUTRAL) {
                // Trees near enemy base have highest priority for clearing
                if (minDistToEnemyArchons < 20) {
                    score = 400 - dist - minDistToEnemyArchons;
                } else {
                    score = 50 - dist;
                }
            } else if (tree.team == rc.getTeam().opponent()) {
                // Enemy trees are good targets
                score = 200 - dist;
            }
            
            if (score > bestScore) {
                bestScore = score;
                blockingTree = tree;
            }
        }
        
        return blockingTree;
    }

    private static void maintainRushPosition() throws GameActionException {
        MapLocation center = getRushCenter();
        float distToCenter = rc.getLocation().distanceTo(center);
        
        // Archons maintain position to support rush but stay mobile
        if (distToCenter > 10) {
            Direction toCenter = rc.getLocation().directionTo(center);
            tryMoveAggressive(toCenter);
        } else {
            // Aggressive repositioning for rush support
            if (Math.random() < 0.6) {
                tryMoveAggressive(randomDirection());
            }
        }
    }

    private static void maintainRushProductionPosition(MapLocation archonLoc) throws GameActionException {
        float distToArchon = rc.getLocation().distanceTo(archonLoc);
        
        // Position for optimal rush unit production
        if (distToArchon > 15) {
            Direction toArchon = rc.getLocation().directionTo(archonLoc);
            tryMoveAggressive(toArchon);
        } else if (distToArchon < 5) {
            Direction awayFromArchon = archonLoc.directionTo(rc.getLocation());
            tryMoveAggressive(awayFromArchon);
        } else {
            // Reposition frequently for better rush unit angles
            if (Math.random() < 0.4) {
                tryMoveAggressive(randomDirection());
            }
        }
    }

    private static void rushEnemyBase() throws GameActionException {
        // Move toward enemy base for rush
        MapLocation[] enemyArchons = rc.getInitialArchonLocations(rc.getTeam().opponent());
        MapLocation myLoc = rc.getLocation();
        
        MapLocation closestArchon = enemyArchons[0];
        float minDist = myLoc.distanceTo(closestArchon);
        
        for (MapLocation archon : enemyArchons) {
            float dist = myLoc.distanceTo(archon);
            if (dist < minDist) {
                minDist = dist;
                closestArchon = archon;
            }
        }
        
        Direction toEnemy = myLoc.directionTo(closestArchon);
        tryMoveAggressive(toEnemy);
    }

    private static void supportRush() throws GameActionException {
        // Tanks support rush by moving toward enemy locations
        MapLocation[] enemyArchons = rc.getInitialArchonLocations(rc.getTeam().opponent());
        MapLocation myLoc = rc.getLocation();
        
        MapLocation closestArchon = enemyArchons[0];
        float minDist = myLoc.distanceTo(closestArchon);
        
        for (MapLocation archon : enemyArchons) {
            float dist = myLoc.distanceTo(archon);
            if (dist < minDist) {
                minDist = dist;
                closestArchon = archon;
            }
        }
        
        // Tanks move toward enemy to support rush
        if (minDist > 20) {
            Direction toEnemy = myLoc.directionTo(closestArchon);
            tryMoveAggressive(toEnemy);
        } else {
            // Close to enemy - aggressive positioning
            if (Math.random() < 0.5) {
                tryMoveAggressive(randomDirection());
            }
        }
    }

    private static MapLocation getRushCenter() {
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

    /**
     * Returns a random Direction
     * @return a random Direction
     */
    static Direction randomDirection() {
        return new Direction((float)Math.random() * 2 * (float)Math.PI);
    }

    /**
     * Early Rush Horde: Very aggressive movement for maximum pressure
     * Extremely aggressive movement with minimal safety checks for fastest rush.
     *
     * @param dir The intended direction of movement
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryMoveAggressive(Direction dir) throws GameActionException {
        return tryMove(dir,15,2); // Very aggressive movement with minimal angle checks
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