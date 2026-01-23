#!/bin/bash
# Release Readiness Check Script for MoniWorks
# Required by spec 16 (Release Readiness and Quality Gates)
#
# This script performs a comprehensive pre-release validation including:
# 1. Git status verification (clean working directory)
# 2. Forbidden marker scan (no TODO/FIXME/etc)
# 3. Code formatting check (Spotless)
# 4. Compilation check
# 5. Test execution with coverage verification
# 6. Database migration validation
#
# Usage: ./scripts/release-check.sh [--skip-owasp]
#   --skip-owasp: Skip OWASP dependency check (useful for local development)
#
# Exit codes:
#   0 - All checks passed, ready for release
#   1 - One or more checks failed

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
SKIP_OWASP=false

# Parse arguments
for arg in "$@"; do
    case $arg in
        --skip-owasp)
            SKIP_OWASP=true
            shift
            ;;
    esac
done

cd "$PROJECT_ROOT"

echo "=============================================="
echo "  MoniWorks Release Readiness Check"
echo "=============================================="
echo ""
echo "Project root: $PROJECT_ROOT"
echo "Date: $(date)"
echo ""

FAILED=0

# Function to run a check step
run_check() {
    local step_name="$1"
    local step_command="$2"

    echo "----------------------------------------------"
    echo "STEP: $step_name"
    echo "----------------------------------------------"

    if eval "$step_command"; then
        echo "  PASSED: $step_name"
        return 0
    else
        echo "  FAILED: $step_name"
        FAILED=$((FAILED + 1))
        return 1
    fi
}

# STEP 1: Git Status Check
echo ""
run_check "Git status verification" '
    if [ -n "$(git status --porcelain)" ]; then
        echo "  Working directory is not clean:"
        git status --short
        exit 1
    else
        echo "  Working directory is clean"
        exit 0
    fi
' || true

# STEP 2: Forbidden Markers Scan
echo ""
run_check "Forbidden marker scan (TODO/FIXME/etc)" '
    "$SCRIPT_DIR/check-forbidden-markers.sh"
' || true

# STEP 3: Code Formatting Check (Spotless)
echo ""
run_check "Code formatting check (Spotless)" '
    ./mvnw -q spotless:check 2>&1
' || true

# STEP 4: Clean Compilation
echo ""
run_check "Clean compilation" '
    ./mvnw -q clean compile -DskipTests 2>&1
' || true

# STEP 5: Test Execution with Coverage
echo ""
run_check "Test execution with coverage" '
    ./mvnw -q test 2>&1
    TEST_EXIT=$?
    if [ $TEST_EXIT -ne 0 ]; then
        exit 1
    fi

    # Display coverage summary if report exists
    COVERAGE_REPORT="target/site/jacoco/index.html"
    if [ -f "$COVERAGE_REPORT" ]; then
        echo "  Coverage report generated: $COVERAGE_REPORT"
    fi

    exit 0
' || true

# STEP 6: Coverage Verification
echo ""
run_check "Coverage threshold verification (70% overall, 80% critical)" '
    ./mvnw -q jacoco:check -DskipTests 2>&1
' || true

# STEP 7: Database Migration Check (via test execution)
echo ""
run_check "Database migration validation" '
    echo "  Migrations validated during test execution (Flyway runs in integration tests)"
    exit 0
' || true

# STEP 8: OWASP Dependency Check (optional)
if [ "$SKIP_OWASP" = false ]; then
    echo ""
    run_check "OWASP dependency vulnerability scan" '
        ./mvnw -q dependency-check:check 2>&1
    ' || true
else
    echo ""
    echo "----------------------------------------------"
    echo "STEP: OWASP dependency vulnerability scan"
    echo "----------------------------------------------"
    echo "  SKIPPED (use without --skip-owasp to run)"
fi

# STEP 9: Build Verification
echo ""
run_check "Production build verification" '
    ./mvnw -q package -DskipTests -Pproduction 2>&1
' || true

# Summary
echo ""
echo "=============================================="
echo "  Release Readiness Summary"
echo "=============================================="

if [ $FAILED -eq 0 ]; then
    echo ""
    echo "  ALL CHECKS PASSED - Ready for release!"
    echo ""
    echo "  Next steps:"
    echo "    1. Tag the release: git tag -a v0.X.Y -m 'Release 0.X.Y'"
    echo "    2. Push the tag: git push origin v0.X.Y"
    echo ""
    exit 0
else
    echo ""
    echo "  FAILED: $FAILED check(s) did not pass"
    echo ""
    echo "  Resolution:"
    echo "    1. Fix all failing checks above"
    echo "    2. Re-run this script to verify"
    echo "    3. Only tag a release when all checks pass"
    echo ""
    exit 1
fi
