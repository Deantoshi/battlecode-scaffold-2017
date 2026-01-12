package minimax_2_1;
import battlecode.common.*;

public strictfp class Tank {
    static RobotController rc;

    public static void run(RobotController rc) throws GameActionException {
        Tank.rc = rc;
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
        TreeInfo[] nearbyTrees = rc.senseNearbyTrees(10.0f, Team.NEUTRAL);
        for (TreeInfo tree : nearbyTrees) {
            if (tree.containedBullets > 0 && rc.canShake(tree.ID)) {
                rc.shake(tree.ID);
                return;
            }
        }

        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        RobotInfo[] allies = rc.senseNearbyRobots(10, rc.getTeam());
        boolean outnumbered = enemies.length > allies.length;
        
        if (outnumbered && enemies.length > 0) {
            RobotInfo closest = Utils.findClosestEnemy(rc, enemies);
            if (closest != null) {
                Direction away = rc.getLocation().directionTo(closest.location).opposite();
                Nav.tryMove(away);
                return;
            }
        }
        
        RobotInfo target = null;
        
        if (enemies.length > 0) {
            target = Utils.findLowestHealthTarget(enemies);
            if (target != null) {
                Direction dir = rc.getLocation().directionTo(target.location);
                float dist = rc.getLocation().distanceTo(target.location);
                
                if (dist <= rc.getType().bulletSightRadius) {
                    if (dist < 10 && rc.canFireTriadShot()) {
                        rc.fireTriadShot(dir);
                    } else if (rc.canFireSingleShot()) {
                        rc.fireSingleShot(dir);
                    }
                }
                
                if (dist > 2) {
                    Nav.moveToward(target.location);
                    return;
                }
            }
        }

        MapLocation enemyArchon = Comms.getEnemyArchonLocation();
        if (enemyArchon != null) {
            Nav.moveToward(enemyArchon);
            return;
        }

        Nav.tryMove(Nav.randomDirection());
    }
}
