# Prediction Logic

> ## ⛔ SUPERSEDED — 17 October 2026
>
> **This document describes algorithm v1 and is retained only for history. Do not implement from it.**
>
> It is contradicted by `CycleCare-Redesign-Docs/11-prediction-engine.md` (the replacement algorithm) and `CycleCare-Redesign-Docs/12-algorithm-validation.md` (its verification).
>
> **The specific contradiction that matters:** §"Local Adjustments" below instructs *"keep the simple rolling average and surface a lower-confidence message instead of trimming outliers."* Version 2 does the opposite — it excludes outliers using a robust estimator, because a single unvalidated 200-day gap can otherwise push a prediction months out. That defect is confirmed in `00-current-state-review.md` §3.4.
>
> Version 1 also produces **no prediction range at all**, yet other documents in this folder and the UI copy imply dated ranges. Version 2 emits a calibrated interval with a measured coverage target.

---

<details>
<summary>Superseded content — algorithm v1</summary>

## Deterministic Baseline

- `nextPeriod = lastPeriodStart + averageCycleLength`
- `ovulation = nextPeriod - 14 days`
- `fertileWindow = ovulation - 4 days .. ovulation + 1 day`

## Local Adjustments

- Use the last up to 6 logged start-to-start intervals to compute a rolling average cycle length
- If there are fewer than 2 usable intervals, keep the confidence label at `Baseline estimate`
- If recent cycles vary significantly, keep the simple rolling average and surface a lower-confidence message instead of trimming outliers
- Keep outputs descriptive rather than absolute

## Overdue Behavior

- Keep `daysUntilNextPeriod` negative when the predicted start date is already past due
- Let `cycleDay` continue beyond the average cycle length until a new logged cycle start becomes the anchor

## Product Guardrails

- Always label fertility outputs as estimates
- Never present avoidance or conception guarantees
- Keep explanatory copy wellness-first and non-clinical

## End of superseded v1 content

</details>
