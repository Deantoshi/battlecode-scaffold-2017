---
name: gptnet-web-search
description: Use this skill when working with GPTNet's web package and the user asks for web search, webpage reading, or browser interactions. It captures the exact open/navigate/snapshot/act workflow used by web.
---

# GPTNet Web Search (Managed Browser)

## When to use this skill

Use this when a task involves any of the following in `gptnet/web`:

- “Search the web” requests
- Opening and summarizing URLs
- Clicking/typing in pages through the managed browser
- Updating or debugging browser-tool behavior

## How `web` does web search

GPTNet `web` does web search through its managed browser tool (not a separate search API):

- Tool definition + formatting + system prompt: `web/src/main.ts`
- Browser control API: `web/server/index.ts` (`POST /api/browser/control`)
- Playwright browser manager/actions: `web/server/browser-service.ts`

The system prompt in `main.ts` requires:

1. Use `browser` `action: "open"` or `"navigate"` first for links/page content.
2. For interactions, use `action: "snapshot"` to get refs (`e1`, `e2`, …), then `action: "act"`.

## Recommended web-search workflow

1. **If user gave a full URL**
   - Use `open` (or `navigate` if reusing a tab).
2. **If user gave a search query**
   - Open a search URL (DuckDuckGo/Google/Bing depending on user preference), for example:
     - `https://duckduckgo.com/?q=<url-encoded query>`
   - Read extracted content and top links.
   - Use `snapshot` if you need precise click automation.
3. **Open result pages**
   - Use `navigate` in the current tab or `open` in a new tab.
4. **Synthesize answer**
   - Provide concise findings with source URLs.
   - Mention uncertainty when sources disagree.

## Required interaction rules

- Always run `snapshot` before `act` when using `ref` selectors.
- Re-run `snapshot` after page changes/navigation (refs can become stale).
- Preserve and pass `targetId` so actions apply to the expected tab.
- If there is no active tab, `open` first.

## Action examples

```json
{"action":"open","url":"https://duckduckgo.com/?q=pi+coding+agent+skills"}
{"action":"snapshot","targetId":"<tab-id>"}
{"action":"act","request":{"kind":"click","targetId":"<tab-id>","ref":"e5"}}
{"action":"navigate","targetId":"<tab-id>","url":"https://example.com"}
```

## Important constraints from server implementation

- Only `http://` and `https://` URLs are allowed.
- `localhost` and private-network IPs are blocked.
- Readable page text is truncated by `maxChars` (default 12,000; max 50,000).
- Image/media/font resources are blocked during page load for faster extraction.

## Useful debug actions

When search or interactions fail, check:

- `console` for page logs
- `errors` for uncaught page errors
- `requests` for network activity
- `tabs` to verify active tab state
