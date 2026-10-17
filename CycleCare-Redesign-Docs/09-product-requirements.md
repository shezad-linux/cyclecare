# 09 — Product Requirements and MVP Scope

## 1. Product statement

CycleCare is a private, offline-capable cycle-tracking app for users who want to record period history, log daily observations, view a calendar, receive optional reminders and understand simple patterns in their own records. It should make uncertainty visible and let users control their data.

The MVP's differentiator is not “AI-powered” or “100% accurate” predictions. It is a clear, reliable daily logging experience with conservative predictions and privacy that is accurately described.

## 2. Primary user needs

- Record a period start quickly, with the ability to correct a mistake.
- Review historical dates without timezone shifts or accidental duplicate entries.
- Log symptoms/mood when useful without being forced to answer every field.
- Understand whether a date is confirmed or estimated.
- Use the app without an account or network.
- Control reminders and avoid exposing private details on the lock screen.
- Export their information and delete it without unnecessary friction.
- Optionally sync across devices only after understanding what is uploaded and how recovery works.

## 3. MVP scope

### Must have

- Local-first onboarding; no mandatory account.
- Create/edit/delete confirmed period records.
- Date-based daily logs with optional symptoms/mood and notes.
- Calendar with clear distinction between confirmed, logged and estimated states.
- Today screen with a simple next action and estimates only when history supports them.
- Deterministic, versioned estimate logic with uncertainty messaging.
- Local reminders with configurable time and private preview by default.
- Local export.
- Privacy settings, local data deletion and transparent backup behavior.
- Accessibility and localization foundations.
- Privacy policy, Health apps declaration and accurate Play Data safety disclosures.

### Optional after local-first quality is proven

- Supabase account and encrypted sync.
- Cross-device recovery.
- Additional insight categories.
- Paid theme/personalization options or other privacy-compatible premium features.

### Explicit non-goals for MVP

- Medical diagnosis, treatment recommendations or disease risk scoring.
- Claiming the app can accurately detect ovulation from period dates alone.
- Claiming the app is a contraceptive method or can identify “safe days.”
- Remote AI analysis of raw health records.
- Public profiles, social sharing or community comparison of reproductive records.
- Behavioral ads targeted by reproductive-health data.

## 4. Functional requirements

### FR-1: Local use

The user can install and use logging, calendar, estimates supported by available data, reminders and export without signing in or being connected to the internet.

**Acceptance:** Airplane-mode testing passes for all core flows; network failure does not prevent local writes.

### FR-2: Confirmed period history

The user can add, edit and delete a confirmed period start and optional end date. Validation is clear and correction does not silently discard records.

**Acceptance:** Duplicate, overlapping or invalid dates follow documented warning/confirmation behavior; changed history recomputes derived outputs.

### FR-3: Daily logs

The user can record optional daily observations and edit/delete them later. Blank fields remain unknown/not tracked rather than being translated to negative symptoms.

**Acceptance:** Multiple symptoms can be associated with one date; text is not emitted to logs or telemetry.

### FR-4: Calendar and Today

The UI distinguishes confirmed records from estimates using more than color alone.

**Acceptance:** TalkBack announces the date and its states; sparse history produces an uncertainty/empty state.

### FR-5: Estimates and insights

Domain functions produce deterministic estimates from validated inputs and display their limitations.

**Acceptance:** Test fixtures cover sparse, irregular, corrected and deleted history; no copy asserts certainty or contraception.

### FR-6: Reminders

Users can opt in to selected reminder types, set local-time preferences and control previews.

**Acceptance:** No duplicate schedule on reboot/timezone change; permission denial is handled; app makes no exact-time promise it cannot meet.

### FR-7: Export and deletion

Users can create a local export and delete local data. Cloud data/account deletion is provided only if cloud sync is shipped.

**Acceptance:** Clear scope and confirmation; failures are reported; local deletion also reconciles background jobs and app-owned files.

### FR-8: Optional sync

If shipped, the user explicitly enables sync, understands metadata/key recovery, can continue offline, and can disable/delete remote data.

**Acceptance:** Encrypted sync, RLS isolation, conflict handling and account deletion pass tests; no service-role secret is in the client.

## 5. Non-functional requirements

- **Availability:** core use works offline.
- **Integrity:** no silent data loss during edits, migrations, sync retries or conflict resolution.
- **Privacy:** collect the minimum required data; sensitive data never appears in telemetry by default.
- **Security:** platform-backed key protection and authenticated, user-scoped remote access.
- **Accessibility:** TalkBack, text scaling, contrast and consistent semantics.
- **Performance:** local interaction should remain responsive on representative mid/low-range Android devices; define target budgets after baseline profiling.
- **Maintainability:** business rules reside in testable domain functions; UI is not the source of validation logic.
- **Transparency:** explain what is local, what can sync, what is estimated and how deletion behaves.

## 6. Key product decisions and assumptions

- Default mode is local-only.
- Account sign-in is not required for MVP.
- Supabase is planned for optional sync, not mandatory online storage.
- No third-party ad SDK is recommended for the initial privacy-sensitive product.
- Predictions are estimates rather than clinical output.
- UI is Android-native and inspired by human-interface principles, not a pixel-level iOS clone.

These assumptions must be explicitly approved or revised before implementation.

## 7. Success measures

Track a limited set of product-quality measures that don't require uploading health content:

- Percentage of new installs completing the first log.
- Time to complete a common logging task in usability tests.
- Crash/ANR rate and data-loss incidents.
- Short- and medium-term retention by install cohort.
- User-reported confusion about estimates or calendar markers.
- Accessibility issues found and resolved.
- Export/deletion success rate.
- If sync exists: sync success, conflict rate, recovery success and deletion propagation.
- Store listing conversion and review themes.

Do not use “cycle regularity”, symptoms, fertility timing, or health content to judge growth/marketing performance.
