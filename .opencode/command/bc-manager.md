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

Plan 1-3 improvements that address the MOST IMPACTFUL issues across all maps.
Prioritize fixes that help multiple map types, not just one.

STEP 6 - IMPLEMENT CODE:
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

## Unit Capabilities Reference

| Unit | Cost | Capabilities |
|------|------|--------------|
| **ARCHON** | - | Hires Gardeners. High HP. Cannot attack. Mobile base. |
| **GARDENER** | 100 | Plants bullet trees (income). Waters trees to heal them. Builds combat units. Cannot attack. |
| **SOLDIER** | 100 | Ranged combat. Fires single, triad (3-way), or pentad (5-way) shots. Balanced stats. |
| **LUMBERJACK** | 100 | Melee combat. Chops trees. Strike ability deals AoE damage to ALL nearby units (including allies). |
| **SCOUT** | 80 | Very fast. Huge vision radius. Can shake trees to steal bullets. Extremely fragile. |
| **TANK** | 300 | High HP, high damage. Body slams destroy trees. Expensive late-game unit. |
| **BULLET TREE** | 50 | Generates bullet income when watered. Planted by Gardeners. |
