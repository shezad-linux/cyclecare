#!/usr/bin/env bash
#
# Accessibility regression checks that can run without a device.
#
# The automated half of 14 §12.11 and 15 §14.8. A manual TalkBack
# traversal needs a person and a screen reader and is NOT satisfied by
# anything here; this script only guards what a test can assert:
#
#  - every interactive element a screen reader reaches must carry a
#    role and a label (asserted by the Compose semantics tests on a
#    device);
#  - the checks that keep the suite honest — that the accessibility
#    tests still exist and that the touch-target and contrast guards
#    still run.
#
# On a device this is enforced by TalkBackSemanticsTest, RtlLayoutTest
# and the contrast report. In CI, where the instrumented job is advisory
# and no emulator image is reliably available, this script fails when the
# device-level accessibility coverage has been removed, so a regression
# in the suite cannot land silently.
#
# Usage:
#   ./scripts/guard-accessibility-coverage.sh
#
# Exit codes:
#   0  the device-level accessibility coverage is present
#   1  a required accessibility suite is missing or has no tests
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

echo "Accessibility coverage guard"
echo "==========================="

FAILED=0

# 1. The device-level semantics and layout suites must exist and must
#    contain real tests. Their absence means a whole category of
#    accessibility coverage has been dropped.
for suite in \
  "app/src/androidTest/java/com/cyclecare/app/TalkBackSemanticsTest.kt:TalkBack semantics" \
  "app/src/androidTest/java/com/cyclecare/app/RtlLayoutTest.kt:RTL layout" \
  "app/src/androidTest/java/com/cyclecare/app/ScreenStateMatrixTest.kt:state matrix"; do
  file="${suite%%:*}"; label="${suite#*:}"
  if [ ! -f "$file" ]; then
    echo "  FAIL: the ${label} suite is missing (${file})."
    FAILED=1
    continue
  fi
  n="$(grep -c '@Test' "$file" || true)"
  if [ "${n:-0}" -eq 0 ]; then
    echo "  FAIL: the ${label} suite has no tests."
    FAILED=1
  else
    echo "  OK: ${label} — ${n} tests in $(basename "$file")."
  fi
done

# 2. The touch-target and contrast guards are the other two automatable
#    accessibility requirements; assert they exist so CI keeps running
#    them.
for guard in \
  "scripts/contrast-report.py:contrast" \
  "core/designsystem/src/test/java/com/cyclecare/core/designsystem/token/DesignTokenTest.kt:touch targets and contrast tokens"; do
  file="${guard%%:*}"; label="${guard#*:}"
  if [ ! -f "$file" ]; then
    echo "  FAIL: the ${label} check is missing (${file})."
    FAILED=1
  else
    echo "  OK: ${label} check present ($(basename "$file"))."
  fi
done

# 3. Run the contrast report now, because it is cheap and deterministic,
#    and fail if any pair with a WCAG target misses it.
echo "-- running the contrast report"
if ! python3 scripts/contrast-report.py > /tmp/carecare-contrast.txt 2>&1; then
  echo "  FAIL: the contrast report failed."
  tail -5 /tmp/carecare-contrast.txt
  FAILED=1
elif ! grep -q "All pairs with a WCAG target meet it" /tmp/carecare-contrast.txt; then
  echo "  FAIL: a contrast pair missed its WCAG target."
  grep -v "PASS" /tmp/carecare-contrast.txt | tail -10
  FAILED=1
else
  echo "  OK: every contrast pair with a WCAG target meets it."
fi

echo
if [ "$FAILED" -ne 0 ]; then
  echo "RESULT: FAIL — device-level or automatable accessibility coverage is missing."
  exit 1
fi
echo "RESULT: PASS — the automatable accessibility checks are present and green."
echo "  NOTE: a manual TalkBack traversal on a physical device is still"
echo "  required and is not covered by this script."
exit 0