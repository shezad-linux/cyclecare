# The dark-pattern copy review

The review behind `14` §12.13 — *"No dark-pattern copy anywhere in the UI"* —
and the phrase-absence test that makes it repeatable.

**Status.** Reviewed at `workstream/phase5-6-ui`, against
`03` §2.4, `03` §2.5, `03` §7, `05` §9 and `11` §5.
**Scope.** Every `<string>` and every `<plurals>` item in every main source
set: **747 strings across 10 resource files**.

---

## 1. The rules the copy was reviewed against

| Source | Rule |
|---|---|
| `03` §2.4 | Estimates are visibly estimates. No confident exact-date language. |
| `03` §2.5 | No shame or pressure. No streak-loss language, guilt notifications, fertility assumptions, or judgmental language for missing logs or irregular cycles. |
| `03` §7 | The preferred and the avoided phrasings. |
| `05` §9 | No screen presents predictions as certainty. Fertile-window copy never implies contraception. Insight copy distinguishes association from causation. |
| `11` §5 | The prediction copy table, including "Fertile window, always". |

## 2. What the review changed

| # | File | Was | Now | Rule |
|---|---|---|---|---|
| 1 | `feature/onboarding/src/main/res/values/strings.xml` | `this_helps_predict_your_next_cycle_accurately` = "This helps predict your next cycle accurately." | `this_sets_your_starting_point_for_cycle_estimates` = "This sets your starting point for cycle estimates. Dates can vary as you log more." | `03` §2.4, `11` §5. An accuracy claim the estimate cannot support. **This was the item the Phase 4 workstream reported.** |
| 2 | `feature/logging/.../strings.xml` | `hero_insight_fertile_window` = "You're in your fertile window. Logging symptoms helps track patterns." | "You may be in your estimated fertile window. Logging symptoms helps track patterns." | `03` §7 ("You are definitely ovulating."), `05` §9. The window is an estimate. |
| 3 | `feature/logging/.../strings.xml` | `hero_insight_mid_cycle` = "Mid cycle phase. Fertile window approaching in a few days." | "Mid cycle phase. Logging symptoms helps track patterns." | `11` §5. The window's start is the engine's, so "a few days" is a claim the feature cannot make. The accurate countdown now comes from the engine's own window. |
| 4 | `feature/home/.../strings.xml` | `FertilityTeaserCard` subtitle was a bare range under the title "Fertile window". | `estimated_fertile_days` = "Estimated fertile days: %1$s – %2$s", plus `not_a_contraceptive_method` = "This is not a contraceptive method." on the Today teaser card, the cycle-status screen and the fertility-detail screen. | `11` §5: "Estimated fertile days: … This is not a contraceptive method." |
| 5 | `feature/home/.../strings.xml` | `fertile_window_unavailable` shown whenever the snapshot had no range. | `not_enough_history_for_fertile_days` = "Not enough history yet to estimate fertile days." | `03` §7 ("Not enough history to estimate this yet."). |
| 6 | `feature/calendar/.../strings.xml` | `summary_fertile_detail_future` / `_today` / `_past` ended at the day count. | Each now ends "This is not a contraceptive method." | `11` §5, "always". |
| 7 | `feature/calendar/.../strings.xml` | `day_state_fertile` = "fertile", `day_state_ovulation` = "ovulation". | "estimated fertile", "estimated ovulation". | `03` §2.4. These are the words a screen reader reads out for a day the engine estimated. |

## 3. What the review found and left alone

These are reported, not changed. The first is the ledger's row A2, which
the lead owns.

### 3.1 The Logging feature's positive streak copy — ledger row A2, open

`feature/logging/src/main/res/values/strings.xml`:

- `no_streak_yet` = "No streak yet"
- `plurals/streak_days` = "%1$d day streak!"
- `saved_checkin_complete` = "Saved! Check-in complete. Keep up the great habit."

`03` §2.5 and `14` §6 ban *streak-loss* language, guilt and confirmshaming.
They do not ban a positive counter. `13` §17.12 says "No guilt, streak, or
false-precision copy anywhere", and `13` §15 scopes the same rule to the
notification channel, where the copy is clean. The specification does not
say which reading governs, so the criterion is recorded as partial and the
decision is left open. **No code was changed.** The phrase-absence test
bans losing a streak and does not ban having one, so this copy does not
make the test red either way.

### 3.2 The Logging feature's cycle score

`feature/logging/.../LoggingUiState.kt:268` — `calculateCycleScore`
returns `75 + 4 × sections + 2 × streak`, capped at 100, and the hero
shows it as "Score: 80" (`score_value`).

There is no documented rationale for the 75-point base, which `05` §9
requires ("All thresholds have a documented rationale"), and a number
with no clinical meaning is the kind of false precision `03` §7 warns
about. It is a product decision rather than a copy fix, so it is reported
here rather than changed.

### 3.3 The calendar's "Peak fertility" wording

`feature/calendar/.../strings.xml` — `summary_ovulation_detail_today` =
"Peak fertility • Tracking symptoms today may help with future insights."

The status label immediately above it is "Ovulation estimate", so the
framing is present in the composition, but "Peak fertility" on its own
reads as a fact rather than an estimate. Borderline against `03` §7
("You are definitely ovulating."). Reported, not changed.

### 3.4 Copy that was checked and is clean

- The privacy wording. `cycle_data_storage_and_ad_privacy` is scoped
  ("Advertising services may read your device's advertising identifier,
  but cycle data is not included in ad-targeting requests"), which is the
  form `03` §7 requires. It appears in two modules with identical text.
  Neither was changed.
- "Your data stays private on your device" (onboarding) — scoped to the
  device, and true.
- The Settings copy for exact alarms — "Reminders still work without
  this, but they can be delayed by a few minutes." Honest about the
  consequence of declining, which is the opposite of notification
  pressure.
- The notification copy in `core/domain` — hedged ("Your period may
  start…"), no guilt, no streak, no false precision. Reviewed in the
  Phase 7 audit; not re-opened here.
- The export copy — states the failure and that no changes were made
  ("Import failed. No changes were made.").

## 4. The phrase-absence test

`app/src/test/java/com/cyclecare/app/DarkPatternCopyTest.kt`.

It walks every `src/main/res/values/strings.xml` in the repository,
extracts every `<string>` and every `<plurals>` item, and fails when one
contains a phrase from the banned list. The list has 35 phrases across
the seven categories `03` §2.5, `03` §7, `05` §9 and `11` §5 name, and
each phrase carries the rule it comes from.

Three of the four cases are there to stop the test passing for the wrong
reason:

- `theScanActuallyReadsTheUserFacingStrings` — the scan has to find more
  than 500 strings across at least 8 resource files.
- `theMatcherActuallyRejectsABannedPhrase` — the matcher is proved
  against a phrase that is on the list.
- `theBannedListCoversEveryCategoryTheReviewNames` — one phrase per
  category, so the audit cannot lose a category silently.

The absence check was proved against a real violation: adding "You missed
your tracking streak." to `feature/calendar`'s `cancel` string makes the
test fail with
`feature/calendar/src/main/res/values/strings.xml:cancel -> "You missed
your tracking streak. Cancel"`.

## 5. What is still open

- Ledger row A2: whether `13` §17.12's "anywhere" reaches a positive
  streak counter. Not decided here.
- §3.2 and §3.3 above: reported, not decided here.
