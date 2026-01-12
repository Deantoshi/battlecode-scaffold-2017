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
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        
        for (RobotInfo enemy : enemies) {
            if (enemy.type == RobotType.ARCHON) {
                Comms.broadcastLocation(2, 3, enemy.location);
            }
        }
        
        if (enemies.length > 0) {
            RobotInfo target = findTarget();
            if (tryShoot(target)) {
                return;
            }
            if (tryMoveToAttack(target)) {
                return;
            }
        }

        RobotInfo[] nearbyAllies = rc.senseNearbyRobots(10, rc.getTeam());
        int allyCount = 0;
        for (RobotInfo ally : nearbyAllies) {
            if (ally.type == RobotType.SOLDIER || ally.type == RobotType.LUMBERJACK || ally.type == RobotType.TANK) {
                allyCount++;
            }
        }
        
        if (allyCount < 1) {
            MapLocation myLoc = rc.getLocation();
            MapLocation archonLoc = Comms.getFriendlyArchonLocation();
            if (archonLoc != null && myLoc.distanceTo(archonLoc) > 20) {
                if (Nav.moveToward(archonLoc)) {
                    return;
                }
            }
        }

        MapLocation enemyArchon = Comms.getEnemyArchonLocation();
        if (enemyArchon != null) {
            if (Nav.moveToward(enemyArchon)) {
                return;
            }
        }

        Nav.tryMove(Nav.randomDirection());
    }

    static boolean tryMoveToAttack(RobotInfo target) throws GameActionException {
        if (target == null) return false;
        float dist = rc.getLocation().distanceTo(target.location);
        if (dist > 3.0f) {
            return Nav.moveToward(target.location);
        }
        return false;
    }

    static RobotInfo findTarget() throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        return Utils.findLowestHealthTarget(enemies);
    }

    static boolean tryShoot(RobotInfo target) throws GameActionException {
        if (target == null) return false;
        Direction dir = rc.getLocation().directionTo(target.location);
        float distToTarget = rc.getLocation().distanceTo(target.location);
        
        if (distToTarget > rc.getType().bulletSightRadius) {
            return false;
        }
        
        if (rc.canFirePentadShot() && distToTarget < 2.0f) {
            rc.firePentadShot(dir);
            return true;
        }
        
        if (rc.canFireTriadShot() && distToTarget < 3.0f) {
            rc.fireTriadShot(dir);
            return true;
        }
        
        if (rc.canFireSingleShot()) {
            rc.fireSingleShot(dir);
            return true;
        }
        
        return false;
    }
}
