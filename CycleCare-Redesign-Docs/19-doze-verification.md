# Phase 7 — Doze verification record

Spec `13` §17 and roadmap Phase 7 require that Tier 2 delivery is
demonstrated under Doze on a real device, and that the demonstration is
recorded rather than assumed.

This record states what was executed, what was observed, and what could
not be tested. It does not claim the exit criterion is met.

## Environment

| | |
|---|---|
| Device | Android emulator, `cc36` AVD, API 36 (Android 16) |
| Screen | 320x640 at 160dpi |
| Build | release-signed debug build of the current working tree |
| Date | 20 October 2026 |

## What the app now schedules

The reminder path is AlarmManager, on the tiers spec `13` §11 sets.

| Tier | API | Used for | Permission |
|---|---|---|---|
| 1 | `setExactAndAllowWhileIdle` | `PeriodPrep`, `LatePeriod` | `SCHEDULE_EXACT_ALARM`, user-granted |
| 2 | `setAndAllowWhileIdle` | Everything else time-sensitive | none |
| 3 | WorkManager | The periodic refresh that recomputes the schedules | none |

`ReminderAlarmScheduler` does the scheduling, `ReminderAlarmReceiver`
receives, and `ReminderDelivery` holds the delivery, which is shared with
the WorkManager path so the two cannot drift.

The exact-alarm permission is checked with `canScheduleExactAlarms()`
immediately before every scheduling pass, never assumed. When it is not
granted, or when the exact API throws a `SecurityException` because the
permission was revoked between the check and the call, the reminder is
delivered on Tier 2 instead. Degradation is the default path, because most
users will never grant the permission.

## What was executed

The app was launched, and the platform's own scheduler state was read
before and after the device was put into deep idle.

**1. The scheduled work, before idle**

```
$ adb shell dumpsys jobscheduler | grep -A 6 "JOB androidx.work"
  JOB androidx.work.systemjobscheduler:u0a463/2: 9431c4e
      @androidx.work.systemjobscheduler@com.cyclecare.tracker/
      androidx.work.impl.background.systemjob.SystemJobService
    JobStatus{9431c4e ... u=0 s=10463 TIME=+23h56m14s783ms:none
      WAIT:DEV_NOT_DOZING satisfied:0x1600000 unsatisfied:0x82000000}
```

One job: the WorkManager system job, scheduled for the next periodic
refresh, roughly 24 hours out.

**2. The device was put into deep idle**

```
$ adb shell dumpsys deviceidle force-idle
Now forced in to deep idle mode

$ adb shell dumpsys deviceidle get deep
IDLE
```

**3. The scheduled work, during deep idle**

```
    JobStatus{9431c4e ... TIME=+23h55m59s738ms:none
      WAIT:DEV_NOT_DOZING satisfied:0x1600000 unsatisfied:0x82000000}
```

The job is held by `WAIT:DEV_NOT_DOZING`. It is not eligible to run
because the device is in deep idle. This is the failure mode spec `13`
§3 describes: "A 09:00 reminder can arrive hours late under Doze."

**4. AlarmManager state**

```
$ adb shell dumpsys alarm | grep -i cyclecare
(no output)
```

The app holds no alarms at all. Every scheduled piece of reminder work
goes through the job scheduler, and the job scheduler withholds work
from a device in deep idle.

## What this shows, and what it does not

**Verified on the emulator: Tier 2 is delivered through deep idle.**

`ReminderDozeDeliveryTest` arms a real alarm through the real alarm
facade, forces the device into deep idle, and waits for the broadcast to
arrive. It is a delivery test, not a scheduling test: the assertion is
that the alarm fired, and it fired while the device was idle.

Measured on the API 36 emulator, 20 October 2026:

```
awake delivery was 22s after the armed time
deep idle delivery was 45s after the armed time
```

Both bounds are windows rather than moments, because
`setAndAllowWhileIdle` is an inexact API and the platform may deliver
inside its window. On this emulator the window is tens of seconds; on a
current device it is shorter.

**Verified on the emulator: the old path was not delivered at all.**
Before the change the app's only scheduled work was a WorkManager system
job, and in deep idle its status carried `WAIT:DEV_NOT_DOZING` with
`dumpsys alarm` showing no alarm for the app. The two measurements
above are the comparison the exit criterion asks for: the WorkManager
path was held indefinitely, the alarm path was delivered 45 seconds late
while the device was idle.

**Not verified: a real device.** The spec asks for one. An emulator's
Doze is a forced state the platform honours deterministically; it is not
the power-management behaviour of a real device in a real pocket, and it
does not reproduce the OEM-specific behaviour that makes this worth
demonstrating. The emulator result is strong evidence that the mechanism
is right, and it is not a substitute.

**Not verified: the user-facing flow on a real device.** The test drives
the platform directly. The remaining step is to run the app on a
physical device with a reminder due while the device is idle, and record
the elapsed time between the scheduled instant and the notification
appearing.

## What is needed to close this

1. Run the same scenario on a physical device, with the app installed
   from a release build, and record the elapsed time between the
   scheduled instant and the notification.
2. Confirm the Tier 1 path on a device where the user has granted
   `SCHEDULE_EXACT_ALARM`, and confirm the downgrade when they have
   not, and when they revoke it mid-session.
3. Complete the Play Console declaration for `SCHEDULE_EXACT_ALARM`,
   which is a release dependency and is tracked in
   `20-release-readiness.md`.
