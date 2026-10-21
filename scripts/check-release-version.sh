#!/usr/bin/env bash
#
# Validates the release version before a release build.
#
# Why this exists
# ---------------
# Play orders releases by versionCode, and it refuses an upload whose
# versionCode has already been used. So versionCode has two properties
# that have to hold, and neither of them is enforced by the build:
#
#   1. it is a positive integer, and
#   2. it is strictly greater than the versionCode of the previous
#      release tag.
#
# versionName has to be non-empty, and when the build is tagged the tag
# and the source have to agree about it: `v1.4` has to build an app
# whose versionName is `1.4`. If they disagree, the artifact in the
# artifact store and the tag that names the release describe different
# versions, and nobody notices until somebody has to answer "which
# build is this?".
#
# The previous versionCode is read from the previous tag's own
# app/build.gradle.kts, so it is the version that tag actually built.
#
# Usage:
#   scripts/check-release-version.sh [--version-code N] [--version-name S]
#                                   [--build-file PATH]
#
# Exits non-zero if any check fails. When $GITHUB_OUTPUT is set, the
# validated values are written to it for the next workflow job.

set -euo pipefail

cd "$(dirname "$0")/.."

BUILD_FILE="app/build.gradle.kts"
VERSION_CODE_OVERRIDE=""
VERSION_NAME_OVERRIDE=""

while [ "$#" -gt 0 ]; do
    case "$1" in
        --version-code)
            VERSION_CODE_OVERRIDE="${2:?--version-code needs a value}"
            shift 2
            ;;
        --version-name)
            VERSION_NAME_OVERRIDE="${2:?--version-name needs a value}"
            shift 2
            ;;
        --build-file)
            BUILD_FILE="${2:?--build-file needs a path}"
            shift 2
            ;;
        -h|--help)
            sed -n '2,32p' "$0"
            exit 0
            ;;
        -*)
            echo "FAIL: unknown option '$1'." >&2
            exit 2
            ;;
        *)
            echo "FAIL: unexpected argument '$1'." >&2
            exit 2
            ;;
    esac
done

# ── Helpers ─────────────────────────────────────────────────────────

# Reads a versionCode out of a build script. Two shapes are accepted:
#   val defaultVersionCode = <n>     (current)
#   versionCode = <n>                 (before the constant was introduced)
# Anything else is rejected rather than guessed at.
read_code() {
    local value
    value=$(printf '%s\n' "$1" \
        | grep -m1 -E '^[[:space:]]*val defaultVersionCode[[:space:]]*=' \
        | sed -E 's/.*=[[:space:]]*([0-9]+).*/\1/' || true)
    printf '%s' "${value}" | grep -qE '^[0-9]+$' || return 0
    printf '%s' "${value}"
}

# Reads a versionName out of a build script, same two shapes.
read_name() {
    local value
    value=$(printf '%s\n' "$1" \
        | grep -m1 -E '^[[:space:]]*val defaultVersionName[[:space:]]*=' \
        | sed -E 's/.*=[[:space:]]*"([^"]+)".*/\1/' || true)
    printf '%s' "${value}" | grep -qE '.+' || return 0
    printf '%s' "${value}"
}

read_code_legacy() {
    local value
    value=$(printf '%s\n' "$1" \
        | grep -m1 -E '^[[:space:]]*versionCode[[:space:]]*=' \
        | sed -E 's/.*=[[:space:]]*([0-9]+).*/\1/' || true)
    printf '%s' "${value}" | grep -qE '^[0-9]+$' || return 0
    printf '%s' "${value}"
}

read_name_legacy() {
    local value
    value=$(printf '%s\n' "$1" \
        | grep -m1 -E '^[[:space:]]*versionName[[:space:]]*=' \
        | sed -E 's/.*=[[:space:]]*"([^"]+)".*/\1/' || true)
    printf '%s' "${value}" | grep -qE '.+' || return 0
    printf '%s' "${value}"
}

FAILURES=0
fail() {
    echo "  FAIL: $*" >&2
    FAILURES=1
}

# ── The current version ─────────────────────────────────────────────
echo "Release version check"

if [ ! -f "${BUILD_FILE}" ]; then
    echo "FAIL: no build file at ${BUILD_FILE}." >&2
    exit 1
fi

BUILD_TEXT=$(cat "${BUILD_FILE}")

SOURCE_CODE=$(read_code "${BUILD_TEXT}")
SOURCE_NAME=$(read_name "${BUILD_TEXT}")
if [ -z "${SOURCE_CODE}" ]; then
    SOURCE_CODE=$(read_code_legacy "${BUILD_TEXT}")
fi
if [ -z "${SOURCE_NAME}" ]; then
    SOURCE_NAME=$(read_name_legacy "${BUILD_TEXT}")
fi

VERSION_CODE="${VERSION_CODE_OVERRIDE:-${CYCLECARE_VERSION_CODE:-${SOURCE_CODE}}}"
VERSION_NAME="${VERSION_NAME_OVERRIDE:-${CYCLECARE_VERSION_NAME:-${SOURCE_NAME}}}"

echo "  build file        : ${BUILD_FILE}"
echo "  source commit     : $(git rev-parse HEAD)"
echo "  versionCode       : ${VERSION_CODE} (source declares ${SOURCE_CODE:-none})"
echo "  versionName       : ${VERSION_NAME} (source declares ${SOURCE_NAME:-none})"

# ── Check 1: versionCode is a positive integer ──────────────────────
if ! printf '%s' "${VERSION_CODE}" | grep -qE '^[0-9]+$'; then
    fail "versionCode '${VERSION_CODE}' is not an integer."
elif [ "${VERSION_CODE}" -le 0 ]; then
    fail "versionCode ${VERSION_CODE} is not positive."
else
    echo "  OK: versionCode ${VERSION_CODE} is a positive integer."
fi

# ── Check 2: versionName is not empty ───────────────────────────────
if [ -z "${VERSION_NAME}" ]; then
    fail "versionName is empty."
else
    echo "  OK: versionName '${VERSION_NAME}' is not empty."
fi

# ── The tags ────────────────────────────────────────────────────────
# Release tags are the `v<versionName>` ones. Anything else in the
# repository is not a release and is not used for the comparison.
#
# When several tags point at the same commit the highest one wins, so
# the answer does not depend on the order git happens to list them in.
TAGS_AT_HEAD=$(git tag --points-at HEAD --sort=-v:refname 2>/dev/null | grep -E '^v[0-9]' || true)

CURRENT_TAG=""
if [ -n "${GITHUB_REF:-}" ] && [ "${GITHUB_REF#refs/tags/}" != "${GITHUB_REF}" ]; then
    CURRENT_TAG="${GITHUB_REF#refs/tags/}"
else
    CURRENT_TAG=$(printf '%s\n' "${TAGS_AT_HEAD}" | head -1)
fi

# The previous release tag is the highest release tag that is an
# ancestor of this commit and is not on this commit itself.
PREVIOUS_TAG=""
while read -r tag; do
    [ -n "${tag}" ] || continue
    if printf '%s\n' "${TAGS_AT_HEAD}" | grep -qx "${tag}"; then
        continue
    fi
    PREVIOUS_TAG="${tag}"
    break
done <<EOF
$(git tag --merged HEAD --sort=-v:refname 2>/dev/null | grep -E '^v[0-9]' || true)
EOF

echo "  current tag       : ${CURRENT_TAG:-none (not a tagged build)}"
echo "  previous tag      : ${PREVIOUS_TAG:-none}"

# ── Check 3: the tag and the source agree about versionName ─────────
if [ -n "${CURRENT_TAG}" ] && printf '%s' "${CURRENT_TAG}" | grep -qE '^v[0-9]'; then
    TAG_VERSION_NAME="${CURRENT_TAG#v}"
    if [ "${TAG_VERSION_NAME}" = "${VERSION_NAME}" ]; then
        echo "  OK: tag ${CURRENT_TAG} and versionName '${VERSION_NAME}' agree."
    else
        fail "tag ${CURRENT_TAG} says versionName '${TAG_VERSION_NAME}' but the source says '${VERSION_NAME}'."
    fi
fi

# ── Check 4: versionCode is strictly greater than the previous tag's ─
if [ -z "${PREVIOUS_TAG}" ]; then
    echo "  SKIP: there is no previous release tag, so there is nothing to compare against."
    echo "        The first release of a repository has no predecessor; every release after it does."
elif ! git cat-file -e "${PREVIOUS_TAG}:${BUILD_FILE}" 2>/dev/null; then
    fail "cannot read ${BUILD_FILE} at tag ${PREVIOUS_TAG}, so the version bump cannot be verified."
else
    PREVIOUS_TEXT=$(git show "${PREVIOUS_TAG}:${BUILD_FILE}")
    PREVIOUS_CODE=$(read_code "${PREVIOUS_TEXT}")
    if [ -z "${PREVIOUS_CODE}" ]; then
        PREVIOUS_CODE=$(read_code_legacy "${PREVIOUS_TEXT}")
    fi
    if [ -z "${PREVIOUS_CODE}" ]; then
        fail "cannot read a versionCode from ${BUILD_FILE} at tag ${PREVIOUS_TAG}."
    elif ! printf '%s' "${VERSION_CODE}" | grep -qE '^[0-9]+$'; then
        fail "versionCode '${VERSION_CODE}' cannot be compared with ${PREVIOUS_CODE}."
    else
        echo "  previous tag versionCode: ${PREVIOUS_CODE}"
        echo "  comparison: ${VERSION_CODE} > ${PREVIOUS_CODE}"
        if [ "${VERSION_CODE}" -gt "${PREVIOUS_CODE}" ]; then
            echo "  OK: versionCode ${VERSION_CODE} is strictly greater than ${PREVIOUS_CODE}."
        else
            fail "versionCode ${VERSION_CODE} is not strictly greater than ${PREVIOUS_CODE} (tag ${PREVIOUS_TAG})."
        fi
    fi
fi

# ── Result ──────────────────────────────────────────────────────────
if [ "${FAILURES}" -ne 0 ]; then
    echo "Version check FAILED."
    exit 1
fi

echo "Version check PASSED."

if [ -n "${GITHUB_OUTPUT:-}" ]; then
    {
        echo "version_code=${VERSION_CODE}"
        echo "version_name=${VERSION_NAME}"
        echo "tag=${CURRENT_TAG}"
        echo "previous_tag=${PREVIOUS_TAG}"
        echo "source_commit=$(git rev-parse HEAD)"
    } >> "${GITHUB_OUTPUT}"
fi
