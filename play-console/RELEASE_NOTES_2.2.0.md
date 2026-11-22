# Play Console release notes — CycleCare v2.2.0 (code 11)

Release notes for the final Android 17-migration release. Paste the user-facing
"What's new" into Play Console → App releases → v2.2.0 → What's new. The
internal/technical summary is included below for the internal-test track.

## What's new for users

- **Android 17 support:** CycleCare now supports Android 17 (API 37).
- **Security improvements:** the system credential prompt is now provided by
  BiometricPrompt; the legacy fingerprint permission was removed; a crash on a
  corrupt app-lock state is fixed.
- **Bug fixes:** the lock screen now correctly shows the number of wrong-PIN
  attempts remaining.

## Internal / technical summary

- Android 17 migration complete: `targetSdk`/`compileSdk` 37 (minSdk 26),
  behavior-change audits for SDK 37, platform, and 15/14 reviewed.
- Toolchain: Gradle 9.9.2, AGP 9.5.2, Kotlin 2.4.21, KSP 2.3.14.
- AndroidX minors + direct dependency modernization (lifecycle 2.11.1,
  activity-compose 1.13.0, navigation-compose 2.10.2, room 2.8.5, datastore
  1.3.0, work 2.12.0, coroutines 1.11.0, guava 33.8.0-android, etc.).
- Deprecations remediated: `KeyguardManager.createConfirmDeviceCredentialIntent`
  → BiometricPrompt (legacy path kept for API 26–28); unused
  Notification/MediaPlayer/JobInfo/LiveData-family APIs reviewed and confirmed
  unused.
- Security: `android.permission.USE_FINGERPRINT` removed (tools:node remove);
  `SecureAuthController` reworked for API ≥ 29; PIN hash storage unchanged
  (F1 owner decision still open); no data-collection SDKs.
- Migration fixes: kotlinx-serialization pinned to 1.8.1 (Room 2.8.5 bytecode
  alignment — `FieldBundle$$serializer` requires the 1.8.1 default method),
  `AppLockGate` attempts-remaining display off-by-one fixed.
- Verification: instrumented suite green on API 37 (271 tests, 269 passed,
  0 failures, 0 errors, 2 skipped, Pixel_10a swiftshader); unit tests 505/505;
  `lintDebug` 0 errors; `apksigner verify` v2, 1 signer.
