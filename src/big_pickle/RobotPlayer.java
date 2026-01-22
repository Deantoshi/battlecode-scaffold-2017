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
        }
	}

    static void runArchon() throws GameActionException {
        System.out.println("I'm an archon!");

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {

                // Generate a random direction
                Direction dir = randomDirection();

                // Randomly attempt to build a gardener in this direction
                if (rc.canHireGardener(dir) && Math.random() < .01) {
                    rc.hireGardener(dir);
                }

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

                // Check how many trees we have to defend
                TreeInfo[] myTrees = rc.senseNearbyTrees(20f, rc.getTeam());
                boolean haveAnyTrees = myTrees.length > 0; // ANY trees need immediate defense
                boolean manyTrees = myTrees.length > 3; // Critical mass needs more defense
                
                // Generate a random direction
                Direction dir = randomDirection();

                // URGENT PRIORITY: Build soldiers immediately for ANY tree defense
                // Much lower threshold (40 bullets) for fastest possible deployment
                int soldierBuildCost = 40; // Further reduced for immediate response
                boolean canAffordSoldier = rc.getTeamBullets() >= soldierBuildCost;
                
                // EMERGENCY DEFENSE: Build soldiers immediately if we have any trees and can afford them
                if (haveAnyTrees && canAffordSoldier && rc.canBuildRobot(RobotType.SOLDIER, dir)) {
                    rc.buildRobot(RobotType.SOLDIER, dir);
                    System.out.println("EMERGENCY: Built soldier to defend " + myTrees.length + " trees! Bullets: " + rc.getTeamBullets());
                }
                // NORMAL DEFENSE: Build soldiers even if no trees yet (preemptive protection)
                else if (canAffordSoldier && rc.canBuildRobot(RobotType.SOLDIER, dir)) {
                    rc.buildRobot(RobotType.SOLDIER, dir);
                    System.out.println("PREEMPTIVE: Built soldier before trees appear! Bullets: " + rc.getTeamBullets());
                }
                // CRITICAL: Only plant trees if we have enough bullets AND no trees to defend yet
                else if (rc.getTeamBullets() > 80 && rc.canPlantTree(dir) && !haveAnyTrees) {
                    rc.plantTree(dir);
                    System.out.println("First tree planted! Will start soldier production immediately. Bullets: " + rc.getTeamBullets());
                }
                // Randomly attempt to build a lumberjack in this direction
                else if (rc.canBuildRobot(RobotType.LUMBERJACK, dir) && Math.random() < .01 && rc.isBuildReady()) {
                    rc.buildRobot(RobotType.LUMBERJACK, dir);
                }

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
        Team myTeam = rc.getTeam();

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {
                MapLocation myLocation = rc.getLocation();

                // See if there are any nearby enemy robots
                RobotInfo[] robots = rc.senseNearbyRobots(-1, enemy);

                // Debug bullet count and tree status
                if (rc.getRoundNum() % 50 == 0) {
                    TreeInfo[] myTrees = rc.senseNearbyTrees(20f, myTeam);
                    System.out.println("Soldier bullets: " + rc.getTeamBullets() + ", Nearby trees: " + myTrees.length);
                }

                // SIMPLIFIED TREE DEFENSE: Focus exclusively on lumberjack threats
                if (robots.length > 0) {
                    System.out.println("Soldier sees " + robots.length + " enemies!");
                    
                    // Find highest priority target: Lumberjacks near trees > everything else
                    RobotInfo highestPriorityTarget = null;
                    float highestPriorityScore = -9999f;
                    
                    // Scan for our trees to assess threats
                    TreeInfo[] nearbyTrees = rc.senseNearbyTrees(15f, myTeam);
                    System.out.println("Tree defense scan: " + nearbyTrees.length + " trees in range");
                    
                    for (RobotInfo enemyRobot : robots) {
                        float score = 0f;
                        
                        // LUMBERJACKS get MASSIVE priority if near our trees (they destroy our economy)
                        if (enemyRobot.type == RobotType.LUMBERJACK) {
                            // Count trees this lumberjack threatens
                            int treesThreatened = 0;
                            float minTreeDist = 999f;
                            for (TreeInfo tree : nearbyTrees) {
                                float distToTree = enemyRobot.location.distanceTo(tree.location);
                                if (distToTree < 8f) { // Lumberjack attack range + buffer
                                    treesThreatened++;
                                    if (distToTree < minTreeDist) {
                                        minTreeDist = distToTree;
                                    }
                                }
                            }
                            // MASSIVE priority for tree-destroying lumberjacks
                            score += 10000f + (treesThreatened * 5000f) - (minTreeDist * 200f);
                            System.out.println("CRITICAL THREAT: LUMBERJACK threatening " + treesThreatened + " trees! Score: " + score);
                        }
                        
                        // ARCHONS get high priority (victory condition)
                        else if (enemyRobot.type == RobotType.ARCHON) {
                            score += 1000f;
                        }
                        
                        // GARDENERS get good priority (economy)
                        else if (enemyRobot.type == RobotType.GARDENER) {
                            score += 500f;
                        }
                        
                        // SOLDIERS get base priority
                        else if (enemyRobot.type == RobotType.SOLDIER) {
                            score += 200f;
                        }
                        
                        // Distance factor (closer = higher priority)
                        float distance = myLocation.distanceTo(enemyRobot.location);
                        score -= distance * 50f;
                        
                        if (score > highestPriorityScore) {
                            highestPriorityScore = score;
                            highestPriorityTarget = enemyRobot;
                        }
                    }
                    
                    RobotInfo target = highestPriorityTarget;
                    float closestDist = myLocation.distanceTo(target.location);
                    Direction toEnemy = myLocation.directionTo(target.location);
                    
                    System.out.println("TARGETING: " + target.type + " Priority: " + highestPriorityScore + " Distance: " + closestDist);
                    
                    // AGGRESSIVE positioning: Get very close for maximum accuracy
                    float moveThreshold = rc.getType().bodyRadius + 0.1f; // Extremely aggressive
                    
                    // Move first if not in optimal range
                    boolean hasMoved = false;
                    if (closestDist > moveThreshold) {
                        if (tryMove(toEnemy)) {
                            hasMoved = true;
                            System.out.println("Soldier moved aggressively, distance: " + closestDist);
                        } else {
                            // Try flanking if direct approach fails
                            for (int i = 15; i <= 45; i += 15) {
                                if (tryMove(toEnemy.rotateLeftDegrees(i)) || tryMove(toEnemy.rotateRightDegrees(i))) {
                                    hasMoved = true;
                                    System.out.println("Soldier flanked to engage");
                                    break;
                                }
                            }
                        }
                    }
                    
                    // Fire with priority: Use triad shots against high-value lumberjack threats
                    if (target.type == RobotType.LUMBERJACK && highestPriorityScore > 10000 && closestDist < 6f && rc.canFireTriadShot() && rc.getTeamBullets() >= 25) {
                        rc.fireTriadShot(toEnemy);
                        System.out.println("URGENT: FIRED TRIAD at tree-threatening LUMBERJACK! Distance: " + closestDist);
                    }
                    // Regular single shots for normal combat
                    else if (rc.canFireSingleShot() && rc.getTeamBullets() >= 10) {
                        rc.fireSingleShot(toEnemy);
                        System.out.println("FIRED at " + target.type + "! Distance: " + closestDist);
                    }
                    // Triad shots for crowded combat
                    else if (closestDist < 4f && robots.length >= 3 && rc.canFireTriadShot() && rc.getTeamBullets() >= 25) {
                        rc.fireTriadShot(toEnemy);
                        System.out.println("FIRED TRIAD (crowded) at " + target.type);
                    }
                } else {
                    // No enemies, prioritize tree protection patrol
                    TreeInfo[] nearbyTrees = rc.senseNearbyTrees(20f, myTeam); // Increased patrol range
                    boolean dodged = false;
                    
                    // First check for bullets and try to dodge
                    BulletInfo[] bullets = rc.senseNearbyBullets();
                    if (bullets.length > 0) {
                        for (BulletInfo bullet : bullets) {
                            if (willCollideWithMe(bullet)) {
                                Direction dodgeDir = bullet.dir.rotateLeftDegrees(90);
                                if (!rc.canMove(dodgeDir)) {
                                    dodgeDir = bullet.dir.rotateRightDegrees(90);
                                }
                                if (rc.canMove(dodgeDir)) {
                                    tryMove(dodgeDir);
                                    dodged = true;
                                }
                                break;
                            }
                        }
                    }
                    
                    // If no bullets to dodge, patrol strategically around trees
                    if (!dodged) {
                        if (nearbyTrees.length > 0) {
                            // Find the most isolated tree that needs protection
                            TreeInfo mostVulnerableTree = null;
                            float maxDistFromOthers = -1f;
                            
                            for (TreeInfo tree : nearbyTrees) {
                                float minDistToOtherTrees = 999f;
                                for (TreeInfo otherTree : nearbyTrees) {
                                    if (tree != otherTree) {
                                        float dist = tree.location.distanceTo(otherTree.location);
                                        if (dist < minDistToOtherTrees) {
                                            minDistToOtherTrees = dist;
                                        }
                                    }
                                }
                                if (minDistToOtherTrees > maxDistFromOthers) {
                                    maxDistFromOthers = minDistToOtherTrees;
                                    mostVulnerableTree = tree;
                                }
                            }
                            
                            if (mostVulnerableTree != null) {
                                Direction toVulnerableTree = myLocation.directionTo(mostVulnerableTree.location);
                                tryMove(toVulnerableTree);
                                System.out.println("Patrolling to protect vulnerable tree (" + nearbyTrees.length + " total)");
                            }
                        } else {
                            // No trees nearby, move toward likely tree locations
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