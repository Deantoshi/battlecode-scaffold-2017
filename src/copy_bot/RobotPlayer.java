package copy_bot;
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

        // Initialize classes
        Gardener.init(rc);
        Soldier.init(rc);
        Tank.init(rc);
        Scout.init(rc);
        Nav.init(rc);
        BulletSpending.init(rc);

        // Here, we've separated the controls into a different method for each RobotType.
        // You can add the missing ones or rewrite this into your control structure.
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
            case TANK:
                runTank();
                break;
            case SCOUT:
                runScout();
                break;
        }
	}

    static void runArchon() throws GameActionException {
        System.out.println("I'm an archon!");

        MapLocation spawnLoc = rc.getLocation();

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to perform this loop again
            try {

                // Generate a random direction
                Direction dir = randomDirection();

                // Hire up to 20 gardeners indefinitely
                BulletSpending.tryHireGardener(dir);

                // Broadcast gardeners hired
                rc.broadcast(2, BulletSpending.getGardenersHired());

                // Broadcast archon's location for other robots on the team to know
                MapLocation myLocation = rc.getLocation();
                rc.broadcast(0,(int)myLocation.x);
                rc.broadcast(1,(int)myLocation.y);

                BulletSpending.donateForVP();

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

            // Try/catch blocks stop unhandled exceptions, which cause your robot to perform this loop again
            try {

                // Listen for home archon's location
                int xPos = rc.readBroadcast(0);
                int yPos = rc.readBroadcast(1);
                MapLocation archonLoc = new MapLocation(xPos,yPos);

                // Generate a random direction
                Direction dir = randomDirection();

                // Check for nearby trees blocking build areas
                TreeInfo[] nearbyTrees = rc.senseNearbyTrees(5.0f, null);
                if (nearbyTrees.length > 0) {
                    tryMove(randomDirection());
                }

                // Attempt to build robots
                BulletSpending.buildRobot();

                // Water trees
                Gardener.waterTree();

                // Plant trees
                BulletSpending.plantTree();

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

            // Try/catch blocks stop unhandled exceptions, which cause your robot to perform this loop again
            try {
                MapLocation myLocation = rc.getLocation();

                // Fire
                Soldier.fire();

                // Static defense: stay near archon base instead of moving towards enemy
                MapLocation archonLoc = new MapLocation(rc.readBroadcast(0), rc.readBroadcast(1));
                if (myLocation.distanceTo(archonLoc) > 10) {
                    Nav.tryMoveBug(archonLoc);
                }

                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println("Soldier Exception");
                e.printStackTrace();
            }
        }
    }

    static void runTank() throws GameActionException {
        System.out.println("I'm an tank!");
        Team enemy = rc.getTeam().opponent();

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to perform this loop again
            try {
                MapLocation myLocation = rc.getLocation();

                // See if there are any nearby enemy robots
                RobotInfo[] robots = rc.senseNearbyRobots(-1, enemy);

                // Fire at enemies
                Tank.fire();

                // Move to defensive position around base
                Tank.defensiveMove();

                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println("Tank Exception");
                e.printStackTrace();
            }
        }
    }

    static void runLumberjack() throws GameActionException {
        System.out.println("I'm a lumberjack!");
        Team enemy = rc.getTeam().opponent();

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to perform this loop again
            try {

                // See if there are any enemy robots within striking range (distance 1 from lumberjack's radius)
                RobotInfo[] robots = rc.senseNearbyRobots(RobotType.LUMBERJACK.bodyRadius+GameConstants.LUMBERJACK_STRIKE_RADIUS, enemy);

                if(robots.length > 0 && !rc.hasAttacked()) {
                    // Use strike() to hit all nearby robots!
                    rc.strike();
                } else {
                    // Static defense: stay near archon base instead of moving towards enemy
                    MapLocation archonLoc = new MapLocation(rc.readBroadcast(0), rc.readBroadcast(1));
                    MapLocation myLoc = rc.getLocation();
                    if (myLoc.distanceTo(archonLoc) > 8) {
                        Nav.tryMoveBug(archonLoc);
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
            // Try/catch blocks stop unhandled exceptions, which cause your robot to perform this loop again
            try {
                MapLocation myLocation = rc.getLocation();

                // Fire
                Scout.fire();

                // Static defense: patrol near archon base instead of moving towards enemy
                MapLocation archonLoc = new MapLocation(rc.readBroadcast(0), rc.readBroadcast(1));
                if (myLocation.distanceTo(archonLoc) > 15) {
                    Nav.tryMoveBug(archonLoc);
                } else {
                    // Patrol around base
                    Nav.tryMoveBug(Nav.spawnLoc);
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
            // No move performed, so try slightly further
            currentCheck++;
        }

        // A move never happened, so return false.
        return false;
    }
}
