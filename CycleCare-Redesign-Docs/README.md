# CycleCare — Product, System Design, Algorithm, and UX Specification

**Status:** Repository audited. Target architecture specified. Not yet implemented.
**Repository audit date:** 17 October 2026
**Platform in scope:** Android, Kotlin, Jetpack Compose.
**Architecture direction:** Local-first, with optional privacy-preserving cloud sync using Supabase.

## Purpose

This documentation set is the build contract for the CycleCare redesign. It guides product decisions, the prediction algorithm, notification behaviour, UI design, database evolution, implementation, verification and Google Play release planning.

**The repository has now been inspected directly.** Every claim about the current implementation is verified by reading source, inspecting the exported Room schema, or by running Gradle. Where an earlier version of these documents relied on a *described* architecture, `00-current-state-review.md` now records what was actually found.

## Verified starting state

The app **compiles** (2m21s, 2 warnings) and has **95 passing unit tests** and 71 instrumented tests. It is a working, tested, local-only application. `SyncAdapter` is a `NoOp`. The problems are correctness, quality and honesty problems — not a broken build.

There is **no network layer at all**: no Supabase, no HTTP client, no serialization library. There is **no localization** — `strings.xml` contains one string and all user-facing copy is hardcoded English in Compose.

## Decisions taken

| # | Decision | Specified in |
|---|---|---|
| 1 | **Room remains the operational source of truth.** Core use never requires a network or an account. | `01` |
| 2 | **Advertising is retained.** Health data never reaches the ad SDK — verified, and guarded in CI. | `16` |
| 3 | **Cloud sync is in scope**, as opt-in client-side encrypted sync. | `17` |
| 4 | **Whole-app rewrite is permitted.** There is a live user base, so the schema migration must be strictly lossless. | `02` §9 |
| 5 | **Prediction:** robust classical estimation with empirical intervals, replacing a 70-line heuristic with no range and no outlier handling. | `11`, `12` |
| 6 | **Notifications:** relevance scoring, frequency budget, self-disabling reminders, and hybrid timing. | `13` |
| 7 | **UI:** four bottom destinations with Settings in the app bar; a complete token and component system. | `14`, `15` |
| 8 | `USE_EXACT_ALARM` is **not available** to CycleCare under Play policy. `SCHEDULE_EXACT_ALARM` for two reminder types only, with degradation as the default path. | `13` §11, `07` §2 |

## Recommended architecture in one sentence

Keep Room as the operational source of truth and preserve full offline use; make Supabase sync an explicit opt-in capability with client-side encryption; and state the privacy posture accurately, including the fact that ad-supported apps collect advertising and device data even when no health value is ever transmitted.

## Document map

| File | Responsibility |
|---|---|
| `00-current-state-review.md` | **Verified** findings, flaws, strengths, and the decisions taken |
| `01-system-architecture.md` | Modules, runtime flows, boundaries, Supabase's role, non-functional requirements |
| `02-database-and-sync.md` | Entities, constraints, encrypted envelope, conflicts, deletion, **and the lossless v2→v3 migration** |
| `03-ui-ux-design-system.md` | Design principles and experience intent (**principles layer**) |
| `04-security-and-privacy.md` | Data inventory, threat model, local protection, encryption, RLS, ads, deletion |
| `05-cycle-domain-and-health-safety.md` | Input validation, uncertainty, health-content boundaries (**safety layer, binding on `11`**) |
| `06-testing-and-acceptance.md` | Test strategy, release gates, definition of done, redesign-specific gates |
| `07-google-play-release-and-discovery.md` | Policy checkpoints, declarations, store listing, concrete ranking plan |
| `08-implementation-roadmap.md` | **Ordered phases with dependencies, exit criteria and a risk register** |
| `09-product-requirements.md` | MVP scope, user needs, functional requirements, non-goals, success measures |
| `10-analytics-and-monetization.md` | Privacy-compatible telemetry and monetization (**amended** — ads retained) |
| **`11-prediction-engine.md`** | **The algorithm:** robust estimator, drift adjustment, empirical intervals, typed quality, versioning |
| **`12-algorithm-validation.md`** | **Proof:** corpus generators, hold-out protocol, gates G1–G13, golden fixtures, tuning discipline |
| **`13-notification-intelligence.md`** | **The notification algorithm:** relevance scoring, budget, self-disabling, hybrid timing, idempotent delivery |
| **`14-design-system.md`** | **Tokens** (light + dark), type ramp, shape, spacing, motion, semantic cycle states, components, a11y targets, localization |
| **`15-screen-specifications.md`** | **Screens:** layout, states, copy, semantics, navigation restructure, ad placement, file decomposition |
| **`16-monetization-and-ads.md`** | **Ads:** verified data flows, truthful disclosure, consent, Play declarations, CI guard |
| **`17-sync-and-key-management.md`** | **Sync:** vault key lifecycle, recovery, envelope crypto, RLS, outbox, conflicts, deletion |

## How to use these documents

- Read `00` first. It is the verified baseline everything else is written against.
- Follow `08` in order. It is ordered by **risk**, not by convenience: the schema migration ships as its own release because there is a live user base.
- `11` + `12` are the algorithm. `12`'s gates are release blockers, not targets to be adjusted until they pass.
- `14` + `15` are the build contract for UI. `03` supplies the principles; where they disagree on a concrete value, `14` and `15` win.
- `04`, `05` and `06` are release blockers. `05` remains binding on all output from `11`.
- `16` and `17` cover the commercial and cloud decisions.

## Non-negotiable constraints

These survive every trade-off in this document set:

1. **Estimates are estimates.** No output may imply contraception, diagnosis, or certainty. Sparse data produces a visible uncertainty state, never a fabricated value.
2. **Observed records stay distinct from derived estimates**, in code, database, UI and export.
3. **No health value reaches the ad SDK** — enforced by a CI guard, not by convention.
4. **Never claim "zero data leaves the device", "no tracking", "fully encrypted" or "100% private".** The app is ad-supported and, with sync, transmits encrypted envelopes plus metadata.
5. **Core use works offline with no account.** No screen blocks on network state for local content.
6. **Migration is strictly lossless.** No destructive fallback on any production path.
7. **No numerical security, accuracy or quality ratings** without measured evidence behind them.

## Source and verification notes

Guidance consulted on 17 October 2026:

- [Apple Human Interface Guidelines — Design principles](https://developer.apple.com/design/human-interface-guidelines/design-principles/)
- [Apple Human Interface Guidelines — Privacy](https://developer.apple.com/design/human-interface-guidelines/privacy/)
- [Apple Human Interface Guidelines — Notifications](https://developer.apple.com/design/human-interface-guidelines/notifications/)
- [Supabase — Row Level Security](https://supabase.com/docs/guides/database/postgres/row-level-security)
- [Supabase — Securing your data](https://supabase.com/docs/guides/database/secure-data)
- [Supabase — Production checklist](https://supabase.com/docs/guides/deployment/going-into-prod)
- [Google Play — Health and Medical Apps policy](https://support.google.com/googleplay/android-developer/answer/16679511)
- [Google Play — App Discovery and Ranking](https://support.google.com/googleplay/android-developer/answer/9958766)
- [Google Play — Sensitive permissions policy (exact alarms)](https://support.google.com/googleplay/android-developer/answer/16558241)
- [Android — Schedule exact alarms](https://developer.android.com/about/versions/14/changes/schedule-exact-alarms)
- [Android — Schedule alarms](https://developer.android.com/develop/background-work/services/alarms)
- [Android — Target API requirements](https://developer.android.com/google/play/requirements/target-sdk)
- [Android — Auto Backup](https://developer.android.com/identity/data/autobackup)
- [Android — Jetpack Compose accessibility](https://developer.android.com/develop/ui/compose/accessibility)

These are implementation guidance, not a legal opinion or proof that the app complies with any policy.