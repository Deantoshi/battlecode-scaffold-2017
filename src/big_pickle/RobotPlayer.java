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

        // Store archon location for strategic coordination
        archonLocation = rc.getLocation();
        archonLocationSet = true;

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {

                // Hybrid Assassin: Use centralized spending policy for economic warfare
                BulletSpending.spendPolicy();

                // Hybrid Assassin: Archons support economic warfare with strategic positioning
                if (rc.getRoundNum() < 150) {
                    // Early game: find good position for supporting raids
                    tryMoveAggressive(randomDirection());
                } else {
                    // Later game: maintain position to support VP pressure
                    maintainStrategicPosition();
                }

                // Broadcast archon's location for economic raid coordination
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

                // Hybrid Assassin: Use centralized spending policy - economic warfare focus
                BulletSpending.spendPolicy();

                // Hybrid Assassin: Gardeners position to support military production
                maintainProductionPosition(archonLoc);

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

                // Hybrid Assassin: Soldiers support economic warfare with aggressive targeting
                RobotInfo[] robots = rc.senseNearbyRobots(-1, enemy);

                if (robots.length > 0) {
                    RobotInfo target = selectEconomicTarget(robots);
                    float distToTarget = myLocation.distanceTo(target.location);
                    
                    // Hybrid Assassin: Attack economic targets aggressively
                    if (distToTarget <= 6) {
                        if (rc.canFireSingleShot()) {
                            rc.fireSingleShot(myLocation.directionTo(target.location));
                        }
                        // Use triad shots if multiple economic targets nearby
                        if (distToTarget <= 4 && rc.canFireTriadShot() && 
                            target.type == RobotType.GARDENER) {
                            rc.fireTriadShot(myLocation.directionTo(target.location));
                        }
                    }
                    
                    // Hybrid Assassin: Pursue economic targets aggressively
                    if (distToTarget > 4) {
                        Direction toTarget = myLocation.directionTo(target.location);
                        tryMoveAggressive(toTarget);
                    }
                } else {
                    // No economic targets - hunt enemy economy
                    huntEnemyEconomy();
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

                // Hybrid Assassin: Lumberjacks hunt enemy economy and clear strategic trees
                MapLocation myLocation = rc.getLocation();
                
                // Priority 1: Strike enemy economic units if in range
                RobotInfo[] enemyRobots = rc.senseNearbyRobots(2, enemy);
                RobotInfo economicTarget = null;
                for (RobotInfo robot : enemyRobots) {
                    if (robot.type == RobotType.GARDENER || robot.type == RobotType.ARCHON) {
                        economicTarget = robot;
                        break;
                    }
                }
                
                if (economicTarget != null && rc.canStrike()) {
                    rc.strike();
                } else {
                    // Priority 2: Chop trees near enemy base or blocking our raids
                    TreeInfo[] trees = rc.senseNearbyTrees(-1);
                    TreeInfo targetTree = selectStrategicTree(trees);
                    
                    if (targetTree != null) {
                        float distToTree = myLocation.distanceTo(targetTree.location);
                        if (distToTree <= RobotType.LUMBERJACK.bodyRadius + 1.0f) {
                            rc.chop(targetTree.location);
                        } else {
                            Direction toTree = myLocation.directionTo(targetTree.location);
                            tryMoveAggressive(toTree);
                        }
                    } else {
                        // Priority 3: Hunt enemy economy locations
                        huntEnemyEconomy();
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

                // Hybrid Assassin: Scouts are primary economic assassins - hunt gardeners aggressively
                RobotInfo[] robots = rc.senseNearbyRobots(-1, enemy);
                
                // Priority 1: Hunt enemy gardeners (primary economic targets)
                RobotInfo gardenerTarget = null;
                RobotInfo archonTarget = null;
                
                for (RobotInfo robot : robots) {
                    if (robot.type == RobotType.GARDENER) {
                        gardenerTarget = robot;
                        break; // Gardeners are highest priority
                    }
                    if (robot.type == RobotType.ARCHON && archonTarget == null) {
                        archonTarget = robot;
                    }
                }
                
                RobotInfo target = (gardenerTarget != null) ? gardenerTarget : archonTarget;
                
                if (target != null) {
                    float distToTarget = myLocation.distanceTo(target.location);
                    
                    // Hybrid Assassin: Attack economic targets relentlessly
                    if (distToTarget <= 5) {
                        if (rc.canFireSingleShot()) {
                            rc.fireSingleShot(myLocation.directionTo(target.location));
                        }
                    }
                    
                    // Hybrid Assassin: Pursue economic targets aggressively
                    if (distToTarget > 2) {
                        Direction toTarget = myLocation.directionTo(target.location);
                        tryMoveAggressive(toTarget);
                    } else {
                        // Close range - circle to maintain attack position
                        tryMoveAggressive(randomDirection());
                    }
                } else {
                    // No economic targets - search aggressively for enemy economy
                    searchForEnemyEconomy();
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

                // Hybrid Assassin: Tanks support economic warfare with heavy firepower
                RobotInfo[] robots = rc.senseNearbyRobots(-1, enemy);

                if (robots.length > 0) {
                    RobotInfo target = selectEconomicTarget(robots);
                    float distToTarget = myLocation.distanceTo(target.location);
                    
                    // Hybrid Assassin: Attack economic targets with heavy firepower
                    if (distToTarget <= 8) {
                        if (rc.canFireSingleShot()) {
                            rc.fireSingleShot(myLocation.directionTo(target.location));
                        }
                        // Use pentad shots on economic targets for maximum damage
                        if (distToTarget <= 6 && rc.canFirePentadShot() && 
                            target.type == RobotType.GARDENER) {
                            rc.firePentadShot(myLocation.directionTo(target.location));
                        }
                    }
                    
                    // Hybrid Assassin: Move toward economic targets
                    if (distToTarget > 6) {
                        Direction toTarget = myLocation.directionTo(target.location);
                        tryMoveAggressive(toTarget);
                    }
                } else {
                    // No targets - support economic raids
                    supportEconomicRaids();
                }

                Clock.yield();

            } catch (Exception e) {
                System.out.println("Tank Exception");
                e.printStackTrace();
            }
        }
    }

    // Helper methods for Hybrid Assassin economic warfare strategy
    
    private static RobotInfo selectEconomicTarget(RobotInfo[] enemies) {
        RobotInfo bestTarget = enemies[0];
        float bestScore = Float.MIN_VALUE;
        
        for (RobotInfo enemy : enemies) {
            float score = 0;
            float dist = rc.getLocation().distanceTo(enemy.location);
            
            // Priority scoring for economic warfare
            if (enemy.type == RobotType.GARDENER) {
                score = 1000 - dist; // Highest priority
            } else if (enemy.type == RobotType.ARCHON) {
                score = 800 - dist; // High priority
            } else if (enemy.type == RobotType.SCOUT) {
                score = 300 - dist; // Medium priority
            } else if (enemy.type == RobotType.LUMBERJACK) {
                score = 200 - dist; // Lower priority
            } else {
                score = 100 - dist; // Lowest priority
            }
            
            if (score > bestScore) {
                bestScore = score;
                bestTarget = enemy;
            }
        }
        
        return bestTarget;
    }

    private static TreeInfo selectStrategicTree(TreeInfo[] trees) {
        TreeInfo bestTree = null;
        float bestScore = Float.MIN_VALUE;
        
        for (TreeInfo tree : trees) {
            float score = 0;
            float dist = rc.getLocation().distanceTo(tree.location);
            
            // Priority for trees near suspected enemy locations
            MapLocation myLoc = rc.getLocation();
            MapLocation[] enemyArchons = rc.getInitialArchonLocations(rc.getTeam().opponent());
            
            float minDistToEnemyArchons = Float.MAX_VALUE;
            for (MapLocation archon : enemyArchons) {
                float archonDist = tree.location.distanceTo(archon);
                minDistToEnemyArchons = Math.min(minDistToEnemyArchons, archonDist);
            }
            
            if (tree.team == Team.NEUTRAL) {
                // Neutral trees near enemy base have highest priority
                if (minDistToEnemyArchons < 15) {
                    score = 500 - dist - minDistToEnemyArchons;
                } else {
                    score = 100 - dist;
                }
            } else if (tree.team == rc.getTeam().opponent()) {
                // Enemy trees are always good targets
                score = 300 - dist;
            }
            
            if (score > bestScore) {
                bestScore = score;
                bestTree = tree;
            }
        }
        
        return bestTree;
    }

    private static void maintainStrategicPosition() throws GameActionException {
        MapLocation center = getEconomicCenter();
        float distToCenter = rc.getLocation().distanceTo(center);
        
        // Archons maintain position to support raids but stay mobile
        if (distToCenter > 8) {
            Direction toCenter = rc.getLocation().directionTo(center);
            tryMoveAggressive(toCenter);
        } else {
            // Strategic repositioning occasionally
            if (Math.random() < 0.4) {
                tryMoveAggressive(randomDirection());
            }
        }
    }

    private static void maintainProductionPosition(MapLocation archonLoc) throws GameActionException {
        float distToArchon = rc.getLocation().distanceTo(archonLoc);
        
        // Position for optimal military production while maintaining some defense
        if (distToArchon > 12) {
            Direction toArchon = rc.getLocation().directionTo(archonLoc);
            tryMoveAggressive(toArchon);
        } else if (distToArchon < 4) {
            Direction awayFromArchon = archonLoc.directionTo(rc.getLocation());
            tryMoveAggressive(awayFromArchon);
        } else {
            // Reposition occasionally for better production angles
            if (Math.random() < 0.2) {
                tryMoveAggressive(randomDirection());
            }
        }
    }

    private static void huntEnemyEconomy() throws GameActionException {
        // Move toward suspected enemy economic locations
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

    private static void searchForEnemyEconomy() throws GameActionException {
        MapLocation[] enemyArchons = rc.getInitialArchonLocations(rc.getTeam().opponent());
        MapLocation myLoc = rc.getLocation();
        
        // Scouts search aggressively for enemy economic locations
        MapLocation targetArchon = enemyArchons[(int)(Math.random() * enemyArchons.length)];
        Direction toTarget = myLoc.directionTo(targetArchon);
        
        // Add some randomness to search pattern
        if (Math.random() < 0.3) {
            toTarget = toTarget.rotateLeftDegrees((float)(Math.random() * 90 - 45));
        }
        
        tryMoveAggressive(toTarget);
    }

    private static void supportEconomicRaids() throws GameActionException {
        // Tanks support raids by moving toward enemy locations
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
        
        // Tanks move toward enemy to support raids
        if (minDist > 15) {
            Direction toEnemy = myLoc.directionTo(closestArchon);
            tryMoveAggressive(toEnemy);
        } else {
            // Close to enemy - position strategically
            if (Math.random() < 0.3) {
                tryMoveAggressive(randomDirection());
            }
        }
    }

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

    /**
     * Returns a random Direction
     * @return a random Direction
     */
    static Direction randomDirection() {
        return new Direction((float)Math.random() * 2 * (float)Math.PI);
    }

    /**
     * Hybrid Assassin: Aggressive movement for economic warfare
     * More aggressive movement with fewer safety checks for faster raids.
     *
     * @param dir The intended direction of movement
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryMoveAggressive(Direction dir) throws GameActionException {
        return tryMove(dir,20,3); // More aggressive movement with fewer angle checks
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