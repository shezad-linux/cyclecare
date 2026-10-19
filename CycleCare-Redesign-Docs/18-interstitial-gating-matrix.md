# Interstitial gating — behaviour matrix

Source: `CycleCare-Redesign-Docs/15-screen-specifications.md` §11.3 (conditions)
and §11.2 (the "never place an ad" list, which §11.3 condition 6 refers to
as "the forbidden list").

> Conditions — **all** must hold:
> If any condition fails, the interstitial is **dropped, not deferred**.

## The eight conditions

| # | Condition (spec wording) | State it reads | Where that state lives today | Result when it fails | Test |
|---|---|---|---|---|---|
| 1 | User has completed onboarding. | `onboardingComplete: Boolean` | `ProfileRepository.observeProfile() != null` — the profile row is the flag; `CycleCareApp.kt` already branches on `profile == null` to show onboarding | Drop | `drop_whenOnboardingIsNotComplete` |
| 2 | At least `MIN_SESSIONS_BEFORE_INTERSTITIAL` (3) sessions completed. | `completedSessions: Int` | **Does not exist.** Needs a persisted counter incremented on each foreground transition | Drop | `drop_whenFewerThanThreeSessions` |
| 3 | At least `MIN_MINUTES_BETWEEN_INTERSTITIALS` (10) since the last one — replacing the current 60-second cooldown. | `millisSinceLastInterstitial: Long?` | **Not persisted.** `InterstitialAdManager.lastShownTimeMillis` is in-memory and dies with the process, and the interval is a hard-coded 60 000 ms | Drop | `drop_whenShownLessThanTenMinutesAgo` |
| 4 | No unsaved draft anywhere in the app. | `hasUnsavedDraft: Boolean` | **No global concept exists.** `LoggingEditorState` is private to `LoggingViewModel` in a NavHost-scoped ViewModel | Drop | `drop_whenAnUnsavedDraftExists` |
| 5 | No reminder delivered within the last 30 minutes. | `millisSinceLastReminderDelivery: Long?` | `ReminderEvent.deliveredAt` exists and is stored as `deliveredAtEpochMillis`, but `ReminderEventsRepository` has no time-window query and `observeRecent` excludes dismissed events | Drop | `drop_whenAReminderWasDeliveredRecently` |
| 6 | The current screen is not in the forbidden list. | `currentRoute: String?` | **Exists.** `navController.currentBackStackEntryAsState()` → `currentRoute`; the same pattern as the banner rule `routeAllowsBanner` | Drop | `drop_whenOnAForbiddenScreen` |
| 7 | The user has not disabled ads via consent. | `adsConsentGranted: Boolean` | **Exists.** `GoogleMobileAdsConsentManager.canRequestAds` | Drop | `drop_whenConsentIsNotGranted` |
| 8 | The daily interstitial cap is not exceeded. | `interstitialsShownToday: Int` | **Does not exist.** No impression counter anywhere | Drop | `drop_whenTheDailyCapIsReached` |

## The forbidden screen list (condition 6)

§11.3 condition 6 refers to "the forbidden list" without enumerating it. The
enumeration lives in §11.2, "Never place an ad". The interstitials are shown
in response to a tab navigation, so the screen that matters is the one the
user is *leaving* — the one they were reading.

| Forbidden route | Reason | §11.2 clause |
|---|---|---|
| `log` | "On the logging screen" | On the logging screen, or during an unsaved draft |
| `home/fertility-detail` | "Adjacent to insight content or fertility detail being read" | Adjacent to insight content or fertility detail being read |
| `insights/cycle-trends` | "Adjacent to insight content … being read" | Adjacent to insight content or fertility detail being read |
| `insights/symptom-patterns` | "Adjacent to insight content … being read" | Adjacent to insight content or fertility detail being read |
| `settings` | detail screen | never any detail screen (§11.2 table) |
| `reminders` | detail screen | never any detail screen (§11.2 table) |
| `privacy` | detail screen | never any detail screen (§11.2 table) |
| `export` | detail screen | never any detail screen (§11.2 table) |
| `onboarding` | first launch | On the first launch, or in the first session |
| `null` | transient state, not a real screen | — |

The two "first launch" and "first session" clauses in §11.2 are already
covered by conditions 1 and 2, so they are not repeated as route rules.

## Where the state has to come from

| Need | Mechanism | Files |
|---|---|---|
| Session count | Increment on each foreground transition, persisted | `AppVisibilityObserver` already exists and observes `ProcessLifecycleOwner.onStart/onStop` |
| Last interstitial time | Persisted, written when an interstitial is actually shown | new stats store |
| Unsaved draft | The logging editor reports its dirty state to a shared holder | `LoggingViewModel` already owns `editorFlow` |
| Reminder delivery | New DAO query that ignores `dismissedAt` | `ReminderEventDao` |
| Daily cap | Persisted counter with a day stamp | new stats store |

## Behaviours the spec calls out explicitly

**Drop, not defer.** §11.3: "If any condition fails, the interstitial is
dropped, not deferred. Deferring an interstitial to the next screen is
exactly the interruption pattern that produces uninstalls."

This is a change in the call site. Today `CycleCareApp.kt` defers the
*navigation* into the ad callback:

```kotlin
interstitialAdManager.showAd(activity) {
    navController.navigateToTopLevel(route)
}
```

So when the ad is blocked the navigation still happens, but by the gate
returning "drop" and the caller navigating directly — never by queueing the
ad for the next screen.

**Repeated events.** The same request arriving twice must not consume two
impressions. A shown interstitial writes one impression; a dropped one writes
nothing and does not update `lastShownTime`.

**The daily cap counts impressions, not requests.** A request that is
dropped for any other reason does not consume cap.

## Gaps in the specification

| Gap | Handling |
|---|---|
| **The daily cap value is never given.** §11.3 condition 8 says "the daily interstitial cap" and §11.4 says interstitials "count toward the interstitial cap", but no number appears in `15` or in `13` §9. | **Resolved as a product decision, not a specification requirement.** The approved value is 3 per day, recorded as `MAX_INTERSTITIALS_PER_DAY`. It is a dedicated constant rather than a reuse of `MAX_SENDS_PER_DAY`, so a change to the notification budget cannot silently change the ad budget, and it is configurable per evaluation without touching the other seven conditions. Raising it needs a documented decision informed by actual impression and retention data. |
| **Condition 6's forbidden list is not enumerated in §11.3.** | Derived from §11.2's "Never place an ad" list, tabulated above, in one named set so it can be adjusted in one place. |
| **Condition 5's 30-minute window is not named.** `POST_LOG_GRACE_MINUTES = 30` exists but is named for *logs*, and this condition is about *reminders*. | A separate constant, `MIN_MINUTES_AFTER_REMINDER = 30`, so the two are not conflated. |
| **"Session" is undefined.** | A session is one foreground period: the app moving to foreground. Backed by `AppVisibilityObserver`, which already observes exactly that transition. |

## Cooldown

The app originally had a 60 second cooldown in `InterstitialAdManager`. Spec
§11.3 condition 3 replaces it outright: "At least
`MIN_MINUTES_BETWEEN_INTERSTITIALS` (10) since the last one — replacing the
current 60-second cooldown." The ten minute rule is now enforced by the gate
against a persisted timestamp, so it also survives the process being killed,
which the old in-memory field did not.

The old cooldown's other job, stopping two interstitials inside a minute of
each other, is covered twice over: by the ten minute rule, and by the
coordinator's in-memory mirror of the last-shown timestamp, which refuses a
second request in the same instant before the first impression has even been
written to disk.
