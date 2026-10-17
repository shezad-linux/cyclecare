# Data Model

> ## ⚠️ DESCRIBES SCHEMA v2 — SUPERSEDED 17 October 2026
>
> **Current schema and migration: `CycleCare-Redesign-Docs/02-database-and-sync.md` §9.**
>
> The entity list below matches Room schema **v2**. Schema **v3** changes it substantially, and because there is a **live installed user base** the migration must be strictly lossless.
>
> Key v2 → v3 changes, all detailed in `02` §9.3:
>
> - `symptom_logs.mood` and `energyLevel` become **nullable**. The v2 schema forced every user to answer both on every log, because neither enum has a `None` variant.
> - `symptom_logs.painLevel` and `dischargeType` become nullable, but `None` is **preserved as a distinct tracked value**. `NULL` means "not tracked"; `None` means "explicitly none". Conflating them destroys data.
> - `cycle_records.endEpochDay` becomes nullable — an in-progress period is currently unrepresentable.
> - Audit and tombstone columns are added to every table. There are **none** today, so backfill timestamps are necessarily synthetic and must never be displayed as real creation dates.
> - `cycleLength` is retained for audit but stops being an input. Production data may already disagree with its own start gaps, because nothing has ever cross-validated it.
> - New tables: `sync_outbox`, `engagement_profile`, `app_settings`, `daily_symptoms`.
> - **No table in v2 has any index or foreign key.**
>
> One line below is technically true and materially misleading: *"Sensitive settings like PIN are hashed before persistence."* It is a **single-round SHA-256** with no key-stretching and no salt (`Repositories.kt:684-688`). A four-digit PIN has 10,000 possible values, so this is recoverable in microseconds if the verifier is extracted — an entropy problem no KDF can solve.

# Data Model

## Core Records

- `UserProfile`: age band, goals, average cycle length, period length, last period start
- `CycleRecord`: start date, end date, source, optional notes
- `SymptomLog`: date, mood, pain, energy, discharge, medication, notes
- `PredictionSnapshot`: next period, ovulation date, fertile window, confidence note
- `ReminderRule`: type, schedule, enablement
- `InsightSnapshot`: averages, recent cycle and period trend series, symptom breakdowns, phase highlights, and a concise narrative summary for insights and export
- `ConsentState`: notification preview and privacy acknowledgements
- `ExportArtifact`: generated local report metadata

## Persistence Strategy

- Room entities use stable string IDs and epoch-day dates
- Preferences remain in DataStore for low-complexity settings
- Sensitive settings like PIN are hashed before persistence
