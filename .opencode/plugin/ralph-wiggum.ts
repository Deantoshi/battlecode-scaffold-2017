import type { Plugin, PluginInput, Hooks } from "@opencode-ai/plugin"
import { tool } from "@opencode-ai/plugin"
import * as fs from "fs"
import * as path from "path"

interface RalphState {
  active: boolean
  prompt: string
  iteration: number
  maxIterations: number
  completionPromise: string | null
  sessionID: string
  startTime: number
}

const STATE_FILE_NAME = ".opencode-ralph-state.json"

function getStateFilePath(directory: string): string {
  return path.join(directory, ".opencode", STATE_FILE_NAME)
}

function loadState(directory: string): RalphState | null {
  const stateFile = getStateFilePath(directory)
  try {
    if (fs.existsSync(stateFile)) {
      const content = fs.readFileSync(stateFile, "utf-8")
      return JSON.parse(content) as RalphState
    }
  } catch (e) {
    console.error("Failed to load ralph state:", e)
  }
  return null
}

function saveState(directory: string, state: RalphState): void {
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
    console.error("Failed to clear ralph state:", e)
  }
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
  const { client, directory, $ } = input

  // Track active sessions to avoid duplicate processing
  const processingSession = new Set<string>()

  return {
    // Event handler for session.idle
    event: async ({ event }) => {
      if (event.type !== "session.idle") return

      const sessionID = event.properties.sessionID

      // Prevent concurrent processing of the same session
      if (processingSession.has(sessionID)) return

      const state = loadState(directory)
      if (!state || !state.active) return
      if (state.sessionID !== sessionID) return

      processingSession.add(sessionID)

      try {
        // Check if we've hit max iterations
        if (state.maxIterations > 0 && state.iteration >= state.maxIterations) {
          console.log(`Ralph loop completed: max iterations (${state.maxIterations}) reached`)
          clearState(directory)

          // Show toast notification
          await client.tui.showToast({
            body: {
              title: "Ralph Loop Complete",
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
            console.log(`Ralph loop completed: completion promise satisfied at iteration ${state.iteration}`)
            clearState(directory)

            await client.tui.showToast({
              body: {
                title: "Ralph Loop Complete",
                message: `Completion promise satisfied at iteration ${state.iteration}`,
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
        const systemMsg = `[Ralph Loop: Iteration ${state.iteration}${state.maxIterations > 0 ? `/${state.maxIterations}` : ""}]${state.completionPromise ? `\n\nTo complete this loop, output: <promise>${state.completionPromise}</promise>\nONLY output this when the task is COMPLETELY and UNEQUIVOCALLY done.` : ""}`

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

        console.log(`Ralph loop: submitted iteration ${state.iteration}`)

      } finally {
        processingSession.delete(sessionID)
      }
    },

    // Custom tools
    tool: {
      ralph_loop: tool({
        description: "Start a Ralph Wiggum loop - an iterative self-referential development loop that continuously re-feeds the same prompt until completion criteria are met",
        args: {
          prompt: tool.schema.string().describe("The prompt/task to iterate on"),
          max_iterations: tool.schema.number().optional().describe("Maximum number of iterations (0 = unlimited)"),
          completion_promise: tool.schema.string().optional().describe("Text that must appear in <promise>...</promise> tags to complete the loop")
        },
        async execute(args, context) {
          const state: RalphState = {
            active: true,
            prompt: args.prompt,
            iteration: 0,
            maxIterations: args.max_iterations ?? 0,
            completionPromise: args.completion_promise ?? null,
            sessionID: context.sessionID,
            startTime: Date.now()
          }

          saveState(directory, state)

          let response = `Ralph loop started!\n`
          response += `- Prompt: "${args.prompt.substring(0, 100)}${args.prompt.length > 100 ? '...' : ''}"\n`
          response += `- Max iterations: ${args.max_iterations ?? "unlimited"}\n`
          if (args.completion_promise) {
            response += `- Completion promise: "${args.completion_promise}"\n`
            response += `\nTo complete the loop, output: <promise>${args.completion_promise}</promise>`
          }

          return response
        }
      }),

      cancel_ralph: tool({
        description: "Cancel an active Ralph Wiggum loop",
        args: {},
        async execute(_args, _context) {
          const state = loadState(directory)
          if (!state || !state.active) {
            return "No active Ralph loop to cancel."
          }

          const iteration = state.iteration
          clearState(directory)

          return `Ralph loop cancelled at iteration ${iteration}.`
        }
      }),

      ralph_status: tool({
        description: "Check the status of an active Ralph Wiggum loop",
        args: {},
        async execute(_args, _context) {
          const state = loadState(directory)
          if (!state || !state.active) {
            return "No active Ralph loop."
          }

          const elapsed = Math.round((Date.now() - state.startTime) / 1000)
          let status = `Ralph loop active:\n`
          status += `- Current iteration: ${state.iteration}\n`
          status += `- Max iterations: ${state.maxIterations || "unlimited"}\n`
          status += `- Elapsed time: ${elapsed}s\n`
          if (state.completionPromise) {
            status += `- Completion promise: "${state.completionPromise}"\n`
          }
          status += `- Prompt: "${state.prompt.substring(0, 100)}${state.prompt.length > 100 ? '...' : ''}"`

          return status
        }
      })
    }
  }
}

export default plugin
