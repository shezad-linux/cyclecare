# 15 — Screen Specifications, Navigation, and Ad Placement

**Status:** Implementation contract for all UI work. Composes `14-design-system.md` and consumes `11`, `13`.
**Depends on:** `03` (principles), `14` (tokens and components), `11` (estimate states), `13` (notification surfaces), `16` (ads).

## 1. Navigation restructure

### 1.1 Current state

`CycleCareApp.kt:112-118` declares five top-level destinations — `home`, `calendar`, `log`, `insights`, `settings` — with labels "Home", "Calendar", "Track", "Insights", "Settings". Note the label/route mismatch on the third item ("Track" vs route `log`).

Twelve routes exist in the `NavHost` (`:567-711`). Three of them — `reminders`, `privacy`, `export` — are top-level routes with no tab and no bottom-bar entry, making them reachable only by deep link.

### 1.2 Target state

**Four bottom destinations; Settings promoted to the app bar.**

| Route | Kind | Label |
|---|---|---|
| `today` | Tab (start destination) | Today |
| `calendar` | Tab | Calendar |
| `log` | Tab | Log |
| `insights` | Tab | Insights |
| `today/cycle-status` | Detail | — |
| `insights/cycle-trends` | Detail | — |
| `insights/symptom-patterns` | Detail | — |
| `settings` | Pushed from app bar | — |
| `settings/reminders` | Child of settings | — |
| `settings/privacy` | Child of settings | — |
| `settings/export` | Child of settings | — |
| `app-lock` | Overlay, outside `NavHost` | — |

`home` is renamed `today` for consistency with the label; `privacy` and `export` move under `settings`; `reminders` becomes `settings/reminders`.

### 1.3 Why Settings moves, and the discoverability mitigation

`09` §2 lists export and data deletion as primary user needs. Removing Settings from the thumb zone makes them harder to find, so this relocation is only acceptable **with all four mitigations below, which are mandatory:**

1. A Settings action in the top app bar on **every** tab.
2. A direct "Export my data" and "Delete all data" entry in Privacy, reachable in two taps from anywhere.
3. Deep links retained so `privacy`, `export` and `reminders` remain reachable from notifications and external intents.
4. A one-time, dismissible nudge on first run to Settings after onboarding — never repeated, never a badge.

If usability testing shows Settings remains hard to find, revert to five tabs. That is a legitimate outcome of this change, not a failure; record the result either way.

### 1.4 Behaviour

- Bottom-nav labels always visible (never icon-only), with proper `contentDescription` on icons — fixing `contentDescription = null` at `:532`.
- Tab switching preserves scroll position and per-tab state via `popUpTo(start) { saveState = true }` + `restoreState = true`, as today.
- Detail routes hide the bottom bar.
- System back from a tab returns to `today`, as today.
- Predictive back enabled (`android:enableOnBackInvokedCallback`) — verify each back step.
- The notification-permission gate at `:258-309` must not trap back/switch-access users. The current `BackHandler(enabled = true) {}` at `:317` must be removed or given a real exit.
- **`ReminderBell` (`CycleCareApp.kt:732-747`) is dead code** — defined, never called. With Settings in the app bar it gets a real home there, carrying only actionable unread count (`14` §2 principle 9). If it carries no meaningful state, delete it rather than shipping an empty badge.

### 1.5 Expected test impact

`CycleCareAppStateNavigationTest` (19–20 tests) asserts five-tab navigation, tab-back behaviour and saved-state restoration across `recreate`. These must be rewritten. The saved-state and tab-back cases are the highest risk of regression and should be rewritten first, before any screen work begins.

## 2. Shared screen contract

Every screen implements the state model from `03` §6, and every state has a designed implementation plus a test:

Loading (skeleton at final dimensions — never a centred spinner for known layouts) · populated · empty · offline · save-in-progress · save-success · validation error · permission/channel disabled · unsupported or corrupt data.

Additional shared rules:

- Offline is a quiet persistent strip, never a modal. The app is local-first.
- Save confirmation persists visibly; a toast is never the only evidence a health record was saved.
- Validation errors are inline with a correction path, never modal.
- Destructive confirmations state exactly what is lost.
- Every screen is scrollable to its primary action when content overflows at 200% font scale.

## 3. Onboarding

Four steps, all skippable where data is not essential (`03` §5.1).

| Step | Content | Skippable |
|---|---|---|
| 1 | Purpose and privacy: what is stored locally, that an account is optional, what sync would upload **if** enabled | No |
| 2 | Tracking goal and age band — both offer "Prefer not to say" | Yes |
| 3 | Reminder preference — offered, **without** requesting notification permission yet | Yes |
| 4 | First value: land directly on Today with a real next action | — |

Rules:

- No account creation. No tutorial carousel. No "skip" that silently discards a choice.
- Notification permission is requested **in context** — only after a reminder is switched on, with an explanation before the OS dialog (`03` §2.3).
- On completion, set a first-use flag; if the nudge in §1.3 is used, fire it once here.

Current: `OnboardingScreen.kt` (1025 lines) with 26 test tags and 2 unit + 2 UI tests. Preserve the tag vocabulary.

## 4. Today

The screen users open most. It answers two questions in under three seconds: **where am I in my cycle**, and **what should I do now**.

### 4.1 Layout, in order

1. **Top app bar** — "Today" + the actual calendar date + Settings action + reminder badge.
2. **Cycle status hero** — the single most important element.
   - `CycleRing` with `displayLarge` day count.
   - Status line from the prediction (`11`).
   - Phase chip using the semantic tokens (`14` §6), always with an icon.
   - **The hero gradient appears only when the current day is a confirmed period day.** On any other day it is a plain `SectionCard`. A gradient that implies "period day" without one is a false signal.
3. **Range or reason** — if a window exists: "Estimated Oct 24–27". If `INSUFFICIENT_HISTORY`: "Not enough history to estimate a range yet" plus a prompt to log. If stale: a prompt to log the last start. If `IMPOSSIBLE_INTERVAL`: the gap question from `11` §5, with an edit action.
4. **Primary action** — one only. "Log today" normally; "Record period start" when no period is active; "Log period end" when one is.
5. **Secondary** — today's log summary (opens `log` for that date), reminders status if disabled, "How is this estimated?" disclosure.
6. **Trend strip** — at most three compact metrics, each with a plain-language label. No more (`03` §5.2).
7. **Native ad** (see §11).

### 4.2 Rules

- Never show prediction metrics, fertility estimates, cycle-health scores and promotions at equal visual weight.
- Never imply a cycle-health score. No "unhealthy" language ever (`05` §5).
- The estimate disclosure reveals algorithm version and contributing records.
- Cycle day continues past the expected length, bounded by `overdueDays` phrasing rather than an unbounded number (`11` §4.8).

Current: `HomeScreen.kt`, 2055 lines, 3 routes, 29 test tags. **Split per §12.**

## 5. Calendar

- Month/year header with month navigation; content description announces the month on change.
- 7-column grid, 48dp minimum cells, states per `14` §6.
- A persistent legend is mandatory — the states are distinguished by fill, border style and icon, and the legend teaches that mapping.
- Selecting a date opens a detail panel with that day's logs, an edit action, and for a period date, which estimates change if edited.
- Editing a period start states explicitly: "This will move your next period estimate to Oct 26–29." (`03` §5.3).
- Never suggest a fertile window is a guaranteed state.
- On expanded width, two-pane: grid plus detail.

Current: `CalendarScreen.kt`, 1342 lines, 14 test tags, 2 unit + 6 UI tests. Preserve tags.

## 6. Log

Optimised for one-handed, one-thumb use in under five seconds (`14` §2 principle 11).

### 6.1 Flow

- Date context is fixed at the top and changeable via `DateStepper` — never a free-text field.
- **Period start/end is separate from symptom logging.** The user never faces a full daily questionnaire.
- Quick-pick grid of symptoms; selection is multi-select, each with `radiusFull` chip.
- Mood, energy, pain and discharge each offer "Not tracked" as an explicit, equal-weight choice (`09` FR-3). This is a schema change: `Mood` and `EnergyLevel` have no `None` variant today, so users are currently forced to answer (`02` delta).
- Medication and notes are optional, clearly marked private, and never passed to any SDK (`04` §8).
- **Draft preservation** — unsaved input survives benign navigation and process death. Never silently discarded (`03` §5.4).
- Undo for any entry, surviving navigation.
- Save shows persistent confirmation with an Undo affordance.

### 6.2 Rules

- Blank is a valid state and is stored as such. It must never be coerced to a negative value (`05` §1).
- Guardrail breaches (a 300-day gap, an end before a start) prompt a correction dialog, never a silent rejection (`11` §4.2).
- No interstitial may appear on this screen (§11.2).

Current: `LoggingScreen.kt`, 1224 lines, 11 test tags, 3 unit + 2 UI tests, plus saved-state tests.

## 7. Insights

- Descriptive before interpretive: "You logged cramps on 4 of the last 6 recorded period days" — not "cramps are caused by…".
- **Sample size and time range are always shown.** No percentage without a denominator.
- Sparse data yields "Not enough logged history yet" — never an invented insight.
- Correlation is never presented as causation.
- Every insight has a "Why am I seeing this?" path exposing the underlying counts (`05` §6).
- Users can disable insight categories.
- No single "health score" (`03` §5.5).

All statistics must derive from the **shared interval extractor** (`11` §7) so Insights cannot contradict Today. This is a correctness requirement, not a style one.

Current: `InsightsScreen.kt`, 1117 lines, 3 routes, 13 test tags, 1 unit + 2 UI tests. `InsightsViewModel` is 7 lines of pass-through; it needs real mapping logic for the new estimate shape.

## 8. Settings and its children

### 8.1 Settings (root)

| Section | Contents |
|---|---|
| Tracking | Goal, age band, cycle assumptions, units, locale |
| Reminders | Link to `settings/reminders` |
| Privacy and data | Link to `settings/privacy` — **export and delete live here, not nested deeper** |
| Display | Theme, text size help, reduced motion note |
| Automatic decisions | Learned send time, self-disabled reminders — each explained and reversible (`13` §9) |
| About | Version, prediction explanation, help, disclaimers, privacy policy |

### 8.2 Reminders

Per `13` §14: per-reminder toggle, **lead-time stepper**, **send-time picker** (currently impossible — `SettingsScreen.kt:115-133` is read-only), lock-screen preview toggle, quiet hours, delivery tier status with an exact-alarm permission request, and a self-disabled section showing dismissal statistics with re-enable.

Stated plainly in-app: reminders are approximate, and exact timing depends on an optional system permission.

### 8.3 Privacy and data

Data summary (what is stored, locally); export; **delete local data**; app lock; lock-screen preview; sync (deferred, §10); and the privacy policy link.

"Delete local data" and any future "delete cloud data" remain separate, unambiguous, individually confirmed actions, each reporting real completion or failure (`04` §9).

### 8.4 Export

Format selection, a **preview of what will be included**, destination via the system share sheet, then confirmation of where the file went. Explain that once shared, the app cannot control the file (`04` §7).

Current: `ExportScreen.kt`, 223 lines, **no tests** despite writing files to disk.

### 8.5 App lock

`AppLockGate.kt` (269 lines) currently calls `privacyRepository` and `appResetRepository` **from composable scope** and performs a destructive `resetAllLocalData()` there. All of this must move into a ViewModel; composables must not mutate.

PIN entry moves to the standard numeric keypad with proper semantics. Failed-attempt cooldown is retained (5-strike logic is tested) and the lock screen must not leak whether a PIN exists. `SecureAuthController.kt` (273 lines) currently has **no tests at all** despite being the security-critical path — this is the highest-priority test gap in the codebase.

## 9. Reminder Centre

Currently `ReminderCenterScreen.kt` (273 lines) living in `:app`. Move to `:feature:settings` or a dedicated module — it is a feature screen inside the shell module.

Shows delivery history, unread state, per-type statistics, and quick enable/disable. Unread badge reflects actionable items only.

## 10. Sync surfaces

Deferred. No sync UI ships until `17` is implemented and tested. Until then, Settings shows no sync section rather than a non-functional one.

## 11. Ad placement

Ads remain in the product by decision. This section exists so that placement is **designed rather than incidental** — the current placement is incidental.

### 11.1 Confirmed current placement

- `AdBanner` at `CycleCareApp.kt:508` — **above the bottom navigation, therefore on every tab** including Settings and the logging flow.
- `NativeAdvancedAd` at `:592` — Home only.
- `InterstitialAdManager` — 60-second cooldown, no context gating.

### 11.2 Target placement rules

| Surface | Rule |
|---|---|
| Banner | **Today and Insights only.** Never Calendar (it would shift the grid), never Log, never Settings, never any detail screen |
| Native | Today only, below the primary card, above the banner |
| Interstitial | See §11.3 |

**Never place an ad:**

- On the logging screen, or during an unsaved draft.
- Adjacent to insight content or fertility detail being read.
- On the first launch, or in the first session.
- Immediately after the user saves a period record.
- Within `POST_LOG_GRACE_MINUTES` (30) of any period or symptom log.
- Immediately after a notification tap.
- On any screen showing an error, permission prompt or destructive confirmation.

This is partly a Play policy requirement and partly the obvious product judgement: interrupting someone immediately after they log a period is the single worst moment in this app to show an advertisement.

### 11.3 Interstitial gating

Interstitials are the format most likely to damage retention and most likely to draw review attention. Conditions — **all** must hold:

1. User has completed onboarding.
2. At least `MIN_SESSIONS_BEFORE_INTERSTITIAL` (3) sessions completed.
3. At least `MIN_MINUTES_BETWEEN_INTERSTITIALS` (10) since the last one — replacing the current 60-second cooldown.
4. No unsaved draft anywhere in the app.
5. No reminder delivered within the last 30 minutes.
6. The current screen is not in the forbidden list.
7. The user has not disabled ads via consent.
8. The daily interstitial cap is not exceeded.

If any condition fails, the interstitial is **dropped, not deferred**. Deferring an interstitial to the next screen is exactly the interruption pattern that produces uninstalls.

### 11.4 Rendering requirements

- UMP consent is resolved before any ad request. `canRequestAds` gates every surface (already correct).
- Reserve the ad's height from first composition — **no layout shift** when it loads or fails.
- On load failure the container collapses and content reflows once; it must not leave a permanent gap (current behaviour returns early, which is right).
- Ad containers use the app's radius tokens (`14` §5.2). The hardcoded 28/22/14dp radii in `Ads.kt:241,298,274` must become tokens.
- The "Ad" attribution label is mandatory on native ads (already present at `Ads.kt:265`).
- Ads must be visually distinguishable from app content and must not adopt app card styling so closely that they read as native UI.
- Ads are excluded from the notification frequency budget in `13` §9 but count toward the interstitial cap.

### 11.5 Honesty requirement

Every ad-supported surface and the store listing must state that the app is ad-supported. The privacy posture is specified in `16`; the ad SDK behaviour is verified in the code — all three request paths use a bare `AdRequest.Builder().build()` with **no targeting parameters**, so no cycle data, symptom, note or profile value is ever passed to Google.

That is a genuinely strong property and it must be stated plainly rather than buried. It does **not** mean the app is tracking-free, and the policy must not imply otherwise.

## 12. File decomposition

`HomeScreen.kt` at 2055 lines, `CalendarScreen.kt` at 1342 and `LoggingScreen.kt` at 1224 each hold an entire screen in one file with no per-screen convention. This is unmaintainable and blocks review.

| Module | Current | Target split |
|---|---|---|
| `feature:home` | `HomeScreen.kt` 2055 | `HomeRoute.kt`, `HomeScreen.kt`, `HomeHero.kt`, `HomeMetricGrid.kt`, `HomeTrendStrip.kt`, `HomeEstimateDisclosure.kt`, `HomeCycleStatusScreen.kt`, `HomeFertilityDetailScreen.kt`, `HomePreviews.kt` |
| `feature:calendar` | `CalendarScreen.kt` 1342 | `CalendarRoute.kt`, `CalendarScreen.kt`, `CalendarGrid.kt`, `CalendarDayCell.kt`, `CalendarLegend.kt`, `DateDetailSheet.kt`, `CalendarPreviews.kt` |
| `feature:logging` | `LoggingScreen.kt` 1224 | `LoggingRoute.kt`, `LoggingScreen.kt`, `PeriodEntry.kt`, `SymptomPicker.kt`, `DailyAttributes.kt`, `LoggingPreviews.kt` |
| `feature:insights` | `InsightsScreen.kt` 1117 | `InsightsRoute.kt`, `Overview.kt`, `CycleTrendsScreen.kt`, `SymptomPatternsScreen.kt`, `InsightCard.kt`, `InsightsPreviews.kt` |
| `feature:settings` | `SettingsScreen.kt` 837 | `SettingsRoute.kt`, `SettingsScreen.kt`, `RemindersScreen.kt`, `AutomaticDecisionsScreen.kt`, `SettingsPreviews.kt` |
| `feature:privacy` | 3 files, 1102 | `PrivacyRoute.kt`, `AppLockScreen.kt`, `PinKeypad.kt`, `DeleteDataScreen.kt`, `SecureAuthController.kt` |
| `app` | `ReminderCenterScreen.kt` 273 | Move to `feature:settings` |

`CalendarDayCell.kt` and `PinKeypad.kt` become reusable, separately tested components in `:core:designsystem` where appropriate.

## 13. Typography migration

Per `14` §4.1, `headlineSmall` 28sp (9 usages) becomes `headlineLarge`, and `titleMedium` 18sp (17 usages) becomes `titleLarge`. Call sites must be **reviewed individually**, not substituted blindly:

- `feature/home` — 29 test tags; hero and metrics need `displayLarge`/`displayMedium`.
- `feature/onboarding` — 26 test tags; step headings use `headlineMedium`.
- `feature/calendar` — 14 tags; day numbers and month header.
- `feature/insights` — 13 tags; insight titles and narrative.
- `feature/logging` — 11 tags; section headers.
- `app/CycleCareApp.kt` — nav labels and app-bar titles; fix icon descriptions at the same time.

## 14. Acceptance criteria

1. Four bottom destinations; Settings reachable from every tab's app bar; deep links preserved.
2. `privacy`, `export` and `reminders` reachable in two taps from anywhere, with the first-run nudge.
3. No ad on Calendar, Log, Settings or any detail screen.
4. No interstitial within 30 minutes of a log, or in the first three sessions.
5. No layout shift when ads load or fail.
6. Every screen in `03` §6 has a designed, tested state.
7. Every screen works offline with no network dependency for local content.
8. Every screen passes at 200% font scale, in dark mode, in RTL, and under TalkBack.
9. No hardcoded user-facing strings remain; all in `strings.xml`.
10. Confirmed versus estimated states are distinguishable without colour.
11. No screen shows two competing primary actions.
12. No composable mutates state directly — the `AppLockGate` and `CycleCareApp.kt:147-160` violations are resolved.
13. Every existing test tag is preserved or deliberately migrated, and `CycleCareAppStateNavigationTest` is rewritten for four tabs.
14. No file exceeds ~600 lines.
15. `SecureAuthController` and export have test coverage.