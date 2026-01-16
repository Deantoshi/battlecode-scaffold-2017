#!/bin/bash
# create-variants.sh - Create 5 variant copies of a base bot
# Usage: ./scripts/create-variants.sh <bot_name>

set -e

BOT_NAME="$1"

if [ -z "$BOT_NAME" ]; then
    echo "Usage: $0 <bot_name>"
    echo "Example: $0 mybot"
    exit 1
fi

if [ ! -d "src/$BOT_NAME" ]; then
    echo "ERROR: Base bot not found at src/$BOT_NAME/"
    exit 1
fi

if [ ! -f "src/$BOT_NAME/RobotPlayer.java" ]; then
    echo "ERROR: RobotPlayer.java not found in src/$BOT_NAME/"
    exit 1
fi

echo "=== Creating 5 variants of $BOT_NAME ==="

# Clean up any existing variants
for i in 1 2 3 4 5; do
    if [ -d "src/${BOT_NAME}_v$i" ]; then
        echo "Removing existing variant: ${BOT_NAME}_v$i"
        rm -rf "src/${BOT_NAME}_v$i"
    fi
done

# Create 5 variant copies
for i in 1 2 3 4 5; do
    VARIANT="${BOT_NAME}_v$i"
    echo "Creating variant $i: $VARIANT"

    # Copy the folder
    cp -r "src/$BOT_NAME" "src/$VARIANT"

    # Update package declarations in all Java files
    for f in src/$VARIANT/*.java; do
        if [ -f "$f" ]; then
            # Use perl for cross-platform compatibility (macOS sed differs from GNU sed)
            perl -i -pe "s/package $BOT_NAME;/package $VARIANT;/g" "$f"
        fi
    done
done

echo ""
echo "=== Variants created ==="
ls -la src/${BOT_NAME}_v*/RobotPlayer.java 2>/dev/null || echo "Warning: No RobotPlayer.java files found"

echo ""
echo "=== Verifying package declarations ==="
for i in 1 2 3 4 5; do
    VARIANT="${BOT_NAME}_v$i"
    echo "$VARIANT:"
    grep "^package" src/$VARIANT/*.java 2>/dev/null | head -1 || echo "  (no package found)"
done

echo ""
echo "Done. Created variants: ${BOT_NAME}_v1 through ${BOT_NAME}_v5"
