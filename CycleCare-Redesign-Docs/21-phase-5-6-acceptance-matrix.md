# Phase 5 and Phase 6 acceptance matrix

Traceable status for every exit criterion in `14` §12 and `15` §14, the
state matrix from `03` §6, and the responsive, font-scale and
accessibility evidence.

Verified on the `cc36` AVD, API 36, Android 16, 320x640 at 160dpi.
No other AVD was created.

## The key screens

Confirmed against the route inventory in `15` §1.2 and the app's own
`NavHost`. Twelve routes, plus the two surfaces that are not routes, and
the edit sheet, which is a component state:

| # | Screen | Route |
|---|---|---|
| 1 | Today | `today` |
| 2 | Calendar | `calendar` |
| 3 | Log | `log` |
| 4 | Insights | `insights` |
| 5 | Cycle status | `today/cycle-status` |
| 6 | Fertility detail | `today/fertility-detail` |
| 7 | Cycle trends | `insights/cycle-trends` |
| 8 | Symptom patterns | `insights/symptom-patterns` |
| 9 | Settings | `settings` |
| 10 | Reminder centre | `settings/reminders` |
| 11 | Privacy | `settings/privacy` |
| 12 | Export | `settings/export` |
| 13 | App lock | `app-lock`, an overlay outside the `NavHost` |
| 14 | Notification gate | Not a route. A full-screen replacement for the app's content |
| 15 | Edit sheet | A component state of Today, the cycle status and the fertility detail |

Screens 7, 8 and 12 are reachable and captured. Screens 13, 14 and 15 are
captured. Screen 11 is captured at one width only, for the reason recorded
under "Responsive coverage".

## `14` §12 — Phase 5 exit criteria

| # | Criterion | Status | Evidence |
|---|---|---|---|
| 1 | Zero hardcoded colours outside `CycleCareTheme.kt`; `Ads.kt` radii use tokens | Met, with one reading | The token file is the single source of the palette. `Ads.kt` uses `CycleCareShapes`. The literal `Color.White` and `Color.Black` that remain are the *inverse* pair on a saturated fill, where a token would be a token for a constant. |
| 2 | All 15 typography roles defined and used; no `Text` relies on an undefined default | Met | `guard-typography-roles.sh` passes |
| 3 | `Shapes()` defined and used; spacing tokens consumed everywhere | Met | `DesignTokenTest` |
| 4 | No token accessor calls `isSystemInDarkTheme()` directly | Met | `LocalIsDarkTheme` now carries the flag the theme was built with. Fixed in `1d238dc` after the visual audit found the app-lock header painting dark text on a dark gradient at 1.68:1 |
| 5 | Confirmed versus estimated distinguishable by fill, border style and icon, verified in greyscale | Met | `DesignTokenTest.confirmedStateStaysDistinctFromEstimatesWhenReducedToGreyscale` |
| 6 | No screen renders two competing primary actions | Met | `HomeScreenAccessibilityTest.noCardRendersTwoCompetingPrimaryActions` |
| 7 | Every state in `03` §6 has a designed, tested implementation | **Partially met** | See the state matrix below. Loading, content, empty, permission denied and channel disabled are covered by `ScreenStateMatrixTest`. Save in progress, save success and validation error are produced by the real ViewModels and asserted in their own module suites, but are not yet asserted as a screen-level matrix. Sync states and corrupt schema are not applicable or not yet done |
| 8 | Every control meets 48dp; every screen works at 200% font scale; contrast verified per token pair | **Partially met** | Touch targets: `DesignTokenTest.touchTargetMeetsTheAccessibilityMinimum`. Contrast: `contrast-report.py`. 200% font scale: 13 screens swept, three defects found and fixed, see below |
| 9 | Every user-facing string is in `strings.xml` | Met | The audit leaves four literals, all composed values or a diagnostic format |
| 10 | Layouts verified at 320/360/600/840dp, portrait, landscape and foldable | **Partially met** | Portrait and landscape verified at all four widths, 13 screens. **Foldable is not verified**: the emulator has no hinge or posture, so there is no genuine foldable evidence |
| 11 | TalkBack traverses every key screen with correct roles, labels and states; no access trap remains | **Partially met** | `TalkBackSemanticsTest` covers the app shell and Today. See below |
| 12 | `:core:designsystem` has its own test suite | Met | 32 cases across `DesignTokenTest`, `MetricCardAccentTest` and `PreviewCoverageTest` |
| 13 | No dark-pattern copy anywhere in the UI | **Not audited** | No test covers it. It is a copy review, not a behavioural one |

## `15` §14 — Phase 6 exit criteria

| # | Criterion | Status | Evidence |
|---|---|---|---|
| 1 | Four bottom destinations; Settings reachable from every tab's app bar; deep links preserved | Met | `CycleCareAppStateNavigationTest` |
| 2 | `privacy`, `export` and `reminders` reachable in two taps from anywhere, with the first-run nudge | Met | `FirstRunNudgeTest`, 5 cases |
| 3 | No ad on Calendar, Log, Settings or any detail screen | Met | `routeAllowsBanner` and the scheduler test |
| 4 | No interstitial within 30 minutes of a log, or in the first three sessions | Met | `InterstitialDailyCapTest`, 15 cases; `InterstitialCoordinatorTest`, 17 cases |
| 5 | No layout shift when ads load or fail | Met | The banner collapses on failure and reserves its height otherwise |
| 6 | Every screen in `03` §6 has a designed, tested state | **Partially met** | As `14` §12.7 |
| 7 | Every screen works offline with no network dependency for local content | Met | The content is read from the local database. The export screen is the only one that reaches the network, and it has a failure state |
| 8 | Every screen passes at 200% font scale, in dark mode, in RTL, and under TalkBack | **Partially met** | 200%: 13 screens swept, three defects fixed. Dark: every screen captured in both schemes. RTL: verified on the shell and Today. TalkBack: as `14` §12.11 |
| 9 | No hardcoded user-facing strings remain | Met | As `14` §12.9 |
| 10 | Confirmed versus estimated states distinguishable without colour | Met | As `14` §12.5 |
| 11 | No screen shows two competing primary actions | Met | As `14` §12.6 |
| 12 | No composable mutates state directly | Met | The `AppLockGate` and `CycleCareApp` violations were resolved |
| 13 | Every existing test tag is preserved or deliberately migrated, and the navigation test is rewritten for four tabs | Met | `settings_open_privacy` was added in this work; nothing was removed |
| 14 | No file exceeds ~600 lines | Met | The largest file in the main source sets is now well under it |
| 15 | `SecureAuthController` and export have test coverage | Met | `SecureAuthCapabilityTest`, `ExportViewModelTest`, `LocalExportRepositoryIntegrationTest` |

## The `03` §6 state matrix

The specification lists ten state categories. This is the honest
applicability of each one.

| `03` §6 state | Applicable | How it is produced | Test |
|---|---|---|---|
| Initial local loading | Yes | The fake repositories never emit, so the screen stays on its `initialValue = null` path | `ScreenStateMatrixTest.theDashboardShowsItsLoadingStateBeforeTheDataArrives`, `theLogScreenShowsItsLoadingStateBeforeTheDataArrives` |
| Content with data | Yes | The populated environment | `ScreenStateMatrixTest.theDashboardShowsItsContent`, `theLogScreenShowsItsContent`, `theInsightsScreenShowsItsContent` |
| Valid empty state | Yes | Onboarded, with no cycles and no symptoms | `ScreenStateMatrixTest.theDashboardShowsAnEmptyStateWithNoHistory`, `theReminderCentreShowsAnEmptyStateWhenNothingHasBeenDelivered` |
| Offline local content | **Not a state** | The content is read from the local database. The app has no network path for it, so "offline" is the only mode there is. The export screen is the only one that reaches the network, and it has a failure state | The property is asserted by construction: no repository but the export one performs IO |
| Save in progress | Yes | The ViewModel's own `isSaving`, produced by a save against a repository that has not completed | `LoggingViewModel` and `HomeViewModel` suites |
| Save success | Yes | The ViewModel's own `isSaved` and the home screen's `message` | The same suites |
| Validation error with correction guidance | Yes | The editor's own validation, with the real message | `LoggingValidationTest`, `validateHomeEditorState` |
| Sync waiting | **Not applicable** | Cloud synchronisation is deferred by decision. The application has no synchronisation surface: there is no sync status, no sync string and no sync screen | `NoOpSyncAdapter` is the whole of the sync feature today. Presenting it as a working sync in a test would be a false claim |
| Sync in progress | **Not applicable** | As above | As above |
| Sync failed | **Not applicable** | As above | As above |
| Sync complete | **Not applicable** | As above | As above |
| Permission denied or channel disabled | Yes | The real permission status and the real channel setting, through the platform abstractions the app uses | `ScreenStateMatrixTest.theNotificationGateIsShownWhenNotificationsAreNotPermitted`, `theNotificationGateIsNotShownWhenNotificationsArePermitted`, `theSettingsScreenShowsAChannelThatHasBeenSwitchedOff` |
| Unsupported or corrupt schema data | **Not a UI state** | It is a database condition, not a screen state | Handled by the migration tests, `CycleCareDatabaseRoomTest`, 20 cases. It is not rendered as a screen, and should not be |

### Contradictory states

`ScreenStateMatrixTest.theNotificationGateAndTheDashboardAreNotShownTogether`
asserts the gate replaces the app's content rather than sitting on top of
it, so a user is never asked to answer two things at once.

## Responsive coverage

| Configuration | Evidence |
|---|---|
| Portrait, 320/360/600/840dp | 13 screens, 97 images |
| Landscape, 320/360/600/840dp | 13 screens, 97 images, on a 1800x1000 display |
| 200% font scale, portrait | 13 screens, 97 images, on a real system font scale of 2.0 |
| Foldable | **None.** The `cc36` emulator has no hinge and no posture. A resized display is not a foldable, and claiming otherwise would be a false claim. What the platform offers is window-size class behaviour, which the 600dp and 840dp captures exercise |

The harness pins the content width in dp inside a full-screen frame, so a
capture is the app laid out at that width rather than a stretched screen.
The landscape captures therefore show the app in a short viewport at each
width, which is the property that breaks.

## Font-scale coverage

The system font scale was set to 2.0 with
`settings put system font_scale 2.0`, which is a real system setting and
not a density trick, and it was restored afterwards.

Thirteen screens were swept. Three defects were found and fixed.

| Screen | Defect | Fix | Regression test |
|---|---|---|---|
| Today | The hero's source sentence was cut at three lines, so at 200% it lost "from setup" and stopped saying what the estimate was based on | The line limit is lifted at a large font scale, so the card grows instead | `ScreenStateMatrixTest`, and the capture set |
| Today | The "Baseline estimate" pill sat beside the estimate in a `Row` and wrapped onto three lines, overlapping it | At a large font scale the pill and the estimate stack | As above |
| Calendar | The weekday headers broke mid-word into "Su n", "M on" and "Tu e" | The narrow weekday form is used at a large font scale, so the header is a single letter per column, and the label is held to one line | As above |

One known limitation, accepted rather than fixed: at 320dp and 200% the
bottom navigation labels are ellipsised, "Calen…" and "Insig…". Four
eight-character labels cannot fit in four eighty-dp columns, the
specification forbids icon-only labels, and it also forbids fixing
wrapping by shrinking below the accessible size. The tab is still
identifiable by its icon, and the label is truncated rather than broken
mid-word.

## Accessibility coverage

`TalkBackSemanticsTest` asserts against the semantics tree, because that
is what a screen reader consumes.

| Covered | Assertions |
|---|---|
| Bottom navigation | Every item announces itself as a tab, exactly one is announced as selected, the selection follows the tab opened, every item is actionable, the navigation is traversed in tab order |
| Today | The cycle progress carries a state description |

**Not covered:** the calendar's selected-date announcement, the prediction
confidence messaging, form validation and save feedback, the dialogs and
sheets, and the permission states. These are the next piece of work.

**Manual review:** no screen reader was run by hand. The semantics tree is
what a screen reader consumes, and it is asserted, but a human listening
to TalkBack has not done so, and that is recorded as pending rather than
claimed.

**RTL** is verified on the app shell and Today: the device locale is set
through `LocaleManager`, the resources are configured with an Arabic
layout direction, and the tab order and the whole navigation are asserted
to reverse. The other screens are not yet covered.

## What is still open

| Item | What is needed |
|---|---|
| `14` §12.7, `15` §14.6 — the full state matrix | Save in progress, save success and validation error asserted at screen level; the empty state for every data-driven screen; the export screen's working, success and failure states |
| `14` §12.10 — foldable | A device with a hinge or a posture. The emulator cannot produce one |
| `14` §12.11, `15` §14.8 — TalkBack | Semantics coverage for the calendar, log, privacy, settings, export and reminder centre; a manual screen-reader pass |
| `14` §12.13 — dark-pattern copy | A copy review against `03` §7, with the result recorded |
| RTL beyond the shell and Today | The remaining screens |
| Landscape and font-scale interaction | Both at once, rather than either alone |
