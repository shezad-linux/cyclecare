# Release readiness checklist

Spec `08` Phase 9. This tracks what has to happen before CycleCare is
released, and separates what has been done in the repository from what
has to be done in Play Console, which cannot be done from here.

**Nothing in the "external" section is complete.** None of it has been
submitted, and no claim is made about its state.

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
| Unit and instrumented test suites | 329 unit tests and 127 instrumented tests pass on the API 36 emulator |
| Encryption for local export and import | `core/security`, with a passphrase-derived key, AAD binding and a fail-closed import |

## External: Play Console, not done

These can only be completed in Play Console. They are **not** done, and
the information needed to complete them is prepared below.

| Item | What is needed |
|---|---|
| Data safety form | The answers follow from the verified implementation: cycle and symptom data stays on the device, ad identifiers are processed by Google for advertising, and health data is not sent to the ad SDK. `scripts/guard-no-ad-targeting.sh` enforces the last point in CI. |
| Advertising ID declaration | The app requests the advertising identifier through AdMob and UMP. It must be declared, and it must be consistent with the policy page. |
| Ads declaration | The app is ad-supported: a banner on Today and Insights, and interstitials gated by the eight conditions in `13` §11.3. |
| Health declaration | The app tracks menstrual cycle, symptoms and predictions, so the health category applies and the data is not shared with third parties for health purposes. |
| Permissions declaration | `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, `SCHEDULE_EXACT_ALARM`, `INTERNET` and `ACCESS_NETWORK_STATE` are declared in the manifest. `SCHEDULE_EXACT_ALARM` is a special-access permission and needs its own declaration and a usage justification. |
| Target API level | The app compiles against API 36, which satisfies the requirement for new apps and updates. Confirm the current guidance in Play Console before upload. |
| Content rating and target audience | Not yet prepared. The app is not aimed at children, which the app lock and local-only storage make credible, and this needs a questionnaire. |
| Support channel | The policy page names `support@cyclecare.app`. The channel behind it has to exist and be monitored before release. |

## Not started

| Item | Note |
|---|---|
| Store listing | No listing, no screenshots, no localised copy. The design review images produced for the re-theme cover Today, Calendar, Log and Insights at four widths in both schemes, and can seed the listing once the remaining screens have been reviewed. |
| Device matrix | Fresh install, upgrade from v2, migration path, permission denial, backup and restore, process death, foldable, RTL and 320dp have not been exercised as a matrix. The 320dp and 1.5x font scale cases are covered by the screenshot harness and the navigation label test. |
| Doze demonstration | Recorded in `19-doze-verification.md`. The current mechanism is demonstrably deferred by Doze; the Tier 2 mechanism that would fix it is not implemented, and a real device is still required. |
| Visual sign-off | The re-theme is approved as a direction but not for release. The screens listed in the review have not all been reviewed. |

## Explicitly deferred

| Item | Note |
|---|---|
| Cloud sync | Deferred. The app ships local-first, and the roadmap treats sync as deferrable. The encrypted local export and import is the supported way to move data between devices. Recorded as a decision in `20-sync-decision.md`. |
