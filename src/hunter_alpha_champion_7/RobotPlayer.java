package hunter_alpha_champion_7;
import battlecode.common.*;

/**
 * Rapid Bloom Bot (v8 mutation)
 * Phase 1 (Rounds 1-150): Dense tree farm construction at 90% planting rate.
 * Phase 2 (Round 150+): Aggressive military production - SOLDIER heavy (75%), SCOUT (25%).
 *   All units converge on nearest enemy. Zero tank production.
 *   Front-loaded eco enables rapid swarm via massive bullet income.
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
        System.out.println("I'm an archon! (Swarm Blitz)");

        while (true) {
            try {
                BulletSpending.spendPolicy();

                // Flee from enemies - archon survival is critical
                MapLocation myLocation = rc.getLocation();
                RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
                if (enemies.length > 0) {
                    Direction awayFromEnemy = enemies[0].getLocation().directionTo(myLocation);
                    tryMove(awayFromEnemy);
                } else {
                    tryMove(randomDirection());
                }

                // Broadcast archon location for swarm coordination
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
        System.out.println("I'm a gardener! (Swarm Blitz)");

        while (true) {
            try {
                // Read archon location
                int xPos = rc.readBroadcast(0);
                int yPos = rc.readBroadcast(1);
                MapLocation archonLoc = new MapLocation(xPos, yPos);

                // Centralized spend policy (plant/build)
                BulletSpending.spendPolicy();

                // Water nearby trees to maintain production
                waterNearbyTrees();

                // Stay near archon for protection, but spread out slightly
                MapLocation myLocation = rc.getLocation();
                RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
                if (enemies.length > 0) {
                    Direction toArchon = myLocation.directionTo(archonLoc);
                    tryMove(toArchon);
                } else {
                    tryMove(randomDirection());
                }

                Clock.yield();

            } catch (Exception e) {
                System.out.println("Gardener Exception");
                e.printStackTrace();
            }
        }
    }

    static void waterNearbyTrees() throws GameActionException {
        TreeInfo[] trees = rc.senseNearbyTrees(-1, rc.getTeam());
        if (trees.length == 0) return;

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

    /**
     * Swarm soldier: aggressively converge on nearest enemy.
     * Use single shots for bullet efficiency (1 bullet per shot).
     * Ignore economy - pure combat focus.
     */
    static void runSoldier() throws GameActionException {
        System.out.println("I'm a soldier! (Swarm Blitz)");
        Team enemy = rc.getTeam().opponent();

        while (true) {
            try {
                MapLocation myLocation = rc.getLocation();
                RobotInfo[] robots = rc.senseNearbyRobots(-1, enemy);

                if (robots.length > 0) {
                    // Find closest enemy
                    RobotInfo target = findClosest(robots, myLocation);

                    // Fire single shots for efficiency (1 bullet per shot)
                    if (rc.canFireSingleShot()) {
                        Direction toTarget = myLocation.directionTo(target.location);
                        rc.fireSingleShot(toTarget);
                    }

                    // Swarm: move towards enemy aggressively
                    tryMove(myLocation.directionTo(target.location));
                } else {
                    // No enemies nearby: move towards enemy archon spawn
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
                System.out.println("Soldier Exception");
                e.printStackTrace();
            }
        }
    }

    static void runLumberjack() throws GameActionException {
        System.out.println("I'm a lumberjack! (Swarm Blitz)");
        Team enemy = rc.getTeam().opponent();

        while (true) {
            try {
                // Strike if enemies within strike radius
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
                        // Clear neutral trees blocking swarm path
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

    /**
     * Swarm scout: fast harassment unit. Converge on enemies, not kite away.
     * Shake trees for bullets while moving towards enemy.
     */
    static void runScout() throws GameActionException {
        System.out.println("I'm a scout! (Swarm Blitz)");
        Team enemy = rc.getTeam().opponent();

        while (true) {
            try {
                MapLocation myLocation = rc.getLocation();
                RobotInfo[] enemies = rc.senseNearbyRobots(-1, enemy);

                // Swarm behavior: move TOWARDS enemies, not away
                if (enemies.length > 0) {
                    RobotInfo closest = findClosest(enemies, myLocation);
                    Direction toEnemy = myLocation.directionTo(closest.location);
                    tryMove(toEnemy);

                    // Fire single shot at closest enemy
                    if (rc.canFireSingleShot()) {
                        rc.fireSingleShot(toEnemy);
                    }
                } else {
                    // Move towards enemy archon spawn locations
                    MapLocation[] enemyArchons = rc.getInitialArchonLocations(enemy);
                    if (enemyArchons.length > 0) {
                        Direction toEnemy = myLocation.directionTo(enemyArchons[0]);
                        tryMove(toEnemy);
                    } else {
                        tryMove(randomDirection());
                    }
                }

                // Shake trees for bullets while moving
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
        System.out.println("I'm a tank! (Swarm Blitz - legacy unit)");
        Team enemy = rc.getTeam().opponent();

        while (true) {
            try {
                MapLocation myLocation = rc.getLocation();
                RobotInfo[] enemies = rc.senseNearbyRobots(-1, enemy);

                if (enemies.length > 0) {
                    RobotInfo target = findClosest(enemies, myLocation);

                    if (rc.canFireTriadShot()) {
                        rc.fireTriadShot(rc.getLocation().directionTo(target.location));
                    } else if (rc.canFireSingleShot()) {
                        rc.fireSingleShot(rc.getLocation().directionTo(target.location));
                    }

                    tryMove(myLocation.directionTo(target.location));
                } else {
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

    static RobotInfo findClosest(RobotInfo[] robots, MapLocation from) {
        RobotInfo closest = robots[0];
        float closestDist = from.distanceTo(closest.location);
        for (int i = 1; i < robots.length; i++) {
            float dist = from.distanceTo(robots[i].location);
            if (dist < closestDist) {
                closestDist = dist;
                closest = robots[i];
            }
        }
        return closest;
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
