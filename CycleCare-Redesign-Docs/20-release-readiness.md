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
| **Audit date** | 2026-10-20 |
| **Commit** | `d689a7e167991aa2a2caa50fc43f4005cad86979` |
| **Unit tests** | 333, 0 failures |
| **Instrumented tests** | 166, 0 failures, 2 skipped (the opt-in Doze tests) |
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
| Unit and instrumented test suites | 333 unit and 166 instrumented, both green on the API 36 emulator |
| Encryption for local export and import | `core/security`, with a passphrase-derived key, AAD binding and a fail-closed import |

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
