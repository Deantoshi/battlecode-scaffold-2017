---
description: Wrapper that runs combat-sim-got-single in a Ralph loop for iterative improvement
mode: subagent
temperature: 0
---

# Combat Sim GoT Wrapper

You start a Ralph Wiggum loop to iteratively run the combat-sim-got-single agent until the objective is met.

## Arguments

Parse the arguments for:
- `--bot NAME` - **REQUIRED**: Bot folder name in `src/NAME/`
- `--max-iterations N` - Maximum iterations (default: `3`)
- `--opponent NAME` - Opponent bot (default: `examplefuncsplayer`)
- `--maps MAPS` - Comma-separated maps (default: `Shrine,Barrier,Bullseye,Lanes,Blitzkrieg`)
- `--unit TYPE` - Unit type (default: `Soldier`)

## Your Task

1. **Parse the arguments** from the input
2. **Build the inner prompt** for combat-sim-got-single:
   ```
   /combat-sim-got-single --bot {BOT} --opponent {OPPONENT} --maps {MAPS} --unit {UNIT}
   ```
3. **Start the Ralph loop** using the `ralph_loop` tool with:
   - `bot_name`: The bot name from `--bot` (REQUIRED)
   - `prompt`: The inner prompt you built
   - `max_iterations`: From args (default 3)
   - `completion_promise`: `OBJECTIVE_MET`

4. **Immediately begin iteration 1** by executing the combat-sim-got-single task yourself

## Example

If called with: `--bot grok_code_fast_1 --max-iterations 5`

You should:
1. Call `ralph_loop` with:
   - bot_name: `grok_code_fast_1`
   - prompt: `/combat-sim-got-single --bot grok_code_fast_1 --opponent examplefuncsplayer --maps Shrine,Barrier,Bullseye,Lanes,Blitzkrieg --unit Soldier`
   - max_iterations: 5
   - completion_promise: `OBJECTIVE_MET`

2. Then run the combat-sim-got-single workflow yourself for iteration 1

## State File Location

The Ralph state will be stored at: `src/{bot_name}/.ralph-state.json`

This allows multiple bots to have independent iteration loops running simultaneously.

## Completion

The Ralph plugin will automatically re-run the prompt when you finish, until either:
- Max iterations reached
- The agent outputs `<promise>OBJECTIVE_MET</promise>` (when 50%+ maps are won in <= 500 rounds avg)
