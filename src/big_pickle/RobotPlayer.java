package big_pickle;
import battlecode.common.*;

public strictfp class RobotPlayer {
    static RobotController rc;
    static final int ARCHON_CHANNELS = 2;
    static final int GARDENER_CHANNELS = 4;
    static final int SOLDIER_CHANNELS = 6;
    static final int ENEMY_REPORT_CHANNEL = 100;
    static final int MAP_INFO_CHANNEL = 101;
    
    enum Role { SCOUT, FIGHTER, DEFENDER, BUILDER }
    static Role myRole = Role.FIGHTER;

    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        RobotPlayer.rc = rc;
        
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
        System.out.println("Big Pickle Archon activated!");
        
        while (true) {
            try {
                broadcastArchonLocation();
                
                if (rc.getTeamBullets() >= 100 && rc.canHireGardener(getOptimalGardenerDirection())) {
                    rc.hireGardener(getOptimalGardenerDirection());
                }
                
                dodgeIncomingBullets();
                tryMove(getSafeDirection());
                
                Clock.yield();
            } catch (Exception e) {
                System.out.println("Big Pickle Archon Exception");
                e.printStackTrace();
            }
        }
    }

    static void runGardener() throws GameActionException {
        System.out.println("Big Pickle Gardener activated!");
        
        while (true) {
            try {
                if (rc.getTreeCount() < 5 && canPlantTree()) {
                    plantTreeStrategically();
                } else if (rc.isBuildReady() && shouldBuildUnit()) {
                    buildOptimalUnit();
                } else if (rc.getTreeCount() > 0) {
                    waterTrees();
                }
                
                dodgeIncomingBullets();
                tryMove(getSafeDirection());
                
                Clock.yield();
            } catch (Exception e) {
                System.out.println("Big Pickle Gardener Exception");
                e.printStackTrace();
            }
        }
    }

    static void runSoldier() throws GameActionException {
        System.out.println("Big Pickle Soldier activated!");
        Team enemy = rc.getTeam().opponent();
        
        while (true) {
            try {
                RobotInfo[] enemies = rc.senseNearbyRobots(-1, enemy);
                RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
                
                if (enemies.length > 0) {
                    RobotInfo target = selectBestTarget(enemies);
                    Direction targetDir = rc.getLocation().directionTo(target.location);
                    
                    if (rc.canFireSingleShot() && canHitTarget(target)) {
                        rc.fireSingleShot(targetDir);
                    }
                    
                    if (shouldRetreat(enemies, allies)) {
                        tryMove(getRetreatDirection(enemies));
                    } else {
                        tryMove(targetDir);
                    }
                } else {
                    patrolOrExplore();
                }
                
                dodgeIncomingBullets();
                Clock.yield();
            } catch (Exception e) {
                System.out.println("Big Pickle Soldier Exception");
                e.printStackTrace();
            }
        }
    }

    static void runLumberjack() throws GameActionException {
        System.out.println("Big Pickle Lumberjack activated!");
        Team enemy = rc.getTeam().opponent();
        
        while (true) {
            try {
                RobotInfo[] enemies = rc.senseNearbyRobots(RobotType.LUMBERJACK.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS, enemy);
                TreeInfo[] trees = rc.senseNearbyTrees();
                
                if (enemies.length > 0 && !rc.hasAttacked()) {
                    rc.strike();
                } else if (trees.length > 0) {
                    TreeInfo targetTree = selectBestTree(trees);
                    if (rc.canChop(targetTree.ID)) {
                        rc.chop(targetTree.ID);
                    }
                    tryMove(rc.getLocation().directionTo(targetTree.location));
                } else if (enemies.length > 0) {
                    RobotInfo target = selectBestTarget(enemies);
                    tryMove(rc.getLocation().directionTo(target.location));
                } else {
                    patrolOrExplore();
                }
                
                dodgeIncomingBullets();
                Clock.yield();
            } catch (Exception e) {
                System.out.println("Big Pickle Lumberjack Exception");
                e.printStackTrace();
            }
        }
    }

    static void runScout() throws GameActionException {
        System.out.println("Big Pickle Scout activated!");
        Team enemy = rc.getTeam().opponent();
        
        while (true) {
            try {
                RobotInfo[] enemies = rc.senseNearbyRobots(-1, enemy);
                TreeInfo[] neutralTrees = rc.senseNearbyTrees(-1, Team.NEUTRAL);
                
                if (enemies.length > 0) {
                    reportEnemyPositions(enemies);
                    RobotInfo target = selectHighValueTarget(enemies);
                    if (rc.canFireSingleShot() && canHitTarget(target)) {
                        rc.fireSingleShot(rc.getLocation().directionTo(target.location));
                    }
                    tryMove(getKitingDirection(enemies));
                } else if (neutralTrees.length > 0) {
                    TreeInfo targetTree = neutralTrees[0];
                    if (rc.canShake(targetTree.ID)) {
                        rc.shake(targetTree.ID);
                    }
                    tryMove(rc.getLocation().directionTo(targetTree.location));
                } else {
                    exploreMap();
                }
                
                dodgeIncomingBullets();
                Clock.yield();
            } catch (Exception e) {
                System.out.println("Big Pickle Scout Exception");
                e.printStackTrace();
            }
        }
    }

    static void runTank() throws GameActionException {
        System.out.println("Big Pickle Tank activated!");
        Team enemy = rc.getTeam().opponent();
        
        while (true) {
            try {
                RobotInfo[] enemies = rc.senseNearbyRobots(-1, enemy);
                
                if (enemies.length > 0) {
                    RobotInfo target = selectBestTarget(enemies);
                    Direction targetDir = rc.getLocation().directionTo(target.location);
                    
                    if (rc.canFirePentadShot() && canHitTarget(target)) {
                        rc.firePentadShot(targetDir);
                    } else if (rc.canFireSingleShot() && canHitTarget(target)) {
                        rc.fireSingleShot(targetDir);
                    }
                    
                    tryMove(targetDir);
                } else {
                    patrolOrExplore();
                }
                
                Clock.yield();
            } catch (Exception e) {
                System.out.println("Big Pickle Tank Exception");
                e.printStackTrace();
            }
        }
    }

    static Direction getOptimalGardenerDirection() throws GameActionException {
        Direction bestDir = null;
        float maxSpace = 0;
        
        for (int i = 0; i < 8; i++) {
            Direction dir = new Direction(i * (float)Math.PI / 4);
            if (rc.canHireGardener(dir)) {
                float space = calculateAvailableSpace(dir);
                if (space > maxSpace) {
                    maxSpace = space;
                    bestDir = dir;
                }
            }
        }
        
        return bestDir != null ? bestDir : randomDirection();
    }

    static float calculateAvailableSpace(Direction dir) {
        float space = 0;
        MapLocation start = rc.getLocation().add(dir, 2f);
        
        for (float dist = 1f; dist <= 5f; dist += 0.5f) {
            MapLocation check = start.add(dir, dist);
            if (rc.onTheMap(check) && !rc.isLocationOccupied(check)) {
                space += 0.5f;
            } else {
                break;
            }
        }
        
        return space;
    }

    static boolean canPlantTree() throws GameActionException {
        for (int i = 0; i < 6; i++) {
            Direction dir = new Direction(i * (float)Math.PI / 3);
            if (rc.canPlantTree(dir)) {
                return true;
            }
        }
        return false;
    }

    static void plantTreeStrategically() throws GameActionException {
        Direction bestDir = null;
        float maxSpace = 0;
        
        for (int i = 0; i < 6; i++) {
            Direction dir = new Direction(i * (float)Math.PI / 3);
            if (rc.canPlantTree(dir)) {
                float space = calculateAvailableSpace(dir);
                if (space > maxSpace) {
                    maxSpace = space;
                    bestDir = dir;
                }
            }
        }
        
        if (bestDir != null) {
            rc.plantTree(bestDir);
        }
    }

    static boolean shouldBuildUnit() throws GameActionException {
        int soldierCount = countAlliedUnits(RobotType.SOLDIER);
        int lumberjackCount = countAlliedUnits(RobotType.LUMBERJACK);
        int scoutCount = countAlliedUnits(RobotType.SCOUT);
        
        TreeInfo[] enemyTrees = rc.senseNearbyTrees(-1, rc.getTeam().opponent());
        TreeInfo[] neutralTrees = rc.senseNearbyTrees(-1, Team.NEUTRAL);
        
        if (enemyTrees.length > 3 && lumberjackCount < soldierCount) {
            return true;
        }
        if (neutralTrees.length > 5 && scoutCount < 2) {
            return true;
        }
        if (soldierCount < lumberjackCount + scoutCount) {
            return true;
        }
        
        return Math.random() < 0.3;
    }

    static void buildOptimalUnit() throws GameActionException {
        RobotType toBuild = determineOptimalUnitType();
        Direction bestDir = getOptimalBuildDirection(toBuild);
        
        if (bestDir != null && rc.canBuildRobot(toBuild, bestDir)) {
            rc.buildRobot(toBuild, bestDir);
        }
    }

    static RobotType determineOptimalUnitType() {
        TreeInfo[] enemyTrees = rc.senseNearbyTrees(-1, rc.getTeam().opponent());
        TreeInfo[] neutralTrees = rc.senseNearbyTrees(-1, Team.NEUTRAL);
        
        if (enemyTrees.length > 2) {
            return RobotType.LUMBERJACK;
        }
        if (neutralTrees.length > 3) {
            return RobotType.SCOUT;
        }
        
        return RobotType.SOLDIER;
    }

    static Direction getOptimalBuildDirection(RobotType type) throws GameActionException {
        for (int i = 0; i < 8; i++) {
            Direction dir = new Direction(i * (float)Math.PI / 4);
            if (rc.canBuildRobot(type, dir)) {
                return dir;
            }
        }
        return null;
    }

    static void waterTrees() throws GameActionException {
        TreeInfo[] trees = rc.senseNearbyTrees();
        for (TreeInfo tree : trees) {
            if (tree.health < tree.maxHealth && rc.canWater(tree.ID)) {
                rc.water(tree.ID);
                break;
            }
        }
    }

    static RobotInfo selectBestTarget(RobotInfo[] enemies) {
        RobotInfo bestTarget = null;
        float bestScore = -1;
        
        for (RobotInfo enemy : enemies) {
            float score = calculateTargetScore(enemy);
            if (score > bestScore) {
                bestScore = score;
                bestTarget = enemy;
            }
        }
        
        return bestTarget;
    }

    static float calculateTargetScore(RobotInfo enemy) {
        float distance = rc.getLocation().distanceTo(enemy.location);
        float healthScore = (enemy.maxHealth - enemy.health) / enemy.maxHealth;
        float typeScore = getTargetTypeScore(enemy.type);
        
        return typeScore * 10 + healthScore * 5 - distance / 10;
    }

    static float getTargetTypeScore(RobotType type) {
        switch (type) {
            case ARCHON: return 3.0f;
            case GARDENER: return 2.5f;
            case SCOUT: return 1.5f;
            case SOLDIER: return 2.0f;
            case LUMBERJACK: return 1.8f;
            case TANK: return 2.2f;
            default: return 1.0f;
        }
    }

    static boolean canHitTarget(RobotInfo target) {
        return rc.canFireSingleShot() || rc.canFireTriadShot() || rc.canFirePentadShot();
    }

    static boolean shouldRetreat(RobotInfo[] enemies, RobotInfo[] allies) {
        if (enemies.length > allies.length + 1) {
            return true;
        }
        
        float enemyHealth = 0, allyHealth = 0;
        for (RobotInfo enemy : enemies) {
            enemyHealth += enemy.health;
        }
        for (RobotInfo ally : allies) {
            allyHealth += ally.health;
        }
        
        return enemyHealth > allyHealth * 1.5f;
    }

    static Direction getRetreatDirection(RobotInfo[] enemies) {
        MapLocation myLoc = rc.getLocation();
        float avgX = 0, avgY = 0;
        
        for (RobotInfo enemy : enemies) {
            avgX += enemy.location.x;
            avgY += enemy.location.y;
        }
        avgX /= enemies.length;
        avgY /= enemies.length;
        
        MapLocation enemyCenter = new MapLocation(avgX, avgY);
        return enemyCenter.directionTo(myLoc);
    }

    static TreeInfo selectBestTree(TreeInfo[] trees) {
        TreeInfo bestTree = null;
        float bestScore = -1;
        
        for (TreeInfo tree : trees) {
            float score = 0;
            if (tree.team == rc.getTeam().opponent()) {
                score = 100;
            } else if (tree.team == Team.NEUTRAL) {
                score = 50;
            }
            
            float distance = rc.getLocation().distanceTo(tree.location);
            score -= distance / 10;
            
            if (score > bestScore) {
                bestScore = score;
                bestTree = tree;
            }
        }
        
        return bestTree;
    }

    static RobotInfo selectHighValueTarget(RobotInfo[] enemies) {
        for (RobotInfo enemy : enemies) {
            if (enemy.type == RobotType.ARCHON) {
                return enemy;
            }
        }
        return selectBestTarget(enemies);
    }

    static Direction getKitingDirection(RobotInfo[] enemies) {
        return getRetreatDirection(enemies);
    }

    static void patrolOrExplore() throws GameActionException {
        MapLocation target = getPatrolTarget();
        Direction dir = rc.getLocation().directionTo(target);
        tryMove(dir);
    }

    static void exploreMap() throws GameActionException {
        MapLocation target = getExplorationTarget();
        Direction dir = rc.getLocation().directionTo(target);
        tryMove(dir);
    }

    static MapLocation getPatrolTarget() {
        int round = rc.getRoundNum();
        float x = (float) (Math.sin(round / 100.0) * rc.getMapWidth() / 2 + rc.getMapWidth() / 2);
        float y = (float) (Math.cos(round / 100.0) * rc.getMapHeight() / 2 + rc.getMapHeight() / 2);
        return new MapLocation(x, y);
    }

    static MapLocation getExplorationTarget() {
        return new MapLocation(
            (float) (Math.random() * rc.getMapWidth()),
            (float) (Math.random() * rc.getMapHeight())
        );
    }

    static void broadcastArchonLocation() throws GameActionException {
        MapLocation myLocation = rc.getLocation();
        int archonIndex = getArchonIndex();
        rc.broadcast(archonIndex * 2, (int) myLocation.x);
        rc.broadcast(archonIndex * 2 + 1, (int) myLocation.y);
    }

    static int getArchonIndex() {
        RobotInfo[] archons = rc.senseNearbyRobots(-1, rc.getTeam(), RobotType.ARCHON);
        for (int i = 0; i < archons.length; i++) {
            if (archons[i].ID == rc.getID()) {
                return i;
            }
        }
        return 0;
    }

    static void reportEnemyPositions(RobotInfo[] enemies) throws GameActionException {
        for (int i = 0; i < Math.min(enemies.length, 5); i++) {
            int channel = ENEMY_REPORT_CHANNEL + i * 3;
            rc.broadcast(channel, (int) enemies[i].location.x);
            rc.broadcast(channel + 1, (int) enemies[i].location.y);
            rc.broadcast(channel + 2, enemies[i].type.ordinal());
        }
    }

    static int countAlliedUnits(RobotType type) {
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        int count = 0;
        for (RobotInfo ally : allies) {
            if (ally.type == type) {
                count++;
            }
        }
        return count;
    }

    static void dodgeIncomingBullets() throws GameActionException {
        BulletInfo[] bullets = rc.senseNearbyBullets();
        for (BulletInfo bullet : bullets) {
            if (willCollideWithMe(bullet)) {
                Direction dodgeDir = getDodgeDirection(bullet);
                if (dodgeDir != null && rc.canMove(dodgeDir)) {
                    rc.move(dodgeDir);
                    return;
                }
            }
        }
    }

    static Direction getDodgeDirection(BulletInfo bullet) {
        Direction bulletDir = bullet.dir;
        Direction left = bulletDir.rotateLeftDegrees(90);
        Direction right = bulletDir.rotateRightDegrees(90);
        
        if (rc.canMove(left)) return left;
        if (rc.canMove(right)) return right;
        
        return null;
    }

    static Direction getSafeDirection() throws GameActionException {
        for (int i = 0; i < 8; i++) {
            Direction dir = new Direction(i * (float)Math.PI / 4);
            if (rc.canMove(dir)) {
                return dir;
            }
        }
        return randomDirection();
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

        while(currentCheck <= checksPerSide) {
            if(rc.canMove(dir.rotateLeftDegrees(degreeOffset*currentCheck))) {
                rc.move(dir.rotateLeftDegrees(degreeOffset*currentCheck));
                return true;
            }
            if(rc.canMove(dir.rotateRightDegrees(degreeOffset*currentCheck))) {
                rc.move(dir.rotateRightDegrees(degreeOffset*currentCheck));
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

        if (Math.abs(theta) > Math.PI/2) {
            return false;
        }

        float perpendicularDist = (float)Math.abs(distToRobot * Math.sin(theta));

        return (perpendicularDist <= rc.getType().bodyRadius);
    }
}