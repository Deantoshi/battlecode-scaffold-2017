#!/usr/bin/env bun
import path from "path"
import { Global } from "../packages/opencode/src/global/index.ts"

type Tokens = {
  input: number
  output: number
  reasoning: number
}

type Result = {
  sessions: number
  messages: number
  tokens: Tokens
  filter: {
    dir: string
    title?: string
    after?: number
    before?: number
  }
}

const help = `
Usage: bun scripts/token-tracker.ts [options]

Options:
  --dir <path>        Filter by session directory (default: cwd)
  --title <prefix>    Filter by session title prefix
  --after <time>      Include sessions updated after time (ms or ISO)
  --before <time>     Include sessions updated before time (ms or ISO)
  --json              Output JSON
  -h, --help          Show help

Example:
  bun scripts/token-tracker.ts --title "variant-loop:" --after "2026-01-25T00:00:00Z"
`

const parseTime = (value: string) => {
  const num = Number(value)
  if (Number.isFinite(num)) return num
  const stamp = Date.parse(value)
  if (Number.isFinite(stamp)) return stamp
  return undefined
}

const toNum = (value: unknown) => {
  if (typeof value === "number" && Number.isFinite(value)) return value
  return 0
}

const args = [...process.argv.slice(2)]
const opts = {
  dir: path.resolve(process.cwd()),
  title: "",
  after: 0,
  before: Number.POSITIVE_INFINITY,
  json: false,
}

while (args.length > 0) {
  const arg = args.shift()
  if (!arg) continue

  if (arg === "--help" || arg === "-h") {
    console.log(help.trim())
    process.exit(0)
  }

  if (arg === "--json") {
    opts.json = true
    continue
  }

  if (arg === "--dir") {
    const value = args.shift()
    if (!value) {
      console.error("Missing value for --dir")
      process.exit(1)
    }
    opts.dir = path.resolve(value)
    continue
  }

  if (arg === "--title") {
    const value = args.shift()
    if (!value) {
      console.error("Missing value for --title")
      process.exit(1)
    }
    opts.title = value
    continue
  }

  if (arg === "--after") {
    const value = args.shift()
    if (!value) {
      console.error("Missing value for --after")
      process.exit(1)
    }
    const time = parseTime(value)
    if (time === undefined) {
      console.error(`Invalid --after value: ${value}`)
      process.exit(1)
    }
    opts.after = time
    continue
  }

  if (arg === "--before") {
    const value = args.shift()
    if (!value) {
      console.error("Missing value for --before")
      process.exit(1)
    }
    const time = parseTime(value)
    if (time === undefined) {
      console.error(`Invalid --before value: ${value}`)
      process.exit(1)
    }
    opts.before = time
    continue
  }

  console.error(`Unknown argument: ${arg}`)
  process.exit(1)
}

const root = path.join(Global.Path.data, "storage")
const rootStat = await Bun.file(root).stat().catch(() => null)
const missing = !rootStat || !rootStat.isDirectory()
if (missing) {
  const empty: Result = {
    sessions: 0,
    messages: 0,
    tokens: { input: 0, output: 0, reasoning: 0 },
    filter: {
      dir: opts.dir,
    },
  }
  if (opts.json) {
    console.log(JSON.stringify(empty, null, 2))
    process.exit(0)
  }
  console.log("No opencode storage found.")
  process.exit(0)
}

const sessionGlob = new Bun.Glob("session/*/*.json")
const sessionFiles = await Array.fromAsync(
  sessionGlob.scan({
    cwd: root,
    absolute: true,
  }),
)

const ids: string[] = []
for (const file of sessionFiles) {
  const session = await Bun.file(file).json().catch(() => null)
  if (!session) continue
  if (typeof session.id !== "string") continue
  if (typeof session.directory !== "string") continue
  const dir = path.resolve(session.directory)
  if (dir !== opts.dir) continue
  if (opts.title && typeof session.title === "string" && !session.title.startsWith(opts.title)) continue
  if (opts.title && typeof session.title !== "string") continue
  const updated = session.time && typeof session.time.updated === "number" ? session.time.updated : 0
  if (updated < opts.after) continue
  if (updated > opts.before) continue
  ids.push(session.id)
}

const tokens: Tokens = { input: 0, output: 0, reasoning: 0 }
const counts = {
  messages: 0,
}

for (const id of ids) {
  const msgGlob = new Bun.Glob(`message/${id}/*.json`)
  const msgFiles = await Array.fromAsync(
    msgGlob.scan({
      cwd: root,
      absolute: true,
    }),
  )

  for (const file of msgFiles) {
    const msg = await Bun.file(file).json().catch(() => null)
    if (!msg) continue
    if (msg.role !== "assistant") continue
    if (!msg.tokens) continue
    tokens.input += toNum(msg.tokens.input)
    tokens.output += toNum(msg.tokens.output)
    tokens.reasoning += toNum(msg.tokens.reasoning)
    counts.messages += 1
  }
}

const rangeAfter = opts.after > 0 ? opts.after : undefined
const rangeBefore = Number.isFinite(opts.before) ? opts.before : undefined

const result: Result = {
  sessions: ids.length,
  messages: counts.messages,
  tokens,
  filter: {
    dir: opts.dir,
    title: opts.title || undefined,
    after: rangeAfter,
    before: rangeBefore,
  },
}

if (opts.json) {
  console.log(JSON.stringify(result, null, 2))
  process.exit(0)
}

console.log(`Sessions: ${result.sessions}`)
console.log(`Assistant messages: ${result.messages}`)
console.log(`Input tokens: ${result.tokens.input}`)
console.log(`Output tokens: ${result.tokens.output}`)
console.log(`Reasoning tokens: ${result.tokens.reasoning}`)
if (rangeAfter) console.log(`After: ${new Date(rangeAfter).toISOString()}`)
if (rangeBefore) console.log(`Before: ${new Date(rangeBefore).toISOString()}`)
