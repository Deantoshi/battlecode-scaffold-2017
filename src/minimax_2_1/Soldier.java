package minimax_2_1;
import battlecode.common.*;

public strictfp class Soldier {
    static RobotController rc;

    public static void run(RobotController rc) throws GameActionException {
        Soldier.rc = rc;
        Nav.init(rc);
        Comms.init(rc);

        while (true) {
            try {
                doTurn();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                Clock.yield();
            }
        }
    }

    static void doTurn() throws GameActionException {
        TreeInfo[] nearbyTrees = rc.senseNearbyTrees(5.0f, Team.NEUTRAL);
        for (TreeInfo tree : nearbyTrees) {
            if (tree.containedBullets > 0 && rc.canShake(tree.ID)) {
                rc.shake(tree.ID);
                return;
            }
        }

        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        
        // SURVIVAL: Check if we should retreat due to low HP
        if (shouldRetreat()) {
            retreatToArchon();
            return;
        }
        
        if (enemies.length > 0) {
            Comms.broadcastEnemySpotted(enemies[0].location);
            RobotInfo target = findArchonTarget(enemies);
            if (tryShoot(target)) {
                // Try to kite after shooting
                tryKitingMove(target);
                return;
            }
            target = findGardenerTarget(enemies);
            if (tryShoot(target)) {
                tryKitingMove(target);
                return;
            }
            target = Utils.findLowestHealthTarget(enemies);
            if (tryShoot(target)) {
                tryKitingMove(target);
                return;
            }
            // If can't shoot, try kiting anyway
            if (enemies.length > 0) {
                tryKitingMove(enemies[0]);
            }
        } else {
            int round = rc.getRoundNum();
            MapLocation enemyArchon = Comms.getEnemyArchonLocation();
            
            // AGGRESSIVE MODE: After round 500, push hard to enemy base
            if (round >= 500 && enemyArchon != null) {
                Direction toEnemy = rc.getLocation().directionTo(enemyArchon);
                Nav.tryMove(toEnemy);
                return;
            }
            
            // Normal mode: hunt enemies
            if (enemyArchon != null) {
                Nav.moveToward(enemyArchon);
            } else {
                MapLocation lastSighting = Comms.getLastEnemySighting();
                if (lastSighting != null) {
                    Nav.moveToward(lastSighting);
                } else {
                    Nav.tryMove(Nav.randomDirection());
                }
            }
        }
    }

    static boolean tryMoveToAttack(RobotInfo target) throws GameActionException {
        if (target == null) return false;
        float dist = rc.getLocation().distanceTo(target.location);
        if (dist > 2.0f) {
            if (Nav.moveToward(target.location)) {
                return true;
            }
            MapLocation loc = target.location;
            for (int i = 0; i < 8; i++) {
                Direction d = new Direction((float)(i * Math.PI / 4));
                MapLocation nearby = loc.add(d, 2.0f);
                if (Nav.moveToward(nearby)) {
                    return true;
                }
            }
            Nav.tryMove(Nav.randomDirection());
            return true;
        }
        return false;
    }

    static RobotInfo findTarget() throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemies.length == 0) return null;
        
        RobotInfo best = null;
        float bestScore = Float.MAX_VALUE;
        for (RobotInfo enemy : enemies) {
            float dist = rc.getLocation().distanceTo(enemy.location);
            float health = enemy.health;
            float score = health + dist * 0.5f;
            if (enemy.type == RobotType.ARCHON) score -= 200;
            else if (enemy.type == RobotType.GARDENER) score -= 100;
            if (score < bestScore) {
                bestScore = score;
                best = enemy;
            }
        }
        return best;
    }

    static RobotInfo findArchonTarget(RobotInfo[] enemies) throws GameActionException {
        for (RobotInfo enemy : enemies) {
            if (enemy.type == RobotType.ARCHON) {
                return enemy;
            }
        }
        return null;
    }
    
    static RobotInfo findGardenerTarget(RobotInfo[] enemies) throws GameActionException {
        for (RobotInfo enemy : enemies) {
            if (enemy.type == RobotType.GARDENER) {
                return enemy;
            }
        }
        return null;
    }
    
    static boolean tryShoot(RobotInfo target) throws GameActionException {
        if (target == null) return false;
        Direction dir = rc.getLocation().directionTo(target.location);
        
        if (rc.canFirePentadShot()) {
            rc.firePentadShot(dir);
            return true;
        }
        if (rc.canFireTriadShot()) {
            rc.fireTriadShot(dir);
            return true;
        }
        if (rc.canFireSingleShot()) {
            rc.fireSingleShot(dir);
            return true;
        }
        return false;
    }
    
    static boolean shouldRetreat() throws GameActionException {
        float myHealth = rc.getHealth();
        float maxHealth = rc.getType().maxHealth;
        float healthPercent = myHealth / maxHealth;
        
        // Retreat if HP < 30% and enemies are nearby
        if (healthPercent < 0.30f) {
            RobotInfo[] nearbyEnemies = rc.senseNearbyRobots(10, rc.getTeam().opponent());
            if (nearbyEnemies.length > 0) {
                return true;
            }
        }
        return false;
    }

    static void retreatToArchon() throws GameActionException {
        MapLocation archonLoc = Comms.getFriendlyArchonLocation();
        if (archonLoc != null) {
            Direction awayFromEnemy = rc.getLocation().directionTo(archonLoc);
            Nav.tryMove(awayFromEnemy);
        } else {
            Nav.tryMove(Nav.randomDirection());
        }
    }

    static boolean tryKitingMove(RobotInfo target) throws GameActionException {
        if (target == null) return false;
        float dist = rc.getLocation().distanceTo(target.location);
        
        // If too close (< 4), move away (kite)
        if (dist < 4.0f) {
            Direction away = rc.getLocation().directionTo(target.location).opposite();
            if (Nav.tryMove(away)) {
                return true;
            }
        }
        // If in good range (4-6), strafe perpendicular
        else if (dist < 7.0f) {
            Direction toTarget = rc.getLocation().directionTo(target.location);
            Direction left = toTarget.rotateLeftDegrees(90);
            Direction right = toTarget.rotateRightDegrees(90);
            if (Nav.tryMove(left) || Nav.tryMove(right)) {
                return true;
            }
        }
        return false;
    }
}
