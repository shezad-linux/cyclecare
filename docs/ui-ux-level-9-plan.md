# UI/UX Level 9 Plan

> ## ⛔ SUPERSEDED — 17 October 2026
>
> **This plan is abandoned. Do not implement from it.**
>
> It is contradicted by `CycleCare-Redesign-Docs/14-design-system.md` and `CycleCare-Redesign-Docs/15-screen-specifications.md`, which are the current build contract for UI.
>
> **The specific contradiction:** the Assumptions section states that changes should occur *"without changing privacy, prediction, reminders, or data behavior."* The approved redesign changes **all four** — the privacy posture (§"Ads are retained"), the prediction algorithm (`11`), the notification model (`13`), and the Room schema with a lossless v2→v3 migration (`02` §9).
>
> It also assumes *"Existing Figma/local design-preview docs remain the visual source of truth"*, but the shipped UI has no such reference: `14` is. Its plan was written before the audit established that `:core:designsystem` has only 4 of 15 typography roles defined, **no `Shapes()` at all**, and a spacing object with zero consumers.
>
> Several of its observations remain accurate and are carried into `14` and `15` — notably that bottom navigation reads as stock Material, that card rhythm and shape need normalising, and that large font scale is untested.

<details>
<summary>Superseded content</summary>

## Assumptions

- Goal means a production-polished Android Compose experience, not a full product redesign.
- Existing Figma/local design-preview docs remain the visual source of truth.
- Changes should improve the current app without changing privacy, prediction, reminders, or data behavior.
- Emulator verification is required for visual sanity, navigation, and crash checks.

## Facts

- The app is a modular Jetpack Compose Android app.
- Core UI primitives live in `core/designsystem`.
- Primary surfaces include onboarding, dashboard, calendar, daily logging, insights, settings, privacy, export, and reminder center.
- Current screens already use a shared theme and several custom cards, but bottom navigation and some surfaces still read as stock Material.

## UI Goal

Reach a level 9 mobile UX by making the app feel cohesive, calm, private, and operationally clear:

- Strong first-screen dashboard hierarchy with obvious next action.
- Consistent card rhythm, shape, elevation, and spacing across modules.
- Bottom navigation that feels integrated into the product instead of default system chrome.
- Readable typography at default and large font scale.
- Clear status language for prediction confidence, local storage, reminders, and incomplete data.
- Emulator-verified layout with no obvious clipping, overlap, or crash paths.

## Implementation Plan

1. Harden shared design tokens.
   - Remove negative letter spacing.
   - Add a fuller Material color role mapping for containers, outlines, and inverse roles.
   - Normalize card radii toward a tighter, more professional range while preserving the existing soft health-app tone.

2. Upgrade shell navigation.
   - Give the bottom bar an app-specific container color, selected indicator, and label/icon colors.
   - Keep the ad slot above navigation, but visually separate it from app navigation.
   - Preserve existing navigation routes and test tags.

3. Polish high-traffic screens.
   - Home: preserve the advanced dashboard but improve hero legibility and card rhythm through shared primitives.
   - Logging: keep fast daily entry controls, reduce visual noise through shared card styling.
   - Insights: preserve locked/empty states while making progress and narrative cards feel intentional.

4. Verify on emulator.
   - Build and install debug app.
   - Launch with adb.
   - Capture screenshots for dashboard and at least one secondary tab.
   - Dump UI tree for tap target and text sanity.
   - Check logcat crash buffer.

## Risks

- Visual polish can regress snapshot/UI tests if they assert exact hierarchy or text bounds.
- Large font scale could expose existing long labels in bottom navigation or metric cards.
- Ads may consume vertical space and make the dashboard feel cramped on smaller emulator profiles.
- A design-system change affects every feature module, so build verification is mandatory.

## Verification Criteria

- `./gradlew testDebugUnitTest` or targeted equivalent passes, unless blocked by environment.
- `./gradlew :app:installDebug` succeeds on the connected emulator.
- App launches without crash.
- Home, Track, and Insights are reachable from bottom navigation.
- Captured screenshots show no obvious overlapping text, blank primary surfaces, or broken colors.

</details>
