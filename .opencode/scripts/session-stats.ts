#!/usr/bin/env bun
/**
 * Standalone session stats viewer
 * Usage: bun run session-stats.ts <session-id>
 *
 * Reads session data directly from ~/.local/share/opencode/storage/
 * Works independently from any project's .opencode/ folder
 */

import fs from "fs/promises"
import path from "path"
import os from "os"

// Determine storage path based on XDG or fallback
const getStoragePath = (): string => {
  const xdgData = process.env.XDG_DATA_HOME || path.join(os.homedir(), ".local", "share")
  return path.join(xdgData, "opencode", "storage")
}

interface SessionInfo {
  id: string
  title: string
  time: {
    created: number
    updated: number
  }
  projectID?: string
  directory?: string
}

interface MessageInfo {
  id: string
  sessionID: string
  role: "user" | "assistant"
  cost?: number
  tokens?: {
    input: number
    output: number
    reasoning: number
    cache: {
      read: number
      write: number
    }
  }
  modelID?: string
  providerID?: string
}

interface PartInfo {
  id: string
  type: string
  tool?: string
  messageID: string
}

interface SessionStats {
  sessionID: string
  title: string
  totalMessages: number
  totalCost: number
  totalTokens: {
    input: number
    output: number
    reasoning: number
    cache: {
      read: number
      write: number
    }
  }
  models: Record<
    string,
    {
      messages: number
      tokens: {
        input: number
        output: number
        reasoning: number
        cache: {
          read: number
          write: number
        }
      }
      cost: number
    }
  >
  toolUsage: Record<string, number>
  timeRange: {
    created: number
    updated: number
  }
}

async function readJsonFile<T>(filePath: string): Promise<T | null> {
  try {
    const content = await fs.readFile(filePath, "utf-8")
    return JSON.parse(content) as T
  } catch {
    return null
  }
}

async function getSessionInfo(sessionID: string): Promise<SessionInfo | null> {
  const storagePath = getStoragePath()
  const sessionDir = path.join(storagePath, "session")

  // Session files are organized as: session/<projectID>/<sessionID>.json
  // We need to search through project directories
  try {
    const projectDirs = await fs.readdir(sessionDir)

    for (const projectID of projectDirs) {
      const sessionFile = path.join(sessionDir, projectID, `${sessionID}.json`)
      const session = await readJsonFile<SessionInfo>(sessionFile)
      if (session) {
        return session
      }
    }
  } catch (error) {
    console.error(`Error reading session info: ${error}`)
  }

  return null
}

async function getMessages(sessionID: string): Promise<MessageInfo[]> {
  const storagePath = getStoragePath()
  const messageDir = path.join(storagePath, "message", sessionID)

  try {
    const messageFiles = await fs.readdir(messageDir)
    const messages: MessageInfo[] = []

    for (const file of messageFiles) {
      if (file.endsWith(".json")) {
        const messagePath = path.join(messageDir, file)
        const message = await readJsonFile<MessageInfo>(messagePath)
        if (message) {
          messages.push(message)
        }
      }
    }

    return messages
  } catch {
    return []
  }
}

async function getParts(messageID: string): Promise<PartInfo[]> {
  const storagePath = getStoragePath()
  const partDir = path.join(storagePath, "part", messageID)

  try {
    const partFiles = await fs.readdir(partDir)
    const parts: PartInfo[] = []

    for (const file of partFiles) {
      if (file.endsWith(".json")) {
        const partPath = path.join(partDir, file)
        const part = await readJsonFile<PartInfo>(partPath)
        if (part) {
          parts.push(part)
        }
      }
    }

    return parts
  } catch {
    return []
  }
}

async function getSessionStats(sessionID: string): Promise<SessionStats> {
  const sessionInfo = await getSessionInfo(sessionID)
  if (!sessionInfo) {
    throw new Error(`Session not found: ${sessionID}`)
  }

  const messages = await getMessages(sessionID)

  const stats: SessionStats = {
    sessionID,
    title: sessionInfo.title,
    totalMessages: messages.length,
    totalCost: 0,
    totalTokens: {
      input: 0,
      output: 0,
      reasoning: 0,
      cache: {
        read: 0,
        write: 0,
      },
    },
    models: {},
    toolUsage: {},
    timeRange: {
      created: sessionInfo.time.created,
      updated: sessionInfo.time.updated,
    },
  }

  // Process messages
  for (const message of messages) {
    if (message.role === "assistant") {
      stats.totalCost += message.cost || 0

      const modelKey = `${message.providerID || "unknown"}/${message.modelID || "unknown"}`
      if (!stats.models[modelKey]) {
        stats.models[modelKey] = {
          messages: 0,
          tokens: { input: 0, output: 0, reasoning: 0, cache: { read: 0, write: 0 } },
          cost: 0,
        }
      }
      stats.models[modelKey].messages++
      stats.models[modelKey].cost += message.cost || 0

      if (message.tokens) {
        stats.totalTokens.input += message.tokens.input || 0
        stats.totalTokens.output += message.tokens.output || 0
        stats.totalTokens.reasoning += message.tokens.reasoning || 0
        stats.totalTokens.cache.read += message.tokens.cache?.read || 0
        stats.totalTokens.cache.write += message.tokens.cache?.write || 0

        stats.models[modelKey].tokens.input += message.tokens.input || 0
        stats.models[modelKey].tokens.output += message.tokens.output || 0
        stats.models[modelKey].tokens.reasoning += message.tokens.reasoning || 0
        stats.models[modelKey].tokens.cache.read += message.tokens.cache?.read || 0
        stats.models[modelKey].tokens.cache.write += message.tokens.cache?.write || 0
      }
    }

    // Get parts to count tool usage
    const parts = await getParts(message.id)
    for (const part of parts) {
      if (part.type === "tool" && part.tool) {
        stats.toolUsage[part.tool] = (stats.toolUsage[part.tool] || 0) + 1
      }
    }
  }

  return stats
}

function formatNumber(num: number): string {
  if (num >= 1000000) {
    return (num / 1000000).toFixed(1) + "M"
  } else if (num >= 1000) {
    return (num / 1000).toFixed(1) + "K"
  }
  return num.toString()
}

function formatDate(timestamp: number): string {
  return new Date(timestamp).toLocaleString()
}

function displaySessionStats(stats: SessionStats) {
  const width = 56

  function renderRow(label: string, value: string): string {
    const availableWidth = width - 1
    const paddingNeeded = availableWidth - label.length - value.length
    const padding = Math.max(0, paddingNeeded)
    return `│${label}${" ".repeat(padding)}${value} │`
  }

  // Header
  console.log("┌────────────────────────────────────────────────────────┐")
  console.log("│                   SESSION OVERVIEW                     │")
  console.log("├────────────────────────────────────────────────────────┤")
  const truncatedID = stats.sessionID.length > 35 ? stats.sessionID.substring(0, 32) + "..." : stats.sessionID
  console.log(renderRow("Session ID", truncatedID))
  const truncatedTitle = stats.title.length > 35 ? stats.title.substring(0, 32) + "..." : stats.title
  console.log(renderRow("Title", truncatedTitle))
  console.log(renderRow("Messages", stats.totalMessages.toLocaleString()))
  console.log(renderRow("Created", formatDate(stats.timeRange.created)))
  console.log(renderRow("Updated", formatDate(stats.timeRange.updated)))
  console.log("└────────────────────────────────────────────────────────┘")
  console.log()

  // Cost & Tokens
  console.log("┌────────────────────────────────────────────────────────┐")
  console.log("│                    COST & TOKENS                       │")
  console.log("├────────────────────────────────────────────────────────┤")
  console.log(renderRow("Total Cost", `$${stats.totalCost.toFixed(4)}`))
  const totalTokens =
    stats.totalTokens.input +
    stats.totalTokens.output +
    stats.totalTokens.reasoning +
    stats.totalTokens.cache.read +
    stats.totalTokens.cache.write
  console.log(renderRow("Total Tokens", formatNumber(totalTokens)))
  console.log(renderRow("Input", formatNumber(stats.totalTokens.input)))
  console.log(renderRow("Output", formatNumber(stats.totalTokens.output)))
  console.log(renderRow("Reasoning", formatNumber(stats.totalTokens.reasoning)))
  console.log(renderRow("Cache Read", formatNumber(stats.totalTokens.cache.read)))
  console.log(renderRow("Cache Write", formatNumber(stats.totalTokens.cache.write)))
  console.log("└────────────────────────────────────────────────────────┘")
  console.log()

  // Models
  if (Object.keys(stats.models).length > 0) {
    console.log("┌────────────────────────────────────────────────────────┐")
    console.log("│                      MODEL USAGE                       │")
    console.log("├────────────────────────────────────────────────────────┤")

    for (const [model, usage] of Object.entries(stats.models)) {
      const modelDisplay = model.length > 54 ? model.substring(0, 51) + "..." : model
      console.log(`│ ${modelDisplay.padEnd(54)} │`)
      console.log(renderRow("  Messages", usage.messages.toLocaleString()))
      console.log(renderRow("  Input Tokens", formatNumber(usage.tokens.input)))
      console.log(renderRow("  Output Tokens", formatNumber(usage.tokens.output)))
      console.log(renderRow("  Reasoning Tokens", formatNumber(usage.tokens.reasoning)))
      console.log(renderRow("  Cache Read", formatNumber(usage.tokens.cache.read)))
      console.log(renderRow("  Cache Write", formatNumber(usage.tokens.cache.write)))
      console.log(renderRow("  Cost", `$${usage.cost.toFixed(4)}`))
      console.log("├────────────────────────────────────────────────────────┤")
    }
    // Remove last separator
    process.stdout.write("\x1B[1A")
    console.log("└────────────────────────────────────────────────────────┘")
    console.log()
  }

  // Tools
  if (Object.keys(stats.toolUsage).length > 0) {
    const sortedTools = Object.entries(stats.toolUsage).sort(([, a], [, b]) => b - a)

    console.log("┌────────────────────────────────────────────────────────┐")
    console.log("│                      TOOL USAGE                        │")
    console.log("├────────────────────────────────────────────────────────┤")

    const maxCount = Math.max(...sortedTools.map(([, count]) => count))
    const totalToolUsage = Object.values(stats.toolUsage).reduce((a, b) => a + b, 0)

    for (const [tool, count] of sortedTools) {
      const barLength = Math.max(1, Math.floor((count / maxCount) * 20))
      const bar = "█".repeat(barLength)
      const percentage = ((count / totalToolUsage) * 100).toFixed(1)

      const maxToolLength = 18
      const truncatedTool = tool.length > maxToolLength ? tool.substring(0, maxToolLength - 2) + ".." : tool
      const toolName = truncatedTool.padEnd(maxToolLength)

      const content = ` ${toolName} ${bar.padEnd(20)} ${count.toString().padStart(3)} (${percentage.padStart(4)}%)`
      const padding = Math.max(0, width - content.length - 1)
      console.log(`│${content}${" ".repeat(padding)} │`)
    }
    console.log("└────────────────────────────────────────────────────────┘")
  }
}

// Main execution
const main = async () => {
  const sessionID = process.argv[2]

  if (!sessionID) {
    console.error("Usage: bun run session-stats.ts <session-id>")
    console.error("\nTo find session IDs, run: opencode session list")
    process.exit(1)
  }

  try {
    const stats = await getSessionStats(sessionID)
    displaySessionStats(stats)
  } catch (error) {
    console.error(`Error: ${error instanceof Error ? error.message : String(error)}`)
    process.exit(1)
  }
}

main()
