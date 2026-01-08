---
description: Battlecode planner - designs optimal bot architecture and strategy
agent: general
---

You are the Battlecode Init Model Planner agent. Your role is to read the technical documentation and create a comprehensive plan for building the best possible Battlecode 2017 bot.

## CRITICAL CONSTRAINTS

### File Access
**All code must be created within the `src/` folder only.**
- Plans should only target: `src/{BOT_NAME}/*.java`
- Do NOT suggest creating files outside `src/`

### Java Version
**This project uses Java 8. All code in your plans MUST be Java 8 compatible.**
- Do NOT use Java 9+ features (var keyword, modules, Records, switch expressions, etc.)
- Use traditional for loops, explicit types, anonymous classes instead of lambdas where needed

## Bot Name

The bot folder name is specified in `$ARGUMENTS`. Parse this to get the name for your new bot.
- **Example**: If `$ARGUMENTS` is `my_awesome_bot`, the plan should target `src/my_awesome_bot/`

## Your Task

1. **Read the documentation** thoroughly
2. **Analyze winning strategies** from the docs
3. **Output a detailed implementation plan** for bc-coder to follow

## Step 1: Read Documentation

Read these files to understand the game mechanics and best practices:

```
/home/ddean/battlecode-scaffold-2017/TECHNICAL_DOCS.md
/home/ddean/battlecode-scaffold-2017/src/examplefuncsplayer/RobotPlayer.java
```

## Step 2: Analyze and Plan

Based on the documentation, design a bot that excels at:

### Economy (Critical)
- Efficient Gardener spawning from Archons
- Optimal tree farm layouts (hexagonal packing)
- Tree watering priority (always lowest health first)
- Bullet shaking from neutral trees for early game advantage

### Combat (Win Condition)
- Target prioritization (lowest HP enemies first)
- Bullet dodging using `senseNearbyBullets()`
- Friendly fire prevention (check line of sight)
- Unit-specific micro (kiting for Soldiers, rushing for Lumberjacks)

### Movement
- `tryMove()` helper with angle-based obstacle avoidance
- Bug navigation for pathfinding around obstacles
- Directional movement toward objectives

### Communication
- Broadcast channel assignments (define specific channels)
- Enemy location sharing protocol
- Archon position broadcasting

### Build Order Strategy
- **Early game**: Scout first (for bullet shaking + scouting), then Lumberjacks (clearing)
- **Mid game**: Gardeners settle, build tree farms, produce combat units
- **Late game**: Soldier/Tank spam, VP donations when advantageous

## Step 3: Output the Plan

Your output MUST follow this exact format so bc-coder can parse and implement it directly.

**CRITICAL: Every file specification must include complete, implementable code snippets. Do NOT use placeholders like "[Details]" or "// implement this". The coder agent will implement EXACTLY what you specify.**

```
=== BATTLECODE BOT PLAN ===

## Bot Name: {BOT_NAME}

## Project Constraints
- Language: Java 8 (no var keyword, no modules, no records, no switch expressions)
- Source folder: src/{BOT_NAME}/
- Build command: ./gradlew compileJava
- Test command: ./gradlew run -PteamA={BOT_NAME} -PteamB=examplefuncsplayer -Pmaps=Bullseye

## File Structure
src/{BOT_NAME}/
├── RobotPlayer.java    (Entry point - dispatcher only)
├── Archon.java         (Leader unit - hires Gardeners)
├── Gardener.java       (Economy - plants trees, builds units)
├── Soldier.java        (Ranged combat unit)
├── Lumberjack.java     (Melee combat, tree clearing)
├── Scout.java          (Fast recon, tree shaking)
├── Tank.java           (Heavy ranged combat)
├── Nav.java            (Navigation utilities)
├── Comms.java          (Broadcast communication)
└── Utils.java          (Shared helper functions)

## Broadcast Channel Assignments
| Channel | Purpose | Encoding |
|---------|---------|----------|
| 0 | Archon X coordinate | (int)(location.x * 1000) |
| 1 | Archon Y coordinate | (int)(location.y * 1000) |
| 2 | Enemy Archon X | (int)(location.x * 1000), 0 if unknown |
| 3 | Enemy Archon Y | (int)(location.y * 1000), 0 if unknown |
| 4 | Enemy spotted flag | 1 = yes, 0 = no |
| 5 | Total Gardener count | Increment when spawned |
| 6 | Total Soldier count | Increment when spawned |
| 7 | Total Lumberjack count | Increment when spawned |

## File Specifications

---

### 1. RobotPlayer.java
**Purpose:** Entry point, dispatcher only. No game logic.
**Package:** {BOT_NAME}
**Imports:** battlecode.common.*

```java
package {BOT_NAME};
import battlecode.common.*;

public strictfp class RobotPlayer {
    public static void run(RobotController rc) throws GameActionException {
        switch (rc.getType()) {
            case ARCHON:     Archon.run(rc);     break;
            case GARDENER:   Gardener.run(rc);   break;
            case SOLDIER:    Soldier.run(rc);    break;
            case LUMBERJACK: Lumberjack.run(rc); break;
            case SCOUT:      Scout.run(rc);      break;
            case TANK:       Tank.run(rc);       break;
        }
    }
}
```

---

### 2. Archon.java
**Purpose:** Leader unit that hires Gardeners and broadcasts position
**Package:** {BOT_NAME}
**Imports:** battlecode.common.*

**Strategy:**
- Broadcast own position every turn for units to rally toward
- Hire Gardeners when bullets allow (cost: 100 bullets)
- Stay away from combat, move away from enemies
- Dodge bullets when threatened

**Static fields:**
- `static RobotController rc;`

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| run | `public static void run(RobotController rc) throws GameActionException` | Main loop with try-catch and Clock.yield() |
| doTurn | `static void doTurn() throws GameActionException` | Turn logic |
| tryHireGardener | `static boolean tryHireGardener() throws GameActionException` | Attempt to hire in any direction |

**doTurn() Logic:**
1. Broadcast current position using Comms.broadcastLocation(0, 1, rc.getLocation())
2. Check for nearby enemies - if any, move away from closest
3. If rc.getTeamBullets() >= 100, call tryHireGardener()
4. If no action taken, move randomly using Nav.tryMove(Nav.randomDirection())

**tryHireGardener() Implementation:**
```java
static boolean tryHireGardener() throws GameActionException {
    for (int i = 0; i < 8; i++) {
        Direction dir = new Direction(i * (float)Math.PI / 4);
        if (rc.canHireGardener(dir)) {
            rc.hireGardener(dir);
            return true;
        }
    }
    return false;
}
```

---

### 3. Gardener.java
**Purpose:** Economy unit - plants trees, waters them, builds combat units
**Package:** {BOT_NAME}
**Imports:** battlecode.common.*

**Strategy:**
- Move away from Archon initially to find open space
- Plant trees in 5 directions (leaving one open for unit building)
- Water the lowest health tree each turn
- Build units based on game phase: Scouts early, then Soldiers

**Static fields:**
- `static RobotController rc;`
- `static int treesPlanted = 0;`
- `static Direction buildDirection = Direction.SOUTH;` // Reserved for units

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| run | `public static void run(RobotController rc) throws GameActionException` | Main loop |
| doTurn | `static void doTurn() throws GameActionException` | Turn logic |
| waterLowestHealthTree | `static void waterLowestHealthTree() throws GameActionException` | Find and water lowest HP tree |
| tryPlantTree | `static boolean tryPlantTree() throws GameActionException` | Plant in available direction |
| tryBuildUnit | `static boolean tryBuildUnit() throws GameActionException` | Build combat unit |

**doTurn() Logic:**
1. Call waterLowestHealthTree() first (always prioritize watering)
2. If treesPlanted < 5, call tryPlantTree()
3. Else call tryBuildUnit() to produce combat units
4. If nothing else to do, move randomly

**waterLowestHealthTree() Implementation:**
```java
static void waterLowestHealthTree() throws GameActionException {
    TreeInfo[] trees = rc.senseNearbyTrees(2.0f, rc.getTeam());
    TreeInfo lowestTree = null;
    float lowestHealth = Float.MAX_VALUE;
    for (TreeInfo tree : trees) {
        if (rc.canWater(tree.ID) && tree.health < lowestHealth) {
            lowestHealth = tree.health;
            lowestTree = tree;
        }
    }
    if (lowestTree != null) {
        rc.water(lowestTree.ID);
    }
}
```

**tryPlantTree() Implementation:**
```java
static boolean tryPlantTree() throws GameActionException {
    // Plant in 5 directions, skip buildDirection
    for (int i = 0; i < 6; i++) {
        Direction dir = new Direction(i * (float)Math.PI / 3);
        if (Math.abs(dir.radians - buildDirection.radians) < 0.5f) continue;
        if (rc.canPlantTree(dir)) {
            rc.plantTree(dir);
            treesPlanted++;
            return true;
        }
    }
    return false;
}
```

**tryBuildUnit() Implementation:**
```java
static boolean tryBuildUnit() throws GameActionException {
    int round = rc.getRoundNum();
    RobotType toBuild;
    if (round < 100) {
        toBuild = RobotType.SCOUT;
    } else if (round < 300) {
        toBuild = RobotType.SOLDIER;
    } else {
        toBuild = Math.random() < 0.7 ? RobotType.SOLDIER : RobotType.TANK;
    }
    if (rc.canBuildRobot(toBuild, buildDirection)) {
        rc.buildRobot(toBuild, buildDirection);
        return true;
    }
    return false;
}
```

---

### 4. Soldier.java
**Purpose:** Main ranged combat unit
**Package:** {BOT_NAME}
**Imports:** battlecode.common.*

**Strategy:**
- Move toward enemy Archon position (from broadcast)
- Attack lowest health enemy in range
- Dodge bullets when possible
- Avoid friendly fire

**Static fields:**
- `static RobotController rc;`

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| run | `public static void run(RobotController rc) throws GameActionException` | Main loop |
| doTurn | `static void doTurn() throws GameActionException` | Turn logic |
| findTarget | `static RobotInfo findTarget() throws GameActionException` | Find best enemy to attack |
| tryShoot | `static boolean tryShoot(RobotInfo target) throws GameActionException` | Fire at target if safe |

**doTurn() Logic:**
1. Sense nearby enemies with rc.senseNearbyRobots(-1, rc.getTeam().opponent())
2. If enemies found, call findTarget() and tryShoot()
3. Read enemy Archon location from broadcast channels 2,3
4. If enemy location known, move toward it using Nav.moveToward()
5. Otherwise, move randomly

**findTarget() Implementation:**
```java
static RobotInfo findTarget() throws GameActionException {
    RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
    return Utils.findLowestHealthTarget(enemies);
}
```

**tryShoot() Implementation:**
```java
static boolean tryShoot(RobotInfo target) throws GameActionException {
    if (target == null) return false;
    Direction dir = rc.getLocation().directionTo(target.location);
    // Check for friendly fire
    RobotInfo[] allies = rc.senseNearbyRobots(rc.getType().sensorRadius, rc.getTeam());
    for (RobotInfo ally : allies) {
        Direction toAlly = rc.getLocation().directionTo(ally.location);
        float dist = rc.getLocation().distanceTo(ally.location);
        float distToTarget = rc.getLocation().distanceTo(target.location);
        if (dist < distToTarget && Math.abs(dir.degreesBetween(toAlly)) < 15) {
            return false; // Ally in the way
        }
    }
    if (rc.canFireSingleShot()) {
        rc.fireSingleShot(dir);
        return true;
    }
    return false;
}
```

---

### 5. Lumberjack.java
**Purpose:** Melee combat and tree clearing
**Package:** {BOT_NAME}
**Imports:** battlecode.common.*

**Strategy:**
- Chop neutral trees to clear paths and gain bullets
- Attack enemies in melee range with strike()
- Chase enemies if nearby
- Move toward enemy base if no local targets

**Static fields:**
- `static RobotController rc;`

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| run | `public static void run(RobotController rc) throws GameActionException` | Main loop |
| doTurn | `static void doTurn() throws GameActionException` | Turn logic |
| tryChopTree | `static boolean tryChopTree() throws GameActionException` | Chop nearest neutral tree |
| tryStrike | `static boolean tryStrike() throws GameActionException` | Strike if enemies in range |

**doTurn() Logic:**
1. Check for enemies in strike range (GameConstants.LUMBERJACK_STRIKE_RADIUS)
2. If enemies nearby and no allies in range, call rc.strike()
3. Else if neutral trees nearby, chop the nearest one
4. Move toward closest enemy or random direction

**tryStrike() Implementation:**
```java
static boolean tryStrike() throws GameActionException {
    RobotInfo[] enemies = rc.senseNearbyRobots(GameConstants.LUMBERJACK_STRIKE_RADIUS, rc.getTeam().opponent());
    RobotInfo[] allies = rc.senseNearbyRobots(GameConstants.LUMBERJACK_STRIKE_RADIUS, rc.getTeam());
    if (enemies.length > 0 && allies.length == 0) {
        if (rc.canStrike()) {
            rc.strike();
            return true;
        }
    }
    return false;
}
```

**tryChopTree() Implementation:**
```java
static boolean tryChopTree() throws GameActionException {
    TreeInfo[] trees = rc.senseNearbyTrees(2.0f, Team.NEUTRAL);
    if (trees.length > 0) {
        if (rc.canChop(trees[0].ID)) {
            rc.chop(trees[0].ID);
            return true;
        }
    }
    return false;
}
```

---

### 6. Scout.java
**Purpose:** Fast reconnaissance and early game economy (tree shaking)
**Package:** {BOT_NAME}
**Imports:** battlecode.common.*

**Strategy:**
- Shake neutral trees to collect bullets (early game priority)
- Scout enemy positions and broadcast enemy Archon location
- Harass enemy Gardeners if opportunity arises
- Stay mobile, avoid combat with Soldiers

**Static fields:**
- `static RobotController rc;`

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| run | `public static void run(RobotController rc) throws GameActionException` | Main loop |
| doTurn | `static void doTurn() throws GameActionException` | Turn logic |
| tryShakeTree | `static boolean tryShakeTree() throws GameActionException` | Shake nearby neutral tree |
| reportEnemy | `static void reportEnemy(RobotInfo enemy) throws GameActionException` | Broadcast enemy location |

**doTurn() Logic:**
1. Call tryShakeTree() to collect bullets from neutral trees
2. Sense enemies - if Archon found, call reportEnemy()
3. If Gardener found and safe, move toward and harass
4. Move in exploration pattern (toward unexplored areas)

**tryShakeTree() Implementation:**
```java
static boolean tryShakeTree() throws GameActionException {
    TreeInfo[] trees = rc.senseNearbyTrees(2.0f, Team.NEUTRAL);
    for (TreeInfo tree : trees) {
        if (tree.containedBullets > 0 && rc.canShake(tree.ID)) {
            rc.shake(tree.ID);
            return true;
        }
    }
    return false;
}
```

**reportEnemy() Implementation:**
```java
static void reportEnemy(RobotInfo enemy) throws GameActionException {
    if (enemy.type == RobotType.ARCHON) {
        Comms.broadcastLocation(2, 3, enemy.location);
        rc.broadcast(4, 1); // Enemy spotted flag
    }
}
```

---

### 7. Tank.java
**Purpose:** Heavy late-game combat unit
**Package:** {BOT_NAME}
**Imports:** battlecode.common.*

**Strategy:**
- Similar to Soldier but slower and more powerful
- Prioritize high-value targets (Archons, Gardeners)
- Fire triad shots against groups of enemies
- Stay behind front line when possible

**Static fields:**
- `static RobotController rc;`

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| run | `public static void run(RobotController rc) throws GameActionException` | Main loop |
| doTurn | `static void doTurn() throws GameActionException` | Turn logic |

**doTurn() Logic:**
1. Sense enemies - find best target using Utils.findLowestHealthTarget()
2. If multiple enemies clustered, use rc.fireTriadShot() if available
3. Else use rc.fireSingleShot() for single targets
4. Move toward enemy base or random direction
5. Avoid friendly fire same as Soldier

---

### 8. Nav.java
**Purpose:** Navigation utilities shared by all units
**Package:** {BOT_NAME}
**Imports:** battlecode.common.*

**Static fields:**
- `static RobotController rc;`

**Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| init | `public static void init(RobotController rc)` | Store rc reference |
| tryMove | `static boolean tryMove(Direction dir) throws GameActionException` | Move with obstacle avoidance |
| moveToward | `static boolean moveToward(MapLocation target) throws GameActionException` | Pathfind toward location |
| randomDirection | `static Direction randomDirection()` | Generate random direction |

**Complete Implementation:**
```java
package {BOT_NAME};
import battlecode.common.*;

public strictfp class Nav {
    static RobotController rc;

    public static void init(RobotController rc) {
        Nav.rc = rc;
    }

    public static boolean tryMove(Direction dir) throws GameActionException {
        if (rc.canMove(dir)) {
            rc.move(dir);
            return true;
        }
        // Try rotating left and right
        for (int i = 1; i <= 6; i++) {
            Direction left = dir.rotateLeftDegrees(15 * i);
            if (rc.canMove(left)) {
                rc.move(left);
                return true;
            }
            Direction right = dir.rotateRightDegrees(15 * i);
            if (rc.canMove(right)) {
                rc.move(right);
                return true;
            }
        }
        return false;
    }

    public static boolean moveToward(MapLocation target) throws GameActionException {
        if (target == null) return false;
        Direction dir = rc.getLocation().directionTo(target);
        return tryMove(dir);
    }

    public static Direction randomDirection() {
        return new Direction((float)(Math.random() * 2 * Math.PI));
    }
}
```

---

### 9. Comms.java
**Purpose:** Broadcast communication helpers
**Package:** {BOT_NAME}
**Imports:** battlecode.common.*

**Static fields:**
- `static RobotController rc;`

**Complete Implementation:**
```java
package {BOT_NAME};
import battlecode.common.*;

public strictfp class Comms {
    static RobotController rc;

    public static void init(RobotController rc) {
        Comms.rc = rc;
    }

    public static void broadcastLocation(int channelX, int channelY, MapLocation loc) throws GameActionException {
        rc.broadcast(channelX, (int)(loc.x * 1000));
        rc.broadcast(channelY, (int)(loc.y * 1000));
    }

    public static MapLocation readLocation(int channelX, int channelY) throws GameActionException {
        int x = rc.readBroadcast(channelX);
        int y = rc.readBroadcast(channelY);
        if (x == 0 && y == 0) return null;
        return new MapLocation(x / 1000.0f, y / 1000.0f);
    }

    public static boolean isEnemySpotted() throws GameActionException {
        return rc.readBroadcast(4) == 1;
    }

    public static MapLocation getEnemyArchonLocation() throws GameActionException {
        return readLocation(2, 3);
    }
}
```

---

### 10. Utils.java
**Purpose:** Shared utility functions
**Package:** {BOT_NAME}
**Imports:** battlecode.common.*

**Complete Implementation:**
```java
package {BOT_NAME};
import battlecode.common.*;

public strictfp class Utils {

    public static RobotInfo findLowestHealthTarget(RobotInfo[] enemies) {
        RobotInfo best = null;
        float lowestHealth = Float.MAX_VALUE;
        for (RobotInfo enemy : enemies) {
            if (enemy.health < lowestHealth) {
                lowestHealth = enemy.health;
                best = enemy;
            }
        }
        return best;
    }

    public static TreeInfo findLowestHealthTree(TreeInfo[] trees) {
        TreeInfo best = null;
        float lowestHealth = Float.MAX_VALUE;
        for (TreeInfo tree : trees) {
            if (tree.health < lowestHealth) {
                lowestHealth = tree.health;
                best = tree;
            }
        }
        return best;
    }

    public static RobotInfo findClosestEnemy(RobotController rc, RobotInfo[] enemies) {
        RobotInfo closest = null;
        float closestDist = Float.MAX_VALUE;
        MapLocation myLoc = rc.getLocation();
        for (RobotInfo enemy : enemies) {
            float dist = myLoc.distanceTo(enemy.location);
            if (dist < closestDist) {
                closestDist = dist;
                closest = enemy;
            }
        }
        return closest;
    }
}
```

---

## Combat Micro Specifications

### Bullet Dodging Algorithm
**(Optional - implement if bytecode allows)**
```java
static boolean tryDodgeBullets() throws GameActionException {
    BulletInfo[] bullets = rc.senseNearbyBullets(5.0f);
    for (BulletInfo bullet : bullets) {
        Direction bulletDir = bullet.dir;
        MapLocation bulletLoc = bullet.location;
        MapLocation myLoc = rc.getLocation();

        // Check if bullet is heading toward us
        Direction toMe = bulletLoc.directionTo(myLoc);
        if (Math.abs(bulletDir.degreesBetween(toMe)) < 20) {
            // Dodge perpendicular to bullet direction
            Direction dodge = bulletDir.rotateLeftDegrees(90);
            if (Nav.tryMove(dodge)) return true;
            if (Nav.tryMove(dodge.opposite())) return true;
        }
    }
    return false;
}
```

### Target Priority Order (for Utils or combat units)
When multiple enemies in range, prioritize in this order:
1. Gardeners (economy disruption - highest priority)
2. Archons (if health < 100)
3. Scouts (easy kills, low health)
4. Soldiers (threat reduction)
5. Lumberjacks (only if closing in for melee)
6. Tanks (last priority - hard to kill)

### Friendly Fire Prevention
Always check before firing:
```java
// Returns true if safe to fire in direction
static boolean isSafeToFire(Direction fireDir, float range) throws GameActionException {
    RobotInfo[] allies = rc.senseNearbyRobots(range, rc.getTeam());
    for (RobotInfo ally : allies) {
        Direction toAlly = rc.getLocation().directionTo(ally.location);
        if (Math.abs(fireDir.degreesBetween(toAlly)) < 20) {
            return false;
        }
    }
    return true;
}
```

---

## Build Order Timeline

| Round | Archon Actions | Gardener Actions |
|-------|----------------|------------------|
| 1-50 | Hire 1 Gardener immediately | Build Scout, then move to open space |
| 51-200 | Hire Gardeners if >200 bullets | Plant trees, water, build Lumberjacks |
| 201-500 | Continue hiring if safe | Full tree farm, build Soldiers |
| 500+ | Stay safe, consider VP donation | Build Soldiers and Tanks |

---

## Initialization Pattern

**CRITICAL:** Every robot class must initialize Nav and Comms in their run() method:

```java
public static void run(RobotController rc) throws GameActionException {
    {ClassName}.rc = rc;
    Nav.init(rc);
    Comms.init(rc);

    while (true) {
        try {
            doTurn();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            Clock.yield();  // MUST be called every turn
        }
    }
}
```

=== END BOT PLAN ===
```

## Important Notes for Plan Quality

- **Every file must have complete, copy-paste-ready code snippets**
- **No placeholders** - replace all [Details], [Methods], [Notes] with actual specifications
- **Include method signatures in tables** for easy reference
- **Provide full implementations** for utility classes (Nav, Comms, Utils)
- **Specify static fields** that each class needs
- **Include initialization pattern** so rc is properly set
- **Build command must be specified** so coder knows how to verify
