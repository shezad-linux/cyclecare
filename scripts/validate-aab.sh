#!/usr/bin/env bash
#
# Validates a built Android App Bundle.
#
# Why this exists
# ---------------
# "The build succeeded" is not the same as "the artifact is usable".
# A bundle can be produced by a build that was misconfigured, that was
# signed with the wrong key, or that was written to the wrong path, and
# the only place that finds out is the moment somebody tries to upload
# it. This script is the check that happens before that.
#
# What it checks
# --------------
#   1. The bundle exists and is not empty.
#   2. The bundle is a valid Android App Bundle.
#        - `bundletool validate` when bundletool is available. This is
#          the real check: it parses the protobuf manifest, the
#          resource table and the bundle config.
#        - otherwise a documented structural fallback that confirms the
#          zip contains `base/manifest/AndroidManifest.xml` and
#          `BundleConfig.pb`. This is weaker, and the script says so
#          in its output. It is NOT a bundletool validation and it is
#          never reported as one.
#   3. The SHA-256 checksum, which is what the provenance file and the
#      release record quote.
#
# bundletool is looked up in this order:
#   1. $BUNDLETOOL — a path to an executable, or to a .jar (run with
#      `java -cp`), which is how the release workflow passes the jar it
#      downloaded.
#   2. `bundletool` on PATH.
#
# Usage:
#   scripts/validate-aab.sh [--checksum-file FILE] [--require-bundletool] [AAB]
#
# The AAB defaults to app/build/outputs/bundle/release/app-release.aab.
# Exits non-zero if any check fails.

set -euo pipefail

cd "$(dirname "$0")/.."

AAB="app/build/outputs/bundle/release/app-release.aab"
CHECKSUM_FILE=""
REQUIRE_BUNDLETOOL=0

while [ "$#" -gt 0 ]; do
    case "$1" in
        --checksum-file)
            CHECKSUM_FILE="${2:?--checksum-file needs a path}"
            shift 2
            ;;
        --require-bundletool)
            REQUIRE_BUNDLETOOL=1
            shift
            ;;
        -h|--help)
            sed -n '2,40p' "$0"
            exit 0
            ;;
        -*)
            echo "FAIL: unknown option '$1'." >&2
            exit 2
            ;;
        *)
            AAB="$1"
            shift
            ;;
    esac
done

fail() {
    echo "FAIL: $*" >&2
    exit 1
}

sha256_of() {
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$1" | awk '{print $1}'
    elif command -v shasum >/dev/null 2>&1; then
        shasum -a 256 "$1" | awk '{print $1}'
    elif command -v openssl >/dev/null 2>&1; then
        openssl dgst -sha256 "$1" | awk '{print $NF}'
    else
        echo ""
    fi
}

# ── 1. The bundle exists ────────────────────────────────────────────
echo "Validating Android App Bundle: ${AAB}"

if [ ! -f "${AAB}" ]; then
    fail "no bundle at ${AAB}. Run ./gradlew bundleRelease first."
fi

SIZE_BYTES=$(wc -c < "${AAB}" | tr -d ' ')
if [ "${SIZE_BYTES}" -eq 0 ]; then
    fail "the bundle at ${AAB} is empty."
fi
echo "  OK: file exists (${SIZE_BYTES} bytes)."

# ── 2. The bundle is a valid App Bundle ─────────────────────────────
BUNDLETOOL_ARGS=()
if [ -n "${BUNDLETOOL:-}" ]; then
    if [ ! -e "${BUNDLETOOL}" ]; then
        fail "BUNDLETOOL is set to '${BUNDLETOOL}' but that path does not exist."
    fi
    if [ -f "${BUNDLETOOL}" ] && [ "${BUNDLETOOL##*.}" = "jar" ]; then
        BUNDLETOOL_ARGS=(java -cp "${BUNDLETOOL}" com.android.tools.build.bundletool.BundleToolMain)
    else
        BUNDLETOOL_ARGS=("${BUNDLETOOL}")
    fi
elif command -v bundletool >/dev/null 2>&1; then
    BUNDLETOOL_ARGS=(bundletool)
fi

VALIDATION_METHOD="structural fallback"

if [ "${#BUNDLETOOL_ARGS[@]}" -gt 0 ]; then
    echo "  bundletool: ${BUNDLETOOL_ARGS[*]}"
    set +e
    BUNDLETOOL_OUTPUT=$("${BUNDLETOOL_ARGS[@]}" validate --bundle="${AAB}" 2>&1)
    BUNDLETOOL_STATUS=$?
    set -e
    if [ "${BUNDLETOOL_STATUS}" -ne 0 ]; then
        echo "${BUNDLETOOL_OUTPUT}" | sed 's/^/    /' >&2
        fail "bundletool validation failed for ${AAB}."
    fi
    # bundletool prints the whole file listing on success. It is not
    # useful in a log, so only the verdict is kept.
    echo "  OK: bundletool validate passed."
    VALIDATION_METHOD="bundletool"
else
    echo "  bundletool: NOT AVAILABLE on this machine."
    if [ "${REQUIRE_BUNDLETOOL}" -eq 1 ]; then
        fail "bundletool is required (--require-bundletool) but is not installed."
    fi
    echo "  Falling back to the structural check. This is NOT a bundletool"
    echo "  validation and it does not prove the bundle is uploadable."

    STRUCTURE_STATUS=0
    if command -v python3 >/dev/null 2>&1; then
        STRUCTURE_OUTPUT=$(python3 - "${AAB}" <<'PY' 2>&1
import sys
import zipfile

path = sys.argv[1]
required = ["base/manifest/AndroidManifest.xml", "BundleConfig.pb"]

try:
    with zipfile.ZipFile(path) as archive:
        names = set(archive.namelist())
        bad = archive.testzip()
except zipfile.BadZipFile as exc:
    print(f"not a zip archive: {exc}")
    sys.exit(1)

if bad is not None:
    print(f"corrupt entry in the archive: {bad}")
    sys.exit(1)

missing = [name for name in required if name not in names]
for name in required:
    if name in names:
        print(f"present: {name}")
if missing:
    print("missing: " + ", ".join(missing))
    sys.exit(1)
PY
        ) || STRUCTURE_STATUS=$?
    else
        echo "    python3 is not available; using unzip -Z1 instead."
        STRUCTURE_OUTPUT=$(unzip -Z1 "${AAB}" 2>&1) || STRUCTURE_STATUS=$?
        if [ "${STRUCTURE_STATUS}" -eq 0 ]; then
            for name in base/manifest/AndroidManifest.xml BundleConfig.pb; do
                if printf '%s\n' "${STRUCTURE_OUTPUT}" | grep -qx "${name}"; then
                    echo "present: ${name}"
                else
                    echo "missing: ${name}"
                    STRUCTURE_STATUS=1
                fi
            done
        else
            STRUCTURE_OUTPUT="unzip could not read the archive: ${STRUCTURE_OUTPUT}"
        fi
    fi

    echo "${STRUCTURE_OUTPUT}" | sed 's/^/    /'
    if [ "${STRUCTURE_STATUS}" -ne 0 ]; then
        fail "the structural check failed for ${AAB}."
    fi
    echo "  OK: structural check passed (base/manifest/AndroidManifest.xml, BundleConfig.pb)."
fi

# ── 3. The checksum ─────────────────────────────────────────────────
CHECKSUM=$(sha256_of "${AAB}")
if [ -z "${CHECKSUM}" ]; then
    fail "no SHA-256 implementation found (need sha256sum, shasum or openssl)."
fi

if [ -n "${CHECKSUM_FILE}" ]; then
    printf '%s  %s\n' "${CHECKSUM}" "$(basename "${AAB}")" > "${CHECKSUM_FILE}"
    echo "  checksum written to ${CHECKSUM_FILE}"
fi

echo "  SHA-256: ${CHECKSUM}"
echo "Validation PASSED (${VALIDATION_METHOD})."
