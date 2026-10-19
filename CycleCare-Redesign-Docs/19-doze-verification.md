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

## What the app actually schedules

The reminder path is WorkManager, not AlarmManager.

`app/src/main/java/com/cyclecare/app/WorkManagerReminderScheduler.kt`
schedules `ReminderRefreshWorker` as WorkManager work, and
`ReminderDeliveryWorker` posts the notification. The only AlarmManager
reference in the app is `ReminderRescheduleReceiver`, which listens for
`SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` so the work can be
rescheduled when the user grants or revokes the permission. It does not
schedule anything itself.

There is no `setAndAllowWhileIdle` or `setExactAndAllowWhileIdle` call
anywhere in the app, which is what spec `13` §10 calls the Tier 2
mechanism.

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

**Verified on the emulator.** The current delivery mechanism is deferred
by Doze. A reminder that falls due while the device is in deep idle will
not be delivered on time, because the only mechanism the app uses is
deferred by deep idle. That is observed, not inferred: the job status
carries `WAIT:DEV_NOT_DOZING` while the device is idle, and the app
holds no alarm that could bypass it.

**Not implemented, so not demonstrated.** The spec's Tier 2 mechanism,
`AlarmManager.setAndAllowWhileIdle`, does not exist in the app. The
comparison the exit criterion asks for, "Tier 2 delivery is materially
better than the current WorkManager behaviour", therefore cannot be
made. Implementing it is a separate piece of work, and it is the thing
the demonstration is meant to justify.

**Not testable on the emulator.** The spec asks for a real device. An
emulator's Doze is a forced state that the platform honours
deterministically; it is not the power-management behaviour of a real
device in a real pocket, and it does not reproduce the OEM-specific
behaviour that makes this worth demonstrating. A real-device run is
still required, and it cannot be substituted by the emulator run above.

## What is needed to close this

1. Implement the Tier 2 alarm path: `setAndAllowWhileIdle` for the
   time-sensitive reminder types, with `SCHEDULE_EXACT_ALARM` requested
   only for `PeriodPrep` and `LatePeriod` as spec `13` §10 requires.
2. Re-run the scenario above and show the alarm's `dumpsys alarm` entry
   with its `time` and its `alarm` origin, and that it fires during
   deep idle.
3. Repeat on a physical device, where the app is installed from a
   release build, and record the elapsed time between the scheduled
   instant and the notification.
