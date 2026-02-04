#!/usr/bin/env bun
/**
 * Standalone session stats viewer
 *
 * Usage:
 *   bun run session-stats.ts <session-id>           # Single session stats
 *   bun run session-stats.ts --runs                  # List all variant-loop runs
 *   bun run session-stats.ts --run <RUN_ID>          # Per-iteration breakdown for a run
 *   bun run session-stats.ts --iter <RUN_ID> <N>     # Stats for a specific iteration
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
  parentID?: string
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
  variant?: string
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
      variant?: string
    }
  >
  toolUsage: Record<string, number>
  reasoningVariants: Set<string>
  timeRange: {
    created: number
    updated: number
  }
}

// Parsed variant-loop title structure
interface VariantLoopTitle {
  runID: string
  bot: string
  opponent: string
  map: string
  agent: string // "archetype-creator" | "archetype-implementer"
  iteration: number
  variantNum?: number // undefined for archetype-creator, 1-10 for implementer
}

function parseVariantLoopTitle(title: string): VariantLoopTitle | null {
  // Format: variant-loop:<RUN_ID>:<BOT>:<OPPONENT>:<MAP>:<agent>:iter:<N>:<context>
  if (!title.startsWith("variant-loop:")) return null

  const parts = title.split(":")
  // parts[0] = "variant-loop"
  // parts[1] = RUN_ID (e.g. "20260203-172857-30556")
  // parts[2] = BOT
  // parts[3] = OPPONENT
  // parts[4] = MAP
  // parts[5] = agent name
  // parts[6] = "iter"
  // parts[7] = iteration number
  // parts[8] = context (e.g. "phase0" or "v3")
  if (parts.length < 8 || parts[6] !== "iter") return null

  const result: VariantLoopTitle = {
    runID: parts[1],
    bot: parts[2],
    opponent: parts[3],
    map: parts[4],
    agent: parts[5],
    iteration: parseInt(parts[7], 10),
  }

  if (parts[8]?.startsWith("v")) {
    result.variantNum = parseInt(parts[8].substring(1), 10)
  }

  return result
}

// Scan all sessions across all project directories
async function getAllSessions(): Promise<SessionInfo[]> {
  const storagePath = getStoragePath()
  const sessionDir = path.join(storagePath, "session")
  const sessions: SessionInfo[] = []

  try {
    const projectDirs = await fs.readdir(sessionDir)

    for (const projectID of projectDirs) {
      const projectPath = path.join(sessionDir, projectID)
      const stat = await fs.stat(projectPath)
      if (!stat.isDirectory()) continue

      const files = await fs.readdir(projectPath)
      for (const file of files) {
        if (!file.endsWith(".json")) continue
        const session = await readJsonFile<SessionInfo>(path.join(projectPath, file))
        if (session) sessions.push(session)
      }
    }
  } catch {
    // storage dir may not exist
  }

  return sessions
}

// Aggregated stats for an iteration (or entire run)
interface AggregatedStats {
  label: string
  sessionCount: number
  sessions: { id: string; title: string; agent: string; variantNum?: number }[]
  totalMessages: number
  totalCost: number
  totalTokens: {
    input: number
    output: number
    reasoning: number
    cache: { read: number; write: number }
  }
  models: Record<
    string,
    {
      messages: number
      tokens: {
        input: number
        output: number
        reasoning: number
        cache: { read: number; write: number }
      }
      cost: number
      variant?: string
    }
  >
  toolUsage: Record<string, number>
  reasoningVariants: Set<string>
  timeRange: { earliest: number; latest: number }
}

function newAggregatedStats(label: string): AggregatedStats {
  return {
    label,
    sessionCount: 0,
    sessions: [],
    totalMessages: 0,
    totalCost: 0,
    totalTokens: { input: 0, output: 0, reasoning: 0, cache: { read: 0, write: 0 } },
    models: {},
    toolUsage: {},
    reasoningVariants: new Set(),
    timeRange: { earliest: Infinity, latest: 0 },
  }
}

function mergeSessionIntoAggregated(agg: AggregatedStats, stats: SessionStats, meta: { agent: string; variantNum?: number }) {
  agg.sessionCount++
  agg.sessions.push({ id: stats.sessionID, title: stats.title, agent: meta.agent, variantNum: meta.variantNum })
  agg.totalMessages += stats.totalMessages
  agg.totalCost += stats.totalCost
  agg.totalTokens.input += stats.totalTokens.input
  agg.totalTokens.output += stats.totalTokens.output
  agg.totalTokens.reasoning += stats.totalTokens.reasoning
  agg.totalTokens.cache.read += stats.totalTokens.cache.read
  agg.totalTokens.cache.write += stats.totalTokens.cache.write

  for (const v of stats.reasoningVariants) agg.reasoningVariants.add(v)

  if (stats.timeRange.created < agg.timeRange.earliest) agg.timeRange.earliest = stats.timeRange.created
  if (stats.timeRange.updated > agg.timeRange.latest) agg.timeRange.latest = stats.timeRange.updated

  for (const [model, usage] of Object.entries(stats.models)) {
    if (!agg.models[model]) {
      agg.models[model] = { messages: 0, tokens: { input: 0, output: 0, reasoning: 0, cache: { read: 0, write: 0 } }, cost: 0 }
    }
    agg.models[model].messages += usage.messages
    agg.models[model].cost += usage.cost
    agg.models[model].tokens.input += usage.tokens.input
    agg.models[model].tokens.output += usage.tokens.output
    agg.models[model].tokens.reasoning += usage.tokens.reasoning
    agg.models[model].tokens.cache.read += usage.tokens.cache.read
    agg.models[model].tokens.cache.write += usage.tokens.cache.write
    if (usage.variant && !agg.models[model].variant) agg.models[model].variant = usage.variant
  }

  for (const [tool, count] of Object.entries(stats.toolUsage)) {
    agg.toolUsage[tool] = (agg.toolUsage[tool] || 0) + count
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
    reasoningVariants: new Set(),
    timeRange: {
      created: sessionInfo.time.created,
      updated: sessionInfo.time.updated,
    },
  }

  // Process messages
  for (const message of messages) {
    // Track reasoning variants from user messages
    if (message.role === "user" && message.variant) {
      stats.reasoningVariants.add(message.variant)
    }

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

      // Find the corresponding user message to get variant
      const userMessage = messages.find((m) => m.role === "user" && m.id === message.parentID)
      if (userMessage?.variant && !stats.models[modelKey].variant) {
        stats.models[modelKey].variant = userMessage.variant
      }

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
  if (stats.reasoningVariants.size > 0) {
    const variants = Array.from(stats.reasoningVariants)
      .map((v) => v.toUpperCase())
      .join(", ")
    console.log(renderRow("Reasoning Levels", variants))
  }
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
      if (usage.variant) {
        console.log(renderRow("  Reasoning Level", usage.variant.toUpperCase()))
      }
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

// ═══════════════════════════════════════════════════════════════════════════════
// Variant-loop aggregation commands
// ═══════════════════════════════════════════════════════════════════════════════

function displayAggregatedStats(agg: AggregatedStats) {
  const width = 56

  function renderRow(label: string, value: string): string {
    const availableWidth = width - 1
    const paddingNeeded = availableWidth - label.length - value.length
    const padding = Math.max(0, paddingNeeded)
    return `│${label}${" ".repeat(padding)}${value} │`
  }

  // Header
  console.log("┌────────────────────────────────────────────────────────┐")
  const headerText = agg.label.length > 54 ? agg.label.substring(0, 51) + "..." : agg.label
  const headerPad = Math.max(0, 56 - headerText.length)
  const leftPad = Math.floor(headerPad / 2)
  const rightPad = headerPad - leftPad
  console.log(`│${" ".repeat(leftPad)}${headerText}${" ".repeat(rightPad)}│`)
  console.log("├────────────────────────────────────────────────────────┤")
  console.log(renderRow(" Sessions", agg.sessionCount.toString()))
  console.log(renderRow(" Messages", agg.totalMessages.toLocaleString()))
  if (agg.reasoningVariants.size > 0) {
    const variants = Array.from(agg.reasoningVariants).map((v) => v.toUpperCase()).join(", ")
    console.log(renderRow(" Reasoning Levels", variants))
  }
  if (agg.timeRange.earliest < Infinity) {
    console.log(renderRow(" Started", formatDate(agg.timeRange.earliest)))
    console.log(renderRow(" Ended", formatDate(agg.timeRange.latest)))
    const durationMs = agg.timeRange.latest - agg.timeRange.earliest
    const durationMin = Math.floor(durationMs / 60000)
    const durationSec = Math.floor((durationMs % 60000) / 1000)
    console.log(renderRow(" Duration", `${durationMin}m ${durationSec}s`))
  }
  console.log("└────────────────────────────────────────────────────────┘")
  console.log()

  // Cost & Tokens
  console.log("┌────────────────────────────────────────────────────────┐")
  console.log("│                    COST & TOKENS                       │")
  console.log("├────────────────────────────────────────────────────────┤")
  console.log(renderRow(" Total Cost", `$${agg.totalCost.toFixed(4)}`))
  const totalTokens =
    agg.totalTokens.input + agg.totalTokens.output + agg.totalTokens.reasoning +
    agg.totalTokens.cache.read + agg.totalTokens.cache.write
  console.log(renderRow(" Total Tokens", formatNumber(totalTokens)))
  console.log(renderRow(" Input", formatNumber(agg.totalTokens.input)))
  console.log(renderRow(" Output", formatNumber(agg.totalTokens.output)))
  console.log(renderRow(" Reasoning", formatNumber(agg.totalTokens.reasoning)))
  console.log(renderRow(" Cache Read", formatNumber(agg.totalTokens.cache.read)))
  console.log(renderRow(" Cache Write", formatNumber(agg.totalTokens.cache.write)))
  if (agg.sessionCount > 0) {
    console.log("├────────────────────────────────────────────────────────┤")
    console.log(renderRow(" Avg Cost/Session", `$${(agg.totalCost / agg.sessionCount).toFixed(4)}`))
  }
  console.log("└────────────────────────────────────────────────────────┘")
  console.log()

  // Models
  if (Object.keys(agg.models).length > 0) {
    console.log("┌────────────────────────────────────────────────────────┐")
    console.log("│                      MODEL USAGE                       │")
    console.log("├────────────────────────────────────────────────────────┤")

    for (const [model, usage] of Object.entries(agg.models)) {
      const modelDisplay = model.length > 54 ? model.substring(0, 51) + "..." : model
      console.log(`│ ${modelDisplay.padEnd(54)} │`)
      if (usage.variant) {
        console.log(renderRow("  Reasoning Level", usage.variant.toUpperCase()))
      }
      console.log(renderRow("  Messages", usage.messages.toLocaleString()))
      console.log(renderRow("  Input Tokens", formatNumber(usage.tokens.input)))
      console.log(renderRow("  Output Tokens", formatNumber(usage.tokens.output)))
      console.log(renderRow("  Reasoning Tokens", formatNumber(usage.tokens.reasoning)))
      console.log(renderRow("  Cache Read", formatNumber(usage.tokens.cache.read)))
      console.log(renderRow("  Cache Write", formatNumber(usage.tokens.cache.write)))
      console.log(renderRow("  Cost", `$${usage.cost.toFixed(4)}`))
      console.log("├────────────────────────────────────────────────────────┤")
    }
    process.stdout.write("\x1B[1A")
    console.log("└────────────────────────────────────────────────────────┘")
    console.log()
  }

  // Tools
  if (Object.keys(agg.toolUsage).length > 0) {
    const sortedTools = Object.entries(agg.toolUsage).sort(([, a], [, b]) => b - a)

    console.log("┌────────────────────────────────────────────────────────┐")
    console.log("│                      TOOL USAGE                        │")
    console.log("├────────────────────────────────────────────────────────┤")

    const maxCount = Math.max(...sortedTools.map(([, count]) => count))
    const totalToolUsage = Object.values(agg.toolUsage).reduce((a, b) => a + b, 0)

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

  // Per-session breakdown
  if (agg.sessions.length > 0) {
    console.log()
    console.log("┌────────────────────────────────────────────────────────┐")
    console.log("│                   SESSION BREAKDOWN                    │")
    console.log("├────────────────────────────────────────────────────────┤")
    for (const s of agg.sessions) {
      const label = s.variantNum !== undefined ? ` ${s.agent} v${s.variantNum}` : ` ${s.agent}`
      const maxLabelLen = 28
      const truncLabel = label.length > maxLabelLen ? label.substring(0, maxLabelLen - 2) + ".." : label
      const maxIDLen = width - maxLabelLen - 3 // space for borders and gap
      const truncID = s.id.length > maxIDLen ? s.id.substring(0, maxIDLen - 3) + "..." : s.id
      console.log(renderRow(truncLabel, truncID))
    }
    console.log("└────────────────────────────────────────────────────────┘")
  }
}

// List all variant-loop runs
async function listRuns() {
  const sessions = await getAllSessions()

  // Group by runID
  const runs = new Map<string, { bot: string; opponent: string; map: string; sessions: number; iterations: Set<number>; earliest: number; latest: number }>()

  for (const session of sessions) {
    const parsed = parseVariantLoopTitle(session.title)
    if (!parsed) continue

    let run = runs.get(parsed.runID)
    if (!run) {
      run = { bot: parsed.bot, opponent: parsed.opponent, map: parsed.map, sessions: 0, iterations: new Set(), earliest: Infinity, latest: 0 }
      runs.set(parsed.runID, run)
    }
    run.sessions++
    run.iterations.add(parsed.iteration)
    if (session.time.created < run.earliest) run.earliest = session.time.created
    if (session.time.updated > run.latest) run.latest = session.time.updated
  }

  if (runs.size === 0) {
    console.log("No variant-loop runs found.")
    return
  }

  // Sort by earliest time descending (most recent first)
  const sorted = [...runs.entries()].sort(([, a], [, b]) => b.earliest - a.earliest)

  // Find max run ID length to size the column properly
  const maxIDLen = Math.max(22, ...sorted.map(([id]) => id.length))
  const idCol = maxIDLen + 1 // +1 for padding
  const hDash = "─".repeat(idCol)
  const totalW = idCol + 2 + 20 + 1 + 6 + 1 + 6 + 1 + 7 + 1 + 12 + 1 // borders + columns

  console.log(`┌${hDash}┬────────────────────┬──────┬──────┬───────┬────────────┐`)
  console.log(`│${"VARIANT-LOOP RUNS".padStart(Math.floor((totalW - 2 + 17) / 2)).padEnd(totalW - 2)}│`)
  console.log(`├${hDash}┼────────────────────┼──────┼──────┼───────┼────────────┤`)
  console.log(`│ ${"RUN ID".padEnd(idCol - 1)}│ ${"Bot".padEnd(18)} │ Iter │ Sess │ Map   │ Date       │`)
  console.log(`├${hDash}┼────────────────────┼──────┼──────┼───────┼────────────┤`)

  for (const [runID, run] of sorted) {
    const id = runID.padEnd(idCol - 1)
    const bot = run.bot.padEnd(18).substring(0, 18)
    const iters = run.iterations.size.toString().padStart(4)
    const sess = run.sessions.toString().padStart(4)
    const map = run.map.padEnd(5).substring(0, 5)
    const date = new Date(run.earliest).toLocaleDateString()
    console.log(`│ ${id}│ ${bot} │ ${iters} │ ${sess} │ ${map} │ ${date.padEnd(10)} │`)
  }

  console.log(`└${hDash}┴────────────────────┴──────┴──────┴───────┴────────────┘`)
  console.log()
  console.log("To see per-iteration breakdown:")
  console.log("  bun run session-stats.ts --run <RUN_ID>")
  console.log()
  console.log("To see a specific iteration:")
  console.log("  bun run session-stats.ts --iter <RUN_ID> <N>")
}

// Show per-iteration breakdown for a run
async function showRun(runID: string) {
  const sessions = await getAllSessions()

  // Filter to this run and group by iteration
  const iterationMap = new Map<number, SessionInfo[]>()
  let runMeta: { bot: string; opponent: string; map: string } | null = null

  for (const session of sessions) {
    const parsed = parseVariantLoopTitle(session.title)
    if (!parsed || parsed.runID !== runID) continue

    if (!runMeta) runMeta = { bot: parsed.bot, opponent: parsed.opponent, map: parsed.map }

    let group = iterationMap.get(parsed.iteration)
    if (!group) {
      group = []
      iterationMap.set(parsed.iteration, group)
    }
    group.push(session)
  }

  if (iterationMap.size === 0) {
    console.error(`No sessions found for run: ${runID}`)
    process.exit(1)
  }

  console.log(`┌────────────────────────────────────────────────────────┐`)
  console.log(`│              VARIANT-LOOP RUN SUMMARY                  │`)
  console.log(`├────────────────────────────────────────────────────────┤`)
  console.log(`│ Run ID:   ${runID.padEnd(44)} │`)
  if (runMeta) {
    console.log(`│ Bot:      ${runMeta.bot.padEnd(44)} │`)
    console.log(`│ Opponent: ${runMeta.opponent.padEnd(44)} │`)
    console.log(`│ Map:      ${runMeta.map.padEnd(44)} │`)
  }
  console.log(`│ Iterations: ${iterationMap.size.toString().padEnd(42)} │`)
  console.log(`└────────────────────────────────────────────────────────┘`)
  console.log()

  // Process each iteration
  const sortedIters = [...iterationMap.keys()].sort((a, b) => a - b)
  const runTotal = newAggregatedStats(`RUN TOTAL: ${runID}`)

  for (const iterNum of sortedIters) {
    const iterSessions = iterationMap.get(iterNum)!
    const iterAgg = newAggregatedStats(`ITERATION ${iterNum}`)

    for (const session of iterSessions) {
      const parsed = parseVariantLoopTitle(session.title)!
      try {
        const stats = await getSessionStats(session.id)
        mergeSessionIntoAggregated(iterAgg, stats, { agent: parsed.agent, variantNum: parsed.variantNum })
        mergeSessionIntoAggregated(runTotal, stats, { agent: parsed.agent, variantNum: parsed.variantNum })
      } catch {
        console.error(`  Warning: could not read stats for session ${session.id}`)
      }
    }

    displayAggregatedStats(iterAgg)
    console.log()
  }

  // Show run total
  if (sortedIters.length > 1) {
    console.log("═══════════════════════════════════════════════════════════")
    displayAggregatedStats(runTotal)
  }
}

// Show stats for a single iteration of a run
async function showIteration(runID: string, iterNum: number) {
  const sessions = await getAllSessions()

  const iterSessions: { session: SessionInfo; parsed: VariantLoopTitle }[] = []
  for (const session of sessions) {
    const parsed = parseVariantLoopTitle(session.title)
    if (!parsed || parsed.runID !== runID || parsed.iteration !== iterNum) continue
    iterSessions.push({ session, parsed })
  }

  if (iterSessions.length === 0) {
    console.error(`No sessions found for run ${runID}, iteration ${iterNum}`)
    process.exit(1)
  }

  const agg = newAggregatedStats(`RUN ${runID} - ITERATION ${iterNum}`)

  for (const { session, parsed } of iterSessions) {
    try {
      const stats = await getSessionStats(session.id)
      mergeSessionIntoAggregated(agg, stats, { agent: parsed.agent, variantNum: parsed.variantNum })
    } catch {
      console.error(`  Warning: could not read stats for session ${session.id}`)
    }
  }

  displayAggregatedStats(agg)
}

// Main execution
const main = async () => {
  const args = process.argv.slice(2)

  if (args.length === 0) {
    console.error("Usage:")
    console.error("  bun run session-stats.ts <session-id>           # Single session stats")
    console.error("  bun run session-stats.ts --runs                  # List all variant-loop runs")
    console.error("  bun run session-stats.ts --run <RUN_ID>          # Per-iteration breakdown")
    console.error("  bun run session-stats.ts --iter <RUN_ID> <N>     # Single iteration stats")
    console.error("\nTo find session IDs, run: opencode session list")
    process.exit(1)
  }

  try {
    if (args[0] === "--runs") {
      await listRuns()
    } else if (args[0] === "--run" && args[1]) {
      await showRun(args[1])
    } else if (args[0] === "--iter" && args[1] && args[2]) {
      await showIteration(args[1], parseInt(args[2], 10))
    } else if (!args[0].startsWith("--")) {
      // Original single-session mode
      const stats = await getSessionStats(args[0])
      displaySessionStats(stats)
    } else {
      console.error(`Unknown option: ${args[0]}`)
      process.exit(1)
    }
  } catch (error) {
    console.error(`Error: ${error instanceof Error ? error.message : String(error)}`)
    process.exit(1)
  }
}

main()
