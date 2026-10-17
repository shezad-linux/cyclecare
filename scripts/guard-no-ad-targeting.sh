#!/usr/bin/env bash
#
# Fails the build if ad-targeting APIs appear in the app module.
#
# Why this exists
# ---------------
# CycleCare's strongest privacy claim is that no cycle date, symptom,
# medication, note, age band or profile value is ever passed to Google.
#
# At the time of writing that claim is upheld by the ABSENCE of
# targeting code: all three ad request paths build a bare
# AdRequest.Builder().build() with no targeting parameters. That is
# verifiable today, but it is a property maintained by nobody. The first
# contributor who adds one line of targeting silently converts a
# defensible claim into a false one, and nothing in the build would
# notice.
#
# This script is the control that makes the claim durable.
#
# Scope: the app module only. Library and feature modules do not call
# the ads SDK.
#
# Reference: CycleCare-Redesign-Docs/16-monetization-and-ads.md section 7

set -euo pipefail

APP_DIR="app/src/main"

FORBIDDEN_APIS='setCustomTargeting|addKeyword|setPublisherProvidedId|requestCustomAssets|customTargeting|CategoryExclusion|setLocation|categoryExclusions'

echo "Checking ${APP_DIR} for ad-targeting APIs..."

if ! matches=$(grep -rnE "${FORBIDDEN_APIS}" "${APP_DIR}" \
    --include='*.kt' --include='*.java' 2>/dev/null); then
    echo "  OK: no ad-targeting API found."
    echo "  Ad requests are built with defaults only; no user content can reach the ad SDK."
    exit 0
fi

echo
echo "FAIL: ad-targeting API detected in the app module:"
echo "$matches" | sed 's/^/  /'
echo
echo "This breaks the guarantee that health data is never sent to the ad SDK."
echo
echo "Before changing this, read:"
echo "  CycleCare-Redesign-Docs/16-monetization-and-ads.md"
echo
echo "If the change is genuinely required, it needs a privacy review and a"
echo "corresponding update to the privacy policy and Play Data safety form."
exit 1