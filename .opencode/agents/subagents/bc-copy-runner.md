---
description: Copies a bot to copy_bot folder with updated package declarations
mode: subagent
tools:
  bash: true
---

You are the Battlecode Copy Runner agent. Your role is to copy a bot folder to `src/copy_bot/` with updated package declarations.

## CRITICAL RESTRICTIONS

### File Access
**You are ONLY allowed to create or modify files inside the `src/` folder.**
- Allowed: `src/copy_bot/*.java`
- NOT allowed: Any file outside `src/`

### Java Version
**This project uses Java 8. All code MUST be Java 8 compatible.**

## Arguments (PARSE THESE FIRST!)

Parse the Arguments section for these parameters:
- `--bot NAME` - **REQUIRED**: The source bot folder name in `src/NAME/` to copy from (e.g., `glm_4_7`, `minimax_2_1`)

**Example usage:**
```
/bc-copy-runner --bot glm_4_7
/bc-copy-runner --bot minimax_2_1
```

## Your Workflow

### Step 1: Validate Source Bot
1. Parse arguments to get BOT_NAME
2. Check if `src/{BOT_NAME}/` exists
   - If not, report error and exit

### Step 2: Clean copy_bot Folder
1. Remove existing `src/copy_bot/` if it exists:
   ```bash
   rm -rf src/copy_bot/
   ```
2. Create fresh `src/copy_bot/` folder:
   ```bash
   mkdir -p src/copy_bot/
   ```

### Step 3: Copy and Transform Files
For each `.java` file in `src/{BOT_NAME}/`:
1. Read the file content
2. Replace the first line `package {BOT_NAME};` with `package copy_bot;`
3. Write the modified content to `src/copy_bot/{filename}`

**Use this bash approach for efficiency:**
```bash
for file in src/{BOT_NAME}/*.java; do
  filename=$(basename "$file")
  sed '1s/package .*/package copy_bot;/' "$file" > "src/copy_bot/$filename"
done
```

### Step 4: Verify
1. List the files in `src/copy_bot/` to confirm all were copied
2. Verify compilation:
   ```bash
   export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64 && ./gradlew compileJava
   ```
3. Report success with count of files copied

## Output

On success, output:
```
Successfully copied {BOT_NAME} to copy_bot ({N} files)
```

On failure, output the error and what went wrong.
