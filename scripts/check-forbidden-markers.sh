#!/bin/bash
# Forbidden Token Scan Script for MoniWorks
# Required by spec 16 (Release Readiness and Quality Gates)
#
# This script scans the application source code for forbidden markers that
# indicate incomplete work. CI should run this script and fail if any markers
# are found.
#
# Scanned markers: TODO, FIXME, XXX, HACK, TEMP, WIP, STUB
# Scope: src/main/java/ and src/test/java/ (excludes generated frontend code)

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

echo "=== MoniWorks Forbidden Token Scan ==="
echo "Scanning for: TODO, FIXME, XXX, HACK, TEMP, WIP, STUB"
echo ""

# Define patterns to search for (using word boundaries to avoid false positives)
# \b ensures we match whole words, not substrings (e.g., won't match "TEMP" in "RECURRING_TEMPLATE")
PATTERNS="\bTODO\b|\bFIXME\b|\bXXX\b|\bHACK\b|\bTEMP\b|\bWIP\b|\bSTUB\b"

# Directories to scan (application code only, not generated files)
SCAN_DIRS=(
    "$PROJECT_ROOT/src/main/java"
    "$PROJECT_ROOT/src/test/java"
)

FOUND_MARKERS=0

for dir in "${SCAN_DIRS[@]}"; do
    if [ -d "$dir" ]; then
        echo "Scanning: $dir"

        # Use grep to find markers, capturing output
        RESULTS=$(grep -rn -E "($PATTERNS)" "$dir" --include="*.java" 2>/dev/null || true)

        if [ -n "$RESULTS" ]; then
            echo "$RESULTS"
            FOUND_MARKERS=$((FOUND_MARKERS + $(echo "$RESULTS" | wc -l)))
        fi
    else
        echo "Directory not found: $dir (skipping)"
    fi
done

echo ""
echo "=== Scan Complete ==="

if [ $FOUND_MARKERS -gt 0 ]; then
    echo "FAILED: Found $FOUND_MARKERS forbidden marker(s)"
    echo ""
    echo "Resolution: Remove or address all TODO/FIXME/XXX/HACK/TEMP/WIP/STUB markers"
    echo "            before creating a release."
    exit 1
else
    echo "PASSED: No forbidden markers found"
    exit 0
fi
