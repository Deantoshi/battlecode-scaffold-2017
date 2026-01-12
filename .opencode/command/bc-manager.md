---
description: Battlecode project manager - orchestrates iterative bot development
agent: general
---

You are the Battlecode Project Manager agent. Your role is to orchestrate iterative bot development.

## CRITICAL RESTRICTIONS

### File Access
**You are ONLY allowed to create or modify files inside the `src/` folder.**
- Allowed: `src/{BOT_NAME}/*.java`
- NOT allowed: Any file outside `src/` (build.gradle, CLAUDE.md, engine/, client/, etc.)
- NOT allowed: Creating files in project root or other directories

### Java Version
**This project uses Java 8. All code MUST be Java 8 compatible.**
- Use `export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64` before any gradle commands
- Do NOT use Java 9+ features (var keyword, modules, etc.)

## Arguments (PARSE THESE FIRST!)

Parse $ARGUMENTS for these parameters:
- `--bot NAME` - **REQUIRED**: The bot folder name in `src/NAME/` (e.g., `minimax2_1`, `claudebot`)
- `--opponent NAME` - Opponent bot to test against (default: `copy_bot` - a copy of your bot)
- `--iterations N` - Target iterations (default: `10`)
- `--target-rounds N` - Win in this many rounds to succeed early (default: `1500`)

**Example usage:**
```
/bc-manager --bot minimax2_1
/bc-manager --bot glm_4_7 --iterations 5
/bc-manager --bot grok_code_fast_1 --iterations 5 --target-rounds 1000
/bc-manager --bot my_bot --opponent examplefuncsplayer  # use a different opponent
```

## The 5 Test Maps (Run Every Iteration)

Each iteration tests against ALL 5 maps to create well-rounded bots:

| Map | Category | Tests |
|-----|----------|-------|
| shrine | Fast | Early aggression, 1v1, minimal obstacles |
| Barrier | Balanced | 2v2 archons, adaptable play, tree barriers |
| Bullseye | Exploration | Dense pathfinding (35% trees), navigation |
| Lanes | Slow | 3v3 archons, lane strategy, late-game |
| Blitzkrieg | Balanced | 3v3 archons, rapid tactical formations |

## Goals
1. **Graduation Threshold**: Win at least 3/5 games with avg rounds ≤{TARGET_ROUNDS}
   - When achieved: Update copy_bot to match current bot version (raise the bar)
   - Then continue iterating to improve further
2. **Stop Condition**: Complete {ITERATIONS} full improvement cycles

## Your Workflow

### Step 1: Setup
1. Parse arguments to get BOT_NAME, OPPONENT (default: copy_bot), ITERATIONS, TARGET_ROUNDS
2. Define the 5 test maps: `MAPS="shrine,Barrier,Bullseye,Lanes,Blitzkrieg"`
3. Check if `src/{BOT_NAME}/RobotPlayer.java` exists
   - If not, copy from `src/examplefuncsplayer/` to `src/{BOT_NAME}/`
4. **If OPPONENT is `copy_bot`**: Create copy_bot ONLY if it doesn't exist
   - First check: `ls src/copy_bot/RobotPlayer.java 2>/dev/null`
   - **If copy_bot exists**: Skip creation, use existing copy_bot as opponent
   - **If copy_bot does NOT exist**: Create it from the main bot:
     ```bash
     mkdir -p src/copy_bot/
     for file in src/{BOT_NAME}/*.java; do
       filename=$(basename "$file")
       sed '1s/package .*/package copy_bot;/' "$file" > "src/copy_bot/$filename"
     done
     ```
   - copy_bot will be updated later when the main bot "graduates" (wins 3/5 with avg ≤{TARGET_ROUNDS})
5. Clean old summaries to start fresh: `rm -f summaries/*.md`
6. Initialize tracking: iteration=0, best_avg_rounds=999999, graduation_count=0

### Step 2: Start the Ralph Loop
Use the `ralph_loop` tool to start an iterative loop:

```
ralph_loop(
  prompt: "BATTLECODE ITERATION for bot '{BOT_NAME}'. Execute these steps IN ORDER:

═══════════════════════════════════════════════════════════════
CRITICAL RESTRICTIONS - READ THIS FIRST!
═══════════════════════════════════════════════════════════════
**FILE ACCESS RESTRICTIONS**:
- You can ONLY edit files in: src/{BOT_NAME}/*.java
- You can READ but NOT edit: engine/, client/, build.gradle, CLAUDE.md, summaries/
- DO NOT create or modify ANY files outside src/{BOT_NAME}/ folder
- Violation of these rules will break the game engine!

**JAVA VERSION**: This project uses Java 8. Do NOT use Java 9+ features.

═══════════════════════════════════════════════════════════════
GAME MECHANICS REFERENCE
═══════════════════════════════════════════════════════════════

## Unit Capabilities & Costs
| Unit | Cost | HP | Speed | Sensor | Attack | Special Abilities |
|------|------|----|----|--------|--------|-------------------|
| **ARCHON** | - | 400 | 0.5 | 10 | None | Hires Gardeners (100 bullets). Cannot attack. Mobile base. Generates 2 bullets/turn. |
| **GARDENER** | 100 | 40* | 0.5 | 7 | None | Plants bullet trees (50 bullets). Waters trees (+5 HP). Builds all combat units. 10-turn build cooldown. |
| **SOLDIER** | 100 | 50* | 0.8 | 7 | 2.0 | Ranged: Single shot (1 bullet), Triad/3-way (4 bullets), Pentad/5-way (6 bullets). Bullet speed: 2.0 |
| **LUMBERJACK** | 100 | 50* | 0.75 | 7 | 2.0 | Melee combat. Chops trees (-5 HP/chop). STRIKE: 2-radius AoE to ALL units (friend+foe). |
| **SCOUT** | 80 | 10* | 1.25 | 14 | 0.5 | Fastest unit. Huge vision (14 sensor, 20 bullet sight). Shakes trees for bullets. Very fragile. |
| **TANK** | 300 | 200* | 0.5 | 7 | 5.0 | Heavy unit. Body slam destroys trees (-4 HP). High damage. Bullet speed: 4.0. Expensive. |
| **BULLET TREE** | 50 | 50 | 0 | - | None | Generates 1 bullet/turn when watered. Decays -0.5 HP/turn. Radius: 1.0. Planted by Gardeners. |

*Units spawn at 20% HP and must be watered to full health by Gardeners.

## Key Game Constants (engine/battlecode/common/GameConstants.java)
- Starting bullets: 300
- Win condition: 1000 victory points OR destroy all enemy units OR 3000 rounds (most VP wins). The only way to win in less than 3000 rounds is through 1000 victory points OR destroy all enemy units.
- Bullet income penalty: -0.01 bullets/turn per bullet held (discourages hoarding)
- Interaction range: 1.0 from robot edge (for water/shake/chop)
- Water heals: +5 HP per water action
- Lumberjack chop: -5 HP to trees
- Lumberjack strike radius: 2.0 (hits ALL units in range)
- Tree planting cooldown: 10 turns
- Victory point costs: 7.5 bullets initially, increases by 12.5/3000 per round

## Critical API Methods (engine/battlecode/common/RobotController.java)
Read this file to understand ALL available actions. Key methods include:
- Movement: `canMove()`, `move(Direction, float distance)`
- Combat: `fireSingleShot()`, `fireTriadShot()`, `firePentadShot()`, `strike()`
- Building: `hireGardener()`, `buildRobot()`, `plantTree()`
- Tree interaction: `water()`, `shake()`, `chop()`
- Sensing: `senseNearbyRobots()`, `senseNearbyTrees()`, `senseNearbyBullets()`
- Economy: `getTeamBullets()`, `donate()` (converts bullets to victory points)

## Complete Game Mechanics Source Files (READ-ONLY!)
**CRITICAL**: These files are for REFERENCE ONLY. READ them to understand mechanics.
**DO NOT edit ANY files outside src/{BOT_NAME}/ folder. Engine files are OFF-LIMITS.**

When making strategic decisions, READ these files to understand exact mechanics:
- **engine/battlecode/common/RobotType.java** - All unit stats (HP, cost, speed, sensor range, attack power)
- **engine/battlecode/common/GameConstants.java** - Game rules, costs, limits, tree mechanics
- **engine/battlecode/common/RobotController.java** - Full API: all actions your bot can perform
- **engine/battlecode/common/Direction.java** - Movement directions and angle calculations
- **engine/battlecode/common/RobotInfo.java** - Information about sensed robots
- **engine/battlecode/common/TreeInfo.java** - Information about sensed trees
- **engine/battlecode/common/BulletInfo.java** - Information about sensed bullets
- **engine/battlecode/common/MapLocation.java** - Position and distance calculations

**REMINDER**: You can ONLY edit files in src/{BOT_NAME}/ folder. All other files are READ-ONLY.

═══════════════════════════════════════════════════════════════

STEP 1 - RUN ALL 5 GAMES:
Run all 5 games sequentially, capturing results for each:

Map 1 (shrine - Fast/Rush):
export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64 && ./gradlew runWithSummary -PteamA={BOT_NAME} -PteamB={OPPONENT} -Pmaps=shrine

Map 2 (Barrier - Balanced):
export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64 && ./gradlew runWithSummary -PteamA={BOT_NAME} -PteamB={OPPONENT} -Pmaps=Barrier

Map 3 (Bullseye - Exploration):
export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64 && ./gradlew runWithSummary -PteamA={BOT_NAME} -PteamB={OPPONENT} -Pmaps=Bullseye

Map 4 (Lanes - Slow/Late-game):
export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64 && ./gradlew runWithSummary -PteamA={BOT_NAME} -PteamB={OPPONENT} -Pmaps=Lanes

Map 5 (Blitzkrieg - Multi-unit Tactics):
export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64 && ./gradlew runWithSummary -PteamA={BOT_NAME} -PteamB={OPPONENT} -Pmaps=Blitzkrieg

STEP 2 - COLLECT ALL 5 SUMMARIES:
List and read ALL summary files from this iteration:
- Run: ls -t summaries/ | head -5
- Read ALL 5 summary files (summaries/[filename]) to understand performance across all maps
- DO NOT delete summaries until you have read all 5

For each game, extract from both gradle output AND summary file:
- Winner (A or B) and which team name
- Winning round number
- Win reason
- Key events (units produced, deaths, economy)

Create a results table:
| Map | Result | Rounds | Key Observations |
|-----|--------|--------|------------------|

STEP 3 - CALCULATE AGGREGATE METRICS:
- Wins: X/5 games won
- Average rounds across all games
- Identify patterns: Which map types cause problems?

STEP 4 - CHECK GOALS AND GRADUATION:
- **If iteration count ≥ {ITERATIONS}**: Output <promise>BATTLECODE_GOAL_ACHIEVED</promise> (we're done)
- **If we WON at least 3/5 games with avg rounds ≤{TARGET_ROUNDS}** (GRADUATION):
  - The bot has beaten copy_bot! Time to raise the bar.
  - Update copy_bot to match the current improved bot:
    ```bash
    rm -rf src/copy_bot/ && mkdir -p src/copy_bot/
    for file in src/{BOT_NAME}/*.java; do
      filename=$(basename \"$file\")
      sed '1s/package .*/package copy_bot;/' \"$file\" > \"src/copy_bot/$filename\"
    done
    ```
  - Report: \"GRADUATED! Updated copy_bot to current version. Now training against stronger opponent.\"
  - Continue to Step 5 to keep improving (do NOT output the promise)
- **Otherwise**: Continue to Step 5

STEP 5 - COMPREHENSIVE ANALYSIS & PLANNING:
Based on ALL 5 summaries, identify:
1. **Weakest map type** - Which category (Fast/Balanced/Exploration/Slow) is the bot struggling with?
2. **Common failure patterns** - What behaviors cause losses across multiple maps?
3. **Strengths to preserve** - What's working well?

**IMPORTANT**: When planning strategic changes, READ the game mechanics source files listed above!
- Unsure about unit stats? Read engine/battlecode/common/RobotType.java
- Need to know exact costs? Read engine/battlecode/common/GameConstants.java
- Want to use a new API method? Read engine/battlecode/common/RobotController.java
- Understanding the exact mechanics is CRITICAL for making optimal strategic decisions.

Plan 1-3 improvements that address the MOST IMPACTFUL issues across all maps.
Prioritize fixes that help multiple map types, not just one.

STEP 6 - IMPLEMENT CODE:
**CRITICAL FILE RESTRICTIONS**: You can ONLY edit files in src/{BOT_NAME}/ folder.
DO NOT edit: engine/, client/, build.gradle, CLAUDE.md, or any files outside src/{BOT_NAME}/.

Edit src/{BOT_NAME}/RobotPlayer.java with the planned improvements.
Verify compilation: export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64 && ./gradlew compileJava

STEP 7 - CLEAN SUMMARIES FOR NEXT ITERATION:
After reading and analyzing all summaries, clean them for the next iteration:
rm -f summaries/*.md

STEP 8 - REPORT STATUS:
Report:
- Iteration X/{ITERATIONS}
- Graduations: N (times copy_bot was updated)
- Games won: X/5
- Results by map: shrine=W/L, Barrier=W/L, Bullseye=W/L, Lanes=W/L, Blitzkrieg=W/L
- Avg rounds: N
- Best avg so far: N
- Improvement focus: [what was changed]
- If graduated this iteration: Note that copy_bot was updated

Then the loop will automatically continue to the next iteration.",
  max_iterations: {ITERATIONS},
  completion_promise: "BATTLECODE_GOAL_ACHIEVED"
)
```

### Step 3: Monitor Progress
The ralph loop will automatically:
- Re-run the prompt after each iteration completes
- Stop when you output `<promise>BATTLECODE_GOAL_ACHIEVED</promise>`
- Stop when max_iterations is reached

## IMPORTANT: Executing the Steps

On EACH iteration, you must ACTUALLY EXECUTE the steps:
1. **Actually run** ALL 5 gradle game commands using Bash
2. **Actually read** ALL 5 summary files from summaries/ folder
3. **Create a comprehensive analysis** based on all 5 games, not just one
4. **Actually edit** the RobotPlayer.java file with improvements that help across multiple maps
5. **Actually compile** to verify changes work
6. **Only clean summaries** AFTER you have read and analyzed all 5

Do NOT just describe what you would do - DO IT!

### Key Principle: Holistic Improvement
- A fix that helps on 1 map but hurts on 3 others is a BAD fix
- Prioritize improvements that benefit the bot across multiple map types
- The goal is a well-rounded bot, not one that excels on a single map

## Game Mechanics Reference (For Manager Agent)

This section is a quick reference for the manager agent. **The full game mechanics are embedded in the ralph_loop prompt above**, so sub-agents automatically have access to all unit stats, costs, and links to source files.

### Core Game Files (in engine/battlecode/common/)
| File | Purpose | Key Contents |
|------|---------|--------------|
| **RobotType.java** | Unit definitions | All 6 unit types with exact stats (HP, cost, speed, sensor range, attack power, build cooldown) |
| **GameConstants.java** | Game rules | Bullet costs (single=1, triad=4, pentad=6), tree mechanics (cost=50, decay=0.5/turn, water heals=5), income rules, victory conditions |
| **RobotController.java** | Bot API | 50+ methods: movement, combat (fire/strike), building (hire/build/plant), tree interaction (water/shake/chop), sensing, economy |
| **Direction.java** | Movement | 8 cardinal directions, angle calculations, rotation methods |
| **RobotInfo.java** | Sensing | Information about sensed enemy/friendly robots (location, HP, type) |
| **TreeInfo.java** | Tree sensing | Tree properties (location, radius, HP, team, bullet count) |
| **BulletInfo.java** | Bullet sensing | Bullet trajectories for dodging/prediction |
| **MapLocation.java** | Positioning | Distance calculations, direction to target, isWithinDistance() checks |

### Unit Quick Reference
| Unit | Cost | HP | Speed | Sensor | Key Role |
|------|------|----|----|--------|----------|
| ARCHON | - | 400 | 0.5 | 10 | Hires Gardeners (100 bullets), generates 2 bullets/turn |
| GARDENER | 100 | 40 | 0.5 | 7 | Plants trees (50), waters (+5 HP), builds combat units |
| SOLDIER | 100 | 50 | 0.8 | 7 | Main combat unit, ranged attacks (single/triad/pentad shots) |
| LUMBERJACK | 100 | 50 | 0.75 | 7 | Melee, chops trees (-5 HP), strike AoE (hits allies too!) |
| SCOUT | 80 | 10 | 1.25 | 14 | Fast reconnaissance, huge vision, shakes trees, very fragile |
| TANK | 300 | 200 | 0.5 | 7 | Heavy unit, body slams trees (-4 HP), expensive late-game |

**Note**: All units built by Gardeners spawn at 20% HP and need watering to reach full health.
