# Release pipeline

How a signed Android App Bundle is produced for CycleCare, and how the
artifact is tied back to the commit and the version it was built from.

This is the operational companion to `.github/workflows/release.yml`.
The workflow file is the thing that runs; this is the thing that
explains what it does, what has to exist before it can run, and what it
deliberately does not do.

**Nothing here publishes anything.** There is no Play upload step, no
production deployment, and no store submission. The output of the
pipeline is a validated, signed AAB and a provenance file. Uploading it
is a separate, human decision made in Play Console.

---

## 1. Contents

1. [What the pipeline produces](#1-what-the-pipeline-produces)
2. [The workflow](#2-the-workflow)
3. [The `release` environment and its four secrets](#3-the-release-environment-and-its-four-secrets)
4. [Tag convention and versioning](#4-tag-convention-and-versioning)
5. [How the signing material is handled](#5-how-the-signing-material-is-handled)
6. [Provenance](#6-provenance)
7. [The scripts](#7-the-scripts)
8. [Local verification](#8-local-verification)
9. [What this pipeline does not do](#9-what-this-pipeline-does-not-do)

---

## 2. What the pipeline produces

| Artifact | What it is |
|---|---|
| `app-release.aab` | The signed Android App Bundle. This is the release artifact. An APK is not produced for release: Play generates the per-device APKs from the AAB. |
| `release-provenance.txt` | The commit, the tag, the version, the build date and the SHA-256 of the bundle, written next to it. |

The artifact is uploaded with a retention of **30 days**. A release
bundle is not a long-lived artifact: it is rebuilt from the tag, and the
tag is the permanent record.

---

## 3. The workflow

`.github/workflows/release.yml`

**Trigger**

| Trigger | What it means |
|---|---|
| `push` of a tag matching `v[0-9]*` | A release. `v1.0`, `v1.1`, `v2.0.3` and so on. |
| `workflow_dispatch` | A manual run of the same pipeline from the Actions tab. There is no tag, so the version check has no tag to compare against and only checks that `versionCode` is a positive integer. |

**Jobs**

| Job | What it does | Needs |
|---|---|---|
| `guards` | The same four checks `ci.yml` runs, unchanged: the ad-targeting guard, the typography guard, the gitleaks secret scan, and the check that no signing or Firebase config is tracked. | — |
| `version` | `scripts/check-release-version.sh`: `versionCode` is a positive integer, `versionName` is not empty, the tag and the source agree about `versionName`, and `versionCode` is strictly greater than the previous release tag's. | `guards` |
| `bundle` | Checks out the exact commit, sets up JDK 21 and Gradle, fetches bundletool, reconstructs the signing material outside the repository, runs the unit tests, builds `bundleRelease`, validates the bundle, writes the provenance file, uploads the artifact, and removes the signing material. | `guards`, `version` |

A release cannot bypass a guard, because the bundle job depends on the
guards job and the version job, and the guards job is the same code
`ci.yml` runs.

**The environment.** The `bundle` job runs in the protected `release`
environment, so the signing secrets are only reachable from a job that
asks for them, and the environment can be given required reviewers and
a deployment branch rule.

**Permissions.** `contents: read` only. The workflow reads the
repository and uploads an artifact; it cannot write to the repository
and it has no Play credentials.

---

## 4. The `release` environment and its four secrets

**These are not configured in this repository yet.** The pipeline is
written and tested; it cannot complete a release until the owner
creates the environment and adds the secrets. Until then, the
`bundle` job fails immediately with a clear error rather than producing
an unsigned or half-signed artifact.

### Creating the environment

1. **Settings → Environments → New environment**, name it `release`.
2. **Required reviewers**: add the owner. This is what makes the
   environment "protected": the bundle job waits for an approval
   before it runs, and the approval is recorded.
3. **Deployment branches and tags**: restrict it to `v*` tags, so only
   a tagged commit can reach the signing material.

### The four secrets

Add these under the `release` environment, not under the repository:

| Secret | What it is |
|---|---|
| `CYCLECARE_KEYSTORE_BASE64` | The upload keystore, base64-encoded. |
| `CYCLECARE_STORE_PASSWORD` | The keystore password. |
| `CYCLECARE_KEY_ALIAS` | The key alias inside the keystore (`upload` for the current key). |
| `CYCLECARE_KEY_PASSWORD` | The key password. |

`CYCLECARE_STORE_FILE` is **not** a secret. It is the path to the
keystore, and the workflow sets it itself, to the decoded file in the
runner's temporary directory.

### Encoding the keystore

```bash
base64 -i signing/upload-keystore.jks | pbcopy        # macOS
base64 -w 0 signing/upload-keystore.jks              # Linux, into the clipboard
```

Paste the single line into `CYCLECARE_KEYSTORE_BASE64`. Confirm the
round trip before saving it:

```bash
base64 -d /tmp/encoded.txt > /tmp/check.jks
keytool -list -keystore /tmp/check.jks
```

### Checking that the key is the right one

```bash
keytool -list -v -keystore signing/upload-keystore.jks
```

The upload key currently in use is `CN=CycleCare Upload, OU=Mobile,
O=CycleCare, L=Bengaluru, ST=Karnataka, C=IN`, valid until 2053-08-08.

---

## 5. Tag convention and versioning

**The tag is `v<versionName>`.** `v1.0` is a release whose
`versionName` is `1.0`.

The version itself lives in `app/build.gradle.kts`:

```kotlin
val defaultVersionCode = 7
val defaultVersionName = "1.0"
```

`versionCode` is the integer Play orders releases by. It has to
increase, and it has to increase past the previous tag's, or Play
rejects the upload. `scripts/check-release-version.sh` reads the
previous tag's own `app/build.gradle.kts`, so the comparison is against
the version that tag actually built, and reports what the comparison
would be:

```
  versionCode       : 7 (source declares 7)
  versionName       : 1.0 (source declares 1.0)
  current tag       : v1.0
  previous tag      : v0.9
  previous tag versionCode: 6
  comparison: 7 > 6
  OK: versionCode 7 is strictly greater than 6.
```

The tag and the source also have to agree about `versionName`: a tag
`v1.1` must build an app whose `versionName` is `1.1`. If they
disagree, the artifact store and the tag describe different versions.

**The release workflow passes the validated version back to Gradle**
(`-Pcyclecare.versionCode=` / `-Pcyclecare.versionName=`), so the
bundle that is built is exactly the bundle that was checked.

### Releasing

```bash
# 1. Bump the version in app/build.gradle.kts
#    val defaultVersionCode = 8
#    val defaultVersionName = "1.1"

# 2. Merge to main, then tag the release commit
git tag -a v1.1 -m "CycleCare 1.1"
git push origin v1.1
```

Pushing the tag starts the pipeline.

---

## 6. How the signing material is handled

The rule is that **no file containing a password, and no key, is ever
in the repository, and no secret is ever printed to the log.**

**In CI**, the pipeline:

1. Decodes `CYCLECARE_KEYSTORE_BASE64` into
   `$RUNNER_TEMP/cyclecare-signing/upload-keystore.jks`. That path is
   outside the repository, so the key cannot be committed even by
   accident, and it is not in `.gitignore` because it does not need to
   be.
2. Rebuilds `keystore.properties` in the same directory, with the
   store file, the store password, the alias and the key password.
   Again outside the repository. `CYCLECARE_KEYSTORE_PROPERTIES`
   points Gradle at it.
3. Passes the credentials to Gradle as the environment variables the
   build script already reads: `CYCLECARE_STORE_FILE`,
   `CYCLECARE_STORE_PASSWORD`, `CYCLECARE_KEY_ALIAS`,
   `CYCLECARE_KEY_PASSWORD`.
4. Removes both files at the end of the job, with `if: always()`, so
   they are removed even when the build fails. The keystore is shredded
   first if `shred` is available. The runner is destroyed afterwards
   anyway.
5. Masks the base64 secret with `::add-mask::` before it is used, so
   that even a stray `echo` cannot put it in the log.

**Locally**, the developer keeps `keystore.properties` and
`signing/upload-keystore.jks` in the repository root. Both are
gitignored, and the CI signing-secret guard fails the build if either
is ever tracked.

This removes the plaintext passwords from CI, which is what
`08-implementation-roadmap.md` asks for. It does not remove them from
a developer's own disk: `04-security-and-privacy.md` records that the
local `keystore.properties` still holds plaintext production
passwords, and that is unchanged here. The local file is a
convenience for one person on one machine; the CI path is where a
password would have ended up in a log, an artifact or a cache.

The workflow's last step checks the working tree for a signing file
after the job, so a future change that puts one there fails loudly.

---

## 7. Provenance

`release-provenance.txt` is uploaded next to the bundle:

```
source_commit=c5f63cb1a3903401c853a99fd51d5cb277801684
source_ref=refs/tags/v1.0
git_describe=v1.0
release_tag=v1.0
previous_release_tag=v0.9
version_code=7
version_name=1.0
build_date=2026-10-21T16:21:04Z
artifact=app-release.aab
artifact_bytes=9858039
sha256=68fa180d47cd4a70abb7c5ee4116dc4c3113b9ae02e6709990ad328353f4d1dd
runner=Linux
java_version=21
```

The checksum is the SHA-256 of the exact file that was uploaded, taken
by the validation script, not recomputed afterwards. Given the commit
and the tag, the build is reproducible: three consecutive local builds
of the same commit produced byte-identical bundles.

---

## 8. The scripts

### `scripts/check-release-version.sh`

```bash
./scripts/check-release-version.sh
./scripts/check-release-version.sh --version-code 8 --version-name 1.1
```

Checks that `versionCode` is a positive integer, that `versionName` is
not empty, that the tag and the source agree about `versionName`, and
that `versionCode` is strictly greater than the previous release tag's.
Prints the values and the comparison. Exits non-zero on any failure.
When `$GITHUB_OUTPUT` is set, it writes the validated values for the
next job.

The first release of a repository has no previous tag, so the
comparison is skipped and says so. Every release after it is compared.

### `scripts/validate-aab.sh`

```bash
./scripts/validate-aab.sh
./scripts/validate-aab.sh --checksum-file /tmp/aab.sha256 path/to/app-release.aab
./scripts/validate-aab.sh --require-bundletool path/to/app-release.aab
```

Checks that the bundle exists and is not empty, validates it, and
prints the SHA-256. Exits non-zero on any failure.

**The validation is real or it says it is not.** If `bundletool` is
available, it runs `bundletool validate --bundle=<aab>`, which parses
the protobuf manifest, the resource table and the bundle config. If it
is not available, the script falls back to a structural check of the
zip: `base/manifest/AndroidManifest.xml` and `BundleConfig.pb` must be
present. That fallback is weaker, and the script prints "This is NOT a
bundletool validation and it does not prove the bundle is uploadable"
when it uses it. It is never reported as a bundletool pass.

`bundletool` is found in this order: `$BUNDLETOOL` (an executable, or
a `.jar`, which is run with `java -cp`), then `bundletool` on `PATH`.
The release workflow downloads the jar and passes it in `$BUNDLETOOL`.
If the download fails, the workflow warns and the structural fallback
runs, and the log says which one happened.

---

## 9. Local verification

Every command below was run on the branch that introduced this
pipeline, at commit `c5f63cb`, on JDK 17 (the project toolchain), with
the local `keystore.properties` and `signing/upload-keystore.jks`.

```bash
# The bundle
./gradlew bundleRelease
ls -la app/build/outputs/bundle/release/app-release.aab

# The signature. An AAB is a JAR, so jarsigner is the right tool;
# apksigner is for APKs.
jarsigner -verify app/build/outputs/bundle/release/app-release.aab

# The checksum
shasum -a 256 app/build/outputs/bundle/release/app-release.aab

# The validation
./scripts/validate-aab.sh app/build/outputs/bundle/release/app-release.aab

# The version
./scripts/check-release-version.sh

# The APK path still works
./gradlew assembleRelease

# The guards
./scripts/guard-no-ad-targeting.sh
./scripts/guard-typography-roles.sh
```

The signing path that CI uses can be reproduced locally, and was, with
the repository's own `keystore.properties` moved aside:

```bash
mkdir -p /tmp/ci-signing
cp signing/upload-keystore.jks /tmp/ci-signing/
sed 's|^storeFile=.*|storeFile=/tmp/ci-signing/upload-keystore.jks|' \
    keystore.properties > /tmp/ci-signing/keystore.properties
mv keystore.properties /tmp/keystore.properties.backup

export CYCLECARE_STORE_FILE=/tmp/ci-signing/upload-keystore.jks
export CYCLECARE_STORE_PASSWORD="$(grep '^storePassword=' /tmp/keystore.properties.backup | cut -d= -f2-)"
export CYCLECARE_KEY_ALIAS="$(grep '^keyAlias=' /tmp/keystore.properties.backup | cut -d= -f2-)"
export CYCLECARE_KEY_PASSWORD="$(grep '^keyPassword=' /tmp/keystore.properties.backup | cut -d= -f2-)"
export CYCLECARE_KEYSTORE_PROPERTIES=/tmp/ci-signing/keystore.properties

./gradlew bundleRelease
```

That is the same mechanism the workflow uses: the keystore and the
properties file outside the repository, the credentials in the
environment.

---

## 10. What this pipeline does not do

| Not done | Why |
|---|---|
| No Play upload, no `google-play` action, no service account | Publishing is a human decision made in Play Console, and the Play credentials are not in this repository. The pipeline stops at a validated, signed AAB. |
| No `versionCode` bump as part of the pipeline | The version is a decision about the product, made in the source, and reviewed like any other change. The pipeline checks it; it does not invent it. |
| No `play` app signing key, only the upload key | Play App Signing holds the app signing key; the repository only ever needs the upload key. |
| No APK artifact | Play generates the per-device APKs from the AAB. An APK is built by `assembleRelease` for local testing only. |
| No automatic tagging | Tags are created by a human, deliberately. |
| The bundletool download is best effort | If the jar cannot be fetched, the structural fallback runs and says so, rather than failing a release for an unrelated reason. `--require-bundletool` exists for the case where a bundletool pass is mandatory. |
| No signing of the APK | `assembleRelease` is unsigned in CI by design, as it is in `ci.yml`. The release artifact is the AAB. |
