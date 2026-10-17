# 04 — Security, Privacy, and Data Governance

## 1. Security objectives

CycleCare handles reproductive-health records, which should be treated as highly sensitive even if the app is not a medical device. The design goal is to minimize collection, minimize exposure, keep core records usable offline, and make remote behavior explicit and auditable.

No control eliminates all risk. A rooted device, compromised OS, stolen unlocked device, malicious accessibility service, screen capture, or user-shared export may expose information. Product copy must not promise absolute privacy or forensic-proof protection.

## 2. Data inventory and trust boundaries

| Data class | Examples | Default location | Upload policy |
|---|---|---|---|
| Reproductive observations | Period dates, flow, symptoms, mood, discharge, notes, medication entries | Room | Never by default; encrypted sync only after opt-in |
| Derived data | Estimated next period, phase estimates, trends | Computed locally | Recompute on each device by default; do not upload unless a specific reason is approved |
| Preferences | Locale, reminder time, lock-screen preview | DataStore/Room | Sync only preferences necessary for user experience; permissions stay device-specific |
| Auth data | Email/identity, session tokens | Supabase Auth/client secure storage | Only if user enables account sync; minimize account attributes |
| Key material | Vault key, recovery secret | Android Keystore and user-controlled recovery flow | Never send plaintext key to Supabase or analytics |
| Operational diagnostics | Error category, app version, coarse performance timing | Local or opt-in service | Allowlist only; no record values, dates, notes or identifiers tied to health data |
| Advertising data | Ad ID, ad requests, SDK diagnostics | Third-party SDK systems | Recommended: no third-party ad SDK in the MVP; never use health data for ad targeting |
| User export | PDF/Markdown/CSV report | User-selected local location | Never upload or share automatically |

## 3. Threat model

Protect against:

- Another authenticated user reading or modifying a record.
- A misconfigured Supabase policy exposing tables through the API.
- Accidental upload of plaintext records or free-text notes.
- Compromised credentials or leaked service-role key.
- Lost, stolen or replaced devices.
- Sync conflict, stale restore or delete resurrection.
- Third-party SDKs collecting more than the user expects.
- Debug logs, crash reports, screenshots, notifications or exports exposing private content.
- A future developer adding analytics or remote AI without revisiting privacy.

Out of scope for a blanket guarantee: a compromised operating system, malware with sufficient device permissions, screenshots intentionally taken by the user, or a recipient of an exported report. These risks should be reduced where practical and stated without overpromising.

## 4. Local device protection

- Store app data in application-private storage.
- Use platform device security and biometric/device-credential APIs for app re-entry where appropriate.
- Protect encryption keys with Android Keystore; app lock and database encryption are separate controls.
- Consider database-level encryption (for example, a vetted SQLCipher integration) if the threat model requires protection from extracted database files. Validate compatibility, key lifecycle, migration behavior, performance and backup handling before adopting it.
- Do not store plaintext PINs, recovery secrets, access tokens, encryption keys or health exports in DataStore, logs or source control.
- Redact health fields from exceptions, diagnostic reports and test snapshots.
- Set secure window flags on highly sensitive screens only after considering accessibility, legitimate screenshot/export needs and Android version behavior; do not use this as the sole privacy control.
- Clear app-owned sensitive caches on logout or confirmed local-data deletion.

### App PIN

A four-digit PIN is a convenience gate, not a strong cryptographic secret.

**Verified implementation:** `Repositories.kt:684-688` stores a **single-round SHA-256** of the PIN. There is no salt, no key-stretching and no work factor. With only 10,000 possible values, an extracted verifier is recoverable in microseconds regardless of hashing — this is not a slow-KDF problem, it is an entropy problem that no KDF can solve.

Required:

- **Primary path** is biometric or device-credential authentication via the platform APIs, with Keystore-backed protection.
- If an app-specific PIN is retained, it must use a vetted slow construction (PBKDF2-HMAC-SHA256 or Argon2id) with escalating attempt throttling on failure.
- **The PIN must never derive or unwrap the vault key** (`17` §6.2). A 4-digit PIN cannot protect a vault.
- App lock, database encryption and vault key protection are **three separate controls**. They must never be described as one, and copy must not conflate them.
- Provide a carefully designed recovery path. Do not improvise a custom cryptographic protocol.

### Verified local-protection gaps

Confirmed by inspection and outstanding:

| Gap | Evidence | Required |
|---|---|---|
| **Database not encrypted at rest** — cycle dates, symptoms, medication and free-text notes sit in plain SQLite | No SQLCipher or equivalent in any build file | Decide and implement. If adopted, validate key lifecycle, migration behaviour, backup interaction and performance first |
| **`keystore.properties` holds plaintext production keystore passwords on disk** | Present in the working tree (gitignored, but present) | Move to environment-injected values; ensure no CI artefact or repository copy contains them |
| **No `dataExtractionRules.xml`** — `allowBackup="false"` alone does not reliably prevent Android 12+ device-to-device transfer | Manifest sets `allowBackup` only | Define explicit backup and device-transfer rules. Required **before** migrating live users (`02` §9.9) |
| **No `network_security_config.xml`** | Confirmed absent | Add one. Cleartext currently relies on the implicit `targetSdk ≥ 28` default; state the intent explicitly rather than by accident |
| **No `proguard-rules.pro`** | Confirmed absent; a release build type exists | Required before release builds. Add secret scanning to CI |
| **`feature:privacy` has zero tests** | 1102 lines covering the PIN keypad, `SecureAuthController` and biometric unlock | Highest-priority test gap in the repository |
| **Composable-scope mutation in the app shell** | `AppLockGate.kt` performs PIN validation and a destructive `resetAllLocalData()` during composition | Move all mutation into a ViewModel (`15` §8.5) |

## 5. Client-side encryption for sync

Recommended pattern:

1. Generate a random vault key on the device.
2. Encrypt each sensitive record payload using a vetted authenticated-encryption library/construction.
3. Use a unique nonce for each encryption and bind record ID/payload schema version as authenticated associated data.
4. Protect the device-local wrapping key with Android Keystore.
5. To support a second device, implement an explicit recovery method: a user-held recovery key or a passphrase-derived wrapping key with a vetted KDF and appropriate cost parameters. Explain that losing both devices and the recovery secret may mean data cannot be recovered.
6. Store only encrypted payload and required non-secret metadata in Supabase.
7. Decrypt and validate on-device; malformed or unknown payload versions fail closed and must not corrupt the local database.
8. Plan key rotation, revoked sessions, lost-key flow, encrypted export, and schema upgrades before public sync launch.

Supabase RLS still matters even with encryption. It limits access to the correct account and reduces cross-user risk. Encryption limits the contents visible to the remote service; neither control replaces the other.

## 6. Supabase access-control requirements

- Enable RLS on every exposed table.
- Define explicit grants and per-operation policies, with owner validation tied to the authenticated identity.
- Test with multiple users and anonymous sessions.
- Keep `service_role` and database credentials only on trusted servers or administrative environments; never ship them in the app.
- Apply migrations through version control and CI rather than undocumented dashboard-only changes.
- Review Security Advisor findings before each production release.
- Rate-limit and validate auth flows, configure allowed redirects/deep links carefully, and handle session expiry without discarding local data.
- Do not add Realtime subscriptions for private record tables unless there is a clear, tested requirement and policies are reviewed.

Official references: [Supabase RLS](https://supabase.com/docs/guides/database/postgres/row-level-security), [Supabase secure data](https://supabase.com/docs/guides/database/secure-data), [Supabase production checklist](https://supabase.com/docs/guides/deployment/going-into-prod).

## 7. Backups and device transfer

Do not rely on a single manifest flag as proof that sensitive data can never be backed up or transferred. Configure the appropriate Android backup/data-extraction rules for supported OS versions, explicitly exclude secret files and any data that should never enter Android cloud backup, and test both cloud backup and device-to-device migration on actual supported versions. Review [Android Auto Backup](https://developer.android.com/identity/data/autobackup).

The product needs an explicit policy:

- **Local-only users:** explain whether restoring or transferring the app restores private records. If no device transfer is supported, say so before users rely on it.
- **Sync users:** restoring records requires both the remote ciphertext and a valid recovery path for the vault key.
- **Exports:** user-selected exports are outside the app's control once shared; explain this in the export flow.

## 8. Advertising and analytics

**Decision: advertising is retained.** The earlier recommendation to remove AdMob and UMP from the initial build is **superseded by `16-monetization-and-ads.md`**. The posture below is the requirement, not a recommendation to remove.

### 8.1 What the code actually does (verified)

Three ad formats are implemented (`Ads.kt`), plus Google UMP consent. All three request paths construct a **bare** `AdRequest.Builder().build()` with no targeting parameters — no `setCustomTargeting`, `addKeyword`, `setPublisherProvidedId`, `setLocation`, contextual hints or custom assets exist anywhere in the codebase.

**Therefore: no period date, symptom, mood, medication, note, age band or profile value is ever passed to Google by the application.** This is a genuine and defensible privacy property.

It is upheld by the *absence of code*, not by a control, which makes it fragile. `16` §7 requires making it structural: a single ad-request factory that accepts no user content, a unit test asserting default-only construction, and a CI check that fails the build if targeting APIs appear in the app module.

### 8.2 What the ad SDKs still collect

Bearing no health data is not the same as collecting nothing. The merged release manifest includes `AD_ID`, `ACCESS_ADSERVICES_AD_ID`, `ACCESS_ADSERVICES_ATTRIBUTION` and `ACCESS_ADSERVICES_TOPICS` — the last granting access to the Android Privacy Sandbox Topics API. The SDKs additionally collect IP address (and coarse location inferred from it), device and app metadata, ad interactions, and install attribution.

**The honest disclosure obligation:** installing a menstrual cycle tracker is itself health-adjacent. Even with zero custom targeting, ad-interaction and app-category signals can reveal that a user uses such an app. This cannot be engineered away and must be stated rather than implied away.

### 8.3 Statements that are permitted and prohibited

**Permitted — verified true:**
- Cycle data, symptoms, medication, notes and profile settings are stored only on the device.
- We never send your cycle data, symptoms or notes to Google or any advertising partner.
- We do not use your health information for ad targeting or ad personalisation.
- You can decline ad personalisation, or remove ads entirely.

**Prohibited — currently present in the published policy and must be removed:**
- "Zero data leaves the device."
- "No tracking" / "No analytics or tracking SDKs" (`docs/privacy-policy.html:279`).
- "Completely private" / "100% private".
- Anything implying the app is ad-free, or that it is local-only.

### 8.4 Placement and consent

- Consent resolves before any ad request; `canRequestAds` gates every surface and must be preserved.
- The privacy options form must stay reachable in Settings.
- No ad on Calendar, Log, Settings or any detail screen; no interstitial within 30 minutes of a health record being logged, or in the first three sessions (`15` §11).
- Interstitials that fail their gating conditions are **dropped, not deferred**.
- Ad-free purchase removes every surface, and export and data deletion remain available without payment.

### 8.5 Analytics

Unchanged and still required:

- Never send period dates, symptoms, fertility estimates, medication values or free-text notes to any analytics or ad service.
- No free-text event properties; a fixed event allowlist with no arbitrary maps.
- No product analytics SDK is currently present in the app. Do not add one without a fresh privacy review.
- Test outbound requests and crash payloads with synthetic sensitive fixtures, and search for those canary values in request bodies, URLs, breadcrumbs and logs.

Operational events that may be acceptable — app version, crash category, coarse performance counters — must still be assessed for whether they reveal use of a reproductive-health feature.

## 9. Privacy operations and deletion

Implement separately testable operations:

1. **Export:** create a local file, preview what it contains, let the user choose where to save/share it, and never upload silently.
2. **Delete local data:** explain exactly what will be removed; cancel pending workers; clear local database, app-owned sensitive files and caches; clear local sync queue and key handles as designed; cancel future reminders and remove app-owned notifications where the OS permits.
3. **Disable sync:** stop future uploads, preserve local data and explain whether existing remote encrypted data remains or will be deleted.
4. **Delete cloud account/data:** revoke or sign out sessions, delete remote rows, delete the auth identity where supported, clear keys and pending state, and provide a final result. Do not report success if a remote operation fails.
5. **Lost key:** explain whether encrypted remote data is permanently unrecoverable; do not silently generate a replacement key and imply old data is readable.

Be transparent that deletion from the live database may not mean immediate removal from every provider backup. Confirm the actual provider retention model and disclose it accurately.

## 10. Policy and legal readiness

- Publish a privacy policy at a public, stable, non-geofenced web URL and link it in the app and Google Play Console.
- Keep the privacy policy consistent with real code and third-party SDK behavior, including collection, use, sharing, retention, deletion, security, and account features.
- Complete the Google Play Health apps declaration, including when the app is in testing tracks, and review the current health-app policy before every release.
- Complete Google Play Data safety and advertising declarations accurately.
- Review applicable Indian and target-market privacy obligations with qualified counsel; do not infer compliance solely from architecture choices.
- Ensure help, consent, and deletion screens are localized as needed.

References: [Google Play Health and Medical Apps](https://support.google.com/googleplay/android-developer/answer/16679511), [Health apps declaration](https://support.google.com/googleplay/android-developer/answer/14738291).

## 11. Security release gate

Release is blocked until:

- No production secret, keystore password or `service_role` key exists in the Android package, the working tree, or any CI artefact. Secret scanning runs in CI.
- `proguard-rules.pro` exists and the release build is verified against it.
- RLS and grants are tested for every remote table.
- Two-user isolation tests pass.
- No plaintext health records appear in Supabase under the encrypted-payload design.
- No sensitive values appear in logs, analytics, crash reports, notifications or URLs.
- Key recovery, key loss and key rotation behavior is documented and tested.
- Local delete, cloud delete, sync disable and account deletion have observable outcomes and failure states.
- Backup/restore behavior is tested on supported Android versions, and `data_extraction_rules.xml` is implemented.
- **The app lock PIN uses a vetted slow KDF with throttling, and cannot unwrap the vault key.**
- **The at-rest database encryption decision is made, implemented if adopted, and copy does not claim protection that does not exist.**
- **`network_security_config.xml` exists and states its intent explicitly.**
- **CI fails the build if ad-targeting APIs appear in the app module** (`16` §7).
- **Ads are absent from Calendar, Log, Settings and detail screens, and gated per `15` §11.3.**
- **Privacy policy, Data safety, advertising declarations and the actual SDK inventory agree** — including an honest statement that the app is ad-supported and that no health data reaches the ad SDK.
