#!/usr/bin/env bash
# Every typography role must be defined AND referenced.
#
# Defining a role proves nothing: the original defect was four roles
# defined and never used, so the display ramp existed on paper while
# numbers were rendered at 18sp. This fails when a role is defined but
# never reached.
#
# displayLarge and displayMedium are reached through their tabular
# variants, which exist so changing digits do not shift the layout, so
# those count as usage.
set -euo pipefail

cd "$(dirname "$0")/.."

ROLES="displayLarge displayMedium displaySmall
headlineLarge headlineMedium headlineSmall
titleLarge titleMedium titleSmall
bodyLarge bodyMedium bodySmall
labelLarge labelMedium labelSmall"

# Tabular variants that stand in for the display roles.
EQUIVALENT_displayLarge="numericHero"
EQUIVALENT_displayMedium="numericLarge"

SEARCH_PATHS=(feature app/src/main/java core/designsystem/src/main/java)

missing=0
for role in $ROLES; do
  if grep -rqE "typography\.${role}\b" "${SEARCH_PATHS[@]}" --include="*.kt" 2>/dev/null; then
    continue
  fi
  eval "alt=\${EQUIVALENT_${role}:-}"
  if [ -n "$alt" ] && grep -rqE "\b${alt}\b" "${SEARCH_PATHS[@]}" --include="*.kt" 2>/dev/null; then
    echo "OK   $role (reached via tabular variant ${alt})"
    continue
  fi
  echo "FAIL $role is defined but never referenced" >&2
  missing=1
done

if [ "$missing" -ne 0 ]; then
  echo "" >&2
  echo "A defined-but-unused role means the type ramp is decorative." >&2
  exit 1
fi

echo "All 15 typography roles are defined and referenced."
