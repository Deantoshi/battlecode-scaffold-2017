#!/bin/bash
# create-10-variants.sh - Creates 10 variant folders from original bot
#
# Usage: ./scripts/create-10-variants.sh <bot>

set -e

BOT="${1:-}"
NUM_VARIANTS=10

if [[ -z "$BOT" ]]; then
    echo "Usage: $0 <bot>"
    exit 1
fi

if [[ ! -d "src/$BOT" ]]; then
    echo "Error: Bot folder not found: src/$BOT"
    exit 1
fi

echo "Creating $NUM_VARIANTS variant folders from $BOT..."

# Clean up existing variants
for v in $(seq 1 $NUM_VARIANTS); do
    if [[ -d "src/${BOT}_v${v}" ]]; then
        rm -rf "src/${BOT}_v${v}"
    fi
done

# Create new variants
for v in $(seq 1 $NUM_VARIANTS); do
    VARIANT_DIR="src/${BOT}_v${v}"

    # Copy all files
    cp -r "src/$BOT" "$VARIANT_DIR"

    # Remove .state folder from variant (not needed)
    rm -rf "$VARIANT_DIR/.state"

    # Update package declarations in all Java files
    for java_file in "$VARIANT_DIR"/*.java; do
        if [[ -f "$java_file" ]]; then
            # Replace package declaration
            sed -i.bak "s/^package ${BOT};/package ${BOT}_v${v};/" "$java_file"
            rm -f "${java_file}.bak"
        fi
    done

    echo "  ✓ Created ${BOT}_v${v}"
done

echo ""
echo "All $NUM_VARIANTS variants created successfully."

# Verify by listing
echo ""
echo "Variant folders:"
ls -d src/${BOT}_v* 2>/dev/null | head -15
