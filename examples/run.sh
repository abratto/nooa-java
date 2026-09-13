#!/usr/bin/env bash
#
# Run NOOA Java examples against a local (or configured) model endpoint.
#
# Usage:
#   ./run.sh --list                 List runnable examples
#   ./run.sh <Example>              Run one entry point (short or FQCN)
#   ./run.sh --all                  Run every runnable example and print a summary
#   ./run.sh --all --timeout 300    Per-example timeout in seconds (default 600)
#   ./run.sh <Example> --skip-build Skip the initial Maven install
#
# Environment:
#   NOOA_MODEL                Model name (default qwen3.8:27b-mlx)
#   NOOA_OLLAMA_BASE_URL      Ollama OpenAI-compatible base (default http://localhost:11434/v1)
#   NOOA_BASE_URL             Any OpenAI-compatible endpoint (overrides Ollama)
#   NOOA_API_KEY              API key for NOOA_BASE_URL / OpenAI
#   NOOA_REASONING_EFFORT     Override per-example reasoning effort (low|medium|high)
#
# The script forces the local Ollama endpoint unless NOOA_BASE_URL is already
# set, so examples do not quietly fall through to a hosted provider.
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
# Maven is invoked from the repository root so relative output paths
# (traces_demo/, snapshot_demo.json, .nooa-demo-memory.db) match the README.
cd "$REPO_ROOT"

DEFAULT_MODEL="qwen3.8:27b-mlx"
DEFAULT_OLLAMA_BASE_URL="http://localhost:11434/v1"
DEFAULT_TIMEOUT=600

# class|label|needs_model|needs_network|needs_node
CATALOG=(
  "QuickstartExamples|Core @Generate, PredictStrategy, helpers, Java workflow|yes|no|no"
  "QuickstartAdvanced|Context blocks, dynamic state, progressive disclosure|yes|no|no"
  "StrategyComparisonDemo|CodeAct vs PredictStrategy vs ReflexionStrategy|yes|no|no"
  "TracingDemo|JSONL tracing to ./traces_demo|yes|no|no"
  "SummarizationDemo|TokenBudgetSummarizer installation|yes|no|no"
  "SnapshotDemo|AgentSnapshot save/restore (Java only)|no|no|no"
  "ShellToolsDemo|ShellTools command execution|no|no|no"
  "MemoryDemo|SQLite memory lifecycle (Java only)|no|no|no"
  "McpDemo|MCP filesystem server over stdio|no|no|yes"
  "LegalIntakeDemo|Structured legal triage with PredictStrategy|yes|no|no"
  "NewsDigestAgent|Deterministic fetch + typed model summary|yes|no|no"
  "WeatherAgent|HTTP fetch + two-stage typed generation|yes|yes|no"
  "Examples04to15|Compatibility notice for the old combined launcher|no|no|no"
  "Examples10to11|Compatibility notice for the old memory/MCP launcher|no|no|no"
)

usage() {
  cat <<'EOF'
Run NOOA Java examples against a local (or configured) model endpoint.

Usage:
  ./run.sh --list                 List runnable examples
  ./run.sh <Example>              Run one entry point (short or FQCN)
  ./run.sh --all                  Run every runnable example and print a summary
  ./run.sh --all --timeout 300    Per-example timeout in seconds (default 600)
  ./run.sh <Example> --skip-build Skip the initial Maven install

Environment:
  NOOA_MODEL                Model name (default qwen3.8:27b-mlx)
  NOOA_OLLAMA_BASE_URL      Ollama OpenAI-compatible base (default http://localhost:11434/v1)
  NOOA_BASE_URL             Any OpenAI-compatible endpoint (overrides Ollama)
  NOOA_API_KEY              API key for NOOA_BASE_URL / OpenAI
  NOOA_REASONING_EFFORT     Override per-example reasoning effort (low|medium|high)

The script forces the local Ollama endpoint unless NOOA_BASE_URL is already
set, so examples do not quietly fall through to a hosted provider.
EOF
}

list_examples() {
  printf '%-24s %-60s %s\n' "EXAMPLE" "DESCRIPTION" "REQUIRES"
  printf '%-24s %-60s %s\n' "------------------------" "------------------------------------------------------------" "--------"
  for entry in "${CATALOG[@]}"; do
    IFS='|' read -r class label model network node <<<"$entry"
    local req=""
    [ "$model" = "yes" ] && req="${req}model "
    [ "$network" = "yes" ] && req="${req}network "
    [ "$node" = "yes" ] && req="${req}node "
    [ -z "$req" ] && req="none"
    printf '%-24s %-60s %s\n' "$class" "$label" "${req% }"
  done
}

entry_for() {
  local needle="$1"
  for entry in "${CATALOG[@]}"; do
    IFS='|' read -r class label model network node <<<"$entry"
    if [ "$class" = "$needle" ] || [ "ai.nooa.examples.$class" = "$needle" ]; then
      echo "$entry"
      return 0
    fi
  done
  return 1
}

ollama_model_present() {
  local model="$1"
  command -v curl >/dev/null 2>&1 || return 0
  curl -sf --max-time 5 http://localhost:11434/api/tags 2>/dev/null \
    | grep -q "\"name\":\"${model}" 
}

# Portable timeout: run a command in the background and kill it after N seconds.
run_with_timeout() {
  local secs="$1"
  shift
  "$@" &
  local pid=$!
  (
    sleep "$secs"
    if kill -0 "$pid" 2>/dev/null; then
      kill -TERM "$pid" 2>/dev/null
      sleep 3
      kill -KILL "$pid" 2>/dev/null
    fi
  ) &
  local watchdog=$!
  wait "$pid"
  local rc=$?
  kill "$watchdog" 2>/dev/null
  wait "$watchdog" 2>/dev/null
  return "$rc"
}

run_one() {
  local class="$1" fqcn="ai.nooa.examples.$1"
  local timeout="$TIMEOUT"

  echo
  echo "════════════════════════════════════════════════════════════════"
  echo "  ▶ ${fqcn}"
  echo "════════════════════════════════════════════════════════════════"
  run_with_timeout "$timeout" \
    mvn -q -pl examples exec:java "-Dexec.mainClass=${fqcn}"
  return $?
}

MODE=""
NAME=""
TIMEOUT="$DEFAULT_TIMEOUT"
SKIP_BUILD="no"

while [ $# -gt 0 ]; do
  case "$1" in
    --list|-l) MODE="list" ;;
    --all|-a) MODE="all" ;;
    --skip-build) SKIP_BUILD="yes" ;;
    --timeout) shift; TIMEOUT="${1:-$DEFAULT_TIMEOUT}" ;;
    --help|-h) usage; exit 0 ;;
    -*) echo "Unknown option: $1" >&2; usage; exit 2 ;;
    *) MODE="one"; NAME="$1" ;;
  esac
  shift
done

export NOOA_MODEL="${NOOA_MODEL:-$DEFAULT_MODEL}"
export NOOA_OLLAMA_BASE_URL="${NOOA_OLLAMA_BASE_URL:-$DEFAULT_OLLAMA_BASE_URL}"

if [ -z "${NOOA_BASE_URL:-}" ]; then
  export NOOA_BASE_URL="$NOOA_OLLAMA_BASE_URL"
  export NOOA_API_KEY="${NOOA_API_KEY:-ollama}"
fi

if [ -z "$MODE" ]; then
  usage
  exit 2
fi

if [ "$MODE" = "list" ]; then
  list_examples
  exit 0
fi

if [ "$SKIP_BUILD" != "yes" ]; then
  echo "Building and installing nooa-core + examples..."
  # 'clean' guards against stale Eclipse JDT (VS Code) class output in target/,
  # which can leave 'Unresolved compilation problem' stubs on the classpath.
  mvn -q -DskipTests -pl examples -am clean install || {
    echo "Build failed." >&2
    exit 1
  }
fi

if ! ollama_model_present "$NOOA_MODEL"; then
  echo "Warning: model '${NOOA_MODEL}' was not found via Ollama at http://localhost:11434."
  echo "         Model-requiring examples may fail. Pull it with: ollama pull ${NOOA_MODEL}"
fi

PASS=0
FAIL=0
SKIP=0
RESULTS=()

if [ "$MODE" = "one" ]; then
  if ! entry_for "$NAME" >/dev/null; then
    echo "Unknown example: $NAME" >&2
    echo "Run ./run.sh --list to see available examples." >&2
    exit 2
  fi
  if run_one "$NAME"; then
    echo "PASS: $NAME"
    exit 0
  else
    echo "FAIL: $NAME"
    exit 1
  fi
fi

# MODE = all
for entry in "${CATALOG[@]}"; do
  IFS='|' read -r class label model network node <<<"$entry"

  if [ "$node" = "yes" ] && ! command -v npx >/dev/null 2>&1; then
    RESULTS+=("SKIP  $class (needs Node.js/npx)")
    SKIP=$((SKIP + 1))
    continue
  fi

  if run_one "$class"; then
    RESULTS+=("PASS  $class")
    PASS=$((PASS + 1))
  else
    RESULTS+=("FAIL  $class")
    FAIL=$((FAIL + 1))
  fi
done

echo
echo "════════════════════════════════════════════════════════════════"
echo "  Validation summary"
echo "════════════════════════════════════════════════════════════════"
for line in "${RESULTS[@]}"; do
  echo "  $line"
done
echo
echo "  Passed: $PASS   Failed: $FAIL   Skipped: $SKIP"
echo
[ "$FAIL" -eq 0 ]
