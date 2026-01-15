import type { Plugin, PluginInput, Hooks } from "@opencode-ai/plugin"
import { tool } from "@opencode-ai/plugin"
import * as fs from "fs"
import * as path from "path"

interface RalphState {
  active: boolean
  botName: string
  prompt: string
  iteration: number
  maxIterations: number
  completionPromise: string | null
  sessionID: string
  startTime: number
}

const STATE_FILE_NAME = ".ralph-state.json"

// State is stored per-bot in src/{bot_name}/.ralph-state.json
function getStateFilePath(directory: string, botName: string): string {
  return path.join(directory, "src", botName, STATE_FILE_NAME)
}

function loadState(directory: string, botName: string): RalphState | null {
  const stateFile = getStateFilePath(directory, botName)
  try {
    if (fs.existsSync(stateFile)) {
      const content = fs.readFileSync(stateFile, "utf-8")
      return JSON.parse(content) as RalphState
    }
  } catch (e) {
    console.error(`Failed to load ralph state for ${botName}:`, e)
  }
  return null
}

function saveState(directory: string, state: RalphState): void {
  const stateFile = getStateFilePath(directory, state.botName)
  const stateDir = path.dirname(stateFile)
  if (!fs.existsSync(stateDir)) {
    fs.mkdirSync(stateDir, { recursive: true })
  }
  fs.writeFileSync(stateFile, JSON.stringify(state, null, 2))
}

function clearState(directory: string, botName: string): void {
  const stateFile = getStateFilePath(directory, botName)
  try {
    if (fs.existsSync(stateFile)) {
      fs.unlinkSync(stateFile)
    }
  } catch (e) {
    console.error(`Failed to clear ralph state for ${botName}:`, e)
  }
}

// Find all active ralph loops by scanning src/*/.ralph-state.json
function findAllActiveStates(directory: string): RalphState[] {
  const states: RalphState[] = []
  const srcDir = path.join(directory, "src")

  try {
    if (!fs.existsSync(srcDir)) return states

    const entries = fs.readdirSync(srcDir, { withFileTypes: true })
    for (const entry of entries) {
      if (entry.isDirectory()) {
        const stateFile = path.join(srcDir, entry.name, STATE_FILE_NAME)
        if (fs.existsSync(stateFile)) {
          try {
            const content = fs.readFileSync(stateFile, "utf-8")
            const state = JSON.parse(content) as RalphState
            if (state.active) {
              states.push(state)
            }
          } catch (e) {
            // Ignore malformed state files
          }
        }
      }
    }
  } catch (e) {
    console.error("Failed to scan for active ralph states:", e)
  }

  return states
}

async function checkCompletionPromise(
  client: PluginInput["client"],
  sessionID: string,
  completionPromise: string
): Promise<boolean> {
  try {
    const messagesResponse = await client.session.messages({
      path: { id: sessionID },
      query: { limit: 5 }
    })

    if (!messagesResponse.data) return false

    // Look for <promise>...</promise> tags in recent assistant messages
    for (const msg of messagesResponse.data) {
      if (msg.info.role === "assistant") {
        for (const part of msg.parts) {
          if (part.type === "text" && "text" in part) {
            // Check for <promise>COMPLETION_TEXT</promise> pattern
            const promiseMatch = part.text.match(/<promise>([\s\S]*?)<\/promise>/i)
            if (promiseMatch) {
              const promiseText = promiseMatch[1].trim()
              if (promiseText === completionPromise.trim()) {
                return true
              }
            }
          }
        }
      }
    }
  } catch (e) {
    console.error("Failed to check completion promise:", e)
  }
  return false
}

const plugin: Plugin = async (input: PluginInput): Promise<Hooks> => {
  const { client, directory } = input

  // Track active sessions to avoid duplicate processing
  const processingBots = new Set<string>()

  return {
    // Event handler for session.idle
    event: async ({ event }) => {
      if (event.type !== "session.idle") return

      const sessionID = event.properties.sessionID

      // Find all active states and process the one matching this session
      const activeStates = findAllActiveStates(directory)

      for (const state of activeStates) {
        if (state.sessionID !== sessionID) continue

        const botName = state.botName

        // Prevent concurrent processing of the same bot
        if (processingBots.has(botName)) continue
        processingBots.add(botName)

        try {
          // Check if we've hit max iterations
          if (state.maxIterations > 0 && state.iteration >= state.maxIterations) {
            console.log(`[${botName}] Ralph loop completed: max iterations (${state.maxIterations}) reached`)
            clearState(directory, botName)

            await client.tui.showToast({
              body: {
                title: `Ralph Loop Complete: ${botName}`,
                message: `Reached maximum iterations (${state.maxIterations})`,
                variant: "info",
                duration: 5000
              }
            })
            return
          }

          // Check completion promise if set
          if (state.completionPromise) {
            const isComplete = await checkCompletionPromise(client, sessionID, state.completionPromise)
            if (isComplete) {
              console.log(`[${botName}] Ralph loop completed: completion promise satisfied at iteration ${state.iteration}`)
              clearState(directory, botName)

              await client.tui.showToast({
                body: {
                  title: `Ralph Loop Complete: ${botName}`,
                  message: `Objective met at iteration ${state.iteration}!`,
                  variant: "success",
                  duration: 5000
                }
              })
              return
            }
          }

          // Increment iteration and continue the loop
          state.iteration++
          saveState(directory, state)

          // Build system message with iteration info
          const systemMsg = `[Ralph Loop: ${botName} - Iteration ${state.iteration}${state.maxIterations > 0 ? `/${state.maxIterations}` : ""}]${state.completionPromise ? `\n\nTo complete this loop, output: <promise>${state.completionPromise}</promise>\nONLY output this when the task is COMPLETELY and UNEQUIVOCALLY done.` : ""}`

          // Small delay to avoid race conditions
          await new Promise(resolve => setTimeout(resolve, 500))

          // Re-submit the prompt
          await client.session.promptAsync({
            path: { id: sessionID },
            body: {
              parts: [{ type: "text", text: state.prompt }],
              system: systemMsg
            }
          })

          console.log(`[${botName}] Ralph loop: submitted iteration ${state.iteration}`)

        } finally {
          processingBots.delete(botName)
        }
      }
    },

    // Custom tools
    tool: {
      ralph_loop: tool({
        description: "Start a Ralph Wiggum loop for a specific bot - iteratively re-runs the prompt until completion criteria are met",
        args: {
          bot_name: tool.schema.string().describe("The bot name (folder in src/)"),
          prompt: tool.schema.string().describe("The prompt/task to iterate on"),
          max_iterations: tool.schema.number().optional().describe("Maximum number of iterations (0 = unlimited)"),
          completion_promise: tool.schema.string().optional().describe("Text that must appear in <promise>...</promise> tags to complete the loop")
        },
        async execute(args, context) {
          const botDir = path.join(directory, "src", args.bot_name)
          if (!fs.existsSync(botDir)) {
            return `Error: Bot folder not found: src/${args.bot_name}/`
          }

          // Check if there's already an active loop for this bot
          const existingState = loadState(directory, args.bot_name)
          if (existingState?.active) {
            return `Error: Ralph loop already active for ${args.bot_name} (iteration ${existingState.iteration}). Use cancel_ralph to stop it first.`
          }

          const state: RalphState = {
            active: true,
            botName: args.bot_name,
            prompt: args.prompt,
            iteration: 1,  // Start at 1 since we're about to run iteration 1
            maxIterations: args.max_iterations ?? 0,
            completionPromise: args.completion_promise ?? null,
            sessionID: context.sessionID,
            startTime: Date.now()
          }

          saveState(directory, state)

          let response = `Ralph loop started for ${args.bot_name}!\n`
          response += `- State file: src/${args.bot_name}/${STATE_FILE_NAME}\n`
          response += `- Iteration: 1${args.max_iterations ? `/${args.max_iterations}` : ""}\n`
          response += `- Prompt: "${args.prompt.substring(0, 100)}${args.prompt.length > 100 ? '...' : ''}"\n`
          if (args.completion_promise) {
            response += `- Completion promise: "${args.completion_promise}"\n`
            response += `\nTo complete the loop, output: <promise>${args.completion_promise}</promise>`
          }

          return response
        }
      }),

      cancel_ralph: tool({
        description: "Cancel an active Ralph Wiggum loop for a specific bot",
        args: {
          bot_name: tool.schema.string().describe("The bot name (folder in src/)")
        },
        async execute(args, _context) {
          const state = loadState(directory, args.bot_name)
          if (!state || !state.active) {
            return `No active Ralph loop for ${args.bot_name}.`
          }

          const iteration = state.iteration
          clearState(directory, args.bot_name)

          return `Ralph loop cancelled for ${args.bot_name} at iteration ${iteration}.`
        }
      }),

      ralph_status: tool({
        description: "Check the status of Ralph Wiggum loops (for a specific bot or all bots)",
        args: {
          bot_name: tool.schema.string().optional().describe("The bot name (folder in src/). If omitted, shows all active loops.")
        },
        async execute(args, _context) {
          if (args.bot_name) {
            // Status for specific bot
            const state = loadState(directory, args.bot_name)
            if (!state || !state.active) {
              return `No active Ralph loop for ${args.bot_name}.`
            }

            const elapsed = Math.round((Date.now() - state.startTime) / 1000)
            let status = `Ralph loop active for ${args.bot_name}:\n`
            status += `- Current iteration: ${state.iteration}\n`
            status += `- Max iterations: ${state.maxIterations || "unlimited"}\n`
            status += `- Elapsed time: ${elapsed}s\n`
            if (state.completionPromise) {
              status += `- Completion promise: "${state.completionPromise}"\n`
            }
            status += `- Prompt: "${state.prompt.substring(0, 100)}${state.prompt.length > 100 ? '...' : ''}"`

            return status
          } else {
            // Status for all bots
            const activeStates = findAllActiveStates(directory)
            if (activeStates.length === 0) {
              return "No active Ralph loops."
            }

            let status = `Active Ralph loops (${activeStates.length}):\n\n`
            for (const state of activeStates) {
              const elapsed = Math.round((Date.now() - state.startTime) / 1000)
              status += `${state.botName}:\n`
              status += `  - Iteration: ${state.iteration}${state.maxIterations ? `/${state.maxIterations}` : ""}\n`
              status += `  - Elapsed: ${elapsed}s\n`
              if (state.completionPromise) {
                status += `  - Promise: "${state.completionPromise}"\n`
              }
              status += `\n`
            }

            return status.trim()
          }
        }
      })
    }
  }
}

export default plugin
