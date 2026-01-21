package training_bot;

import battlecode.common.*;

/**
 * Gardener - Economy and production unit (SHOWCASE MODE).
 *
 * This Gardener demonstrates ALL mechanics for LLMs to learn from:
 * - Plants and waters Bullet Trees for income (2 trees)
 * - Builds ONE of each combat unit type to showcase their behaviors:
 *   1. LUMBERJACK - Tree clearing, shaking, strike AoE
 *   2. SCOUT - Fast recon, harassment, tree shaking
 *   3. TANK - Heavy siege, tree trampling (built early!)
 *   4. SOLDIER - Main ranged combat, bullet dodging
 *
 * Build order is designed to show all unit types including the expensive Tank.
 */
public strictfp class Gardener {
    private static RobotController rc;
    private static int treesPlanted = 0;
    private static final int MAX_TREES = 2; // Plant 2 trees for income

    // Track what we've built for showcase purposes
    private static int lumberjacksBuilt = 0;
    private static int scoutsBuilt = 0;
    private static int soldiersBuilt = 0;
    private static int tanksBuilt = 0;

    // Unit costs from TECHNICAL_DOCS (hardcoded for reliability)
    private static final float LUMBERJACK_COST = 100f;
    private static final float SCOUT_COST = 80f;
    private static final float SOLDIER_COST = 100f;
    private static final float TANK_COST = 300f;

    public static void run(RobotController rc) throws GameActionException {
        Gardener.rc = rc;
        Navigation.init(rc);

        int round = rc.getRoundNum();
        float bullets = rc.getTeamBullets();

        // PRIORITY 1: Always water trees first (maintains income)
        waterTrees();

        // PRIORITY 2: Plant trees early for income (up to 2)
        // Plant first 2 trees before heavy unit production
        if (treesPlanted < MAX_TREES) {
            boolean planted = tryPlantTree();
            if (planted) {
                return; // Focus on planting early
            }
        }

        // PRIORITY 3: Build showcase units
        tryBuildShowcaseUnits();
    }

    /**
     * Waters the lowest-health tree in range.
     * MECHANIC: rc.water(treeID) heals trees - essential for Bullet Tree income.
     * Trees generate ~1 bullet/turn when healthy, so watering is critical!
     */
    private static void waterTrees() throws GameActionException {
        TreeInfo[] trees = rc.senseNearbyTrees(2f, rc.getTeam());

        if (trees.length == 0) {
            return;
        }

        // Find tree with lowest health (prioritize weakest)
        TreeInfo lowestHealthTree = null;
        float lowestHealth = Float.MAX_VALUE;

        for (TreeInfo tree : trees) {
            if (tree.health < lowestHealth && rc.canWater(tree.ID)) {
                lowestHealth = tree.health;
                lowestHealthTree = tree;
            }
        }

        if (lowestHealthTree != null) {
            rc.water(lowestHealthTree.ID);
            // Only log occasionally to reduce spam
            if (rc.getRoundNum() % 100 == 0) {
                System.out.println("GARDENER: Watering tree (health: " + lowestHealth + ")");
            }
        }
    }

    /**
     * Plants a Bullet Tree for income.
     * MECHANIC: rc.plantTree(dir) creates a tree that generates bullets over time.
     * We plant 2 trees to boost economy before building expensive units like Tank.
     *
     * @return true if a tree was planted
     */
    private static boolean tryPlantTree() throws GameActionException {
        // Plant in 6 directions (hexagonal pattern)
        float[] plantAngles = {
            0, (float) Math.PI / 3, (float) (2 * Math.PI / 3),
            (float) Math.PI, (float) (4 * Math.PI / 3), (float) (5 * Math.PI / 3)
        };

        for (float angle : plantAngles) {
            Direction dir = new Direction(angle);
            if (rc.canPlantTree(dir)) {
                rc.plantTree(dir);
                treesPlanted++;
                System.out.println("GARDENER R" + rc.getRoundNum() +
                    ": Planted tree #" + treesPlanted + "/" + MAX_TREES + " for income");
                return true;
            }
        }

        return false;
    }

    /**
     * Builds units in showcase order to demonstrate all unit types.
     *
     * SHOWCASE BUILD ORDER (with Tank early!):
     * 1. LUMBERJACK - Clears trees, collects bullets, melee AoE combat
     * 2. SCOUT - Fast movement, huge vision, harassment
     * 3. TANK - Heavy damage, tree trampling, siege (300 bullets - built early!)
     * 4. SOLDIER - Ranged combat, bullet dodging, main army unit
     *
     * MECHANIC: rc.buildRobot(type, dir) spawns a new unit.
     *
     * @return true if a unit was built this turn
     */
    private static boolean tryBuildShowcaseUnits() throws GameActionException {
        float bullets = rc.getTeamBullets();
        int round = rc.getRoundNum();

        // Determine what to build next in showcase order
        RobotType toBuild = null;
        String reason = "";

        // SHOWCASE PHASE: Build one of each unit type
        // Order: LUMBERJACK -> SCOUT -> TANK -> SOLDIER
        if (lumberjacksBuilt < 1 && bullets >= LUMBERJACK_COST) {
            toBuild = RobotType.LUMBERJACK;
            reason = "LUMBERJACK #1 - demonstrates chop(), shake(), strike()";
        }
        else if (scoutsBuilt < 1 && bullets >= SCOUT_COST) {
            toBuild = RobotType.SCOUT;
            reason = "SCOUT #1 - demonstrates fast movement & harassment";
        }
        else if (tanksBuilt < 1 && bullets >= TANK_COST) {
            // Build TANK early to showcase it!
            toBuild = RobotType.TANK;
            reason = "TANK #1 - demonstrates heavy combat & tree trampling";
        }
        else if (soldiersBuilt < 1 && bullets >= SOLDIER_COST) {
            toBuild = RobotType.SOLDIER;
            reason = "SOLDIER #1 - demonstrates ranged combat & dodging";
        }
        // After showcase complete: build more units
        else if (bullets >= SOLDIER_COST + 50) {
            int totalBuilt = lumberjacksBuilt + scoutsBuilt + soldiersBuilt + tanksBuilt;
            switch (totalBuilt % 4) {
                case 0: toBuild = RobotType.SOLDIER; reason = "Additional SOLDIER"; break;
                case 1: toBuild = RobotType.LUMBERJACK; reason = "Additional LUMBERJACK"; break;
                case 2: toBuild = RobotType.SOLDIER; reason = "Additional SOLDIER"; break;
                case 3: toBuild = RobotType.SCOUT; reason = "Additional SCOUT"; break;
            }
        }

        if (toBuild == null) {
            // Debug: why aren't we building?
            if (round % 100 == 0) {
                System.out.println("GARDENER R" + round + ": Saving bullets=" + (int)bullets +
                    " (L=" + lumberjacksBuilt + " Sc=" + scoutsBuilt +
                    " T=" + tanksBuilt + " So=" + soldiersBuilt + ")");
            }
            return false;
        }

        // Try to build in all 8 directions
        for (int i = 0; i < 8; i++) {
            Direction dir = new Direction(i * (float) Math.PI / 4);
            if (rc.canBuildRobot(toBuild, dir)) {
                rc.buildRobot(toBuild, dir);
                updateBuildCount(toBuild);
                System.out.println("GARDENER R" + round + ": Built " + reason);
                return true;
            }
        }

        // Couldn't build - maybe blocked? Try to move
        if (!rc.hasMoved()) {
            Navigation.wander();
        }

        return false;
    }

    /**
     * Updates build counters and broadcasts.
     */
    private static void updateBuildCount(RobotType type) throws GameActionException {
        switch (type) {
            case SOLDIER:
                soldiersBuilt++;
                Comms.incrementCounter(Comms.CHANNEL_SOLDIER_COUNT);
                break;
            case LUMBERJACK:
                lumberjacksBuilt++;
                Comms.incrementCounter(Comms.CHANNEL_LUMBERJACK_COUNT);
                break;
            case SCOUT:
                scoutsBuilt++;
                Comms.incrementCounter(Comms.CHANNEL_SCOUT_COUNT);
                break;
            case TANK:
                tanksBuilt++;
                Comms.incrementCounter(Comms.CHANNEL_TANK_COUNT);
                break;
            default:
                break;
        }
    }
}
