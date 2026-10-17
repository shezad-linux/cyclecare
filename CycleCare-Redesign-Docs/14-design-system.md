# 14 — Design System Specification

**Status:** Implementation contract for `:core:designsystem`. Completes `03-ui-ux-design-system.md`, which remains the principles document.
**Depends on:** `03` (principles), `11` (estimate states), `13` (notification surfaces), `15` (screen composition).

## 1. Purpose

`03` states the principles. This document specifies the tokens, components and rules that implement them, so that the redesign is a build to contract rather than an aesthetic argument.

The current design system is 4 files and 875 lines with **12 components, 4 of 15 typography roles defined, no `Shapes()` at all, and a spacing object with zero consumers.** This document closes each of those gaps.

## 2. Apple principles → Android implementation

The core requirement. Apple's Human Interface Guidelines supply the *quality bar*; Android supplies the *chrome*. Copying iOS navigation bars or sheets into a Compose app produces a product that feels foreign on its own platform.

| # | Principle | Concrete Android rule | How it is verified |
|---|---|---|---|
| 1 | **Hierarchy over decoration** | One primary action per screen. Elevation only to express hierarchy. Gradients only on the confirmed-period hero — nowhere else. No more than two type sizes competing for attention in one viewport. | Design review checklist; no screen ships with two filled buttons |
| 2 | **Agency** | Every optional onboarding field skippable. "Not tracked" is a first-class stored value, not an absence. Destructive actions are confirmed *and* undoable where technically possible. User-pinned settings are never overridden by any system default (`13` §8). Every automated decision — self-disabled reminders, learned send time, self-disabled channels — is surfaced with a reason and a one-tap reversal. | Settings shows an "Automatic decisions" section; each row is reversible |
| 3 | **Simplicity / progressive disclosure** | Today renders one status and one action. Estimates collapse into a detail view. Insights present one pattern at a time. Maximum two levels of nesting. No card inside a card. | Navigation depth audit |
| 4 | **Familiarity, Android-native** | Material 3 components. Bottom navigation on compact. System date/time pickers. System share sheet for export. System back gesture and predictive back. **No** iOS-style large title bars, no iOS sheet gestures, no Cupertino switches. | Component inventory is 100% Material 3 |
| 5 | **Feedback** | Every save produces visible confirmation that persists; a toast alone is never sufficient evidence a health record was saved (`03` §6). State changes animate. Errors are inline with a correction path, not modal. Loading never blocks on network for local data. | Every mutation path audited in `15` |
| 6 | **Responsibility and honesty** | Estimates labelled as estimates in every surface including notifications (`13` §15). No false precision. **No dark patterns**: no guilt copy, no streak loss, no confirmshaming, no notification pressure tactics. Friction sits before destructive and irreversible actions, never before ordinary ones. | Copy review against `03` §7 and `05` §9 |
| 7 | **Craft and continuity** | One radius scale, one spacing scale, one type ramp. Motion explains cause and effect. No layout shift while loading. Skeletons over spinners where the shape is known. Consistent 8dp rhythm across every module. | Visual regression pass across all screens |
| 8 | **Forgiveness** | Undo for every destructive action. Date entry is forgiving: a near-miss date is confirmed, not rejected. Undo survives navigation. Destructive confirmation states exactly what is lost. | Undo test per destructive path |
| 9 | **Respect for attention** | Badges indicate actionable unread state only — never to induce return visits. The dead `ReminderBell` composable (`CycleCareApp.kt:732-747`) must either be wired with meaningful content or deleted. | Badge audit |
| 10 | **Direct manipulation** | Calendar days are tappable and long-pressable. Lead times are adjusted with steppers, not text fields. Swipe-to-dismiss only where undo is available. | Interaction spec per component |
| 11 | **Efficiency** | Logging a period start is reachable in one tap from Today. Bulk edit for historical records. No field is mandatory after onboarding. | Task timing in usability sessions (`09` §7) |

## 3. Colour tokens

Current palette values are carried forward — they are cohesive and already dark-theme complete. What changes is that **every hardcoded colour becomes a token**.

### 3.1 Light scheme

| Role | Hex |
|---|---|
| `primary` | `#7A3458` |
| `onPrimary` | `#FDF7F2` |
| `primaryContainer` | `#F0D7E2` |
| `onPrimaryContainer` | `#3A1328` |
| `secondary` | `#DC6C57` |
| `onSecondary` | `#FFFFFF` |
| `secondaryContainer` | `#FFD9D1` |
| `onSecondaryContainer` | `#4C160D` |
| `tertiary` | `#6F8D7E` |
| `onTertiary` | `#FFFFFF` |
| `tertiaryContainer` | `#D8E8DE` |
| `onTertiaryContainer` | `#173527` |
| `background` | `#F8F3EC` |
| `onBackground` | `#241826` |
| `surface` | `#FFFBF7` |
| `onSurface` | `#241826` |
| `surfaceVariant` | `#F4E5E2` |
| `onSurfaceVariant` | `#655763` |
| `outline` | `#8B7A84` |
| `outlineVariant` | `#D9C7D1` |
| `inverseSurface` | `#392D37` |
| `inverseOnSurface` | `#F8EEF3` |
| `inversePrimary` | `#FFB1C8` |
| `error` | `#BA1A1A` |
| `onError` | `#FFFFFF` |
| `errorContainer` | `#FFdad6` |
| `onErrorContainer` | `#410002` |
| `surfaceTint` | `#7A3458` |
| `scrim` | `#000000` |

### 3.2 Dark scheme

| Role | Hex |
|---|---|
| `primary` | `#FFC9D8` |
| `onPrimary` | `#43192E` |
| `primaryContainer` | `#5F2542` |
| `onPrimaryContainer` | `#FFD9E5` |
| `secondary` | `#FFB19B` |
| `onSecondary` | `#5D1F12` |
| `secondaryContainer` | `#823426` |
| `onSecondaryContainer` | `#FFD9D1` |
| `tertiary` | `#C3D7CB` |
| `onTertiary` | `#25392E` |
| `tertiaryContainer` | `#3B5144` |
| `onTertiaryContainer` | `#DDEFE4` |
| `background` | `#19131A` |
| `onBackground` | `#F3E9EF` |
| `surface` | `#251D27` |
| `onSurface` | `#F3E9EF` |
| `surfaceVariant` | `#413541` |
| `onSurfaceVariant` | `#D4C3CE` |
| `outline` | `#A996A2` |
| `outlineVariant` | `#51444F` |
| `inverseSurface` | `#F3E9EF` |
| `inverseOnSurface` | `#352934` |
| `inversePrimary` | `#7A3458` |
| `error` | `#FFB4AB` |
| `onError` | `#690005` |
| `errorContainer` | `#93000A` |
| `onErrorContainer` | `#FFDAD6` |
| `surfaceTint` | `#FFC9D8` |
| `scrim` | `#000000` |

`#19131A` rather than pure black is deliberate — it keeps elevation readable on OLED without the harshness of `#000000`.

### 3.3 Surface elevation

Material 3 surface-container levels, tint-derived rather than shadow-derived:

| Token | Light | Dark |
|---|---|---|
| `surfaceContainerLowest` | `#FFFBF7` | `#19131A` |
| `surfaceContainerLow` | `#FDF7F1` | `#211A23` |
| `surfaceContainer` | `#F8F3EC` | `#251D27` |
| `surfaceContainerHigh` | `#F3EDE6` | `#2F2731` |
| `surfaceContainerHighest` | `#EDE7E0` | `#3A313B` |

Shadow is reserved for genuinely floating surfaces (dialogs, sheets, drag targets) and never used to separate cards.

### 3.4 Brand accent tokens (currently hardcoded)

These exist as literals in `CycleCareTheme.kt:153-176` and must become tokens so a palette change propagates.

| Token | Light | Dark |
|---|---|---|
| `periodActiveStart` | `#8F2430` | `#B7333F` |
| `periodActiveEnd` | `#B43A3F` | `#E0635B` |
| `periodActiveContainer` | `#4C1F28` | `#FFD7CE` |
| `periodActiveOnContainer` | `#FFECE8` | `#3F1F27` |
| `periodActiveAccent` | `#B7333F` | `#FFB8AF` |

**Bug to fix:** `PeriodActiveGradientColors`, `PeriodActiveContainerColor` and `PeriodActiveAccentColor` call `isSystemInDarkTheme()` directly (`CycleCareTheme.kt:154, 172, 176`) while `CycleCareTheme` accepts an explicit `darkTheme` parameter (line 139). Forcing `darkTheme = false` on a dark-mode device returns dark values. All token accessors must read the theme from the composition, never the system directly.

## 4. Typography

15 Material roles, fully defined. No `FontFamily` is set anywhere today, so every style uses the platform default — that is acceptable and should stay (bundling a font harms localisation coverage and APK size). Brand identity comes from weight, size and rhythm, not a typeface.

| Role | Size / Line | Weight | Tracking |
|---|---|---|---|
| `displayLarge` | 44 / 48 | SemiBold | −0.5 |
| `displayMedium` | 36 / 42 | SemiBold | −0.25 |
| `displaySmall` | 30 / 36 | SemiBold | 0 |
| `headlineLarge` | 28 / 34 | SemiBold | −0.25 |
| `headlineMedium` | 24 / 30 | SemiBold | 0 |
| `headlineSmall` | 22 / 28 | SemiBold | 0 |
| `titleLarge` | 18 / 24 | SemiBold | 0 |
| `titleMedium` | 16 / 22 | SemiBold | 0.1 |
| `titleSmall` | 14 / 20 | Medium | 0.1 |
| `bodyLarge` | 16 / 24 | Regular | 0.15 |
| `bodyMedium` | 14 / 20 | Regular | 0.2 |
| `bodySmall` | 12 / 16 | Regular | 0.3 |
| `labelLarge` | 14 / 18 | SemiBold | 0.1 |
| `labelMedium` | 12 / 16 | Medium | 0.4 |
| `labelSmall` | 11 / 16 | Medium | 0.4 |

### 4.1 Migration from the current four defined styles

| Current | Size | Migrates to | Effect |
|---|---|---|---|
| `displayMedium` 44/48 — **defined but used 0 times** | 44 | `displayLarge` | No break; it was dead |
| `headlineSmall` 28/34 — used 9× | 28 | `headlineLarge` | Slightly smaller; review 9 call sites |
| `titleMedium` 18/24 — used 17× | 18 | `titleLarge` | Slightly smaller; review 17 call sites |
| `labelLarge` 14/18 | 14 | `labelLarge` | Unchanged |

The net effect is that 106 `Text` calls currently using unstyled Material defaults acquire real styles, and the few explicitly-sized ones shift one slot up the ramp. **Every affected call site must be reviewed individually** — an automated substitution will change meaning, not just size. `15` lists them by screen.

### 4.2 Numeric data

Cycle day, dates and durations use `displayLarge`/`displayMedium` with **tabular figures** where a number changes in place, so digits do not jitter as the day advances.

## 5. Spacing, shape, elevation, motion

### 5.1 Spacing — 8dp grid with 4dp half-steps

Replaces the declared-but-unused `CycleCareSpacing`.

| Token | Value | Typical use |
|---|---|---|
| `space2` | 2 | Hairline gaps |
| `space4` | 4 | Icon-to-label |
| `space8` | 8 | Tight internal padding |
| `space12` | 12 | Chip internals |
| `space16` | 16 | **Card padding — default** |
| `space20` | 20 | **Screen horizontal gutter** |
| `space24` | 24 | Section gap |
| `space32` | 32 | Major group separation |
| `space40` | 40 | Above a primary action |
| `space48` | 48 | Full-bleed top spacing |

### 5.2 Shape — replaces the total absence of `Shapes()`

| Token | Value | Use |
|---|---|---|
| `radiusXs` | 6 | Chips, badges |
| `radiusSm` | 10 | Buttons, small controls |
| `radiusMd` | 14 | Inputs, list rows |
| `radiusLg` | 20 | Cards |
| `radiusXl` | 28 | Sheets, hero surfaces |
| `radiusFull` | 999 | Pills, avatars, progress rings |

`Ads.kt` hardcodes 28/22/14dp corner radii (lines 241, 298, 274) for the native ad container. Ads must use the same radius tokens as the host app or they read as third-party insertions — which, accurately, they are.

### 5.3 Elevation

| Token | Value |
|---|---|
| `elevation0` | 0dp |
| `elevation1` | 1dp |
| `elevation2` | 3dp |
| `elevation3` | 6dp |

Used only for floating surfaces. Cards separate via `surfaceContainer` steps (§3.3), not shadows.

### 5.4 Motion

| Token | Value |
|---|---|
| `durationShort` | 100ms |
| `durationMedium` | 200ms |
| `durationLong` | 350ms |
| `easingStandard` | `CubicBezierEasing(0.2f, 0f, 0f, 1f)` |
| `easingEmphasized` | `CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)` |
| `easingExit` | `CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)` |

Motion rules: animate state change (save confirmation, expansion, calendar month change); never animate for decoration; no continuous or looping animation; no motion that delays task completion. Under `Settings.Global.ANIMATOR_DURATION_SCALE = 0` all transitions become instant and the app must remain fully usable.

## 6. Semantic cycle-state tokens

The most important semantic system in the product: **a user must never confuse something they recorded with something the app guessed.**

| State | Container | On container | Border | Icon | TalkBack label |
|---|---|---|---|---|---|
| **Confirmed period** | `primaryContainer` | `onPrimaryContainer` | none | filled droplet | "Logged period" |
| **Predicted period** | `surfaceVariant` | `onSurfaceVariant` | **dashed** 1.5dp | dashed ring | "Estimated period" |
| **Estimated fertile** | `tertiaryContainer` | `onTertiaryContainer` | **dotted** 1.5dp | dotted ring | "Estimated fertile days" |
| **Estimated ovulation** | `tertiaryContainer` | `onTertiaryContainer` | dotted 1.5dp | small dot | "Estimated ovulation" |
| **Has a log** | — | — | — | small tick, bottom-right | "Has entries for this day" |
| **Today** | — | — | **solid** 2dp ring | — | "Today" |
| **Selected** | — | — | 2.5dp ring + 4% scale | — | "Selected" |

Three independent channels encode state — **fill, border style, and icon** — so no information is carried by hue alone (`03` §5.3). Confirmed and predicted are distinguished by border style *and* by a completely different fill treatment, so they remain distinguishable in greyscale and for all colour-vision types.

At most two semantic layers render on one day cell: a fill for period state plus a log indicator. Never a third.

## 7. Component specifications

Every component must define anatomy and all states. Shared requirement: each has `@Preview` coverage at 320dp and 360dp width, light and dark, at font scale 1.0 and 1.3.

### 7.1 Buttons

| Variant | Use | Min height |
|---|---|---|
| `PrimaryButton` | The single primary action per screen | 56dp |
| `SecondaryButton` | Supporting actions | 48dp |
| `TextButton` | Tertiary, inline | 40dp |
| `DestructiveButton` | Deletion, `error` colouring | 48dp |

States: default, pressed (`primaryPressed` equivalent — 12% darken), disabled (40% opacity **plus** reduced container contrast so it does not rely on opacity alone), loading (progress indicator replacing label, width locked to prevent layout shift), focused (2dp outline, 2dp offset).

### 7.2 ListRow

Anatomy: leading icon (24dp, optional) · title (`titleMedium`) · optional subtitle (`bodySmall`, `onSurfaceVariant`) · trailing slot (chevron, switch, or value) · optional 24dp start badge.

Minimum height **56dp** for all touch targets; 72dp when a subtitle is present. Full-width ripple. `stateDescription` must announce switches and current values.

### 7.3 Input and selection

- `TextField` — `radiusMd`, `space16` padding, floating label, supporting text slot for helper **or** error, character counter where bounded. Error state uses `error` for the border **and** an icon plus text — never colour alone.
- `DateStepper` — replaces free-text date entry. `−` / value / `+`, 48dp targets, long-press to accelerate, haptic detent at each day boundary. Near-miss dates prompt confirmation rather than rejection (principle 8).
- `SegmentedControl` — 2–4 mutually exclusive options, equal-width, single-selection.
- `Chip` / `FilterChip` — selectable, 32dp height, `radiusFull`, selected state uses `primaryContainer` + a check icon.
- `Switch` — label always visible; the row is the target, not the switch alone.

### 7.4 Calendar day cell

The most complex component. Fully specified in §6; implementation rules:

- Minimum target 48dp, arranged in a 7-column grid with 4dp inter-cell spacing.
- Each cell is a `Semantics` node with `contentDescription` composed of the full date, its states, and selection — e.g. *"Tuesday, 24 October. Estimated period. Selected."*
- `stateDescription` carries non-visual state; never rely on `contentDescription` alone for state.
- Month navigation preserves selection and announces the new month.
- Out-of-month days are disabled but visible, to preserve grid stability.
- Cells must not resize during load; skeletons render at final dimensions.

### 7.5 Cards and containers

- `SectionCard` — `radiusLg`, `surfaceContainer`, `space16` padding, optional title row with optional trailing action. **No nested cards.**
- `HeroCard` — the confirmed-period hero. The **only** component permitted a gradient, using `periodActiveStart` → `periodActiveEnd`.
- `MetricRow` — label/value pair, `labelMedium` label over `titleMedium` value.

### 7.6 System surfaces

- `BottomNavBar` — 4 destinations on compact; app bar carries Settings (§2 principle 4 and `15`).
- `Dialog` — destructive or consent decisions only. Never for non-blocking errors.
- `BottomSheet` — optional detail; dismissible, with a drag handle.
- `EmptyState` — icon, title, one-line explanation, one suggested action. Never a bare illustration.
- `ErrorState` — what failed, why, and a retry. Inline wherever possible.
- `OfflineState` — a quiet persistent strip, never a blocking modal. The app is local-first and usable offline; offline is a normal state, not an error.

### 7.7 Progress

- `CycleRing` — `displayLarge` day count at centre, `radiusFull` track, phase segment in the appropriate semantic colour, plus a text label. Exposes `progressBarRangeInfo` for TalkBack (already partially implemented in `HomeScreenUiTest` — extend rather than replace).

## 8. Responsive layout

Use **window size classes**, never device checks. `material3Adaptive` is already declared in `libs.versions.toml:14` and referenced by zero modules — activate it.

| Class | Width | Navigation | Layout |
|---|---|---|---|
| Compact | < 600dp | Bottom bar, 4 destinations | Single column |
| Medium | 600–840dp | Navigation rail | Single column, wider gutters; Calendar may use two panes |
| Expanded | ≥ 840dp | Navigation rail | Two-pane master/detail; Insights and Calendar side-by-side |

Requirements: the calendar grid must not stretch absurdly wide — cap content width and centre. All layouts verified at 320dp (small phone), 360dp (baseline), 600dp, 840dp, landscape, and foldable open. **Every current `@Preview` is hardcoded to 360dp**; that coverage must be replaced.

## 9. Accessibility

Numeric targets, not aspirations:

| Requirement | Target |
|---|---|
| Body text contrast | ≥ 4.5:1 |
| Large text (≥ 18sp bold, ≥ 24sp regular) | ≥ 3:1 |
| Icons and UI component boundaries | ≥ 3:1 |
| Touch target | ≥ 48 × 48dp |
| Font scaling | fully usable at **200%** with no truncation or clipping |
| Reading order | matches visual order |
| Every actionable node | role + label + state + action |
| Motion | respects system animation scale |
| Focus | no keyboard or switch-access trap |

**Existing violation to fix:** `CycleCareApp.kt:317` installs `BackHandler(enabled = true) {}` in the notification-permission gate, which traps switch-access and keyboard users with no exit. Any blocking gate must offer a way out or must not be blocking.

**Existing gap to fix:** bottom-navigation icons use `contentDescription = null` (`:532`), leaving all five icon nodes unlabelled for TalkBack. Every navigation destination needs a proper label.

Also required: enable Compose's `AccessibilityChecks` in tests; add font-scale 2.0 UI tests; add an RTL test (`supportsRtl` is already `true` and untested).

## 10. Localization

**Current state: zero entries in `strings.xml`.** Every user-facing string is a hardcoded English literal in Compose code. This is a release blocker for any localised listing and makes copy review impossible.

Rules:

1. All user-facing strings move to `strings.xml` with semantic keys. No literals in composables.
2. No string concatenation — use `plurals` for counts ("1 cycle logged", "%d cycles logged").
3. Dates and numbers via `DateTimeFormatter` bound to the active locale. Never hardcoded month names or date patterns.
4. Design for **40% text expansion**; no fixed-width text containers; no truncating ellipsis on primary labels.
5. Every user-visible string has an accessibility-safe alternative where punctuation or symbols are ambiguous.
6. Localise the listing only where the in-app experience is genuinely localised (`07` §3).

## 11. Preview and test strategy

Current gaps: `feature/settings`, `feature/privacy`, `feature/export` and `core/designsystem` have **no `debugImplementation(compose.ui.tooling)`**, so previews cannot be added without first fixing their build files.

Required:

- Every component in `:core:designsystem` has previews at 320/360/600/840dp, light and dark, font scale 1.0 and 1.3.
- Every screen has previews for each state in `03` §6: loading, populated, empty, offline, validation error, save result.
- Add `ui-test-junit4` to `:core:designsystem` and write component tests there — it currently has **zero tests**.
- Add Compose `AccessibilityChecks` to test rules.
- Add font-scale and RTL variants to existing screen tests.

## 12. Acceptance criteria

1. Zero hardcoded colours outside `CycleCareTheme.kt`; `Ads.kt` radii use tokens.
2. All 15 typography roles defined and used; no `Text` relies on an undefined default.
3. `Shapes()` defined and used; spacing tokens consumed everywhere.
4. No token accessor calls `isSystemInDarkTheme()` directly.
5. Confirmed versus estimated states distinguishable by fill, border style and icon — verified in greyscale.
6. No screen renders two competing primary actions.
7. Every state in `03` §6 has a designed, tested implementation.
8. Every control meets 48dp; every screen works at 200% font scale; contrast verified per token pair.
9. Every user-facing string is in `strings.xml`.
10. Layouts verified at 320/360/600/840dp, portrait, landscape and foldable.
11. TalkBack traverses every key screen with correct roles, labels and states; no access trap remains.
12. `:core:designsystem` has its own test suite.
13. No dark-pattern copy anywhere in the UI (`03` §2.5).