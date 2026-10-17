# Architecture

> **Current specification: `CycleCare-Redesign-Docs/01-system-architecture.md`.**
>
> This summary remains **accurate** and does not contradict the current set. Verified against the repository: the module list, the runtime boundaries, the `SyncAdapter` placeholder, and the ad/UMP statement are all correct.
>
> One addition for completeness — the audit also found `:core:testing` is **orphaned** (declared in `settings.gradle.kts`, depended on by nothing), `feature:billing` was deleted from source but not from disk, and the `:benchmark` test never compiles. See `00-current-state-review.md` §4.8.

# Architecture

## Principles

- Local-first data ownership
- Low backend dependency
- Offline usability as a hard requirement
- Clear separation between UI, domain logic, and storage

## Module Layout

- `app`: application shell, navigation, WorkManager wiring, app container
- `core:model`: shared models and enums
- `core:domain`: repository contracts and pure business logic
- `core:data`: Room and DataStore-backed repository implementations
- `core:database`: entities, DAOs, database
- `core:designsystem`: theme, components, tokens
- `core:testing`: shared test fixtures and helpers
- `feature:*`: UI and feature view models

## Runtime Boundaries

- Room is the source of truth for cycle and symptom history
- DataStore persists preferences, reminders, and privacy state
- The shipped app has no billing runtime dependency
- AdMob and Google UMP are used for ad-supported surfaces and ad privacy consent
- Future sync is represented by `SyncAdapter` but has no v1 implementation
