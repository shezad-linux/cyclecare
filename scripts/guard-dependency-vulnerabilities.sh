#!/usr/bin/env bash
#
# Dependency vulnerability review.
#
# 06 §5 requires a release candidate to pass a dependency vulnerability
# review. This script is the reproducible half of that: it resolves the
# dependency graph and reports what it can verify offline, and it fails
# when a database is expected but unreachable, so an outage can never be
# mistaken for a clean review.
#
# It deliberately does not invent an advisory source. When no advisory
# feed is configured the script reports that the *scan* could not run and
# exits non-zero, rather than exiting zero and letting a release through
# on the assumption that no vulnerability exists.
#
# Usage:
#   ./scripts/guard-dependency-vulnerabilities.sh            # resolve + report
#   CYCLECARE_ADVISORY_FEED=<url> ./scripts/guard-...sh     # also scan a feed
#
# Exit codes:
#   0  graph resolved and, if a feed was given, it was reachable and clean
#   1  the graph could not be resolved, or a known advisory matched
#   2  a feed was expected (CYCLECARE_ADVISORY_FEED set) but unreachable
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

echo "Dependency vulnerability review"
echo "=============================="

# 1. Resolve the runtime dependency graph. This must succeed before any
#    review is meaningful: a review of an unresolved graph is a review of
#    nothing.
echo "-- resolving the runtime dependency graph"
if ! ./gradlew -q :app:dependencies --configuration releaseRuntimeClasspath > /tmp/carecare-deps.txt 2>/tmp/carecare-deps.err; then
  echo "FAIL: could not resolve the release runtime classpath."
  sed -n '1,40p' /tmp/carecare-deps.err
  exit 1
fi

COORD_COUNT="$(grep -cE '^[+\\]--- ' /tmp/carecare-deps.txt || true)"
if [ "${COORD_COUNT:-0}" -eq 0 ]; then
  echo "FAIL: the dependency graph resolved but contains no coordinates."
  exit 1
fi
echo "  OK: ${COORD_COUNT} dependency coordinates resolved."

# 2. Flag anything on a snapshot, RC or +build version. A moving
#    dependency is not reproducible, and it defeats the point of a
#    release artifact tied to a commit.
echo "-- checking for non-reproducible versions"
SNAPSHOT="$(grep -iE '\-(SNAPSHOT|alpha|beta|rc[0-9]+|\+[0-9a-f]+)' /tmp/carecare-deps.txt | sed 's/^[+\\]--- //' | sort -u || true)"
if [ -n "$SNAPSHOT" ]; then
  echo "  WARN: pre-release or snapshot coordinates are on the release classpath:"
  echo "$SNAPSHOT" | sed 's/^/        /'
  echo "        These are permitted but they are not reproducible. Confirm each is intended."
else
  echo "  OK: every release coordinate is a fixed release version."
fi

# 3. Advisory scan. Only runs when a feed is configured; the default is to
#    fail loudly rather than to assume cleanliness.
FEED="${CYCLECARE_ADVISORY_FEED:-}"
if [ -z "$FEED" ]; then
  echo "-- advisory scan"
  echo "  SKIPPED: no advisory feed configured."
  echo "  Set CYCLECARE_ADVISORY_FEED to a resolvable advisory source to run the scan."
  echo "  Treating 'no scan' as 'no vulnerabilities' is exactly the failure 06 §5 warns"
  echo "  against, so this script exits non-zero."
  exit 2
fi

echo "-- advisory scan against ${FEED}"
if ! curl -fsS --max-time 60 "$FEED" > /tmp/carecare-advisories.json 2>/tmp/carecare-advisories.err; then
  echo "FAIL: the advisory feed could not be read."
  sed -n '1,10p' /tmp/carecare-advisories.err
  exit 2
fi

# Build the set of resolved coordinates, and fail if any appears in the feed.
MATCHES=0
while IFS= read -r coord; do
  if grep -qF -- "$coord" /tmp/carecare-advisories.json; then
    echo "  ADVISORY: $coord"
    MATCHES=$((MATCHES + 1))
  fi
done < <(grep -oE '[a-zA-Z0-9._-]+:[a-zA-Z0-9._-]+:[0-9][^ ]*' /tmp/carecare-deps.txt | sort -u)

if [ "$MATCHES" -gt 0 ]; then
  echo "FAIL: ${MATCHES} resolved dependency coordinates appear in the advisory feed."
  exit 1
fi
echo "  OK: no resolved coordinate appears in the advisory feed."