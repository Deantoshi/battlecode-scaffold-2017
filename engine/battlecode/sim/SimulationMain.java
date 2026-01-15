package battlecode.sim;

import battlecode.common.BodyInfo;
import battlecode.common.GameConstants;
import battlecode.common.MapLocation;
import battlecode.common.RobotType;
import battlecode.common.Team;
import battlecode.common.TreeInfo;
import battlecode.common.RobotInfo;
import battlecode.server.GameInfo;
import battlecode.server.GameMaker;
import battlecode.server.GameState;
import battlecode.world.GameMapIO;
import battlecode.world.GameWorld;
import battlecode.world.InternalRobot;
import battlecode.world.LiveMap;
import battlecode.world.ObjectInfo;
import battlecode.world.DominationFactor;
import battlecode.world.control.NullControlProvider;
import battlecode.world.control.PlayerControlProvider;
import battlecode.world.control.RobotControlProvider;
import battlecode.world.control.TeamControlProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SimulationMain {
    private static final int MAX_ROUNDS = 3000;
    private static final String COMBAT_MAP = "Shrine";
    private static final String NAV_MAP = "Bullseye";
    private static final float CLEARANCE_EPSILON = 0.01f;

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            usage("Missing mode.");
        }

        String mode = args[0].toLowerCase();
        Map<String, String> options = parseOptions(args);

        switch (mode) {
            case "combat":
                runCombat(options);
                break;
            case "navigation":
                runNavigation(options);
                break;
            default:
                usage("Unknown mode: " + mode);
        }
    }

    private static void runCombat(Map<String, String> options) throws Exception {
        String teamA = requireOption(options, "teamA");
        String teamB = requireOption(options, "teamB");
        String teamAUrl = requireOption(options, "teamAUrl", "bc.game.team-a.url");
        String teamBUrl = requireOption(options, "teamBUrl", "bc.game.team-b.url");
        String mapName = getOptionalOption(options, "map", COMBAT_MAP);
        File saveFile = getOptionalFile(options, "saveFile");
        if (saveFile == null) {
            saveFile = defaultSaveFile("combat", teamA + "-vs-" + teamB, mapName, null);
        }

        LiveMap baseMap = GameMapIO.loadMap(mapName, new File("maps"));
        LiveMap simMap = buildCombatMap(baseMap, 5);

        GameMaker gameMaker = new GameMaker(
                new GameInfo(teamA, teamA, teamAUrl, teamB, teamB, teamBUrl,
                        new String[] { simMap.getMapName() }, null, false),
                null
        );
        gameMaker.makeGameHeader();

        RobotControlProvider controlProvider = createControlProvider(gameMaker, teamA, teamAUrl, teamB, teamBUrl, true);
        GameWorld world = new GameWorld(simMap, controlProvider,
                new long[2][GameConstants.TEAM_MEMORY_LENGTH], gameMaker.getMatchMaker());

        GameState state = GameState.RUNNING;
        int maxRounds = Math.min(MAX_ROUNDS, simMap.getRounds());
        int roundLimit = maxRounds + 1;
        while (state == GameState.RUNNING && world.getCurrentRound() < roundLimit) {
            state = world.runRound();
        }

        Team winner = world.getWinner();
        if (winner == null) {
            winner = Team.NEUTRAL;
        }
        String winnerName = winner == Team.A ? teamA : (winner == Team.B ? teamB : "none");
        System.out.println("[combat] winner=" + winnerName + " round=" + world.getCurrentRound());

        if (saveFile != null) {
            ensureParentDir(saveFile);
            gameMaker.makeGameFooter(winner);
            gameMaker.writeGame(saveFile);
            System.out.println("[combat] replay=" + saveFile.getPath());
        }
    }

    private static void runNavigation(Map<String, String> options) throws Exception {
        String teamA = requireOption(options, "teamA");
        String teamAUrl = requireOption(options, "teamAUrl", "bc.game.team-a.url");
        RobotType unitType = parseRobotType(requireOption(options, "unitType"));
        String mapName = getOptionalOption(options, "map", NAV_MAP);
        File saveFile = getOptionalFile(options, "saveFile");
        if (saveFile == null) {
            saveFile = defaultSaveFile("navigation", teamA + "-vs-hidden", mapName, unitType.name());
        }

        LiveMap baseMap = GameMapIO.loadMap(mapName, new File("maps"));
        NavSetup navSetup = buildNavigationMap(baseMap, unitType);

        GameMaker gameMaker = new GameMaker(
                new GameInfo(teamA, teamA, teamAUrl, "hidden", "hidden", "hidden",
                        new String[] { navSetup.map.getMapName() }, null, false),
                null
        );
        gameMaker.makeGameHeader();

        RobotControlProvider controlProvider = createControlProvider(gameMaker, teamA, teamAUrl, "hidden", null, false);
        GameWorld world = new GameWorld(navSetup.map, controlProvider,
                new long[2][GameConstants.TEAM_MEMORY_LENGTH], gameMaker.getMatchMaker());

        Integer successRound = null;
        boolean winnerForced = false;
        boolean stopChecks = false;
        GameState state = GameState.RUNNING;
        int maxRounds = Math.min(MAX_ROUNDS, navSetup.map.getRounds());
        int roundLimit = maxRounds + 1;

        while (state == GameState.RUNNING && world.getCurrentRound() < roundLimit) {
            state = world.runRound();
            if (stopChecks) {
                continue;
            }

            ObjectInfo info = world.getObjectInfo();
            InternalRobot testRobot = info.getRobotByID(navSetup.testRobotId);
            InternalRobot hiddenArchon = info.getRobotByID(navSetup.hiddenArchonId);

            if (testRobot == null) {
                if (!winnerForced) {
                    world.setWinner(Team.B, DominationFactor.DESTROYED);
                    winnerForced = true;
                }
                stopChecks = true;
                continue;
            }
            if (hiddenArchon == null) {
                successRound = world.getCurrentRound();
                if (!winnerForced) {
                    world.setWinner(Team.A, DominationFactor.DESTROYED);
                    winnerForced = true;
                }
                stopChecks = true;
                continue;
            }

            float distance = testRobot.getLocation().distanceTo(hiddenArchon.getLocation());
            if (distance <= testRobot.getType().sensorRadius) {
                successRound = world.getCurrentRound();
                if (!winnerForced) {
                    world.setWinner(Team.A, DominationFactor.PWNED);
                    winnerForced = true;
                }
                stopChecks = true;
                continue;
            }

            if (!world.isRunning()) {
                stopChecks = true;
            }
        }

        boolean success = successRound != null;
        int round = success ? successRound : world.getCurrentRound();
        System.out.println("[navigation] success=" + success + " round=" + round);

        if (saveFile != null) {
            ensureParentDir(saveFile);
            Team winner = world.getWinner();
            if (winner == null) {
                winner = Team.NEUTRAL;
            }
            gameMaker.makeGameFooter(winner);
            gameMaker.writeGame(saveFile);
            System.out.println("[navigation] replay=" + saveFile.getPath());
        }
    }

    private static RobotControlProvider createControlProvider(GameMaker gameMaker,
                                                              String teamA,
                                                              String teamAUrl,
                                                              String teamB,
                                                              String teamBUrl,
                                                              boolean usePlayerTeamB) {
        TeamControlProvider teamProvider = new TeamControlProvider();
        teamProvider.registerControlProvider(
                Team.A,
                new PlayerControlProvider(teamA, teamAUrl, gameMaker.getMatchMaker().getOut())
        );
        if (usePlayerTeamB) {
            teamProvider.registerControlProvider(
                    Team.B,
                    new PlayerControlProvider(teamB, teamBUrl, gameMaker.getMatchMaker().getOut())
            );
        } else {
            teamProvider.registerControlProvider(Team.B, new NullControlProvider());
        }
        teamProvider.registerControlProvider(Team.NEUTRAL, new NullControlProvider());
        return teamProvider;
    }

    private static LiveMap buildCombatMap(LiveMap baseMap, int soldiersPerTeam) {
        Map<Team, List<MapLocation>> archonLocs = collectArchonLocations(baseMap);
        List<MapLocation> archonsA = archonLocs.get(Team.A);
        List<MapLocation> archonsB = archonLocs.get(Team.B);
        if (archonsA.isEmpty() || archonsB.isEmpty()) {
            throw new IllegalStateException("Map missing archon spawns for one or both teams.");
        }

        List<TreeInfo> trees = collectTrees(baseMap);
        List<BodyInfo> bodies = new ArrayList<>();
        int nextId = 0;

        for (TreeInfo tree : trees) {
            bodies.add(copyTree(nextId++, tree));
        }

        for (int i = 0; i < soldiersPerTeam; i++) {
            MapLocation base = archonsA.get(i % archonsA.size());
            MapLocation spawn = findSpawnLocation(base, RobotType.SOLDIER, baseMap, bodies);
            bodies.add(createRobot(nextId++, Team.A, RobotType.SOLDIER, spawn));
        }

        for (int i = 0; i < soldiersPerTeam; i++) {
            MapLocation base = archonsB.get(i % archonsB.size());
            MapLocation spawn = findSpawnLocation(base, RobotType.SOLDIER, baseMap, bodies);
            bodies.add(createRobot(nextId++, Team.B, RobotType.SOLDIER, spawn));
        }

        return new LiveMap(
                baseMap.getWidth(),
                baseMap.getHeight(),
                baseMap.getOrigin(),
                baseMap.getSeed(),
                baseMap.getRounds(),
                baseMap.getMapName(),
                bodies.toArray(new BodyInfo[0])
        );
    }

    private static NavSetup buildNavigationMap(LiveMap baseMap, RobotType unitType) {
        Map<Team, List<MapLocation>> archonLocs = collectArchonLocations(baseMap);
        List<MapLocation> archonsA = archonLocs.get(Team.A);
        List<MapLocation> archonsB = archonLocs.get(Team.B);
        if (archonsA.isEmpty() || archonsB.isEmpty()) {
            throw new IllegalStateException("Map missing archon spawns for one or both teams.");
        }

        List<TreeInfo> trees = collectTrees(baseMap);
        List<BodyInfo> bodies = new ArrayList<>();
        int nextId = 0;

        for (TreeInfo tree : trees) {
            bodies.add(copyTree(nextId++, tree));
        }

        MapLocation testBase = archonsA.get(0);
        MapLocation testSpawn = findSpawnLocation(testBase, unitType, baseMap, bodies);
        int testRobotId = nextId++;
        bodies.add(createRobot(testRobotId, Team.A, unitType, testSpawn));

        MapLocation hiddenBase = archonsB.get(0);
        MapLocation hiddenSpawn = findSpawnLocation(hiddenBase, RobotType.ARCHON, baseMap, bodies);
        int hiddenArchonId = nextId++;
        bodies.add(createRobot(hiddenArchonId, Team.B, RobotType.ARCHON, hiddenSpawn));

        LiveMap map = new LiveMap(
                baseMap.getWidth(),
                baseMap.getHeight(),
                baseMap.getOrigin(),
                baseMap.getSeed(),
                baseMap.getRounds(),
                baseMap.getMapName(),
                bodies.toArray(new BodyInfo[0])
        );

        return new NavSetup(map, testRobotId, hiddenArchonId);
    }

    private static Map<Team, List<MapLocation>> collectArchonLocations(LiveMap baseMap) {
        Map<Team, List<MapLocation>> result = new EnumMap<>(Team.class);
        result.put(Team.A, new ArrayList<>());
        result.put(Team.B, new ArrayList<>());

        for (BodyInfo body : baseMap.getInitialBodies()) {
            if (!body.isRobot()) {
                continue;
            }
            RobotInfo robot = (RobotInfo) body;
            if (robot.getType() == RobotType.ARCHON && (robot.getTeam() == Team.A || robot.getTeam() == Team.B)) {
                result.get(robot.getTeam()).add(robot.getLocation());
            }
        }

        return result;
    }

    private static List<TreeInfo> collectTrees(LiveMap baseMap) {
        List<TreeInfo> trees = new ArrayList<>();
        for (BodyInfo body : baseMap.getInitialBodies()) {
            if (body.isTree()) {
                trees.add((TreeInfo) body);
            }
        }
        return trees;
    }

    private static TreeInfo copyTree(int id, TreeInfo tree) {
        return new TreeInfo(
                id,
                tree.getTeam(),
                tree.getLocation(),
                tree.getRadius(),
                tree.getHealth(),
                tree.getContainedBullets(),
                tree.getContainedRobot()
        );
    }

    private static RobotInfo createRobot(int id, Team team, RobotType type, MapLocation location) {
        return new RobotInfo(id, team, type, location, type.getStartingHealth(), 0, 0);
    }

    private static MapLocation findSpawnLocation(MapLocation base,
                                                 RobotType type,
                                                 LiveMap map,
                                                 List<BodyInfo> occupied) {
        float radius = type.bodyRadius;
        float[] distances = new float[] {
                0.0f,
                radius * 2.0f + 0.25f,
                radius * 4.0f + 0.50f,
                radius * 6.0f + 0.75f
        };
        int angleSteps = 12;

        for (float dist : distances) {
            if (dist == 0.0f) {
                if (isClear(base, radius, map, occupied)) {
                    return base;
                }
                continue;
            }
            for (int i = 0; i < angleSteps; i++) {
                float angle = (float) ((2.0f * Math.PI * i) / angleSteps);
                MapLocation candidate = base.add(angle, dist);
                if (isClear(candidate, radius, map, occupied)) {
                    return candidate;
                }
            }
        }

        System.err.println("[sim] Warning: no clear spawn near " + base + ", using base location.");
        return base;
    }

    private static boolean isClear(MapLocation loc, float radius, LiveMap map, List<BodyInfo> occupied) {
        if (!map.onTheMap(loc, radius)) {
            return false;
        }
        for (BodyInfo body : occupied) {
            float minDist = radius + body.getRadius() + CLEARANCE_EPSILON;
            if (loc.distanceTo(body.getLocation()) <= minDist) {
                return false;
            }
        }
        return true;
    }

    private static RobotType parseRobotType(String value) {
        try {
            return RobotType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            usage("Invalid unitType: " + value);
            return RobotType.SCOUT;
        }
    }

    private static Map<String, String> parseOptions(String[] args) {
        Map<String, String> options = new HashMap<>();
        for (int i = 1; i < args.length; i++) {
            String key = args[i];
            if (!key.startsWith("--")) {
                usage("Invalid option: " + key);
            }
            if (i + 1 >= args.length) {
                usage("Missing value for option: " + key);
            }
            options.put(key.substring(2), args[i + 1]);
            i++;
        }
        return options;
    }

    private static String requireOption(Map<String, String> options, String key) {
        String value = options.get(key);
        if (value == null || value.trim().isEmpty()) {
            usage("Missing --" + key);
        }
        return value.trim();
    }

    private static String requireOption(Map<String, String> options, String key, String sysProp) {
        String value = options.get(key);
        if (value == null || value.trim().isEmpty()) {
            value = System.getProperty(sysProp);
        }
        if (value == null || value.trim().isEmpty()) {
            usage("Missing --" + key + " or system property " + sysProp);
        }
        return value.trim();
    }

    private static String getOptionalOption(Map<String, String> options, String key, String fallback) {
        String value = options.get(key);
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim();
    }

    private static File getOptionalFile(Map<String, String> options, String key) {
        String value = options.get(key);
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return new File(value.trim());
    }

    private static void ensureParentDir(File saveFile) {
        File parent = saveFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
    }

    private static File defaultSaveFile(String mode, String matchup, String mapName, String unitTag) {
        String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
        String base = mode + "_" + sanitize(matchup) + "_on_" + sanitize(mapName);
        if (unitTag != null) {
            base += "_" + sanitize(unitTag);
        }
        base += "_" + timestamp + ".bc17";
        return new File("matches", base);
    }

    private static String sanitize(String value) {
        return value.replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    private static void usage(String reason) {
        System.err.println("[sim] " + reason);
        System.err.println("Usage:");
        System.err.println("  combat --teamA <pkg> --teamB <pkg> --teamAUrl <path> --teamBUrl <path> [--map <MapName>] [--saveFile <path>]");
        System.err.println("  navigation --teamA <pkg> --teamAUrl <path> --unitType <ARCHON|GARDENER|SCOUT|SOLDIER|LUMBERJACK|TANK> [--map <MapName>] [--saveFile <path>]");
        System.exit(64);
    }

    private static final class NavSetup {
        private final LiveMap map;
        private final int testRobotId;
        private final int hiddenArchonId;

        private NavSetup(LiveMap map, int testRobotId, int hiddenArchonId) {
            this.map = map;
            this.testRobotId = testRobotId;
            this.hiddenArchonId = hiddenArchonId;
        }
    }
}
