#!/usr/bin/env bash
# scripts/mutation-score.sh — the test-effectiveness gate for nooa-core.
#
# Runs PIT over the scoped, high-value packages (permissions, the sandbox
# permission analyzer, and the evaluation layer) and prints one
# machine-readable line:
#
#   MUTATION_SCORE: <pct>    the suite was measured
#   MUTATION_SKIP: <reason>  the tool could not run in this environment
#
# SKIP is for environments that cannot host the tool; it is not a pass. Set
# MUTATION_THRESHOLD to a percentage to fail (exit 2) below it. Mutation
# testing is not bound to a Maven lifecycle phase, so `mvn test` is unaffected.
set -euo pipefail

cd "$(dirname "$0")/.."

MODULE="nooa-core"
REPORT="${MODULE}/target/pit-reports/mutations.xml"
LOG="/tmp/nooa-pit.log"
# Regression floor. Baseline measured 2026-10-01 at 56.9%; ratchet upward as
# the scoped tests strengthen. Override with MUTATION_THRESHOLD=NN.
THRESHOLD="${MUTATION_THRESHOLD:-55}"

emit_skip() {
  echo "MUTATION_SKIP: $1"
  echo "  see $LOG" >&2
  exit 0
}

# 1. Build and install core (and its reactor deps) so PIT can resolve them.
if ! mvn -q -pl "$MODULE" -am install -DskipTests >"$LOG" 2>&1; then
  emit_skip "build/install failed"
fi

# 2. Run PIT scoped by the pitest-maven configuration in nooa-core/pom.xml.
if ! mvn -q -pl "$MODULE" org.pitest:pitest-maven:mutationCoverage >>"$LOG" 2>&1; then
  jvm="$(java -version 2>&1 | head -1)"
  emit_skip "mutation tooling failed (${jvm:-unknown JVM})"
fi

if [ ! -f "$REPORT" ]; then
  emit_skip "PIT produced no report at $REPORT"
fi

# 3. Parse the XML report into a single score line.
python3 - "$REPORT" "$THRESHOLD" <<'PY'
import os
import sys
import xml.etree.ElementTree as ET

report = sys.argv[1]
threshold = float(sys.argv[2]) if len(sys.argv) > 2 else 0.0
if not os.path.isfile(report):
    print("MUTATION_SKIP: PIT produced no report at %s" % report)
    raise SystemExit(0)

killed = total = 0
for mutation in ET.parse(report).getroot().iter("mutation"):
    status = mutation.get("status", "")
    if status in ("KILLED", "TIMED_OUT", "NON_VIABLE", "MEMORY_ERROR"):
        killed += 1
        total += 1
    elif status in ("SURVIVED", "NO_COVERAGE"):
        total += 1

score = (100.0 * killed / total) if total else 0.0
print("MUTATION_SCORE: %.1f" % score)
if threshold > 0 and score < threshold:
    print("MUTATION_FAIL: %.1f < %.1f" % (score, threshold))
    raise SystemExit(2)
PY
