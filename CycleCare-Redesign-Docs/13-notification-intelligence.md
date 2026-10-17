# 13 — Notification Intelligence and Delivery Specification

**Status:** Implementation contract. Replaces the delivery and scheduling design in `01-system-architecture.md` §7 and `docs/system-design.md` §5.
**Depends on:** `11` (uncertainty-aware timing), `03` (notification content principles), `05` (health-safety copy), `07` (exact-alarm policy).

## 1. Problem with the current implementation

Verified defects in the existing notification path, all of which this specification replaces:

| Defect | Evidence |
|---|---|
| WorkManager only, no AlarmManager anywhere | No `AlarmManager` usage exists in the project. A 09:00 reminder can arrive hours late under Doze |
| Lead time and send time cannot be edited by the user | `SettingsScreen.kt:115-133` renders `daysBefore`/`hourOfDay` read-only. Only `toggleReminderRule(rule, enabled)` exists |
| Up to 5 notifications/day with no cap | No frequency limit of any kind exists |
| No quiet hours, no DND awareness | No `getCurrentInterruptionFilter` usage |
| No batching implementation | `setGroup()` is set at `ReminderDeliveryWorker.kt:177` but **no group summary is ever built**, so Android renders an orphan group |
| Non-idempotent delivery | `recordEvent` is called at line 56 with no existence check, so a `Result.retry()` re-posts the same notification |
| Lock-screen redaction is incomplete | `VISIBILITY_PRIVATE` is set, but `setPublicVersion()` is never called, so content can still surface on some lock-screen configurations |
| Single hardcoded redaction string | `"Cycle reminder available. Open the app to review it."` for all five types (`ReminderSchedulePlanner.kt:66,89,113,133,163`) |
| `Hydration` and `Pill` are dead enum stubs | `ReminderSchedulePlanner.kt:35-37` returns `null`; no seed rule, no test, filtered out of Settings |
| Late-period detection is fictional | `LatePeriod` is a fixed `+3` day offset from a prediction, not a detector of a period actually being late |
| No snooze, no notification actions | No `addAction` anywhere |

## 2. Design principle

**A notification is a cost imposed on the user. It must earn its place.**

The existing model sends a reminder whenever a date arrives. The model here asks, for every candidate: *is this worth interrupting this person right now?* Most candidates should not win. Silence is the default; sending is the exception.

This aligns with `03` §2.5 (no pressure, no guilt) and is also the single highest-leverage lever for retention and for Play review scores — apps that over-notify get uninstalled.

## 3. Pipeline

```
prediction (11) + rules (13.4) + engagement history (13.7)
        │
        ▼
  ① candidate generation      13.5
        │
        ▼
  ② relevance scoring         13.6      ← the algorithm
        │
        ▼
  ③ budget & fatigue filter   13.8
        │
        ▼
  ④ arbitration & batching   13.9
        │
        ▼
  ⑤ timing resolution         13.10     ← window-aware + quiet hours + learned time
        │
        ▼
  ⑥ tier selection & schedule 13.11     ← exact alarm / inexact alarm / WorkManager
        │
        ▼
  ⑦ delivery (idempotent)     13.12
        │
        ▼
  ⑧ feedback capture          13.7
```

Stages 1–5 are **pure functions** in `:core:domain`, fully unit-testable with an injected clock. Only stage 6 touches Android scheduling APIs.

## 4. Reminder types (MVP)

| Type | Purpose | Default | Notes |
|---|---|---|---|
| `PeriodPrep` | Period likely to start soon | **on**, 2 days lead | Core use case. Highest urgency |
| `LatePeriod` | Expected window has passed with no logged start | **on** | Must become a real lateness detector (§4.1), not a fixed offset |
| `DailyLog` | Optional daily check-in nudge | **on**, 20:00 | Low urgency; first thing to be suppressed under budget pressure |
| `FertileWindowStart` | Estimated fertile window opens | **off** | Only enabled for `ConceptionAwareness` goals |
| `OvulationEstimate` | Estimated ovulation date | **off** | Only enabled for `ConceptionAwareness` goals |

### 4.1 Removing `Hydration` and `Pill`

**Both are removed from the MVP enum.** They are dead stubs today, and neither should be added back without justification:

- `Hydration` is a recurring nag pattern. It conflicts directly with the fatigue model in §3.8 and with `03` §2.5.
- `Pill` is a genuine need, but a pill reminder is only meaningful alongside medication logging, which CycleCare does not have. A reminder to take an unspecified pill is worse than no reminder.

Removing them also resolves the dead mapping in `reminderTypeChannelKey` (`ReminderSchedulePlanner.kt:180-181`).

### 4.2 Late-period becomes a real detector

Currently `LatePeriod` fires at `nextPeriodDate + 3` regardless of state. It must instead fire only when the user's behaviour indicates lateness:

```kotlin
val latePeriodCandidate = prediction.nextPeriodWindow != null &&
    today > prediction.nextPeriodDate.plusDays(LATE_PERIOD_GRACE_DAYS) &&
    observedRecords.none { it.startDate > prediction.anchorDate }
```

| Constant | Value | Rationale |
|---|---|---|
| `LATE_PERIOD_GRACE_DAYS` | 3 | Give the estimate's own uncertainty room before calling something late. If `halfWidthDays` is 5, escalate at `date + 5 + 3`, not `date + 3` |

If the user logs a start, the candidate disappears immediately. Currently the planner must be relied upon to suppress it.

## 5. Candidate generation

```kotlin
data class ReminderCandidate(
    val type: ReminderType,
    val ruleId: String,
    val targetEpochDay: Long?,
    val scheduledForEpochMillis: Long,
    val urgencyBase: Int,
    val windowHalfWidthDays: Int,     // from 11; 0 when no window
    val channelKey: ReminderChannelKey,
    val deepLink: ReminderDeepLinkTarget,
)
```

Generation is driven entirely by the prediction window (§6), never by a bare date.

## 6. Uncertainty-aware timing — the core change

A reminder scheduled at `nextPeriodDate − leadDays` asserts a precision the app does not have. If `11` says the next period falls between Oct 24 and Oct 29, then "Your period starts in 2 days" sent on Oct 22 may be four days early.

**Rule: anchor reminders to the earliest plausible start, not the centre.**

```kotlin
val earliestPlausibleStart = prediction.nextPeriodDate
    .minusDays(prediction.nextPeriodWindow?.halfWidthDays ?: 0)

val scheduledDate = earliestPlausibleStart.minusDays(rule.leadDays)
```

Copy is then written against the range, never against a false point estimate:

| Situation | Copy |
|---|---|
| ±1 day | "Your period may start tomorrow." |
| ±3 days | "Your period is estimated around Oct 24–26." |
| ±7 days (capped) | "Your period is likely sometime next week." |
| No window (`INSUFFICIENT_HISTORY`) | **No period reminder at all.** Send a `DailyLog` nudge instead |

The last row matters: with fewer than 3 intervals there is nothing worth interrupting anyone about. The current code has no such guard.

## 7. Relevance scoring

The algorithm. Every candidate receives a score; only those above `SEND_THRESHOLD` are delivered.

```kotlin
data class RelevanceInput(
    val candidate: ReminderCandidate,
    val goal: TrackingGoal,
    val quality: EstimateQuality,          // from 11
    val hoursSinceLastSend: Long,
    val sendCount24h: Int,
    val sendCount7d: Int,
    val dismissRateForType: Float,         // 0..1, lifetime
    val openRateForType: Float,            // 0..1, lifetime
    val userPinnedTime: Boolean,           // user explicitly chose a time
    val now: LocalDateTime,
)

fun score(input: RelevanceInput): Int =
      input.candidate.urgencyBase
    + leadFitBonus(input.candidate)
    + goalAlignmentBonus(input.candidate.type, input.goal)
    + qualityBonus(input.quality)
    + learnedTimeBonus(input)
    - recencyPenalty(input.hoursSinceLastSend)
    - fatiguePenalty(input.dismissRateForType, input.openRateForType)
    - budgetPressurePenalty(input.sendCount24h, input.sendCount7d)
```

### 7.1 Term definitions

**`urgencyBase`** — inherent time-sensitivity:

| Type | Value | Rationale |
|---|---|---|
| `PeriodPrep` | 50 | The app's core reason to exist |
| `LatePeriod` | 45 | Actionable, but must not become nagging |
| `FertileWindowStart` | 35 | Important when relevant, irrelevant otherwise |
| `OvulationEstimate` | 30 | As above |
| `DailyLog` | 20 | A nudge, never urgent |

**`leadFitBonus`** — rewards lead times suited to the type. `PeriodPrep` at 1–2 days scores +10; at 7 days, −15 (too early to act on). `DailyLog` scores 0 (lead is not meaningful). Keeps the user's lead time meaningful without forbidding it.

**`goalAlignmentBonus`** — +15 when the type matches the stated tracking goal; **−25 when it does not**. A fertility reminder for a `PeriodPrep` user is noise, and a period reminder for a `ConceptionAwareness` user is still wanted. This replaces the current bug where fertility rules are seeded once and never re-evaluated when the goal changes (`filterNot { existingIds.contains(it.id) }` at seed time).

**`qualityBonus`** — from `11` §4.10: `HIGH` +10, `MODERATE` +5, `LOW` 0, `INSUFFICIENT_HISTORY` **−100** (effectively suppressing fertility and period-prep reminders the app cannot support).

**`learnedTimeBonus`** — +8 if this candidate's scheduled time matches the user's observed engagement peak (§8). **Never applies when `userPinnedTime` is true.**

**`recencyPenalty`** — −30 if any reminder was sent in the last 2 hours; −15 within 6 hours; 0 beyond. Prevents pile-up.

**`fatiguePenalty`** — based on the type's measured dismiss/open ratio:

```kotlin
val fatigue = (1f - input.openRateForType) * 40f * (0.5f + input.dismissRateForType)
```

A type that is consistently dismissed without being opened is progressively suppressed.

**`budgetPressurePenalty`** — grows as the daily and weekly budget is consumed, forcing low-urgency candidates to lose:

| Sent today | Penalty |
|---|---|
| 0–1 | 0 |
| 2 | −10 |
| 3 (at limit) | −25 |
| ≥4 (over limit) | −60 |

### 7.2 Thresholds

| Constant | Value |
|---|---|
| `SEND_THRESHOLD` | 40 |
| `SEND_THRESHOLD_OVER_BUDGET` | 75 |
| `SEND_THRESHOLD_PINNED_TIME` | 25 |

A user who explicitly pinned a time is expressing intent; the system honours it more readily. This is the agency guarantee in §3.10.

**Tuning caution.** These weights are hand-chosen and must be reviewed against real send/open/dismiss telemetry before being tuned. Re-run the §10 test suite after any change. Do not fit these weights to a small sample (`12` §9 discipline applies here too).

## 8. Learned send time — local only

Users respond differently. Rather than forcing 09:00 or 20:00 on everyone, learn from local engagement.

**Data captured, locally, never transmitted:** hour-of-day of app opens and notification interaction. Stored in a local `engagement_profile` table holding a 24-slot histogram plus `interactionCount`. This contains **no health content** and must never enter analytics, crash reports or any SDK payload (`10` §2).

```kotlin
fun bestSendHour(profile: EngagementProfile?, rule: ReminderRule): Int {
    if (rule.userPinnedTime) return rule.hourOfDay          // explicit choice always wins
    if (profile == null || profile.interactionCount < MIN_INTERACTIONS_FOR_LEARNING) {
        return DEFAULT_SEND_HOUR                            // 9
    }
    val peak = profile.hourHistogram.maxByOrNull { it.count } ?: return DEFAULT_SEND_HOUR
    return if (peak.countInQuietHours(quietHours)) DEFAULT_SEND_HOUR else peak.hour
}
```

| Constant | Value | Rationale |
|---|---|---|
| `DEFAULT_SEND_HOUR` | 9 | Reasonable default, and outside default quiet hours |
| `MIN_INTERACTIONS_FOR_LEARNING` | 10 | Below this the histogram is noise |

**Agency rules:**

- A pinned user time is **never** overridden.
- The learned time is surfaced in Settings as a suggestion with an explicit accept/decline, not applied silently.
- Learning can be switched off entirely in Settings.
- `hideNotificationPreview` defaults to on and is independent of this.

## 9. Budget and self-disabling

| Constant | Value | Rationale |
|---|---|---|
| `MAX_SENDS_PER_DAY` | 3 | Current worst case is 5 with no cap |
| `MAX_SENDS_PER_WEEK` | 12 | |
| `AUTO_DISABLE_MIN_IMPRESSIONS` | 10 | Do not judge on a handful |
| `AUTO_DISABLE_DISMISS_RATE` | 0.60 | If 60%+ are dismissed, the reminder is not wanted |

**Self-disabling is a feature, not a failure.** When a type crosses the threshold it is disabled and the user is told once, in Settings and via a single in-app notice:

> "Period prep reminders were turned off — you dismissed 7 of the last 10. Turn them back on."

with a one-tap re-enable. Silently continuing to send is worse; silently disabling without explanation is paternalistic. Never nag about being nagged.

## 10. Quiet hours, DND, arbitration and batching

**Quiet hours** — user-configurable, default 22:00–08:00. Candidates falling inside are deferred to quiet-hours end and **coalesced** into a single summary notification rather than delivered individually.

**DND** — check `NotificationManager.getCurrentInterruptionFilter()` before posting. During `INTERRUPTION_FILTER_PRIORITY` or higher, defer non-critical candidates (everything except `LatePeriod`, which the user explicitly opted into).

**Arbitration** — when multiple candidates are due in the same window, sort by score, take the top `MAX_ARBITRATION_BATCH` (2), and fold the remainder into a summary. This is what prevents the "5 notifications on the same morning" failure.

**Batching and the group summary** — the current code sets a group key with no summary, which Android renders as an orphan group. Correct implementation:

```kotlin
val groupKey = ReminderChannels.remindersGroupKey
// each member
.setGroup(groupKey)
// plus exactly one summary
.setGroup(groupKey)
.setGroupSummary(true)
```

The summary notification is the redacted variant, so a batch never leaks detail on a lock screen.

## 11. Scheduling tiers

Play policy: **`USE_EXACT_ALARM` is not available.** It is restricted to alarm/timer apps and calendar apps with event notifications, and CycleCare qualifies for neither. The viable permission is **`SCHEDULE_EXACT_ALARM`**, which is user-granted and **denied by default** on fresh installs targeting API 33+, requires a Play Console declaration, and mandates graceful degradation.

| Tier | Mechanism | Use for | Permission |
|---|---|---|---|
| **1** | `AlarmManager.setExactAndAllowWhileIdle` | `PeriodPrep`, `LatePeriod` only | `SCHEDULE_EXACT_ALARM`, user-granted |
| **2** | `AlarmManager.setAndAllowWhileIdle` | Everything else time-sensitive | **None** — Doze-tolerant, no permission needed |
| **3** | `WorkManager` | Periodic refresh, sync, cleanup, deferrable work | None |

**Tier 2 is the important upgrade.** `setAndAllowWhileIdle` needs no permission and is far more reliable than the current `OneTimeWorkRequest.setInitialDelay` approach, which defers under Doze without bound. Using WorkManager for *time-sensitive delivery* was the core architectural error.

**Requirements for Tier 1:**

1. Declare `SCHEDULE_EXACT_ALARM`; complete the Play Console declaration.
2. Call `canScheduleExactAlarms()` immediately before scheduling; **never assume**.
3. Listen for `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` and reschedule on grant — re-check the grant, since the user may have revoked it immediately.
4. Explain the reason in-app, then invoke `ACTION_REQUEST_SCHEDULE_EXACT_ALARM`.
5. **Degradation is the default path, not an edge case.** Most users will never grant it. The app must be fully functional on Tier 2.
6. Cancellation follows permission revocation automatically; treat a `SecurityException` as a downgrade to Tier 2, never a crash.
7. `BOOT_COMPLETED` and `TIMEZONE_CHANGED` reschedule all tiers — and note WorkManager already handles its own persistence, so the receiver is belt-and-braces.

Reboot/timezone correctness requires recomputing from local-time preferences, never replaying stored UTC instants (`02` §3).

## 12. Idempotent delivery

Fixes the retry double-post. The event key is `(ruleId, type, targetEpochDay)` — stable across retries of the same logical notification.

```kotlin
// Returns true only if this delivery was newly recorded.
suspend fun recordEventIfAbsent(event: ReminderEvent): Boolean =
    dao.insertIfAbsent(event) == 1L
```

```kotlin
val isNew = container.reminderEventsRepository.recordEventIfAbsent(event)
if (!isNew) return Result.success()   // already delivered; do not re-post
container.reminderNotificationClient.post(notification)
```

The insert and the post must be ordered so that a crash between them loses the notification rather than duplicating it — at-most-once is the correct trade for reminders.

## 13. Content, privacy and actions

**Lock-screen redaction must use `setPublicVersion()`.** `VISIBILITY_PRIVATE` alone is insufficient. Build a redacted `Notification` and attach it:

```kotlin
NotificationCompat.Builder(context, channelId)
    .setContentTitle(event.title)
    .setContentText(event.body)
    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
    .setPublicVersion(redactedNotification)      // required — currently missing
    .setGroup(ReminderChannels.remindersGroupKey)
```

**Per-type redaction strings** replace today's single hardcoded message:

| Type | Lock-screen safe text |
|---|---|
| `PeriodPrep` | "CycleCare has an update for you." |
| `LatePeriod` | "CycleCare has an update for you." |
| `DailyLog` | "Ready to check in when you are." |
| `FertileWindowStart` / `OvulationEstimate` | "CycleCare has an update for you." |

**Actions** — each notification offers the next useful step:

| Action | Behaviour |
|---|---|
| **Log now** | Deep link into the logging flow, pre-seeded with the relevant date and type |
| **Snooze** | 2 hours / tomorrow morning, user choice. Persisted so it survives process death |
| **Mark period started** | Writes an observed record directly, **with undo** and a clear confirmation that it is an observation, not an estimate (`05` §1) |

Snooze requires a `PendingIntent` targeting a `SnoozeReceiver`, storing `(type, originalScheduledAt, newScheduledAt)` so a snoozed notification cannot duplicate the original.

**Notification content is readable by the user's other apps** via `NotificationListenerService`. Keep extras minimal — put no cycle dates, symptom values or notes in the notification's `extras` bundle beyond what is visibly rendered.

**Channels** keep the existing three (`CycleAlerts` HIGH, `FertilityAlerts` DEFAULT, `DailyCheckIn` LOW) with per-channel sound presets. Users must be able to disable a channel and have the app reflect that state honestly (§13.14).

## 14. UI requirements

The current Settings screen cannot edit lead time or send time. Both become user-facing, because §7 depends on it:

- **Per-reminder control**: toggle, lead-time stepper, send-time picker, lock-screen preview toggle.
- **Quiet hours**: start/end picker.
- **Delivery**: shows the resolved tier, explains if exact alarms are unavailable and offers to request the permission, and states plainly that reminders are approximate.
- **Engagement**: shows the learned send-time suggestion with accept/decline and a master switch.
- **Disabled reminders**: shows self-disabled types with their dismissal statistics and a re-enable action.

**Permission is requested in context** — after the user enables a reminder, with an explanation before the OS dialog (`03` §2.3). Never on launch. If permission is denied, the app must not re-prompt on every launch (`06` §2, "Disabled notifications do not repeatedly prompt the user").

## 15. Copy rules

| Do | Don't |
|---|---|
| "Your period may start tomorrow." | "Your period starts tomorrow." |
| "Period is estimated around Oct 24–26." | "You're 4 days late." |
| "Ready to log today's check-in?" | "You missed yesterday's log!" |
| "CycleCare has an update for you." (locked) | Any cycle detail on a locked screen |

No streak language, no guilt, no false precision, no implication of pregnancy safety (`03` §2.5, `05` §5).

## 16. Test plan

Pure stages 1–5 are unit-tested in `:core:domain` with an injected clock, JUnit 4 and Truth, matching the existing harness.

| Area | Tests |
|---|---|
| Relevance scoring | Each term in isolation; threshold boundaries; `INSUFFICIENT_HISTORY` suppression; pinned-time override |
| Uncertainty-aware timing | Fires at earliest plausible start, not centre; no reminder when `nextPeriodWindow == null`; grace period scales with `halfWidthDays` |
| Late-period detector | Fires only after grace; disappears immediately on a logged start; never fires with no window |
| Budget | Daily and weekly caps; over-budget threshold escalation; self-disable at 10 impressions / 60% dismissal; re-enable path |
| Engagement | Learned hour chosen; pinned time always wins; below 10 interactions falls back to default; quiet-hour exclusion |
| Quiet hours / DND | Deferral, coalescing, `LatePeriod` exemption |
| Arbitration / batching | Top-2 selection, remainder folded into summary, group summary actually built |
| Idempotency | Second delivery of the same `(rule, type, targetDay)` does not post; survives `Result.retry()` |
| Snooze | Persists across process death; cannot duplicate the original |
| Redaction | `setPublicVersion` present on every notification; no cycle values in `extras` |
| Tier selection | Exact alarm requested only for the two permitted types; degrades to Tier 2 without permission; `SecurityException` → downgrade, not crash |

**Instrumented** (`:app`, existing WorkManager test harness extends to alarms):
- Real `AlarmManager` registration and cancellation per tier.
- `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIMEZONE_CHANGED` rescheduling without duplicates.
- Permission granted → exact alarms scheduled; revoked → graceful downgrade.
- **Device test under Doze** to confirm Tier 2 delivery is materially better than the current WorkManager behaviour. This is the claim that justifies the change and must be demonstrated, not assumed.

**Existing tests:** the 9 `ReminderSchedulePlannerTest` cases and 7 `WorkManagerReminderSchedulerTest` cases must be rewritten against the new tiers — several assert v1 `WorkManager`-only behaviour.

## 17. Acceptance criteria

1. No notification is delivered without a score ≥ `SEND_THRESHOLD`.
2. `MAX_SENDS_PER_DAY` is never exceeded.
3. A type crossing the auto-disable threshold is disabled **and** explained, with one-tap re-enable.
4. No reminder claims a date the prediction window cannot support.
5. No reminder is generated when `nextPeriodWindow == null` and the type depends on the estimate.
6. Retrying delivery never produces a duplicate notification.
7. Every notification has a group summary and a `setPublicVersion` redaction.
8. Exact alarms are requested only for the two permitted types, and the app is fully functional without the permission.
9. User-pinned send times are never overridden.
10. No health value appears in notification `extras` beyond visible content.
11. Quiet hours and DND are honoured, with `LatePeriod` as the documented exception.
12. No guilt, streak, or false-precision copy anywhere.