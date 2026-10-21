# 25 — The delivery diagnostic log: privacy audit

**Scope.** The diagnostics the reminder delivery path writes: the attributes
it logs, the buckets it substitutes for an instant, and the guard that
keeps an instant out of them.

**Rule.** `04` §2 lists the operational diagnostics the app may keep as
"error category, app version, coarse performance timing", and adds the
constraint that matters here: *"no record values, dates, notes or
identifiers tied to health data"*. `Diagnostics.kt:87` states the same
rule in the codebase's own words: *"diagnostics must never carry period
dates, symptoms, notes, medication or free text"*.

**Status of P9-08.** The ledger entry (`22-phase-completion-plan.md:830`
and `:962`, `20-release-readiness.md:76`) says the delivery log still
carries `scheduledAtEpochMillis` at `ReminderDelivery.kt:221`. That was
true of the tree the ledger was written against and it is no longer true:
commit `0fdc9c8` ("Wire the notification intelligence into the delivery
path", merged as `6a82cd2`) removed the attribute and replaced it with
the lead in days. The line numbers in the ledger are stale, not the
finding — at `ReminderDelivery.kt:221` today there is a call that
*persists* a deferred candidate, not a log.

The entry is still worth closing, for two reasons. The ledger does not
say where the fix landed, so the next reader has to find it. And the
second derived-date site the original entry did not mention — the
lateness figure — was still a measurement rather than a bucket. Both are
addressed here.

## 1. The instant is out of the log

The historical defect was the attribute:

```kotlin
"scheduledAtEpochMillis" to scheduledAtEpochMillis.toString(),
```

For a `PeriodPrep` reminder the scheduled instant is the predicted period
date minus the lead time, so the attribute was a derived cycle date.

The substitution is the **lead in days** — `leadDays`, the number of days
before the predicted date the reminder was planned. The alternative the
ledger also offers is the scheduled hour of day, and it is the weaker of
the two:

| Bucket | What it is | Why it cannot be inverted |
|---|---|---|
| **Lead in days** (chosen) | A length between the reminder and the predicted date. It is the user's own configuration: how many days ahead they asked to be reminded. | A length has no anchor. It says how far apart two things are, not when either of them was, so it cannot place either on a calendar and cannot be combined with anything in the log to do so. |
| Scheduled hour of day | The hour the reminder was scheduled for. | A reminder is delivered within a bounded window of its scheduled instant, so the hour of day plus the delivery hour places the scheduled *day*. It narrows rather than removes. It is also close to redundant: the delivery happens at the scheduled hour, so the log line's own time already says it. |

The same reasoning applies to `deferUntilHour`, which is already in the
log. The deferral end is computed from the moment of the attempt
(`quietHoursEnd(now)`, `now + DND_RETRY_MINUTES`, `now +
ARBITRATION_WINDOW_MINUTES`), not from the scheduled instant, so it is a
bucket of the delivery moment and not a handle on the cycle date.

## 2. The lateness figure was a subtraction, and is now a class

The second derived-date site was in the success log:

```kotlin
"deliveryDelayMinutes" to maxOf(
    0L,
    (deliveredAtEpochMillis - scheduledAtEpochMillis) / MILLIS_PER_MINUTE,
),
```

**Judgement: it leaks, weakly but really.** The diagnostic is written at
the moment of delivery, so the log line carries the delivery instant.
The attribute is the difference between that instant and the scheduled
one. Subtract it and the scheduled instant comes back — and for a
`PeriodPrep` or `LatePeriod` reminder the scheduled instant is the
predicted period date minus the lead time, so with `leadDays` also in the
log the period date comes back with it.

The leak is marginal in the common case, where the delivery is on time
and the log line's own time is already the scheduled time. It is not
marginal in the case the attribute exists to describe: a delivery that
was late. There the exact figure is what recovers the scheduled instant
exactly, and it can recover a *different day* from the one the log line
timestamps.

So the figure is gone and a class replaced it:

```
deliveryLatency = OnTime | Late
```

`OnTime` is within five minutes of the scheduled instant; `Late` is
beyond it, with no upper bound. A class cannot be subtracted, so the log
says *that* a delivery was late without saying by how much. The
diagnostic value survives — on time, or not, is the question the Doze
demonstration asks — and the fine figure is exactly the part that could
be inverted, so it is the part that goes.

This is "coarse performance timing" in the sense `04` §2 allows, and it
is a logging change only: the scoring gates, the budgets, the
auto-disable decision, the group summary, the redaction, the quiet-hours
and DND behaviour and the retry de-duplication are untouched.

## 3. Every attribute the delivery path logs

| Event | Attributes | Verdict |
|---|---|---|
| `delivery_routed_in_app` | `ruleId`, `type` | Kept. An identifier and an enum. |
| `delivery_skipped_channel_disabled` | `ruleId`, `type`, `channelKey` | Kept. Identifiers and enums. |
| `delivery_skipped_notifications_unavailable` | `ruleId`, `type`, `channelKey` | Kept. Identifiers and enums. |
| `delivery_skipped_missing_channel_settings` | `ruleId`, `type`, `channelKey` | Kept. Identifiers and enums. |
| `delivery_suppressed` | `ruleId`, `type`, `reason` | Kept. The reason is from a closed set the domain owns (`below the delivery threshold`, `daily budget exhausted`, `below the pinned-time threshold`, `weekly budget exhausted`, `type auto-disabled`, `below threshold`); no free text, no number, no date. |
| `delivery_deferred` | `ruleId`, `type`, `reason`, `deferredCount`, `deferUntilHour` | Kept. The reason is from the closed set (`quiet hours`, `do not disturb`, `arbitration`); the count is a count; the hour is a bucket of twenty-four anchored to the attempt, not to the scheduled instant. |
| `delivery_success` | `ruleId`, `type`, `channelKey`, `leadDays`, `deliveryLatency`, `score`, `threshold` | Changed. `leadDays` is the chosen bucket; `deliveryLatency` replaces the exact delay; the score and the threshold are the scorer's own numbers. |
| `delivery_deduplicated` | `ruleId`, `type` | Kept. Identifiers and enums. |
| `delivery_failed` | `ruleId`, `type`, `channelKey`, plus the throwable | Kept, with a caveat below. |
| `reminder_type_auto_disabled` | `type`, `impressions`, `dismissed`, `opened` | Kept. Counts. The auto-disable *reason* is deliberately not logged: it interpolates the thresholds (`"only 3 impressions, below the 10 minimum"`), and the counts already say it. |
| `auto_disable_failed` | `type`, plus the throwable | Kept, with a caveat below. |
| `refresh_after_delivery_failed` | `ruleId`, `outcome`, plus the throwable | Kept. `outcome` is from a closed set of call-site strings. |
| `reminder_type_re_enabled` / `..._failed` | `type` | Kept. An enum. |
| `deferred_summary_held_for_dnd` | `count` | Kept. A count. |
| `deferred_summary_posted` | `count`, `types` | Kept. A count and a list of enum names. |
| `schedules_cleared` / `schedules_refreshed` | `reason`, `count`, `zone`, `exactAlarms` | Kept. `zone` is a zone identifier, which the ledger already classes among the attributes the logger may carry (`22-phase-completion-plan.md:830`); it is a location hint, not a cycle date, and it names no moment. |
| `reminder_scheduled` / `exact_alarm_denied` | `ruleId`, `tier` | Kept. An identifier and a closed set of two. |
| `channel_sync_failed` / `sound_preview_*` | `preset`, `what`, `extra` | Kept. An enum and two platform error codes. |

### The caveat: throwables

Three call sites log a throwable: the failed post, the failed auto-disable
and the failed refresh. `Diagnostics.kt` sanitizes *attribute keys* and
leaves the throwable alone, so an exception message that carried a date
would reach the log. The app does not compose those messages — they come
from the platform and from Room — so the exposure is second-hand, but it
is not zero: `ReminderEvent.id` is `"$ruleId-$scheduledAtEpochMillis"`,
so an exception that quoted the row it was inserting would quote the
instant with it.

This is **not fixed here**, and the reason is scope rather than
oversight. Sanitising an exception message means changing
`Diagnostics.kt`, which is the logging infrastructure for the whole app
— the navigation, the ads, the application start — and not only for the
delivery path. It is also a change to what a crash report contains, which
is a decision for the owner of the crash reporter rather than for the
delivery workstream. The recommendation is to redact date-shaped
substrings from exception messages centrally, before they reach the crash
reporter, and to keep it as a separate change with its own review.

## 4. The guard

`ReminderDeliveryLogTest` asks the log's builders for their maps and
checks the values. It cannot see a call site that builds its own map, or
one that passes an instant into a builder, so it would not have failed on
the original defect.

`ReminderDeliveryLoggingSourceTest` reads the delivery path's sources,
finds the actual logging call sites, and checks four things about them:

1. **No instant is passed to a log.** The attributes of every call site
   are scanned for the identifiers that carry an instant — the scheduled
   and delivered instants, the epoch conversions, the date and time
   types — and for the calls that turn a time into a string.
2. **Every attribute name is on a reviewed list.** The list is in the
   test, so a new attribute is a deliberate act rather than an accident.
3. **No attribute is named like a timestamp**, and none is named like a
   fine-grained duration.
4. **The buckets are coarse**: the lateness is a class and not a
   number, the deferral hour is in `0..23`.

The same test checks the other side of the rule, because a guard that
banned the word would have had to delete the app's own records: the
scheduled instant is still persisted in the event identity, the event
record, the database column, the alarm payload and the deferral store,
and the test asserts that every one of those is still there while none of
them reaches a log call site. The fix for a leaked instant is to move it
out of the log, not out of the app.

The guard was checked against the defect it is meant to catch. Reinstating
the historical attribute fails four of its tests, and reinstating the
exact delay fails three more.

## 5. What was deliberately not changed

- **The event record.** `scheduledAtEpochMillis` is the event identity
  and part of the event record. The brief is explicit, and it is right:
  the record is the app's own data, kept on the device, and it is the log
  that must not carry it.
- **The gate stack.** The scoring gates, the budgets, the auto-disable
  decision, the group summary, the redaction, the quiet-hours and DND
  behaviour and the retry de-duplication are untouched. `leadDays` was
  already logged and is still logged with the same value.
- **The deferral store.** The deferred candidate keeps its scheduled
  instant, because it has to be delivered at its own time.
- **The crash reporter.** No crash data leaves the device, and the
  throwable caveat above is left for a separate change.
