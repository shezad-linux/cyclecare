# 08 — Implementation Roadmap

## Guiding rule

**Do not rewrite everything at once.** The failure mode for this project is not "too big to finish" — it is shipping a UI redesign, a schema migration, an algorithm change, a notification behaviour change and a new cloud backend in a single release, then being unable to attribute a regression or a data-loss report to any of them.

There is a **live installed user base**. That single fact changes the ordering: anything that risks user data or is trivially reversible comes before anything that improves appearance.

## Phase 0 — Audit and product decisions ✅ COMPLETE

The repository, build, Gradle graph, Room schema, domain logic, notification path and design system have been inspected directly. Findings are recorded in `00-current-state-review.md` with file and line evidence. Product decisions are recorded in `00` §2.

**Build verified:** compiles in 2m21s; 95 unit tests pass; 71 instrumented tests compile.

**Do not re-open this phase.** If new code is added, extend the audit rather than restarting it.

## Phase 1 — Safety, before anything else

Nothing here depends on the redesign. All of it should land in a release **before** the schema migration, because each item either protects migrating users or corrects a false public claim.

| Task | Why first |
|---|---|
| **Fix the broken privacy policy.** Rewrite to the verified truth per `16` §4 — remove "No analytics or tracking SDKs", "permanently deletes all stored data", the incomplete permission list, and every "100% private"-class claim | It is published, it is false, and it is a live policy exposure |
| **Complete Play Data safety, Advertising ID, Ads and Health declarations** to match actual SDK behaviour | Same reason |
| **Decide and implement `data_extraction_rules.xml`**; decide the device-transfer position | Required *before* migrating live users (`02` §9.9). If a migration fails after a phone transfer, there may be no recovery |
| **Move keystore passwords** out of `keystore.properties` into injected values; confirm no CI artefact contains them | Plaintext production passwords on disk |
| **Add `proguard-rules.pro`**; verify a real release build against it | No shrinker config exists for a shipping release |
| **Add `network_security_config.xml`** | Currently relying on an implicit default |
| **Fix the broken macrobenchmark** (`benchmark/build.gradle.kts:35-39`) | Restore the ability to measure anything |
| **Fix the broken migration test** (`CycleCareDatabaseRoomTest.kt:104-114`) | It will throw; it currently hides real migration behaviour |
| **Add CI with secret scanning and the ad-targeting API guard** (`16` §7) | Prevents silent privacy regressions from here on |
| **Test coverage for `feature:privacy`** — PIN keypad, `SecureAuthController`, biometric unlock | Highest-priority untested security surface in the repository |

**Exit criteria:** no false public claim remains; backups and transfer behaviour are decided and tested; CI is running with privacy and secret guards; the two broken test suites pass.

## Phase 2 — Contracts

Write no feature code until these are agreed. Each is a document plus, where noted, a compiling skeleton.

| Contract | Document | Notes |
|---|---|---|
| Schema v3 and the lossless migration | `02` §9 | Reconciliation rules for `cycleLength`, `None` vs `NULL`, duplicate dates |
| Prediction engine v2 | `11` | Constants, contract, interval math |
| Algorithm validation harness | `12` | Corpus, gates G1–G13 |
| Notification intelligence | `13` | Relevance scoring, tiers, budget |
| Design system | `14` | Tokens, type ramp, components |
| Screen specifications and navigation | `15` | Four destinations; ad placement |
| Monetization and ads posture | `16` | |
| Sync and key management | `17` | Resolves the recovery decision left open in `00` |

**Exit criteria:** every constant has a documented rationale; the domain contracts compile as Kotlin interfaces; no consumer still references `PredictionSnapshot.confidenceLabel` or reads `cycleLength` as an interval.

## Phase 3 — Schema migration

**Deliberately separated from Phase 2 and from every visual change.** Live user data is at stake.

- Implement `migration1To2` and `migration2To3`, both registered on the production `databaseBuilder`.
- Add the audit, tombstone, outbox, engagement and settings tables; the nullable-field changes; the indices.
- Run the reconciliation and dedupe logic exactly as specified, including divergence logging.
- Build fixtures from a **production-shaped, anonymised snapshot** — not synthetic data alone, which will not surface duplicate-date rows or `cycleLength` divergence.
- Commit `3.json`.
- No destructive fallback on any production path.

**Exit criteria:** every acceptance criterion in `02` §9.10 passes; row counts and IDs verified identical before and after on a real-shaped fixture; the app launches and behaves correctly on a migrated v2 database.

**Ship this as its own release.** It is the highest-risk change in the project and must be attributable on its own.

## Phase 4 — Prediction engine

- Implement `11` against the shared interval extractor; delete the v1 engine and its 17 tests rather than adapting them.
- Wire the injected clock through `core/data`; remove the `LocalDate.now()` default from the production path.
- Build the corpus harness and golden fixtures from `12`; get all thirteen gates passing.
- Convert `PredictionSnapshot` → `CyclePrediction` and migrate every consumer in one change.
- Make `InsightEngine` consume the shared extractor so the two engines cannot disagree.

**Exit criteria:** G1–G13 pass; `11` §8's twelve criteria hold; no screen shows an unlabelled point estimate; Today and Insights can never contradict each other.

Ship behind the same release as Phase 3 if you can, or immediately after. **Prediction display changes shape** — from a single date to a range — so this needs release notes and a "what changed" surface.

## Phase 5 — Design system

- Move every hardcoded colour into tokens, including the gradient literals in `CycleCareTheme.kt:153-176`.
- Fix the `isSystemInDarkTheme()`-inside-token-function bug.
- Define all 15 typography roles, `Shapes()`, and a consumed spacing scale.
- Build the component library from `14` §7 with full state coverage.
- Add `ui-test-junit4` and `debugImplementation(compose.ui.tooling)` where missing, including `:core:designsystem`, `feature:settings`, `feature:privacy`, `feature:export`.
- Give `:core:designsystem` its own test suite — it has none.
- Move all user-facing strings into `strings.xml`. There are currently **zero** entries beyond `app_name`, and localisation is a prerequisite for any localised listing.

**Exit criteria:** every criterion in `14` §12 passes; no screen uses an undefined typography role; previews exist at 320/360/600/840dp in light and dark.

**This phase changes almost every screen visually.** It should ship as its own release, after Phase 4, so a visual regression is not mistaken for an algorithm regression.

## Phase 6 — Screens

- Restructure navigation to four destinations with Settings in the app bar; preserve deep links.
- Rewrite `CycleCareAppStateNavigationTest` for four tabs — **before** touching screen code, so the safety net exists first.
- Rebuild screens against `15`, splitting the three oversized files.
- Move state mutation out of composable scope in `AppLockGate.kt` and `CycleCareApp.kt`.
- Fix accessibility defects: bottom-nav `contentDescription`, the back-handler trap, missing `stateDescription`.
- Implement ad placement per `15` §11 — removing the banner from Calendar, Log and Settings is a visible improvement, not just a policy one.
- Wire or delete the dead `ReminderBell`.
- Clean up `:core:testing` (adopt or delete) and remove `feature/billing`'s orphaned `build/` artefacts.

**Exit criteria:** every criterion in `15` §14 passes; no file exceeds ~600 lines; all screens work at 200% font scale, in dark mode, in RTL, and under TalkBack.

## Phase 7 — Notifications

- Implement relevance scoring, budget and self-disabling from `13`.
- Replace WorkManager-for-time-sensitive-work with the Tier 1/2/3 alarm design; request `SCHEDULE_EXACT_ALARM` only for `PeriodPrep` and `LatePeriod`.
- Implement idempotent delivery, real group summaries, and `setPublicVersion` redaction.
- Add snooze and actions.
- Add the Settings UI for lead time, send time, quiet hours and automatic decisions.
- Delete the `Hydration` and `Pill` stubs.
- Demonstrate Tier 2 delivery under Doze on a real device.

**Exit criteria:** every criterion in `13` §17 passes; the Doze demonstration is recorded, not assumed.

**This changes user-visible behaviour on a live base.** Roll out through a staged track and watch notification-disable rate as the primary health metric — if `13` §9's scoring is wrong, disable rate rises first (`07` §7.6).

## Phase 8 — Sync

- Provision dev/staging/production Supabase projects; version-controlled migrations only.
- Implement the vault key lifecycle, envelope encryption and AAD binding from `17`.
- Build the passphrase recovery flow — and state the loss-of-key consequence plainly before opt-in.
- Implement the outbox, tombstones, revision conflicts and per-type merge policy.
- Apply RLS and run two-user isolation tests against staging in CI.
- Implement the four distinct deletion operations, each with a real completion state.
- Add sync status UI.

**Exit criteria:** all ten gates in `17` §14 pass; all six local-first invariants in `17` §12 proven by test.

**Deferrable.** If this slips, the app remains complete and shippable without it. Do not let sync delay the user-visible improvements in Phases 4–7.

## Phase 9 — Release readiness

- Target API check (API 36 or higher for new apps and updates from 31 August 2026, per current guidance — verify in Play Console before upload).
- Health apps declaration, Data safety, privacy policy, advertising declarations, content rating.
- Permissions declaration for `SCHEDULE_EXACT_ALARM`.
- Real store copy and screenshots, localised only where the app is genuinely localised.
- Device matrix: fresh install, upgrade from v2, migration path, permission denial, backup/restore, process death, foldable, RTL, 320dp.
- Localised store listing, once localisation ships.
- Support channel verified and monitored. Staged rollout with crash, ANR, data-loss and privacy-report monitoring.

**Exit criteria:** every checklist in `06` and `07` passes and is evidenced.

## Phase 10 — Measure and improve

- Review acquisition and conversion, crash-free sessions, first-log completion, retention by cohort, notification disable rate, ad-free purchase rate, accessibility and support issues.
- Re-run the `12` corpus with real aggregate distribution shapes — **never** with identifiable user records — and adjust constants only with the §9 discipline.
- Tune notification relevance weights against observed send/open/dismiss data, one variable at a time.
- Store listing experiments per `07` §7.5.
- Revisit the luteal-phase prior only if ovulation observations are ever collected, and extend the validation corpus in the same change (`11` §4.5).

**Exit criteria:** every change tied to a hypothesis, a measurable outcome and a privacy review.

## Risk register

| Risk | Impact | Mitigation |
|---|---|---|
| Migration loses or corrupts live data | **Severe** | Own release; production-shaped fixtures; one transaction; verification before commit; Phase 1 backup decision first |
| Prediction display change confuses users | High | Ranges only where coverage ≥ 0.80 is proven; `INSUFFICIENT_HISTORY` shows no range at all; release notes |
| Notification behaviour change increases uninstalls | High | Relevance scoring and budget; staged rollout; watch disable rate as the leading indicator |
| Design-system change regresses UI tests | Medium | Rewrite navigation tests first; per-screen releases; semantic tests over snapshot comparisons |
| Ads damage trust and ratings | Medium | Aggressive interstitial gating; drop rather than defer; real ad-free purchase; honest policy |
| Over-notification compounds ad fatigue | Medium | Review ad and notification budgets together (`16` §10) |
| Exact-alarm permission rejected or unused | Low | Designed as Tier 2 with degradation as the default path |
| Scope creep into sync | Medium | Sync is Phase 8 and deferrable; Phases 4–7 deliver the user-visible value |
| Future contributor adds ad targeting | Medium | CI guard fails the build (`16` §7) |

## Change-management rule

One phase per release, except where two phases are inseparable. Specifically: **do not** ship the UI rebuild, the schema migration, the algorithm change and the notification behaviour change together. Each is independently reviewable and, where possible, independently revertible.

For each phase, record: what changed, what evidence supports it, which tests prove it, and what would make it revert.