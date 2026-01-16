/// <reference path="../env.d.ts" />
import { tool } from "@opencode-ai/plugin"
import * as path from "path"
import * as fs from "fs/promises"
import DESCRIPTION from "./unsafe-write.txt"

export default tool({
  description: DESCRIPTION,
  args: {
    filePath: tool.schema.string().describe("Absolute or relative path to write (relative to cwd)"),
    content: tool.schema.string().describe("Full file content to write"),
  },
  async execute(args) {
    const filepath = path.isAbsolute(args.filePath) ? args.filePath : path.join(process.cwd(), args.filePath)
    await fs.writeFile(filepath, args.content, "utf8")
    return `Wrote ${args.content.length} bytes to ${filepath}`
  },
})
