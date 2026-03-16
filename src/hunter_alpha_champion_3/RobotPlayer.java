package hunter_alpha_champion_3;
import battlecode.common.*;

/**
 * Eco Greed Supreme Bot
 * Phase 1 (Rounds 1-800): Pure economy - gardeners plant trees, all units play defensively
 * Phase 2 (Round 800+): Pivot to VP rush or tank army based on game state
 */
public strictfp class RobotPlayer {
    static RobotController rc;

    public static void run(RobotController rc) throws GameActionException {
        RobotPlayer.rc = rc;
        BulletSpending.init(rc);

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
        System.out.println("I'm an archon! (Eco Greed Supreme)");

        while (true) {
            try {
                BulletSpending.spendPolicy();

                // Flee from enemies - survival is critical for eco strategy
                MapLocation myLocation = rc.getLocation();
                RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
                if (enemies.length > 0) {
                    Direction awayFromEnemy = enemies[0].getLocation().directionTo(myLocation);
                    tryMove(awayFromEnemy);
                } else {
                    tryMove(randomDirection());
                }

                // Broadcast archon location
                rc.broadcast(0, (int)myLocation.x);
                rc.broadcast(1, (int)myLocation.y);

                Clock.yield();

            } catch (Exception e) {
                System.out.println("Archon Exception");
                e.printStackTrace();
            }
        }
    }

	static void runGardener() throws GameActionException {
        System.out.println("I'm a gardener! (Eco Greed Supreme)");

        while (true) {
            try {
                // Read archon location
                int xPos = rc.readBroadcast(0);
                int yPos = rc.readBroadcast(1);
                MapLocation archonLoc = new MapLocation(xPos, yPos);

                // Centralized spend policy (plant/build/donate)
                BulletSpending.spendPolicy();

                // Aggressively water nearby trees to maintain production
                waterNearbyTrees();

                // Move towards archon if enemies nearby for protection
                MapLocation myLocation = rc.getLocation();
                RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
                if (enemies.length > 0) {
                    Direction toArchon = myLocation.directionTo(archonLoc);
                    tryMove(toArchon);
                } else {
                    // Stay near our trees but move slightly to find planting spots
                    tryMove(randomDirection());
                }

                Clock.yield();

            } catch (Exception e) {
                System.out.println("Gardener Exception");
                e.printStackTrace();
            }
        }
    }

    /**
     * Aggressively water all nearby team trees to maximize bullet production.
     * Gardeners can water once per turn.
     */
    static void waterNearbyTrees() throws GameActionException {
        // Find all nearby team trees
        TreeInfo[] trees = rc.senseNearbyTrees(-1, rc.getTeam());
        if (trees.length == 0) return;

        // Water the tree with lowest health (most in need of maintenance)
        TreeInfo weakestTree = null;
        float lowestHealth = Float.MAX_VALUE;

        for (TreeInfo tree : trees) {
            if (rc.canWater(tree.location) && tree.health < lowestHealth) {
                lowestHealth = tree.health;
                weakestTree = tree;
            }
        }

        if (weakestTree != null && rc.canWater(weakestTree.location)) {
            rc.water(weakestTree.location);
        }
    }

    static void runSoldier() throws GameActionException {
        System.out.println("I'm a soldier! (Eco Greed Supreme)");
        Team enemy = rc.getTeam().opponent();

        while (true) {
            try {
                MapLocation myLocation = rc.getLocation();
                RobotInfo[] robots = rc.senseNearbyRobots(-1, enemy);

                // In eco-greed, soldiers are defensive - protect gardeners
                if (robots.length > 0) {
                    RobotInfo target = robots[0];
                    if (rc.canFireSingleShot()) {
                        rc.fireSingleShot(rc.getLocation().directionTo(target.location));
                    }
                    // Move towards threat to defend
                    tryMove(myLocation.directionTo(target.location));
                } else {
                    // Patrol near archon location when no enemies
                    int xPos = rc.readBroadcast(0);
                    int yPos = rc.readBroadcast(1);
                    MapLocation archonLoc = new MapLocation(xPos, yPos);
                    float distToArchon = myLocation.distanceTo(archonLoc);
                    if (distToArchon > 10) {
                        tryMove(myLocation.directionTo(archonLoc));
                    } else {
                        tryMove(randomDirection());
                    }
                }

                Clock.yield();

            } catch (Exception e) {
                System.out.println("Soldier Exception");
                e.printStackTrace();
            }
        }
    }

    static void runLumberjack() throws GameActionException {
        System.out.println("I'm a lumberjack! (Eco Greed Supreme)");
        Team enemy = rc.getTeam().opponent();

        while (true) {
            try {
                // Strike if enemies close
                RobotInfo[] robots = rc.senseNearbyRobots(
                    RobotType.LUMBERJACK.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS, enemy);

                if (robots.length > 0 && !rc.hasAttacked()) {
                    rc.strike();
                } else {
                    robots = rc.senseNearbyRobots(-1, enemy);
                    if (robots.length > 0) {
                        MapLocation myLocation = rc.getLocation();
                        Direction toEnemy = myLocation.directionTo(robots[0].getLocation());
                        tryMove(toEnemy);
                    } else {
                        // Chop neutral trees for bullet collection
                        TreeInfo[] neutralTrees = rc.senseNearbyTrees(-1, Team.NEUTRAL);
                        if (neutralTrees.length > 0 && rc.canChop(neutralTrees[0].location)) {
                            rc.chop(neutralTrees[0].location);
                        } else {
                            tryMove(randomDirection());
                        }
                    }
                }

                Clock.yield();

            } catch (Exception e) {
                System.out.println("Lumberjack Exception");
                e.printStackTrace();
            }
        }
    }

    static void runScout() throws GameActionException {
        System.out.println("I'm a scout! (Eco Greed Supreme)");
        Team enemy = rc.getTeam().opponent();

        while (true) {
            try {
                MapLocation myLocation = rc.getLocation();
                RobotInfo[] enemies = rc.senseNearbyRobots(-1, enemy);

                // Scouts patrol and observe - avoid combat, gather intel
                if (enemies.length > 0) {
                    // Kite away from enemies
                    Direction awayFromNearest = enemies[0].getLocation().directionTo(myLocation);
                    tryMove(awayFromNearest);
                } else {
                    // Patrol towards enemy archon locations
                    MapLocation[] enemyArchons = rc.getInitialArchonLocations(enemy);
                    if (enemyArchons.length > 0) {
                        Direction toEnemy = myLocation.directionTo(enemyArchons[0]);
                        tryMove(toEnemy);
                    } else {
                        tryMove(randomDirection());
                    }
                }

                // Shake trees for bullets while scouting
                TreeInfo[] trees = rc.senseNearbyTrees(-1, Team.NEUTRAL);
                for (TreeInfo tree : trees) {
                    if (tree.containedBullets > 0 && rc.canShake(tree.location)) {
                        rc.shake(tree.location);
                        break;
                    }
                }

                Clock.yield();

            } catch (Exception e) {
                System.out.println("Scout Exception");
                e.printStackTrace();
            }
        }
    }

    static void runTank() throws GameActionException {
        System.out.println("I'm a tank! (Eco Greed Supreme - Phase 2 Pivot)");
        Team enemy = rc.getTeam().opponent();

        while (true) {
            try {
                MapLocation myLocation = rc.getLocation();
                RobotInfo[] enemies = rc.senseNearbyRobots(-1, enemy);

                if (enemies.length > 0) {
                    RobotInfo target = enemies[0];

                    // Use triad shot for more damage
                    if (rc.canFireTriadShot()) {
                        rc.fireTriadShot(rc.getLocation().directionTo(target.location));
                    } else if (rc.canFireSingleShot()) {
                        rc.fireSingleShot(rc.getLocation().directionTo(target.location));
                    }

                    // Aggressive advance
                    tryMove(myLocation.directionTo(target.location));
                } else {
                    // Move towards enemy archon locations
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

    static Direction randomDirection() {
        return new Direction((float)Math.random() * 2 * (float)Math.PI);
    }

    static boolean tryMove(Direction dir) throws GameActionException {
        return tryMove(dir, 20, 3);
    }

    static boolean tryMove(Direction dir, float degreeOffset, int checksPerSide) throws GameActionException {
        if (rc.canMove(dir)) {
            rc.move(dir);
            return true;
        }

        boolean moved = false;
        int currentCheck = 1;

        while (currentCheck <= checksPerSide) {
            if (rc.canMove(dir.rotateLeftDegrees(degreeOffset * currentCheck))) {
                rc.move(dir.rotateLeftDegrees(degreeOffset * currentCheck));
                return true;
            }
            if (rc.canMove(dir.rotateRightDegrees(degreeOffset * currentCheck))) {
                rc.move(dir.rotateRightDegrees(degreeOffset * currentCheck));
                return true;
            }
            currentCheck++;
        }

        return false;
    }

    static boolean willCollideWithMe(BulletInfo bullet) {
        MapLocation myLocation = rc.getLocation();
        Direction propagationDirection = bullet.dir;
        MapLocation bulletLocation = bullet.location;
        Direction directionToRobot = bulletLocation.directionTo(myLocation);
        float distToRobot = bulletLocation.distanceTo(myLocation);
        float theta = propagationDirection.radiansBetween(directionToRobot);

        if (Math.abs(theta) > Math.PI / 2) {
            return false;
        }

        float perpendicularDist = (float) Math.abs(distToRobot * Math.sin(theta));
        return (perpendicularDist <= rc.getType().bodyRadius);
    }
}
