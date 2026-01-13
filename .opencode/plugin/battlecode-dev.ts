import type { Plugin, PluginInput, Hooks } from "@opencode-ai/plugin"
import { tool } from "@opencode-ai/plugin"
import * as fs from "fs"
import * as path from "path"

interface BattlecodeState {
  active: boolean
  iteration: number
  targetIterations: number
  targetWinRounds: number
  botName: string
  opponent: string
  mapName: string
  results: GameResult[]
  bestResult: GameResult | null
  startTime: number
}

interface GameResult {
  iteration: number
  winner: "A" | "B" | "unknown"
  winningTeam: string
  rounds: number
  reason: string
  ourTeam: "A" | "B"
  timestamp: number
}

const STATE_FILE_NAME = ".opencode-battlecode-state.json"

function getStateFilePath(directory: string): string {
  return path.join(directory, ".opencode", STATE_FILE_NAME)
}

function loadState(directory: string): BattlecodeState | null {
  const stateFile = getStateFilePath(directory)
  try {
    if (fs.existsSync(stateFile)) {
      const content = fs.readFileSync(stateFile, "utf-8")
      return JSON.parse(content) as BattlecodeState
    }
  } catch (e) {
    console.error("Failed to load battlecode state:", e)
  }
  return null
}

function saveState(directory: string, state: BattlecodeState): void {
  const stateFile = getStateFilePath(directory)
  const stateDir = path.dirname(stateFile)
  if (!fs.existsSync(stateDir)) {
    fs.mkdirSync(stateDir, { recursive: true })
  }
  fs.writeFileSync(stateFile, JSON.stringify(state, null, 2))
}

function clearState(directory: string): void {
  const stateFile = getStateFilePath(directory)
  try {
    if (fs.existsSync(stateFile)) {
      fs.unlinkSync(stateFile)
    }
  } catch (e) {
    console.error("Failed to clear battlecode state:", e)
  }
}

const plugin: Plugin = async (input: PluginInput): Promise<Hooks> => {
  const { client, directory } = input

  return {
    tool: {
      bc_init: tool({
        description: "Initialize a Battlecode development session with goals",
        args: {
          bot_name: tool.schema.string().describe("Name of the bot to develop (default: claudebot)"),
          opponent: tool.schema.string().optional().describe("Opponent bot name (default: examplefuncsplayer)"),
          map_name: tool.schema.string().optional().describe("Map to use (default: shrine)"),
          target_iterations: tool.schema.number().optional().describe("Number of iterations to complete (default: 10)"),
          target_win_rounds: tool.schema.number().optional().describe("Target rounds to win in (default: 1500)")
        },
        async execute(args, _context) {
          const state: BattlecodeState = {
            active: true,
            iteration: 0,
            targetIterations: args.target_iterations ?? 10,
            targetWinRounds: args.target_win_rounds ?? 1500,
            botName: args.bot_name,
            opponent: args.opponent ?? "examplefuncsplayer",
            mapName: args.map_name ?? "shrine",
            results: [],
            bestResult: null,
            startTime: Date.now()
          }

          saveState(directory, state)

          return `Battlecode dev session initialized!
- Bot: ${state.botName}
- Opponent: ${state.opponent}
- Map: ${state.mapName}
- Target: Win in ≤${state.targetWinRounds} rounds OR complete ${state.targetIterations} iterations

Next: run /bc-runner --teamA=${state.botName} --teamB=${state.opponent} to execute 5 maps in parallel.`
        }
      }),

      bc_check_goal: tool({
        description: "Check if the Battlecode development goals have been met",
        args: {},
        async execute(_args, _context) {
          const state = loadState(directory)
          if (!state || !state.active) {
            return "No active Battlecode session. Use bc_init to start one."
          }

          const elapsed = Math.round((Date.now() - state.startTime) / 1000 / 60)

          // Check win condition
          if (state.bestResult && state.bestResult.rounds <= state.targetWinRounds) {
            return `🎉 GOAL ACHIEVED! Won in ${state.bestResult.rounds} rounds (target: ≤${state.targetWinRounds})

Session complete after ${state.iteration} iterations in ${elapsed} minutes.
Signal completion with: <promise>BATTLECODE_GOAL_ACHIEVED</promise>`
          }

          // Check iteration condition
          if (state.iteration >= state.targetIterations) {
            return `✅ ITERATION GOAL MET! Completed ${state.iteration}/${state.targetIterations} iterations.

Best result: ${state.bestResult ? `${state.bestResult.rounds} rounds` : "No wins yet"}
Time elapsed: ${elapsed} minutes
Signal completion with: <promise>BATTLECODE_GOAL_ACHIEVED</promise>`
          }

          // Still in progress
          const wins = state.results.filter(r => r.winner === "A").length
          const losses = state.results.filter(r => r.winner === "B").length

          return `⏳ Goals not yet met.

Progress: ${state.iteration}/${state.targetIterations} iterations
Record: ${wins}W-${losses}L
Best win: ${state.bestResult ? `${state.bestResult.rounds} rounds` : "None yet"}
Target: Win in ≤${state.targetWinRounds} rounds

Continue iterating!`
        }
      }),

      bc_status: tool({
        description: "Get detailed status of the current Battlecode development session",
        args: {},
        async execute(_args, _context) {
          const state = loadState(directory)
          if (!state || !state.active) {
            return "No active Battlecode session."
          }

          const elapsed = Math.round((Date.now() - state.startTime) / 1000 / 60)
          const wins = state.results.filter(r => r.winner === "A").length
          const losses = state.results.filter(r => r.winner === "B").length

          let status = `=== BATTLECODE DEV STATUS ===
Bot: ${state.botName}
Opponent: ${state.opponent}
Map: ${state.mapName}

Progress: ${state.iteration}/${state.targetIterations} iterations
Record: ${wins}W-${losses}L
Time elapsed: ${elapsed} minutes

Targets:
- Win in ≤${state.targetWinRounds} rounds: ${state.bestResult && state.bestResult.rounds <= state.targetWinRounds ? "✅ ACHIEVED" : "❌ Not yet"}
- Complete ${state.targetIterations} iterations: ${state.iteration >= state.targetIterations ? "✅ ACHIEVED" : "❌ Not yet"}

`
          if (state.results.length > 0) {
            status += "Recent Results:\n"
            const recent = state.results.slice(-5)
            for (const r of recent) {
              const won = r.winner === "A"
              status += `  ${r.iteration}. ${won ? "WIN" : "LOSS"} in ${r.rounds} rounds\n`
            }
          }

          if (state.bestResult) {
            status += `\nBest Win: ${state.bestResult.rounds} rounds (iteration ${state.bestResult.iteration})`
          }

          return status
        }
      }),

      bc_reset: tool({
        description: "Reset the Battlecode development session",
        args: {},
        async execute(_args, _context) {
          clearState(directory)
          return "Battlecode session reset. Use bc_init to start a new session."
        }
      })
    }
  }
}

export default plugin
