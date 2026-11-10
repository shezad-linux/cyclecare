# Release readiness checklist

Spec `08` Phase 9. What has to happen before CycleCare is released, and
what has to happen in Play Console, which cannot be done from here.

**This document is now a summary.** The canonical record of every
phase's status, with the exact exit criteria, the evidence and the
open items, is:

- **`22-phase-completion-plan.md`** — the completion ledger. Read this
  for what is done, what is not, and what each remaining item needs.
- **`23-verification-runbook.md`** — the operational guide. Read this
  for the exact command that produces each piece of evidence, and for
  the procedures that need hardware or a person.

This file keeps only what is useful at a glance: the state of the
release, and the external list.

**Nothing in the external section is complete.** None of it has been
submitted, and no claim is made about its state.

## State of the release

| | |
|---|---|
| **Audit date** | 2026-10-21 (production-readiness pass) |
| **Unit tests** | 505, 0 failures |
| **Instrumented tests** | 268, 0 failures, 2 skipped — the seven intermittencies are closed |
| **Signed AAB** | Built and validated locally; see *Release artifact* |
| **Release build** | `assembleRelease` and `:app:lintRelease` clean |
| **Guards** | Ad-targeting, typography and contrast all pass |

## Done in the repository

| Item | Evidence |
|---|---|
| Release build with R8 and lint | `assembleRelease` and `lintRelease` are clean in CI and locally |
| ProGuard rules for the storage format | `app/proguard-rules.pro`, with the enum and reflection cases documented |
| Network security configuration | `app/src/main/res/xml/network_security_config.xml` |
| Backup and device-transfer behaviour decided and tested | `data_extraction_rules.xml` and `backup_rules.xml`, with a `CycleCareDatabaseRoomTest` case per migration |
| CI with secret scanning and the ad-targeting guard | `.github/workflows/ci.yml`: gitleaks, the signing-secret check, `guard-no-ad-targeting.sh`, `guard-typography-roles.sh` |
| The ad-targeting guard | `scripts/guard-no-ad-targeting.sh`, passing |
| Privacy policy corrected against the verified implementation | `docs/privacy-policy.html`, corrected in the commit that fixed the three false claims |
| Unit test suite | 505 unit tests green |
| Instrumented suite | **7 failures** — two calendar, one responsive-layout, two privacy PIN, one app-lock cooldown, one reset-alarm test. All deterministic; see *Blockers*. |
| Encryption for local export and import | `core/security`, with a passphrase-derived key, AAD binding and a fail-closed import |
| Prediction gates | All thirteen `12` §6 gates have named tests and pass; version bumped to 2.1.0 with `12` §7 golden fixtures |
| Migration data integrity | `02` §9.10 independently re-verified; snapshot/verification and divergence surfacing confirmed; new integrity tests |
| Reproducible signed bundle | `bundleRelease` produces a signed AAB; `.github/workflows/release.yml` reproduces it from a tag with a protected `release` environment |
| Notification-log privacy | The derived period date is out of the delivery log, replaced by a lead-time bucket and a lateness class; source-scanning guard test |

## Release artifact

| | |
|---|---|
| **Artifact** | `app/build/outputs/bundle/release/app-release.aab` (9,858,039 bytes) |
| **SHA-256** | `68fa180d47cd4a70abb7c5ee4116dc4c3113b9ae02e6709990ad328353f4d1dd` |
| **Signature** | `jarsigner`: jar verified, signer `CN=CycleCare Upload` |
| **Validation** | bundletool validate passed; `scripts/validate-aab.sh` green |
| **Version** | `versionCode` 7, `versionName` 1.0 |
| **Reproducibility** | Four independent builds produced byte-identical bundles |

The CI path that produces this from a tag is implemented and the local
signing path was reproduced, but **the workflow has never been executed in
GitHub Actions** and the protected `release` environment and its four
secrets do not yet exist. See *Owner actions*.

## Blockers to release

These are open. None is claimed closed.

| Blocker | Why it blocks |
|---|---|
| 7 instrumented test failures | The instrumented suite is the release gate; it is red. |
| Physical-device Doze | `13` §17's exit criterion is *recorded, not assumed*. Emulator evidence does not satisfy it. |
| Release workflow never executed | The AAB is reproducible locally, but the CI path is unproven and its secrets are unconfigured. |
| Play Console declarations, store listing, support channel | External, unsubmitted, unowned by the repository. |
| Device matrix | Not executed. |
| Visual sign-off of the palette | The palette is approved as a direction, not for release. |
| Manual TalkBack pass | Automated semantics coverage is not a human screen-reader pass. |



## External: Play Console, not done

These can only be completed in Play Console. The information needed to
complete them is prepared below.

| Item | What is needed |
|---|---|
| Data safety form | The answers follow from the verified implementation: cycle and symptom data stays on the device, ad identifiers are processed by Google for advertising, and health data is not sent to the ad SDK. `scripts/guard-no-ad-targeting.sh` enforces the last point in CI. |
| Advertising ID declaration | The app requests the advertising identifier through AdMob and UMP. It must be declared, and it must be consistent with the policy page. |
| Ads declaration | The app is ad-supported: a banner on Today and Insights, and interstitials gated by the eight conditions in `15` §11.3. |
| Health declaration | The app tracks menstrual cycle, symptoms and predictions, so the health category applies and the data is not shared with third parties for health purposes. |
| Permissions declaration | `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, `SCHEDULE_EXACT_ALARM`, `INTERNET` and `ACCESS_NETWORK_STATE` are declared in the manifest. `SCHEDULE_EXACT_ALARM` is a special-access permission and needs its own declaration and a usage justification. |
| Target API level | The app compiles against API 36, which satisfies the requirement for new apps and updates. Confirm the current guidance in Play Console before upload. |
| Content rating and target audience | Not yet prepared. The app is not aimed at children, which the app lock and local-only storage make credible, and this needs a questionnaire. |
| Support channel | The policy page names `support@cyclecare.app`. The channel behind it has to exist and be monitored before release. |

## Also external

| Item | Ledger reference |
|---|---|
| Real-device Doze demonstration | **P7-05** |
| Foldable layout verification | **P5-02** |
| Manual screen-reader pass | **P5-03** |
| Hosted privacy policy page | Owned by the user. Must match `docs/privacy-policy.html` |

## Repository work still open

| Item | Ledger reference |
|---|---|
| A reminder schedule timestamp is in the diagnostic log | **P9-08** |
| A reset that cancels reminders has no test | **P9-11** |
| The 200% font-scale and landscape capture runs abort part way | **P5-06** |
| `PredictionSnapshot.confidenceLabel` is still a `String` on the model | **P2-02** |
| One `InsightEngine` site still reads the stored `cycleLength` | **P2-03** |
| Every constant has a documented rationale | **P2-01** |
| There is no producer for a learned send time | **P7-07** |
| A dependency vulnerability review in CI | **P9-02** |
| A privacy manifest and store disclosure review | **P9-03** |
| The accessibility checks wired into CI | **P9-04** |
| A performance baseline and budgets | **P9-05** |
| The manual screen-reader pass | **P5-03** |

## Not started

| Item | Ledger reference |
|---|---|
| Store listing, screenshots, localised copy | **P9-01** |
| Device matrix | **P9-06** |
| Performance baseline and budgets | **P9-05** |
| Dependency vulnerability review in CI | **P9-02** |
| Privacy manifest and store disclosure review | **P9-03** |
| Accessibility checks wired into CI | **P9-04** |
| Visual sign-off on the palette and the captured screens | **P9-07** |

## Explicitly deferred

| Item | Note |
|---|---|
| Cloud sync | Deferred. The app ships local-first, and the roadmap treats sync as deferrable. The encrypted local export and import is the supported way to move data between devices. Recorded as a decision in `20-sync-decision.md`, and as Phase 8 in the ledger. |

## Amendment — 2026-11-10 (Android 17 / API 37 upgrade, 2.1.0)

The "Target API level" row above describes the state at the time it was
written and is superseded. As of 2026-11-10:

- The app **compiles against API 37 (Android 17)** and **targets API 37**
  (since 2026-11-10). `versionName` is `2.1.0`, `versionCode` `9`.
- The full instrumented suite was re-verified on API 37 (`cc37`, 320x640 at
  160dpi): **268 tests, 0 failures, 2 skipped (opt-in Doze)**, identical to
  the API 36 calibration. Doze delivery latencies on API 37: 22 s awake,
  45 s deep idle — identical to API 36.
- The CI instrumented lane runs on API 37 (advisory). See
  `docs/android-17-upgrade-plan.md` §8/§9/§10 and
  `26-android-17-verification.md` for the complete evidence trail.
- The Play Console target API requirement had not moved as of the last
  check: API 36 is required today (since 2026-08-31); API 37 is expected in
  August 2027. **Confirm in Play Console → Policy status before upload.**
- The exact-alarm (`SCHEDULE_EXACT_ALARM`) declaration remains open in
  `play-console/data-safety-unresolved.md` and is a release dependency for
  2.1.0 regardless of target API level.
- The historical API 36 statement above is kept for the record; do not read
  it as current.
