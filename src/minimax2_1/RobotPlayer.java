package minimax2_1;
import battlecode.common.*;

public strictfp class RobotPlayer {
    static RobotController rc;
    static int myId;
    static int myTeamIdx;
    static MapLocation[] archonLocations;
    static int archonCount = 0;
    static int myArchonIndex = -1;
    static int roundNum;
    static float bullets;
    static int victoryPoints;
    static Team myTeam;
    static Team enemyTeam;
    static MapLocation myLocation;

    static final int BROADCAST_ARCHON_LOCATIONS = 0;
    static final int BROADCAST_RALLY_POINT = 10;
    static final int BROADCAST_ENEMY_SIGHTED = 20;
    static final int BROADCAST_TREE_DANGER = 30;
    static final int BROADCAST_BUILD_ORDER = 40;

    static MapLocation rallyPoint = null;
    static int buildOrder = 0;
    static int lastBuildRound = 0;

    static Direction[] directions = {
        Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };

    public static void run(RobotController rc) throws GameActionException {
        RobotPlayer.rc = rc;
        myId = rc.getID();
        myTeam = rc.getTeam();
        enemyTeam = myTeam.opponent();
        myLocation = rc.getLocation();

        if (rc.getType() == RobotType.ARCHON) {
            initializeArchon();
        }

        initializeSharedState();

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
        }
    }

    static void initializeArchon() throws GameActionException {
        archonLocations = new MapLocation[4];
        int archonId = rc.getID();
        int idx = archonId - 1;
        myArchonIndex = idx;
        archonLocations[idx] = myLocation;
        broadcastArchonLocations();
    }

    static void initializeSharedState() throws GameActionException {
        if (rc.getType() != RobotType.ARCHON) {
            readArchonLocations();
        }
        updateRoundState();
    }

    static void updateRoundState() throws GameActionException {
        roundNum = rc.getRoundNum();
        bullets = rc.getTeamBullets();
        victoryPoints = rc.getTeamVictoryPoints();
    }

    static void broadcastArchonLocations() throws GameActionException {
        for (int i = 0; i < 4; i++) {
            if (archonLocations[i] != null) {
                rc.broadcast(BROADCAST_ARCHON_LOCATIONS + i,
                    (int)(archonLocations[i].x * 10) + (int)(archonLocations[i].y * 10) % 1000);
            }
        }
    }

    static void readArchonLocations() throws GameActionException {
        if (archonLocations == null) {
            archonLocations = new MapLocation[4];
        }
        for (int i = 0; i < 4; i++) {
            int msg = rc.readBroadcast(BROADCAST_ARCHON_LOCATIONS + i);
            if (msg != 0) {
                int x = msg / 10;
                int y = msg % 10;
                if (x > 0 && y > 0) {
                    archonLocations[i] = new MapLocation(x, y);
                }
            }
        }
    }

    static void broadcastEnemySighting(MapLocation loc) throws GameActionException {
        int msg = (int)(loc.x * 10) + 1000 + (int)(loc.y * 10);
        rc.broadcast(BROADCAST_ENEMY_SIGHTED, msg);
    }

    static MapLocation readEnemySighting() throws GameActionException {
        int msg = rc.readBroadcast(BROADCAST_ENEMY_SIGHTED);
        if (msg > 1000) {
            int x = (msg - 1000) / 10;
            int y = msg % 10;
            return new MapLocation(x, y);
        }
        return null;
    }

    static void runArchon() throws GameActionException {
        System.out.println("Archon " + myArchonIndex + " starting");

        while (true) {
            try {
                updateRoundState();
                myLocation = rc.getLocation();

                if (roundNum == 1 && myArchonIndex == 0) {
                    int initialBuild = rc.readBroadcast(BROADCAST_BUILD_ORDER);
                    if (initialBuild == 0) {
                        rc.broadcast(BROADCAST_BUILD_ORDER, 2);
                    }
                }

                updateArchonLocation();
                handleArchonMovement();
                handleArchonBuilding();
                handleArchonHealing();

                Clock.yield();
            } catch (Exception e) {
                System.out.println("Archon Exception");
                e.printStackTrace();
            }
        }
    }

    static void updateArchonLocation() throws GameActionException {
        if (archonLocations[myArchonIndex] == null ||
            !archonLocations[myArchonIndex].equals(myLocation)) {
            archonLocations[myArchonIndex] = myLocation;
            broadcastArchonLocations();
        }
    }

    static void handleArchonMovement() throws GameActionException {
        if (shouldArchonMove()) {
            Direction bestDir = findBestArchonDirection();
            if (bestDir != null && rc.canMove(bestDir)) {
                rc.move(bestDir);
                myLocation = rc.getLocation();
            }
        }
    }

    static boolean shouldArchonMove() throws GameActionException {
        if (rc.getHealth() < RobotType.ARCHON.maxHealth * 0.3) {
            return true;
        }
        if (isInDanger(myLocation)) {
            return true;
        }
        if (hasNearbyEnemies(10)) {
            return true;
        }
        return false;
    }

    static Direction findBestArchonDirection() throws GameActionException {
        MapLocation center = getArchonCenter();
        if (center == null) return randomDirection();

        Direction toCenter = myLocation.directionTo(center);
        Direction awayFromEnemies = findSafeDirectionAwayFromEnemies();

        if (awayFromEnemies != null) return awayFromEnemies;

        return tryMoveInDirection(toCenter);
    }

    static MapLocation getArchonCenter() {
        int count = 0;
        float x = 0, y = 0;
        for (MapLocation loc : archonLocations) {
            if (loc != null) {
                x += loc.x;
                y += loc.y;
                count++;
            }
        }
        if (count == 0) return null;
        return new MapLocation(x / count, y / count);
    }

    static Direction findSafeDirectionAwayFromEnemies() throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, enemyTeam);
        if (enemies.length == 0) return null;

        float dangerX = 0, dangerY = 0;
        for (RobotInfo e : enemies) {
            float dist = myLocation.distanceTo(e.location);
            if (dist < 15) {
                dangerX += (myLocation.x - e.location.x) / (dist * dist);
                dangerY += (myLocation.y - e.location.y) / (dist * dist);
            }
        }

        if (dangerX == 0 && dangerY == 0) return null;

        Direction away = new Direction((float)Math.atan2(dangerY, dangerX));
        return tryMoveInDirection(away);
    }

    static void handleArchonBuilding() throws GameActionException {
        buildOrder = rc.readBroadcast(BROADCAST_BUILD_ORDER);
        int myArchonBuildCount = 0;

        RobotInfo[] myRobots = rc.senseNearbyRobots(-1, myTeam);
        for (RobotInfo r : myRobots) {
            if (r.type == RobotType.GARDENER) {
                myArchonBuildCount++;
            }
        }

        int targetGardeners = Math.min(4, 1 + roundNum / 200);

        if (myArchonBuildCount < targetGardeners / 2) {
            Direction hireDir = findBestGardenerDirection();
            if (hireDir != null && rc.canHireGardener(hireDir)) {
                rc.hireGardener(hireDir);
                System.out.println("Hired gardener at " + hireDir);
            }
        }
    }

    static Direction findBestGardenerDirection() throws GameActionException {
        MapLocation center = getArchonCenter();
        if (center == null) return randomDirection();

        for (int i = 0; i < 8; i++) {
            Direction dir = directions[i];
            MapLocation spot = myLocation.add(dir);
            if (isValidGardenerSpot(spot)) {
                return dir;
            }
        }

        return randomDirection();
    }

    static boolean isValidGardenerSpot(MapLocation spot) throws GameActionException {
        if (!rc.canSenseLocation(spot)) return false;
        if (spot.x < 0 || spot.y < 0) return false;

        for (Direction d : directions) {
            MapLocation adj = spot.add(d);
            if (rc.canSenseLocation(adj) && rc.isLocationOccupiedByRobot(adj)) {
                return false;
            }
        }
        return true;
    }

    static void handleArchonHealing() throws GameActionException {
    }

    static void runGardener() throws GameActionException {
        System.out.println("Gardener starting");
        int lastMoveRound = 0;
        boolean hasBuiltTree = false;

        while (true) {
            try {
                updateRoundState();
                myLocation = rc.getLocation();

                handleGardenerMovement(lastMoveRound);
                if (roundNum != lastMoveRound) {
                    lastMoveRound = roundNum;
                }

                handleGardenerTreeBuilding(hasBuiltTree);
                hasBuiltTree = hasBuiltTree || rc.getTreeCount() > 0;

                handleGardenerRobotBuilding();
                handleGardenerWatering();

                Clock.yield();
            } catch (Exception e) {
                System.out.println("Gardener Exception");
                e.printStackTrace();
            }
        }
    }

    static void handleGardenerMovement(int lastMoveRound) throws GameActionException {
        if (roundNum - lastMoveRound > 10 && !hasNearbyAllies(5)) {
            Direction toRally = getDirectionToRallyPoint();
            if (toRally != null) {
                tryMove(toRally, 15, 3);
            }
        }
    }

    static void handleGardenerTreeBuilding(boolean hasBuiltTree) throws GameActionException {
        if (!hasBuiltTree && rc.getTreeCount() == 0) {
            for (Direction d : directions) {
                if (rc.canPlantTree(d)) {
                    rc.plantTree(d);
                    break;
                }
            }
        }

        if (rc.getTreeCount() < 3 && roundNum > 100) {
            Direction bestDir = findBestTreeDirection();
            if (bestDir != null && rc.canPlantTree(bestDir)) {
                rc.plantTree(bestDir);
            }
        }
    }

    static Direction findBestTreeDirection() throws GameActionException {
        MapLocation center = getArchonCenter();
        Direction awayFromCenter = center != null ? myLocation.directionTo(center) : randomDirection();

        for (int i = 0; i < 8; i++) {
            Direction testDir = awayFromCenter.rotateLeftDegrees(i * 30);
            if (rc.canPlantTree(testDir)) {
                return testDir;
            }
        }

        for (Direction d : directions) {
            if (rc.canPlantTree(d)) {
                return d;
            }
        }
        return randomDirection();
    }

    static void handleGardenerRobotBuilding() throws GameActionException {
        if (!rc.isBuildReady()) return;

        buildOrder = rc.readBroadcast(BROADCAST_BUILD_ORDER);
        int soldiersBuilt = countMyUnits(RobotType.SOLDIER);
        int lumberjacksBuilt = countMyUnits(RobotType.LUMBERJACK);

        boolean needLumberjack = lumberjacksBuilt < 2 || soldiersBuilt > lumberjacksBuilt * 3;
        boolean needSoldier = soldiersBuilt < 10;

        if (needLumberjack) {
            Direction lumberDir = findBuildDirection(RobotType.LUMBERJACK);
            if (lumberDir != null && rc.canBuildRobot(RobotType.LUMBERJACK, lumberDir)) {
                rc.buildRobot(RobotType.LUMBERJACK, lumberDir);
                System.out.println("Built lumberjack");
                return;
            }
        }

        if (needSoldier) {
            Direction soldierDir = findBuildDirection(RobotType.SOLDIER);
            if (soldierDir != null && rc.canBuildRobot(RobotType.SOLDIER, soldierDir)) {
                rc.buildRobot(RobotType.SOLDIER, soldierDir);
                System.out.println("Built soldier");
                return;
            }
        }
    }

    static Direction findBuildDirection(RobotType type) throws GameActionException {
        MapLocation center = getArchonCenter();
        if (center == null) center = myLocation;

        for (int i = 0; i < 8; i++) {
            Direction d = myLocation.directionTo(center).rotateLeftDegrees(i * 45);
            if (rc.canBuildRobot(type, d)) {
                return d;
            }
        }

        for (Direction d : directions) {
            if (rc.canBuildRobot(type, d)) {
                return d;
            }
        }
        return null;
    }

    static void handleGardenerWatering() throws GameActionException {
        TreeInfo[] myTrees = rc.senseNearbyTrees(RobotType.GARDENER.sensorRadius, myTeam);
        TreeInfo lowestTree = null;
        float lowestHealth = Float.MAX_VALUE;

        for (TreeInfo t : myTrees) {
            if (t.health < t.maxHealth && t.health < lowestHealth) {
                lowestHealth = t.health;
                lowestTree = t;
            }
        }

        if (lowestTree != null && rc.canWater(lowestTree.location)) {
            rc.water(lowestTree.location);
        }
    }

    static void runSoldier() throws GameActionException {
        System.out.println("Soldier starting");
        int lastAttackRound = 0;
        MapLocation patrolCenter = getMidpointBetweenArchons();
        if (patrolCenter == null) patrolCenter = myLocation;

        while (true) {
            try {
                updateRoundState();
                myLocation = rc.getLocation();

                handleSoldierMovement(patrolCenter);
                handleSoldierCombat(lastAttackRound);

                Clock.yield();
            } catch (Exception e) {
                System.out.println("Soldier Exception");
                e.printStackTrace();
            }
        }
    }

    static void handleSoldierMovement(MapLocation patrolCenter) throws GameActionException {
        RobotInfo[] nearbyEnemies = rc.senseNearbyRobots(-1, enemyTeam);

        if (nearbyEnemies.length > 0) {
            RobotInfo closestEnemy = getClosestEnemyInfo(nearbyEnemies);
            if (closestEnemy != null) {
                if (shouldApproachEnemy(closestEnemy)) {
                    Direction toEnemy = myLocation.directionTo(closestEnemy.location);
                    tryMove(toEnemy, 20, 3);
                }
            }
        } else {
            if (rallyPoint != null && myLocation.distanceTo(rallyPoint) > 5) {
                Direction toRally = myLocation.directionTo(rallyPoint);
                tryMove(toRally, 15, 3);
            } else if (patrolCenter != null && myLocation.distanceTo(patrolCenter) > 8) {
                Direction toPatrol = myLocation.directionTo(patrolCenter);
                tryMove(toPatrol, 15, 3);
            } else {
                Direction randomDir = randomDirection();
                tryMove(randomDir, 20, 2);
            }
        }
    }

    static void handleSoldierCombat(int lastAttackRound) throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(RobotType.SOLDIER.sensorRadius, enemyTeam);
        BulletInfo[] bullets = rc.senseNearbyBullets(myLocation, 4);

        for (BulletInfo b : bullets) {
            if (willCollideWithMe(b)) {
                Direction away = myLocation.directionTo(b.location).rotateLeftDegrees(180);
                tryMove(away, 30, 2);
            }
        }

        if (enemies.length > 0 && roundNum > lastAttackRound + 3) {
            RobotInfo target = selectBestTarget(enemies);
            if (target != null) {
                fireAtTarget(target);
                lastAttackRound = roundNum;
            }
        }
    }

    static void runLumberjack() throws GameActionException {
        System.out.println("Lumberjack starting");

        while (true) {
            try {
                updateRoundState();
                myLocation = rc.getLocation();

                handleLumberjackCombat();
                handleLumberjackMovement();

                Clock.yield();
            } catch (Exception e) {
                System.out.println("Lumberjack Exception");
                e.printStackTrace();
            }
        }
    }

    static void handleLumberjackCombat() throws GameActionException {
        RobotInfo[] nearbyEnemies = rc.senseNearbyRobots(
            RobotType.LUMBERJACK.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS, enemyTeam);

        if (nearbyEnemies.length > 0 && !rc.hasAttacked()) {
            rc.strike();
            System.out.println("Lumberjack strike!");
        }
    }

    static void handleLumberjackMovement() throws GameActionException {
        RobotInfo[] nearbyEnemies = rc.senseNearbyRobots(-1, enemyTeam);

        if (nearbyEnemies.length > 0) {
            RobotInfo closest = getClosestEnemyInfo(nearbyEnemies);
            if (closest != null) {
                Direction toEnemy = myLocation.directionTo(closest.location);
                tryMove(toEnemy, 15, 3);
            }
        } else {
            Direction toRally = getDirectionToRallyPoint();
            if (toRally != null) {
                tryMove(toRally, 15, 3);
            }
        }
    }

    static void runScout() throws GameActionException {
        System.out.println("Scout starting");

        while (true) {
            try {
                updateRoundState();
                myLocation = rc.getLocation();

                handleScoutExploration();
                handleScoutCombat();

                Clock.yield();
            } catch (Exception e) {
                System.out.println("Scout Exception");
                e.printStackTrace();
            }
        }
    }

    static void handleScoutExploration() throws GameActionException {
        MapLocation enemyArchon = findClosestEnemyArchon();

        if (enemyArchon != null && myLocation.distanceTo(enemyArchon) > 15) {
            Direction toEnemyArchon = myLocation.directionTo(enemyArchon);
            tryMove(toEnemyArchon, 20, 3);
        } else {
            Direction explorationDir = findExplorationDirection();
            tryMove(explorationDir, 30, 4);
        }
    }

    static void handleScoutCombat() throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(RobotType.SCOUT.sensorRadius, enemyTeam);
        RobotInfo closestEnemy = getClosestEnemyInfo(enemies);

        if (closestEnemy != null && closestEnemy.type != RobotType.ARCHON) {
            fireAtTarget(closestEnemy);
        }
    }

    static MapLocation findClosestEnemyArchon() throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, enemyTeam);
        MapLocation closestArchon = null;
        float closestDist = Float.MAX_VALUE;

        for (RobotInfo e : enemies) {
            if (e.type == RobotType.ARCHON) {
                float dist = myLocation.distanceTo(e.location);
                if (dist < closestDist) {
                    closestDist = dist;
                    closestArchon = e.location;
                }
            }
        }
        return closestArchon;
    }

    static Direction findExplorationDirection() throws GameActionException {
        RobotInfo[] allies = rc.senseNearbyRobots(-1, myTeam);
        float centerX = 0, centerY = 0;
        for (RobotInfo a : allies) {
            centerX += a.location.x;
            centerY += a.location.y;
        }
        if (allies.length > 0) {
            centerX /= allies.length;
            centerY /= allies.length;
            MapLocation allyCenter = new MapLocation(centerX, centerY);
            return myLocation.directionTo(allyCenter).rotateLeftDegrees(180);
        }
        return randomDirection();
    }

    static MapLocation getClosestEnemy(RobotInfo[] enemies) {
        MapLocation closest = null;
        float closestDist = Float.MAX_VALUE;
        for (RobotInfo e : enemies) {
            float dist = myLocation.distanceTo(e.location);
            if (dist < closestDist) {
                closestDist = dist;
                closest = e.location;
            }
        }
        return closest;
    }

    static RobotInfo getClosestEnemyInfo(RobotInfo[] enemies) {
        RobotInfo closest = null;
        float closestDist = Float.MAX_VALUE;
        for (RobotInfo e : enemies) {
            float dist = myLocation.distanceTo(e.location);
            if (dist < closestDist) {
                closestDist = dist;
                closest = e;
            }
        }
        return closest;
    }

    static RobotInfo selectBestTarget(RobotInfo[] enemies) {
        RobotInfo bestTarget = null;
        float bestScore = Float.MAX_VALUE;

        for (RobotInfo e : enemies) {
            float score = myLocation.distanceTo(e.location);
            if (e.type == RobotType.ARCHON) score *= 0.5f;
            if (e.type == RobotType.GARDENER) score *= 0.7f;
            if (e.type == RobotType.SOLDIER) score *= 1.0f;
            if (e.type == RobotType.LUMBERJACK) score *= 1.2f;
            if (e.type == RobotType.SCOUT) score *= 1.1f;

            if (score < bestScore) {
                bestScore = score;
                bestTarget = e;
            }
        }
        return bestTarget;
    }

    static void fireAtTarget(RobotInfo target) throws GameActionException {
        Direction toTarget = myLocation.directionTo(target.location);

        if (rc.canFireSingleShot()) {
            rc.fireSingleShot(toTarget);
        }
    }

    static Direction tryMoveInDirection(Direction dir) throws GameActionException {
        if (rc.canMove(dir)) {
            rc.move(dir);
            return dir;
        }
        return null;
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

    static Direction randomDirection() {
        return new Direction((float)Math.random() * 2 * (float)Math.PI);
    }

    static boolean willCollideWithMe(BulletInfo bullet) {
        MapLocation myLoc = rc.getLocation();
        MapLocation bulletLoc = bullet.location;

        Direction propagationDirection = bullet.dir;
        Direction directionToRobot = bulletLoc.directionTo(myLoc);
        float distToRobot = bulletLoc.distanceTo(myLoc);
        float theta = propagationDirection.radiansBetween(directionToRobot);

        if (Math.abs(theta) > Math.PI / 2) {
            return false;
        }

        float perpendicularDist = (float)Math.abs(distToRobot * Math.sin(theta));
        return perpendicularDist <= rc.getType().bodyRadius;
    }

    static boolean isInDanger(MapLocation loc) throws GameActionException {
        RobotInfo[] nearbyEnemies = rc.senseNearbyRobots(loc, 10, enemyTeam);
        return nearbyEnemies.length > 0;
    }

    static boolean hasNearbyEnemies(float radius) throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(radius, enemyTeam);
        return enemies.length > 0;
    }

    static boolean hasNearbyAllies(float radius) throws GameActionException {
        RobotInfo[] allies = rc.senseNearbyRobots(radius, myTeam);
        return allies.length > 0;
    }

    static boolean shouldApproachEnemy(RobotInfo enemy) throws GameActionException {
        int myRobots = countMyUnits(RobotType.SOLDIER) + countMyUnits(RobotType.LUMBERJACK);
        RobotInfo[] enemyRobots = rc.senseNearbyRobots(enemy.location, 10, enemyTeam);

        if (enemyRobots.length > myRobots * 1.5) {
            return false;
        }
        return true;
    }

    static int countMyUnits(RobotType type) throws GameActionException {
        RobotInfo[] robots = rc.senseNearbyRobots(-1, myTeam);
        int count = 0;
        for (RobotInfo r : robots) {
            if (r.type == type) count++;
        }
        return count;
    }

    static MapLocation getMidpointBetweenArchons() {
        MapLocation center = getArchonCenter();
        if (center == null) return null;
        return new MapLocation(center.x + 10, center.y + 10);
    }

    static Direction getDirectionToRallyPoint() throws GameActionException {
        if (rallyPoint == null) {
            int msg = rc.readBroadcast(BROADCAST_RALLY_POINT);
            if (msg > 0) {
                rallyPoint = new MapLocation(msg / 100, msg % 100);
            }
        }
        if (rallyPoint == null) return null;
        return myLocation.directionTo(rallyPoint);
    }
}
