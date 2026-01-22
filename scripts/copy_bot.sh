#!/bin/bash

# Script to clone a bot folder and update package names to 'copy_bot'
# Usage: ./copy_bot.sh src/grok_code_fast_1

set -e

if [ -z "$1" ]; then
    echo "Usage: $0 <source_folder>"
    echo "Example: $0 src/grok_code_fast_1"
    exit 1
fi

# Normalize the source path (remove trailing slash if present)
SOURCE_DIR="${1%/}"

# Extract just the bot name from the path
SOURCE_NAME=$(basename "$SOURCE_DIR")

# Define destination
DEST_DIR="src/copy_bot"

# Check if source exists
if [ ! -d "$SOURCE_DIR" ]; then
    echo "Error: Source directory '$SOURCE_DIR' does not exist"
    exit 1
fi

# Remove existing copy_bot if it exists
if [ -d "$DEST_DIR" ]; then
    echo "Removing existing $DEST_DIR..."
    rm -rf "$DEST_DIR"
fi

# Create destination directory
mkdir -p "$DEST_DIR"

# Copy only .java files from source to destination
echo "Copying Java files from $SOURCE_DIR to $DEST_DIR..."
find "$SOURCE_DIR" -maxdepth 1 -name "*.java" -exec cp {} "$DEST_DIR/" \;

# Update package declarations in all Java files
echo "Updating package declarations to 'copy_bot'..."
for file in "$DEST_DIR"/*.java; do
    if [ -f "$file" ]; then
        # Replace package declaration (handles various package names)
        if sed --version >/dev/null 2>&1; then
            sed -i "s/^package $SOURCE_NAME;/package copy_bot;/" "$file"
            # Also handle cases where package might have different formatting
            sed -i "s/^package  *[a-zA-Z_][a-zA-Z0-9_]*  *;/package copy_bot;/" "$file"
        else
            sed -i '' "s/^package $SOURCE_NAME;/package copy_bot;/" "$file"
            # Also handle cases where package might have different formatting
            sed -i '' "s/^package  *[a-zA-Z_][a-zA-Z0-9_]*  *;/package copy_bot;/" "$file"
        fi
    fi
done

echo "Done! Bot cloned to $DEST_DIR"
echo "You can now run: ./gradlew runWithSummary -PteamA=copy_bot -PteamB=examplefuncsplayer -Pmaps=Shrine"
