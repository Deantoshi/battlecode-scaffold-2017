#!/usr/bin/env bun
import path from "path"
import os from "os"

type Tokens = {
  input: number
  output: number
  reasoning: number
}

type SessionRow = {
  id: string
  title: string
  run: string
  bot: string
  opponent: string
  map: string
  agent: string
  context: string
  updated: number
  messages: number
  tokens: Tokens
  total: number
}

type RunRow = {
  id: string
  bot: string
  opponent: string
  map: string
  sessions: number
  messages: number
  tokens: Tokens
  total: number
  updated: number
}

type Data = {
  runs: RunRow[]
  sessions: SessionRow[]
  totals: {
    runs: number
    sessions: number
    messages: number
    tokens: Tokens
    total: number
  }
  filter: {
    dir: string
    prefix: string
  }
}

const port = Number(process.env["TOKEN_UI_PORT"] || 4873)
const root = path.join(os.homedir(), ".local", "share", "opencode", "storage")

const toNum = (value: unknown) => {
  if (typeof value === "number" && Number.isFinite(value)) return value
  return 0
}

const parseTitle = (title: string) => {
  const parts = title.split(":")
  const run = parts[1] || "unknown"
  const bot = parts[2] || ""
  const opponent = parts[3] || ""
  const map = parts[4] || ""
  const agent = parts[5] || ""
  const context = parts.slice(6).join(":")
  return { run, bot, opponent, map, agent, context }
}

const buildData = async (dir: string, prefix: string): Promise<Data> => {
  const rootStat = await Bun.file(root).stat().catch(() => undefined)
  const missing = !rootStat || !rootStat.isDirectory()

  if (missing) {
    return {
      runs: [],
      sessions: [],
      totals: {
        runs: 0,
        sessions: 0,
        messages: 0,
        tokens: { input: 0, output: 0, reasoning: 0 },
        total: 0,
      },
      filter: {
        dir,
        prefix,
      },
    }
  }

  const sessionFiles = await Array.fromAsync(
    new Bun.Glob("session/*/*.json").scan({
      cwd: root,
      absolute: true,
    }),
  )

  const sessions: SessionRow[] = []

  for (const file of sessionFiles) {
    const raw = (await Bun.file(file).json().catch(() => undefined)) as unknown
    if (!raw || typeof raw !== "object") continue
    const data = raw as Record<string, unknown>
    const id = typeof data["id"] === "string" ? data["id"] : ""
    if (!id) continue
    const title = typeof data["title"] === "string" ? data["title"] : ""
    if (!title) continue
    if (!title.startsWith(prefix)) continue
    const directory = typeof data["directory"] === "string" ? data["directory"] : ""
    if (!directory) continue
    if (path.resolve(directory) !== path.resolve(dir)) continue
    const time = data["time"]
    const updated =
      time && typeof time === "object" && typeof (time as Record<string, unknown>)["updated"] === "number"
        ? ((time as Record<string, unknown>)["updated"] as number)
        : 0

    const split = parseTitle(title)
    const tokens = { input: 0, output: 0, reasoning: 0 }
    const counts = { messages: 0 }

    const msgFiles = await Array.fromAsync(
      new Bun.Glob(`message/${id}/*.json`).scan({
        cwd: root,
        absolute: true,
      }),
    )

    for (const msgFile of msgFiles) {
      const msgRaw = (await Bun.file(msgFile).json().catch(() => undefined)) as unknown
      if (!msgRaw || typeof msgRaw !== "object") continue
      const msg = msgRaw as Record<string, unknown>
      if (msg["role"] !== "assistant") continue
      const tok = msg["tokens"]
      if (!tok || typeof tok !== "object") continue
      const tokData = tok as Record<string, unknown>
      tokens.input += toNum(tokData["input"])
      tokens.output += toNum(tokData["output"])
      tokens.reasoning += toNum(tokData["reasoning"])
      counts.messages += 1
    }

    sessions.push({
      id,
      title,
      run: split.run,
      bot: split.bot,
      opponent: split.opponent,
      map: split.map,
      agent: split.agent,
      context: split.context,
      updated,
      messages: counts.messages,
      tokens,
      total: tokens.input + tokens.output + tokens.reasoning,
    })
  }

  const runMap = new Map<string, RunRow>()
  for (const session of sessions) {
    const existing = runMap.get(session.run)
    const run =
      existing ||
      (() => {
        const created = {
          id: session.run,
          bot: session.bot,
          opponent: session.opponent,
          map: session.map,
          sessions: 0,
          messages: 0,
          tokens: { input: 0, output: 0, reasoning: 0 },
          total: 0,
          updated: 0,
        }
        runMap.set(session.run, created)
        return created
      })()

    run.sessions += 1
    run.messages += session.messages
    run.tokens.input += session.tokens.input
    run.tokens.output += session.tokens.output
    run.tokens.reasoning += session.tokens.reasoning
    run.total += session.total
    if (session.updated > run.updated) run.updated = session.updated
  }

  const runs = [...runMap.values()].toSorted((a, b) => b.updated - a.updated)
  const totals = {
    runs: runs.length,
    sessions: sessions.length,
    messages: sessions.reduce((sum, item) => sum + item.messages, 0),
    tokens: {
      input: sessions.reduce((sum, item) => sum + item.tokens.input, 0),
      output: sessions.reduce((sum, item) => sum + item.tokens.output, 0),
      reasoning: sessions.reduce((sum, item) => sum + item.tokens.reasoning, 0),
    },
    total: sessions.reduce((sum, item) => sum + item.total, 0),
  }

  return {
    runs,
    sessions: sessions.toSorted((a, b) => b.updated - a.updated),
    totals,
    filter: {
      dir,
      prefix,
    },
  }
}

const html = (dataPath: string) => `<!doctype html>
<html lang="en">
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>Variant Loop Token Usage</title>
    <style>
      :root {
        --bg: #f6f1e6;
        --bg-strong: #f0e3cc;
        --ink: #1a1a1a;
        --muted: #5f5a4f;
        --accent: #ff6a00;
        --accent-2: #0c7f7f;
        --card: #ffffffcc;
        --stroke: #d6c6a8;
        --shadow: 0 18px 40px rgba(26, 26, 26, 0.12);
        --mono: "JetBrains Mono", "Iosevka", "SFMono-Regular", ui-monospace, monospace;
        --sans: "Space Grotesk", "Sora", "Avenir Next", "Segoe UI", sans-serif;
      }

      * {
        box-sizing: border-box;
      }

      body {
        margin: 0;
        font-family: var(--sans);
        color: var(--ink);
        background: radial-gradient(circle at top, #fff5e1 0%, #f6f1e6 45%, #efe2c9 100%);
        min-height: 100vh;
      }

      header {
        padding: 32px 24px 12px;
        max-width: 1200px;
        margin: 0 auto;
      }

      h1 {
        margin: 0;
        font-size: clamp(28px, 3vw, 40px);
        letter-spacing: -0.02em;
      }

      .subtitle {
        color: var(--muted);
        font-size: 14px;
        margin-top: 8px;
      }

      main {
        max-width: 1200px;
        margin: 0 auto;
        padding: 0 24px 48px;
        display: grid;
        gap: 20px;
      }

      .panel {
        background: var(--card);
        border: 1px solid var(--stroke);
        border-radius: 20px;
        box-shadow: var(--shadow);
        padding: 20px;
        backdrop-filter: blur(6px);
      }

      .metrics {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
        gap: 12px;
      }

      .metric {
        padding: 14px 16px;
        background: linear-gradient(135deg, #fff5e1, #fff);
        border-radius: 16px;
        border: 1px solid #f0dfc4;
      }

      .metric h3 {
        margin: 0;
        font-size: 12px;
        text-transform: uppercase;
        letter-spacing: 0.08em;
        color: var(--muted);
      }

      .metric p {
        margin: 6px 0 0;
        font-size: 20px;
        font-weight: 600;
      }

      .controls {
        display: grid;
        grid-template-columns: 1fr auto;
        gap: 12px;
        align-items: center;
      }

      .search {
        display: flex;
        align-items: center;
        gap: 8px;
        padding: 10px 14px;
        border-radius: 14px;
        border: 1px solid var(--stroke);
        background: #fff;
      }

      .search input {
        border: none;
        outline: none;
        width: 100%;
        font-size: 14px;
        font-family: var(--sans);
      }

      button {
        border: none;
        background: var(--accent);
        color: #fff;
        padding: 10px 16px;
        border-radius: 12px;
        font-weight: 600;
        cursor: pointer;
        transition: transform 0.2s ease, box-shadow 0.2s ease;
      }

      button:hover {
        transform: translateY(-2px);
        box-shadow: 0 8px 16px rgba(255, 106, 0, 0.25);
      }

      .grid {
        display: grid;
        gap: 16px;
        grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
      }

      table {
        width: 100%;
        border-collapse: collapse;
        font-size: 13px;
      }

      th,
      td {
        padding: 10px;
        text-align: left;
        border-bottom: 1px dashed #e2d3b7;
      }

      th {
        text-transform: uppercase;
        letter-spacing: 0.06em;
        font-size: 11px;
        color: var(--muted);
      }

      tbody tr {
        transition: background 0.2s ease;
      }

      tbody tr:hover {
        background: rgba(12, 127, 127, 0.08);
      }

      .tag {
        display: inline-flex;
        gap: 6px;
        align-items: center;
        padding: 2px 8px;
        background: rgba(12, 127, 127, 0.14);
        color: #0a5f5f;
        border-radius: 999px;
        font-size: 11px;
        font-weight: 600;
      }

      .mono {
        font-family: var(--mono);
      }

      .muted {
        color: var(--muted);
      }

      .selected {
        background: rgba(255, 106, 0, 0.16);
      }

      @media (max-width: 800px) {
        header,
        main {
          padding-left: 16px;
          padding-right: 16px;
        }

        .controls {
          grid-template-columns: 1fr;
        }
      }
    </style>
  </head>
  <body>
    <header>
      <h1>Variant Loop Token Usage</h1>
      <div class="subtitle">Tracking token usage for variant-loop runs from opencode storage.</div>
    </header>
    <main>
      <section class="panel metrics" id="metrics"></section>
      <section class="panel controls">
        <div class="search">
          <span class="tag">Filter</span>
          <input id="search" placeholder="Run id, bot, opponent, map..." />
        </div>
        <button id="refresh">Refresh</button>
      </section>
      <section class="grid">
        <div class="panel">
          <h3>Runs</h3>
          <table>
            <thead>
              <tr>
                <th>Run</th>
                <th>Bot</th>
                <th>Sessions</th>
                <th>Total</th>
              </tr>
            </thead>
            <tbody id="runs"></tbody>
          </table>
        </div>
        <div class="panel">
          <h3>Sessions</h3>
          <table>
            <thead>
              <tr>
                <th>Updated</th>
                <th>Agent</th>
                <th>Tokens</th>
              </tr>
            </thead>
            <tbody id="sessions"></tbody>
          </table>
        </div>
      </section>
    </main>
    <script>
      const state = {
        data: null,
        selected: "",
        search: "",
      }

      const formatNum = (value) => new Intl.NumberFormat().format(value || 0)
      const formatDate = (value) => {
        if (!value) return ""
        return new Date(value).toLocaleString()
      }

      const fetchData = async () => {
        const response = await fetch("${dataPath}")
        const json = await response.json()
        state.data = json
        render()
      }

      const render = () => {
        if (!state.data) return

        const metrics = document.querySelector("#metrics")
        const runsBody = document.querySelector("#runs")
        const sessionsBody = document.querySelector("#sessions")
        if (!metrics || !runsBody || !sessionsBody) return

        const totals = state.data.totals
        metrics.innerHTML = [
          \`<div class="metric"><h3>Runs</h3><p>\${formatNum(totals.runs)}</p></div>\`,
          \`<div class="metric"><h3>Sessions</h3><p>\${formatNum(totals.sessions)}</p></div>\`,
          \`<div class="metric"><h3>Messages</h3><p>\${formatNum(totals.messages)}</p></div>\`,
          \`<div class="metric"><h3>Input Tokens</h3><p>\${formatNum(totals.tokens.input)}</p></div>\`,
          \`<div class="metric"><h3>Output Tokens</h3><p>\${formatNum(totals.tokens.output)}</p></div>\`,
          \`<div class="metric"><h3>Reasoning Tokens</h3><p>\${formatNum(totals.tokens.reasoning)}</p></div>\`,
        ].join("")

        const query = state.search.trim().toLowerCase()
        const runs = state.data.runs.filter((run) => {
          if (!query) return true
          const hay = \`\${run.id} \${run.bot} \${run.opponent} \${run.map}\`.toLowerCase()
          return hay.includes(query)
        })

        runsBody.innerHTML = runs
          .map((run) => {
            const selected = run.id === state.selected ? "selected" : ""
            return \`<tr class="\${selected}" data-run="\${run.id}">
              <td class="mono">\${run.id}</td>
              <td>\${run.bot || "-"}</td>
              <td>\${formatNum(run.sessions)}</td>
              <td>\${formatNum(run.total)}</td>
            </tr>\`
          })
          .join("")

        const active = state.selected || (runs[0] ? runs[0].id : "")
        if (!state.selected && active) state.selected = active

        const sessions = state.data.sessions.filter((session) => session.run === state.selected)
        sessionsBody.innerHTML = sessions
          .map((session) => {
            return \`<tr>
              <td class="muted">\${formatDate(session.updated)}</td>
              <td>\${session.agent || "-"}</td>
              <td class="mono">\${formatNum(session.total)}</td>
            </tr>\`
          })
          .join("")

        const rows = runsBody.querySelectorAll("tr")
        rows.forEach((row) => {
          row.addEventListener("click", () => {
            const id = row.getAttribute("data-run") || ""
            state.selected = id
            render()
          })
        })
      }

      const search = document.querySelector("#search")
      if (search) {
        search.addEventListener("input", (event) => {
          const target = event.target
          if (!target) return
          state.search = target.value || ""
          render()
        })
      }

      const refresh = document.querySelector("#refresh")
      if (refresh) {
        refresh.addEventListener("click", () => fetchData())
      }

      fetchData()
    </script>
  </body>
</html>`

const server = Bun.serve({
  port,
  async fetch(req) {
    const url = new URL(req.url)
    const params = url.searchParams
    const dir = params.get("dir") || process.cwd()
    const prefix = params.get("prefix") || "variant-loop:"

    if (url.pathname === "/data") {
      const data = await buildData(dir, prefix)
      return new Response(JSON.stringify(data), {
        headers: {
          "Content-Type": "application/json",
          "Cache-Control": "no-store",
        },
      })
    }

    if (url.pathname === "/") {
      const dataPath = "/data" + url.search
      return new Response(html(dataPath), {
        headers: {
          "Content-Type": "text/html; charset=utf-8",
        },
      })
    }

    return new Response("Not found", { status: 404 })
  },
})

console.log(`Token UI running at http://localhost:${server.port}`)
