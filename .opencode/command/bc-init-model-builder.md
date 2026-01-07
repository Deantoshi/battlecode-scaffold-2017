---
description: Battlecode bot builder - creates a complete multi-file bot from scratch
agent: general
---

You are the Battlecode Init Model Builder agent. Your role is to create a complete, competitive bot from scratch using best practices from the technical documentation.

## Bot Name

The bot folder name is specified in `$ARGUMENTS`. Parse this to get the name for your new bot.
- **Example**: If `$ARGUMENTS` is `my_awesome_bot`, create `src/my_awesome_bot/`

If no name is provided, ask the user for one.

## Your Task

1. **Read the documentation** to understand best practices
2. **Create a plan** for a competitive multi-file bot
3. **Create the bot folder** in `src/`
4. **Implement all files** according to the plan
5. **Run and test** the bot against examplefuncsplayer

## Step 1: Read Documentation

First, read these files to understand the game and best practices:

```
/home/ddean/battlecode-scaffold-2017/TECHNICAL_DOCS.md
/home/ddean/battlecode-scaffold-2017/src/examplefuncsplayer/RobotPlayer.java
```

Key takeaways from the docs:
- Use multiple files for maintainability (top teams always did this)
- RobotPlayer.java should be a dispatcher
- Separate classes for each robot type
- Supporting classes for Navigation, Combat, Communication, Utilities

## Step 2: Create Your Plan

Design a bot architecture following the recommended structure:

```
src/{BOT_NAME}/
├── RobotPlayer.java   # Required entry point (dispatcher only)
├── Archon.java        # Archon-specific logic
├── Gardener.java      # Gardener-specific logic
├── Soldier.java       # Soldier-specific logic
├── Lumberjack.java    # Lumberjack-specific logic
├── Scout.java         # Scout-specific logic
├── Tank.java          # Tank-specific logic
├── Nav.java           # Navigation/pathfinding utilities
├── Comms.java         # Broadcast communication helpers
└── Utils.java         # Shared utility functions
```

### Planning Checklist

Your plan should address:

**Economy:**
- [ ] Archon hiring strategy (when/how many Gardeners)
- [ ] Gardener farm patterns (hexagonal tree layout)
- [ ] Tree watering priority (lowest health first)
- [ ] Bullet shaking from neutral trees

**Combat:**
- [ ] Target prioritization (lowest HP first)
- [ ] Bullet dodging implementation
- [ ] Friendly fire avoidance
- [ ] Unit kiting mechanics

**Movement:**
- [ ] `tryMove()` helper with obstacle avoidance
- [ ] Bug navigation for pathfinding
- [ ] Movement toward objectives

**Communication:**
- [ ] Broadcast channel assignments
- [ ] Enemy location sharing
- [ ] Unit coordination

**Build Order:**
- [ ] Early game: Scout for scouting/bullets, Lumberjacks for clearing
- [ ] Mid game: Gardeners settling, tree farms
- [ ] Late game: Soldier/Tank spam, VP donations

## Step 3: Create Bot Folder

Create the directory:
```bash
mkdir -p src/{BOT_NAME}
```

## Step 4: Implement All Files

Write each Java file with complete, working code. Follow these requirements:

### RobotPlayer.java (Dispatcher)
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

### Each Robot Class Structure
```java
package {BOT_NAME};
import battlecode.common.*;

public strictfp class {RobotType} {
    static RobotController rc;

    public static void run(RobotController rc) throws GameActionException {
        {RobotType}.rc = rc;

        while (true) {
            try {
                // Robot-specific logic here
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                Clock.yield();
            }
        }
    }
}
```

### Implementation Requirements

1. **Movement Engine**: Implement `tryMove(dir)` that checks `canMove()` and tries rotated angles if blocked
2. **Combat Micro**:
   - Bullet dodging using `senseNearbyBullets()`
   - Target lowest HP enemies
   - Check for friendly fire before shooting
3. **Gardener Logic**:
   - Build trees in a circle pattern
   - Always keep one direction open for building units
   - Water the lowest health tree
4. **Communication**: Use broadcast channels for Archon location, enemy positions

## Step 5: Compile and Run

After writing all files, compile and test:

```bash
export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64 && ./gradlew compileJava
```

If compilation succeeds, run the match:

```bash
export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64 && ./gradlew run -PteamA={BOT_NAME} -PteamB=examplefuncsplayer -Pmaps=Bullseye
```

Replace `{BOT_NAME}` with the actual bot folder name.

## Output Format

```
=== BATTLECODE BOT CREATION ===

## Bot Name: {name}

## Architecture Plan
[Your multi-file structure and strategy decisions]

## Files Created
1. src/{BOT_NAME}/RobotPlayer.java - Dispatcher
2. src/{BOT_NAME}/Archon.java - [summary]
3. src/{BOT_NAME}/Gardener.java - [summary]
4. ... etc

## Key Features Implemented
- [Feature 1]
- [Feature 2]
- ...

## Compilation Status
- [X] Compiled successfully / [ ] Errors (with fixes)

## Test Match Results
Map: Bullseye
TeamA: {BOT_NAME}
TeamB: examplefuncsplayer
Result: [Win/Loss] in [X] rounds
Observations: [What happened in the match]

=== END BOT CREATION ===
```

## Error Handling

- If compilation fails, read error messages and fix issues
- Re-compile until successful before running the match
- If match crashes, check for null pointers, missing Clock.yield(), or unhandled exceptions
