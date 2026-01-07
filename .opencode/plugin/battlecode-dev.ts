import type { Plugin, PluginInput, Hooks } from "@opencode-ai/plugin"
import { tool } from "@opencode-ai/plugin"
import * as fs from "fs"
import * as path from "path"
import { execSync } from "child_process"

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

function parseGameOutput(output: string): Partial<GameResult> {
  const result: Partial<GameResult> = {
    winner: "unknown",
    winningTeam: "unknown",
    rounds: 0,
    reason: "unknown"
  }

  // Parse winner: "[server]               examplefuncsplayer (A) wins (round 2211)"
  const winMatch = output.match(/\[server\]\s+(\S+)\s+\(([AB])\)\s+wins\s+\(round\s+(\d+)\)/i)
  if (winMatch) {
    result.winningTeam = winMatch[1]
    result.winner = winMatch[2] as "A" | "B"
    result.rounds = parseInt(winMatch[3], 10)
  }

  // Parse reason: "[server] Reason: The winning team won by destruction."
  const reasonMatch = output.match(/\[server\]\s+Reason:\s+(.+)/i)
  if (reasonMatch) {
    result.reason = reasonMatch[1].trim()
  }

  return result
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
- Target: Win in ≤${state.targetWinRounds} rounds OR complete ${state.targetIterations} iterations`
        }
      }),

      bc_run_game: tool({
        description: "Run a Battlecode game and record the result",
        args: {
          team_a: tool.schema.string().optional().describe("Team A (default: from session state)"),
          team_b: tool.schema.string().optional().describe("Team B (default: from session state)"),
          map_name: tool.schema.string().optional().describe("Map (default: from session state)")
        },
        async execute(args, _context) {
          const state = loadState(directory)

          const teamA = args.team_a ?? state?.botName ?? "claudebot"
          const teamB = args.team_b ?? state?.opponent ?? "examplefuncsplayer"
          const mapName = args.map_name ?? state?.mapName ?? "shrine"

          try {
            const output = execSync(
              `export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64 && ./gradlew run -PteamA=${teamA} -PteamB=${teamB} -Pmaps=${mapName} 2>&1`,
              {
                cwd: directory,
                maxBuffer: 10 * 1024 * 1024,
                timeout: 300000 // 5 minute timeout
              }
            ).toString()

            const gameResult = parseGameOutput(output)

            const result: GameResult = {
              iteration: state ? state.iteration + 1 : 1,
              winner: gameResult.winner ?? "unknown",
              winningTeam: gameResult.winningTeam ?? "unknown",
              rounds: gameResult.rounds ?? 0,
              reason: gameResult.reason ?? "unknown",
              ourTeam: "A", // We're always team A
              timestamp: Date.now()
            }

            // Update state if active
            if (state && state.active) {
              state.iteration++
              state.results.push(result)

              // Track best result (lowest winning rounds for our team)
              const weWon = result.winner === "A"
              if (weWon) {
                if (!state.bestResult || result.rounds < state.bestResult.rounds) {
                  state.bestResult = result
                }
              }

              saveState(directory, state)
            }

            const weWon = result.winner === "A"
            return `=== GAME RESULT ===
Match: ${teamA} (A) vs ${teamB} (B) on ${mapName}
Winner: ${result.winningTeam} (${result.winner})
Rounds: ${result.rounds}
Reason: ${result.reason}
Our Team (A) ${weWon ? "WON" : "LOST"}

Raw output available in match file.
${state ? `\nIteration: ${state.iteration}/${state.targetIterations}` : ""}
${state?.bestResult ? `Best win so far: ${state.bestResult.rounds} rounds` : ""}`

          } catch (error: any) {
            return `Game failed to run: ${error.message}\n\nCheck that the bot compiles and Java 8 is available.`
          }
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
