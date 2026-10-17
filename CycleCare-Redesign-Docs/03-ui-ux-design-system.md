# 03 — UI/UX Redesign and Design System

> **Status: principles layer.** This document defines the design intent, experience principles and screen-level direction. It is **not** the build contract.
>
> The implementable specification is split across:
> - **`14-design-system.md`** — tokens (light + dark), typography ramp, shape, spacing, elevation, motion, semantic cycle-state system, component anatomy and states, responsive rules, accessibility targets, localization.
> - **`15-screen-specifications.md`** — per-screen layout, state models, copy, semantics, test tags, the four-destination navigation restructure, and ad placement rules.
>
> Where this document and those two disagree on a concrete value, **those two win** — they were written against the verified repository. This document wins on principles and tone.

## 1. Design intent

The redesign should feel calm, discreet, clear, personal, and fast. It should reduce friction for daily logging without making the user feel that cycle predictions are medical certainty. The interface should emphasize the data the user entered and provide estimates only with context.

Use Apple HIG principles—purpose, agency, simplicity, familiarity, responsibility, feedback and craft—as a quality framework. Do **not** copy iOS-specific navigation bars, sheets, typography conventions or controls wholesale. This is an Android app; use Jetpack Compose and Android interaction conventions so the product remains familiar to Android users.

Principle reference: [Apple Human Interface Guidelines — Design principles](https://developer.apple.com/design/human-interface-guidelines/design-principles/).

## 2. Experience principles

1. **Clarity before decoration.** Show the primary status, the next relevant action, and the data date. Avoid gradients, shadows, ornaments, or large illustrations that compete with logging.
2. **Agency over forced flows.** Users can skip optional onboarding questions, dismiss an insight, correct a log, export their data, and use core features without an account.
3. **Privacy by default.** Don't reveal sensitive dates in a lock-screen notification by default. Explain sync before enabling it. Ask permissions only when the related feature is activated.
4. **Estimates are visibly estimates.** Don't use confident exact-date language or red/green medical-status semantics without justification.
5. **No shame or pressure.** Never use streak-loss language, guilt notifications, fertility assumptions, or judgmental language for missing logs or irregular cycles.
6. **One consistent interaction model.** Use the same selection, date editing, confirmation, loading, and error patterns across screens.
7. **Accessible by design.** Support large text, TalkBack semantics, keyboard/switch access where applicable, adequate contrast, touch target size, reduced motion and color-independent meanings.

## 3. Suggested visual direction

Use soft neutrals with a restrained berry/plum accent. The aim is a premium wellness aesthetic, not a stereotyped “pink menstrual app”. Color must not be the sole way to communicate phases or severity.

### Light theme tokens (starting point, tune in design review)

| Token | Hex | Use |
|---|---|---|
| `background` | `#FAF8F7` | App canvas |
| `surface` | `#FFFFFF` | Main content surface |
| `surfaceSubtle` | `#F1EBEE` | Secondary surfaces and selected states |
| `primary` | `#765064` | Primary actions, active navigation |
| `primaryPressed` | `#5E3E50` | Pressed/strong primary state |
| `accent` | `#C58D96` | Secondary highlight, sparingly |
| `textPrimary` | `#2B2529` | Main copy |
| `textSecondary` | `#6F646A` | Supporting copy |
| `border` | `#E1D9DD` | Dividers and control outlines |
| `success` | `#356B55` | Confirmed success only |
| `warning` | `#8A5C25` | Caution and attention |
| `error` | `#A33B43` | Errors and destructive confirmation |

These are initial tokens, not final accessibility approval. Check WCAG contrast for actual foreground/background pairs and verify in dark mode, large text and system font scaling.

### Typography and spacing

- Prefer the platform's readable sans-serif typography; use type roles consistently rather than isolated font sizes.
- Screen titles should be prominent but not oversized; supporting text should remain comfortably readable.
- Use an 8 dp base spacing grid with 4 dp adjustments when needed.
- Prefer aligned content edges, consistent section rhythm and a limited radius scale.
- Use elevation only to clarify hierarchy. Avoid nested cards and heavy shadows.
- Icons need accessible labels when they convey meaning; decorative icons should be hidden from accessibility traversal.

### Shape and motion

- Use rounded corners consistently, not a different shape on every card.
- Motion should explain state change: saved indicator, expansion/collapse, calendar movement, and confirmation.
- Avoid continuous animation, excessive bounce, and motion that delays task completion.
- Respect Android system animation settings and consider reduced-motion preferences.

## 4. Navigation structure

Recommended bottom navigation (four destinations):

1. **Today** — current summary and most useful action.
2. **Calendar** — cycle history and date-specific logs.
3. **Log** — fast entry for period, symptoms and daily observations.
4. **Insights** — history trends and carefully worded observations.

Settings and profile actions are accessed from a consistent top app-bar action, not hidden behind an unrelated health screen. If user testing shows frequent settings use, reconsider the nav structure rather than adding a fifth crowded destination.

Navigation should preserve scroll and unsaved input appropriately. Back should return to the previous context rather than unexpectedly exiting or losing a draft.

## 5. Screen specifications

### 5.1 Onboarding

Keep onboarding short and skippable where the data is not essential.

1. **Purpose and privacy:** explain what is stored locally, whether an account is optional, and what sync would upload if enabled.
2. **Tracking setup:** ask only for the information needed to start. Allow “I don't know” for period/cycle lengths and show why the date is requested.
3. **Personal preferences:** offer reminder settings without requiring notification permission immediately.
4. **First value:** land directly on Today with a useful next action; do not require account creation or a long tutorial.

Permission prompts should be triggered only after a user turns on a feature requiring that permission, with a brief explanation before the OS dialog when context is not self-explanatory. This follows the intent of [Apple's privacy guidance](https://developer.apple.com/design/human-interface-guidelines/privacy/) while using Android's native permission APIs.

### 5.2 Today

Prioritize information hierarchy:

- Top: “Today” and selected date context.
- Primary status: last confirmed period start or next estimated period range, with an explicit “estimate” label when predicted.
- Main action: “Log today” or “Record period start”, depending on state.
- Secondary actions: review today's log, open calendar, adjust reminders.
- Optional insight: one concise trend with a “How is this estimated?” explanation.

Do not show several prediction metrics, fertility estimates, cycle-health scores and promotional cards at equal visual weight. If there is not enough history, say so and guide the user to log data without implying a failure.

### 5.3 Calendar

- Provide month navigation, month/year context and a concise legend.
- Confirmed period days, logged symptoms, and predicted days must use distinct markers and labels.
- Never rely on color alone; use shapes, patterns, labels or TalkBack descriptions.
- Selecting a date opens a date detail panel with logs and an obvious edit action.
- Editing or deleting a period start should explain which estimates will change.
- Avoid suggesting that the fertile window is a guaranteed state.

### 5.4 Logging flow

- Optimize the common path: one tap to choose log type, a small number of clear controls, explicit save feedback.
- Separate period start/end entry from symptom and mood logging so the user does not have to complete an entire daily questionnaire.
- Make “Not tracked” and “Skip” valid choices; blank data is not a negative value.
- Provide undo or edit for accidental entries.
- Preserve an unsaved draft during benign navigation where safe; never silently discard input.
- Keep free-text notes optional and clearly private.

### 5.5 Insights

- Show descriptive patterns first (“You logged cramps on 4 of the last 6 recorded period days”), not diagnostic claims.
- State sample size and time range. Do not display a percentage based on a tiny sample without context.
- Distinguish correlation from causation.
- Make data sparsity visible. No data should yield “Not enough logged history yet”, not an invented insight.
- Explain calculations and let users disable insight categories if they don't find them useful.
- Avoid a single “health score” unless a valid, tested, non-misleading definition exists.

### 5.6 Settings and privacy

Sections:

- **Tracking:** default preferences, cycle assumptions, units and locale.
- **Reminders:** per-reminder control, local time and lock-screen preview setting.
- **Privacy & data:** local data summary, export, delete local data, optional sync, account/data deletion, app lock, and privacy policy.
- **Sync:** state, last successful sync, retry, pause, disable, account status, key recovery status.
- **About:** app version, prediction explanation, help and disclaimers.

Destructive actions require clear consequence text and confirmation. “Delete local data” and “Delete cloud account and synced data” must be separate, unambiguous actions.

## 6. UI state model

Every data-driven screen must define these states before implementation:

- Initial local loading.
- Content with data.
- Valid empty state.
- Offline local content.
- Save in progress and save success.
- Validation error with correction guidance.
- Sync waiting, in progress, failed and complete (where relevant).
- Permission denied or notification channel disabled.
- Unsupported/corrupt schema data handled without crashing.

Prefer inline messages for non-blocking failures. Use dialogs for decisions that are destructive, irreversible, or require explicit consent. A transient toast should not be the only evidence that a health record was saved.

## 7. Content and microcopy rules

Preferred:

- “Estimated next period: Oct 24–27.”
- “Based on 4 recorded cycles. Dates can vary.”
- “Not enough history to estimate this yet.”
- “Saved on this device. Sync is off.”
- “This estimate is not a contraceptive method.”

Avoid:

- “Your period will start on…”
- “You are definitely ovulating.”
- “You are safe from pregnancy today.”
- “Your cycle is unhealthy” without clinical basis.
- “You missed your tracking streak.”
- “Everything is 100% private” unless the exact statement is provably true and properly scoped.

## 8. Accessibility requirements

- Test TalkBack reading order, element role, label, state and action for every key screen.
- Controls must have meaningful semantics and avoid ambiguous icon-only actions.
- Test app font scaling and long translations; no clipping of date labels or destructive-action explanations.
- Check touch target sizes and contrast using current Android guidance.
- Verify calendar dates are announced with their state: confirmed period, prediction, logged symptom, selected date.
- Do not communicate period phase or urgency solely with hue.
- Test manual navigation and automated Compose accessibility checks.

References: [Compose accessibility](https://developer.android.com/develop/ui/compose/accessibility), [Compose accessibility testing](https://developer.android.com/develop/ui/compose/accessibility/testing).

## 9. Design review checklist

Before a screen is accepted, ask:

- Can a first-time user identify the primary action within a few seconds?
- Is an estimated value visually distinguishable from a user-confirmed record?
- Is the screen useful without an internet connection?
- Is sensitive content exposed unnecessarily on the screen or notification?
- Can the user correct an accidental entry?
- Is the empty/error/offline state designed rather than left to default behavior?
- Does the screen work with TalkBack, large text, dark theme and localization?
- Is every visual decoration helping hierarchy or comprehension?
