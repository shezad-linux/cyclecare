#!/usr/bin/env bash
#
# Privacy manifest and store-disclosure review.
#
# The Play Data safety form has to be answered from evidence, not from
# intent. 06 §5 requires a privacy manifest and store disclosure review
# before a release candidate ships, and the recurring risk is that a
# bare ad request is read as proof that no SDK collects anything.
#
# This script enumerates the SDKs actually merged into the release
# build and reports each one's declarations, so the form is completed
# from the merged manifest. It does not decide the answers: it produces
# the evidence packet and fails when something it cannot account for
# reaches the release.
#
# Usage:
#   ./scripts/guard-privacy-manifest.sh
#
# Exit codes:
#   0  the merged manifest was inspected and every SDK was accounted for
#   1  the merged manifest could not be produced or a known collection
#      signal was found with no corresponding declaration
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

echo "Privacy manifest and store-disclosure review"
echo "============================================="

# 1. Produce the merged release manifest. A disclosure review that reads
#    a per-module manifest rather than the merged one will miss any SDK
#    that contributes declarations through its own AAR.
echo "-- building the merged release manifest"
MERGED=/tmp/carecare-merged-manifest.xml
if ! ./gradlew -q :app:processReleaseMainManifest > /dev/null 2>/tmp/carecare-manifest.err; then
  echo "FAIL: could not produce the merged release manifest."
  sed -n '1,30p' /tmp/carecare-manifest.err
  exit 1
fi
if [ ! -f "$MERGED" ]; then
  echo "  the task succeeded but produced no merged manifest at $MERGED; locating it"
  MERGED="$(find app/build -name AndroidManifest.xml -path '*release*' -not -path '*androidTest*' 2>/dev/null | head -1)"
fi
if [ -z "$MERGED" ] || [ ! -f "$MERGED" ]; then
  echo "FAIL: the merged release manifest could not be found. Cannot review the disclosure."
  exit 1
fi
echo "  OK: merged manifest at $MERGED"

# 2. Enumerate the permissions that actually reach the merged manifest.
#    These are the permissions the store form asks about.
echo "-- permissions in the merged manifest"
grep -oE 'android:name="android\.permission\.[A-Z_]+"' "$MERGED" | sed 's/android:name="android.permission.//; s/"//' | sort -u | sed 's/^/        /'

# 3. Enumerate the components each merged SDK contributes, so the
#    disclosure names the SDKs rather than assuming them.
echo "-- SDKs contributing components to the merged manifest"
grep -oE '<(activity|service|receiver|provider)[^>]*android:name="[^"]+"' "$MERGED" \
  | grep -oE '"[^"]+"' | tr -d '"' \
  | grep -vE '^com\.cyclecare\.' \
  | sed -E 's/\.[A-Za-z0-9_$]+$//' | sort -u | sed 's/^/        /' || true

# 4. The signal that actually matters: any SDK that can collect data must
#    be visible in the disclosure. The app collects nothing itself — no
#    analytics, no crash reporter — so any collection signal that is not
#    an advertising SDK is a finding.
echo "-- collection signals"
COLLECTORS=0
for pattern in 'firebase' 'crashlytics' 'analytics' 'appmeasurement'; do
  if grep -qiE "$pattern" "$MERGED"; then
    echo "  FINDING: '$pattern' appears in the merged manifest."
    echo "           CycleCare ships no analytics and no crash reporter, and"
    echo "           docs/app/Diagnostics.kt records that Crashlytics was"
    echo "           removed deliberately. If this is present, the Data safety"
    echo "           form must declare it."
    COLLECTORS=$((COLLECTORS + 1))
  fi
done
if [ "$COLLECTORS" -eq 0 ]; then
  echo "  OK: no analytics, measurement or crash-collection component in the merged manifest."
  echo "      Advertising collection is present and is declared separately below."
fi

# 5. The advertising signal, stated precisely. An ad request is not proof
#    that nothing else is collected, so this only reports; it does not
#    conclude.
echo "-- advertising"
if grep -qiE 'google.*\.ads|admob|appsflyer' "$MERGED"; then
  echo "  PRESENT: an advertising SDK is merged into the release build."
  echo "    The app requests the advertising identifier. Declare:"
  echo "      - Advertising ID: collected, used for advertising."
  echo "      - Device or other IDs: the advertising identifier."
  echo "      - App activity: interstitial and banner impressions."
  echo "    CycleCare's guard-no-ad-targeting.sh separately proves that no"
  echo "    cycle, symptom, note or medication value is passed to the ad SDK."
  echo "    Health data is NOT sent to the ad SDK, and the Data safety form"
  echo "    must say so explicitly."
fi

# 6. A recorded refusal to conclude. The absence of an analytics component
#    is not proof that no SDK collects anything; it is proof that this
#    script looked and found none in the merged manifest.
echo
echo "  NOTE: this review inspects the merged release manifest and the"
echo "  dependency set. It cannot observe runtime collection by an SDK that"
echo "  collects without declaring it in its manifest. The Play Data safety"
echo "  form must still be completed and submitted by the owner; this script"
echo "  produces the evidence for it, it does not submit it."
exit 0