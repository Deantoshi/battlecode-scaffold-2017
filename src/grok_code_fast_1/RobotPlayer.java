package grok_code_fast_1;
import battlecode.common.*;
import java.util.*;

/**
 * 
 * @author 
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
        Navigation.init(rc);
        Nav.init(rc);

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
        Team enemy = rc.getTeam().opponent();
        MapLocation enemyArchonLoc = rc.getInitialArchonLocations(enemy)[0];
        int gardenerCount = rc.readBroadcast(7);

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {

                // Generate a random direction
                Direction dir = randomDirection();

                // Randomly attempt to build a gardener in this direction
                if (rc.canHireGardener(dir) && Math.random() < .15) {
                    rc.hireGardener(dir);
                    rc.broadcast(7, gardenerCount + 1);
                }

                // Move randomly to prioritize survival
                Nav.tryMove(rc.getLocation().add(randomDirection()));

                // Broadcast archon's location for other robots on the team to know
                MapLocation myLocation = rc.getLocation();
                rc.broadcast(0,(int)myLocation.x);
                rc.broadcast(1,(int)myLocation.y);
                rc.broadcast(2,(int)enemyArchonLoc.x);
                rc.broadcast(3,(int)enemyArchonLoc.y);

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

        if (rc.getRoundNum() == 1) {
            rc.broadcast(4, 0);
            rc.broadcast(5, 0);
            rc.broadcast(6, 0);
            rc.broadcast(7, 0);
        }

        int myLumberjacks = 0;

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {

                // Listen for home archon's location
                int xPos = rc.readBroadcast(0);
                int yPos = rc.readBroadcast(1);
                MapLocation archonLoc = new MapLocation(xPos,yPos);

                // Generate a random direction
                Direction dir = randomDirection();

                // Prioritize early lumberjack production for path clearing
                int lumberjackCount = rc.readBroadcast(5);
                int tankCount = rc.readBroadcast(4);
                int soldierCount = rc.readBroadcast(6);
                int gardenerCount = rc.readBroadcast(7);
                if (rc.getRoundNum() < 500 && lumberjackCount < 4) {
                    if (rc.canBuildRobot(RobotType.LUMBERJACK, dir) && rc.isBuildReady()) {
                        rc.buildRobot(RobotType.LUMBERJACK, dir);
                        rc.broadcast(5, lumberjackCount + 1);
                        myLumberjacks++;
                    }
                } else if (lumberjackCount < 2) {
                    if (rc.canBuildRobot(RobotType.LUMBERJACK, dir) && rc.isBuildReady()) {
                        rc.buildRobot(RobotType.LUMBERJACK, dir);
                        rc.broadcast(5, lumberjackCount + 1);
                        myLumberjacks++;
                    }
                } else if (soldierCount == 0) {
                    if (rc.canBuildRobot(RobotType.SOLDIER, dir) && rc.isBuildReady()) {
                        rc.buildRobot(RobotType.SOLDIER, dir);
                        rc.broadcast(6, soldierCount + 1);
                    }
                } else if (soldierCount < 10) {
                    if (rc.canBuildRobot(RobotType.SOLDIER, dir) && rc.isBuildReady()) {
                        rc.buildRobot(RobotType.SOLDIER, dir);
                        rc.broadcast(6, soldierCount + 1);
                    }
                } else if (rc.getRoundNum() < 300 && myLumberjacks < 3) {
                    if (rc.canBuildRobot(RobotType.LUMBERJACK, dir) && rc.isBuildReady()) {
                        rc.buildRobot(RobotType.LUMBERJACK, dir);
                        rc.broadcast(5, lumberjackCount + 1);
                        myLumberjacks++;
                    }
                } else if (rc.getRoundNum() < 500) {
                    float soldierProb = 0.8f;
                    float lumberjackProb = 0.15f;
                    float rand = (float) Math.random();
                    if (rand < soldierProb && rc.canBuildRobot(RobotType.SOLDIER, dir) && rc.isBuildReady()) {
                        rc.buildRobot(RobotType.SOLDIER, dir);
                        rc.broadcast(6, soldierCount + 1);
                    } else if (rand < soldierProb + lumberjackProb && rc.canBuildRobot(RobotType.LUMBERJACK, dir) && rc.isBuildReady()) {
                        rc.buildRobot(RobotType.LUMBERJACK, dir);
                        rc.broadcast(5, lumberjackCount + 1);
                        myLumberjacks++;
                    } else if (rand < soldierProb + lumberjackProb + 0.05f && rc.canBuildRobot(RobotType.TANK, dir) && rc.isBuildReady()) {
                        rc.buildRobot(RobotType.TANK, dir);
                        rc.broadcast(4, tankCount + 1);
                    }
                } else {
                    float rand = (float) Math.random();
                    if (rand < 0.1f && rc.canBuildRobot(RobotType.SOLDIER, dir) && rc.isBuildReady()) {
                        rc.buildRobot(RobotType.SOLDIER, dir);
                        rc.broadcast(6, soldierCount + 1);
                    } else if (rand < 0.9f && rc.canBuildRobot(RobotType.LUMBERJACK, dir) && rc.isBuildReady()) {
                        rc.buildRobot(RobotType.LUMBERJACK, dir);
                        rc.broadcast(5, lumberjackCount + 1);
                        myLumberjacks++;
                    } else if (rc.canBuildRobot(RobotType.TANK, dir) && rc.isBuildReady()) {
                        rc.buildRobot(RobotType.TANK, dir);
                        rc.broadcast(4, tankCount + 1);
                    }
                }

                if (rc.canPlantTree(dir) && Math.random() < 0.0) {
                    rc.plantTree(dir);
                }

                // Check for nearby enemies
                RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
                if (enemies.length == 0) {
                    // No enemies nearby, move towards quadrant center if far away
                    int quadrant = rc.getID() % 4;
                    MapLocation quadrantCenter = getQuadrantCenter(quadrant);
                    if (rc.getLocation().distanceTo(quadrantCenter) > 5.0f) {
                        Direction toQuadrant = rc.getLocation().directionTo(quadrantCenter);
                        Nav.tryMove(quadrantCenter);
                    } else {
                        // Already close, move randomly
                        Nav.tryMove(rc.getLocation().add(randomDirection()));
                    }
                } else {
                    // Enemies nearby, move randomly
                    Nav.tryMove(rc.getLocation().add(randomDirection()));
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

                Direction desiredDir;
                MapLocation moveTarget;
                // If there are some...
                if (robots.length > 0) {
                    // Find closest archon
                    RobotInfo target = null;
                    for (RobotInfo r : robots) {
                        if (r.type == RobotType.ARCHON) {
                            target = r;
                            break; // since sorted by distance, first archon is closest
                        }
                    }
                    if (target == null) {
                        target = robots[0]; // closest enemy
                    }
                    moveTarget = target.location;
                    desiredDir = myLocation.directionTo(target.location);
                } else {
                    int enemyX = rc.readBroadcast(2);
                    int enemyY = rc.readBroadcast(3);
                    MapLocation enemyArchon = new MapLocation((float)enemyX, (float)enemyY);
                    moveTarget = enemyArchon;
                    desiredDir = myLocation.directionTo(enemyArchon);
                }

                // Fire at target
                if (robots.length > 0) {
                    RobotInfo target = null;
                    for (RobotInfo r : robots) {
                        if (r.type == RobotType.ARCHON) {
                            target = r;
                            break;
                        }
                    }
                    if (target == null) {
                        target = robots[0];
                    }
                    Direction dir = rc.getLocation().directionTo(target.location);
                    int triadTargets = 0;
                    int pentadTargets = 0;
                    for (RobotInfo r : robots) {
                        Direction enemyDir = rc.getLocation().directionTo(r.location);
                        float angle = Math.abs(dir.radiansBetween(enemyDir));
                        if (angle <= Math.toRadians(20)) triadTargets++;
                        if (angle <= Math.toRadians(30)) pentadTargets++;
                    }
                    // Check for friendly fire
                    boolean safeToFireSingle = true;
                    boolean safeToFireTriad = true;
                    boolean safeToFirePentad = true;
                    RobotInfo[] friendlies = rc.senseNearbyRobots(-1, rc.getTeam());
                    for (RobotInfo friendly : friendlies) {
                        Direction toFriendly = rc.getLocation().directionTo(friendly.location);
                        float angleDiff = Math.abs(dir.radiansBetween(toFriendly));
                        float dist = rc.getLocation().distanceTo(friendly.location);
                        if (dist < 5.0f) {
                            if (angleDiff < Math.toRadians(10)) safeToFireSingle = false;
                            if (angleDiff < Math.toRadians(20)) safeToFireTriad = false;
                            if (angleDiff < Math.toRadians(30)) safeToFirePentad = false;
                        }
                    }
                    if (rc.canFireSingleShot() && safeToFireSingle) {
                        rc.fireSingleShot(dir);
                    } else if (rc.canFireTriadShot() && triadTargets >= 2 && safeToFireTriad) {
                        rc.fireTriadShot(dir);
                    } else if (rc.canFirePentadShot() && pentadTargets >= 4 && safeToFirePentad) {
                        rc.firePentadShot(dir);
                    } else if (rc.canFireTriadShot() && safeToFireTriad) {
                        rc.fireTriadShot(dir);
                    } else if (rc.canFirePentadShot() && safeToFirePentad) {
                        rc.firePentadShot(dir);
                    }
                }

                // Sense trees blocking path to enemy archon
                if (robots.length == 0) {
                    TreeInfo[] trees = rc.senseNearbyTrees(-1, null);
                    TreeInfo targetTree = null;
                    float minDist = Float.MAX_VALUE;
                    Direction toEnemy = myLocation.directionTo(moveTarget);
                    for (TreeInfo tree : trees) {
                        Direction toTree = myLocation.directionTo(tree.location);
                        if (Math.abs(toTree.radiansBetween(toEnemy)) < Math.PI / 12) { // within 15 degrees
                            float dist = myLocation.distanceTo(tree.location);
                            if (dist <= 3.0f && dist < minDist) { // within firing range
                                minDist = dist;
                                targetTree = tree;
                            }
                        }
                    }
                    if (targetTree != null && rc.canFireSingleShot()) {
                        Direction treeDir = myLocation.directionTo(targetTree.location);
                        boolean safeToFireTree = true;
                        RobotInfo[] friendlies = rc.senseNearbyRobots(-1, rc.getTeam());
                        for (RobotInfo friendly : friendlies) {
                            Direction toFriendly = myLocation.directionTo(friendly.location);
                            float angleDiff = Math.abs(treeDir.radiansBetween(toFriendly));
                            float dist = myLocation.distanceTo(friendly.location);
                            if (dist < 5.0f && angleDiff < Math.toRadians(10)) {
                                safeToFireTree = false;
                                break;
                            }
                        }
                        if (safeToFireTree) {
                            rc.fireSingleShot(treeDir);
                        }
                    }
                }

                // Check for bullets to dodge
                BulletInfo[] bullets = rc.senseNearbyBullets();
                boolean dodged = false;
                for (BulletInfo b : bullets) {
                    if (willCollideWithMe(b)) {
                        desiredDir = b.dir.rotateLeftDegrees(90);
                        dodged = true;
                        break; // dodge the first colliding bullet
                    }
                }

                // Move in the desired direction
                if (dodged) {
                    Nav.tryMove(rc.getLocation().add(desiredDir));
                } else {
                    Nav.tryMove(moveTarget);
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
                int enemyX = rc.readBroadcast(2);
                int enemyY = rc.readBroadcast(3);
                MapLocation enemyArchon = new MapLocation((float)enemyX, (float)enemyY);

                // Prioritize chopping trees on path to enemy archon
                TreeInfo[] pathTrees = rc.senseNearbyTrees(-1, null);
                TreeInfo targetTree = null;
                float minDist = Float.MAX_VALUE;
                for (TreeInfo tree : pathTrees) {
                    if (rc.getLocation().distanceTo(tree.location) <= 2.0f && lineIntersectsCircle(rc.getLocation(), enemyArchon, tree.location, tree.radius)) {
                        float dist = rc.getLocation().distanceTo(tree.location);
                        if (dist < minDist) {
                            minDist = dist;
                            targetTree = tree;
                        }
                    }
                }
                if (targetTree != null) {
                    if (targetTree.containedRobot != null && rc.canShake(targetTree.ID)) {
                        rc.shake(targetTree.ID);
                    } else if (rc.canChop(targetTree.ID)) {
                        rc.chop(targetTree.ID);
                    }
                }

                // See if there are any enemy robots within striking range (distance 1 from lumberjack's radius)
                RobotInfo[] robots = rc.senseNearbyRobots(GameConstants.LUMBERJACK_STRIKE_RADIUS, enemy);
                RobotInfo[] friendlies = rc.senseNearbyRobots(2.0f, rc.getTeam());

                if(robots.length > 0 && friendlies.length == 0 && !rc.hasAttacked()) {
                    // Use strike() to hit all nearby robots!
                    rc.strike();
                } else {
                    // No close robots, so search for robots within sight radius
                    robots = rc.senseNearbyRobots(-1,enemy);

                    Direction desiredDir;
                    MapLocation target;
                    if(robots.length > 0) {
                        target = robots[0].getLocation();
                        desiredDir = rc.getLocation().directionTo(target);
                    } else {
                        target = enemyArchon;
                        desiredDir = rc.getLocation().directionTo(target);
                    }

                    // Proactively clear blocking trees in desired direction
                    TreeInfo[] nearbyTrees = rc.senseNearbyTrees(3.0f, null);
                    for (TreeInfo tree : nearbyTrees) {
                        Direction toTree = rc.getLocation().directionTo(tree.location);
                        float angleDiff = Math.abs(desiredDir.radiansBetween(toTree));
                        if (angleDiff < Math.PI / 6) { // within 30 degrees
                            if (tree.containedRobot != null && rc.canShake(tree.ID)) {
                                rc.shake(tree.ID);
                            } else if (rc.canChop(tree.ID)) {
                                rc.chop(tree.ID);
                            }
                        }
                    }

                    // If there is a robot, move towards it
                    if(robots.length > 0) {
                        Nav.tryMove(target);

                    } else {
                        // Sense nearby trees
                        TreeInfo[] trees = rc.senseNearbyTrees(-1, null);
                        if (trees.length > 0) {
                            // Find the tree with smallest angle to target direction within direct path
                            TreeInfo bestTree = null;
                            float minAngle = Float.MAX_VALUE;
                            float maxAngle = (float)Math.PI; // 180 degrees threshold
                            for (TreeInfo t : trees) {
                                if (t.containedRobot != null) {
                                    minAngle = 0;
                                    bestTree = t;
                                } else {
                                    Direction toTree = rc.getLocation().directionTo(t.location);
                                    float angleDiff = Math.abs(desiredDir.radiansBetween(toTree));
                                    if (angleDiff < maxAngle && angleDiff < minAngle) {
                                        minAngle = angleDiff;
                                        bestTree = t;
                                    }
                                }
                            }
                            // Chop the best tree if possible, otherwise move towards it
                            if (bestTree != null) {
                                if (rc.canChop(bestTree.ID)) {
                                    rc.chop(bestTree.ID);
                                } else {
                                    Direction toTree = rc.getLocation().directionTo(bestTree.location);
                                    Nav.tryMove(rc.getLocation().add(toTree));
                                }
                            } else {
                                // No trees in direct path, move towards target if far away
                                if (rc.getLocation().distanceTo(target) > 5.0f) {
                                    Nav.tryMove(target);
                                } else {
                                    // Already close, move randomly
                                    Nav.tryMove(rc.getLocation().add(randomDirection()));
                                }
                            }
                        } else {
                            // No trees nearby, move towards target if far away
                            if (rc.getLocation().distanceTo(target) > 5.0f) {
                                Nav.tryMove(target);
                            } else {
                                // Already close, move randomly
                                Nav.tryMove(rc.getLocation().add(randomDirection()));
                            }
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

    static MapLocation getQuadrantCenter(int quadrant) {
        MapLocation[] ownArchons = rc.getInitialArchonLocations(rc.getTeam());
        float avgX = 0, avgY = 0;
        for (MapLocation loc : ownArchons) {
            avgX += loc.x;
            avgY += loc.y;
        }
        avgX /= ownArchons.length;
        avgY /= ownArchons.length;
        MapLocation mapCenter = new MapLocation(avgX, avgY);
        float offsetX = 80.0f / 4.0f;
        float offsetY = 80.0f / 4.0f;
        switch(quadrant) {
            case 0: return new MapLocation(mapCenter.x - offsetX, mapCenter.y + offsetY); // NW
            case 1: return new MapLocation(mapCenter.x + offsetX, mapCenter.y + offsetY); // NE
            case 2: return new MapLocation(mapCenter.x + offsetX, mapCenter.y - offsetY); // SE
            case 3: return new MapLocation(mapCenter.x - offsetX, mapCenter.y - offsetY); // SW
            default: return rc.getLocation(); // shouldn't happen
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
        // This corresponds to the smallest radius circle centered at our location that would intersect with the
        // line that is the path of the bullet.
        float perpendicularDist = (float)Math.abs(distToRobot * Math.sin(theta)); // soh cah toa :)

        return (perpendicularDist <= rc.getType().bodyRadius);
    }

    static boolean lineIntersectsCircle(MapLocation start, MapLocation end, MapLocation center, float radius) {
        float dx = end.x - start.x;
        float dy = end.y - start.y;
        float cx = center.x - start.x;
        float cy = center.y - start.y;
        float len2 = dx*dx + dy*dy;
        if (len2 == 0) return start.distanceTo(center) <= radius;
        float t = Math.max(0, Math.min(1, (cx*dx + cy*dy) / len2));
        float projx = start.x + t * dx;
        float projy = start.y + t * dy;
        MapLocation proj = new MapLocation(projx, projy);
        return proj.distanceTo(center) <= radius;
    }
}