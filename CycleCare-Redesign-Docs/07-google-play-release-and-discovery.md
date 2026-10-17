# 07 — Google Play Release, Policy, and Discoverability

## 1. Purpose and realistic ranking expectations

This document aims to make CycleCare releasable and easier to discover. No architecture, UI redesign, keyword list or marketing tactic can guarantee a Google Play ranking. Discovery changes over time and depends on relevance, user response, quality and many platform-specific signals not fully disclosed to developers. Google Play's own guidance emphasizes helping users find relevant, high-quality apps people use and recommend.

Reference: [Google Play — App Discovery and Ranking](https://support.google.com/googleplay/android-developer/answer/9958766).

The practical strategy is to improve **relevance, store-page conversion, reliability, accessibility, retention and user trust**, then measure the result over time.

## 2. Policy checkpoints for a reproductive-health app

### Health apps declaration and content

Google Play requires developers to complete the Health apps declaration, including apps distributed through testing tracks. The declaration should match what the app actually does, even if the developer believes the app is not a medical tool.

- Complete the Health apps declaration in Play Console.
- Review the current [Health and Medical Apps policy](https://support.google.com/googleplay/android-developer/answer/16679511) before each production release.
- Avoid unsupported diagnostic, treatment, fertility certainty or contraception claims.
- If medical functionality or claims are introduced, determine whether additional regulatory proof, disclaimers, or market-specific requirements apply.

### Privacy policy and Data safety

- Publish a privacy policy at an active, publicly accessible web URL. Google Play's health-app policy says the URL must be non-geofenced and not a PDF.
- Link it in the Play Console field and within the app.
- Complete the Data safety form and any applicable advertising/SDK disclosures based on actual code and SDK behavior.
- Disclose account creation, remote sync, encrypted payloads, metadata exposure, retention, deletion and third-party services accurately.
- Re-review disclosures when adding any new SDK, analytics event, ad format, AI service, account field or cloud data path.

References: [Health and Medical Apps policy](https://support.google.com/googleplay/android-developer/answer/16679511), [Health apps declaration form](https://support.google.com/googleplay/android-developer/answer/14738291).

### Alarms and reminders permission

`13` §11 specifies a hybrid scheduling design. The Play position is precise and constrains the design:

- **`USE_EXACT_ALARM` is not available to CycleCare.** It is restricted to alarm/timer apps and calendar apps with event notifications. A cycle tracker qualifies for neither, and requesting it risks rejection.
- **`SCHEDULE_EXACT_ALARM` is the viable permission.** It is granted by the user, **denied by default** on fresh installs targeting API 33+, can be revoked at any time, and requires a Play Console declaration of app functionality.
- Requirement: check `canScheduleExactAlarms()` before scheduling, listen for `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`, explain the need in-app, and **degrade gracefully** when the permission is absent.
- `13` treats degradation as the default path, not an edge case, because most users will never grant it. The app must be fully functional on the Tier 2 inexact-alarm path.

Complete the Permissions declaration form in Play Console. `FOREGROUND_SERVICE_DATA_SYNC` (API 34+) is not currently declared and is not required unless expedited or foreground WorkManager is introduced — it is not, under this design.

### Advertising declarations

Advertising is retained (`16`). Three declarations are required and must match the code:

- **Advertising ID** — required. `com.google.android.gms.permission.AD_ID` reaches the merged manifest via the ad SDK even though the app never declares it. Also declare `ACCESS_ADSERVICES_AD_ID`, `ACCESS_ADSERVICES_ATTRIBUTION` and `ACCESS_ADSERVICES_TOPICS`.
- **Ads** — the app contains ads.
- **Data safety** — declare advertising ID, device and app activity, and approximate location (inferred from IP). Do **not** declare health and fitness data as collected by CycleCare, because none is. The app is nonetheless a health app, covered by the separate Health apps declaration.
- **Age target** — must be consistent with the privacy policy and with the age band the app actually collects.

Re-review all declarations when adding any SDK, changing an ad format, adding an AI service, adding sync, or changing any data path. A mismatch between the Data safety form and actual SDK behaviour is a policy violation.

### Target API level

As of 17 October 2026, Android's official target API guidance says that beginning 31 August 2026, new apps and app updates must target **Android 16 / API level 36 or higher**, subject to the listed platform exceptions. Verify the requirement shown for CycleCare in Play Console before uploading a release because platform requirements may evolve.

Reference: [Android target API requirements](https://developer.android.com/google/play/requirements/target-sdk).

### Release operational checks

- Verify the Play App Signing setup and keep upload credentials secure.
- Keep release and test projects separate from production Supabase.
- Test the signed artifact, not only the debug build.
- Complete device compatibility, crash, policy, permission and data-deletion checks.
- Ensure runtime permissions are requested in context.
- Validate all store listing assets against current image/content/metadata rules.

## 3. Store listing strategy

Google Play's current listing guide documents limits including a 30-character app name, an 80-character short description and a 4,000-character full description. Recheck the current console UI before finalizing localized listings.

Reference: [Create and set up your app](https://support.google.com/googleplay/android-developer/answer/9859152).

### Positioning

Lead with accurate, distinct value rather than generic terms such as “best”, “100% accurate”, or “doctor approved” without evidence. A plausible positioning direction is:

> **Private period tracking that works offline**

Only use that statement if the final app truly supports the described local-first flow and its SDK disclosures are consistent with the privacy wording.

### Metadata principles

- App name: short, memorable and relevant to the actual product.
- Short description: communicate the main user benefit and one defensible differentiator.
- Full description: explain period logging, calendar, reminders, optional sync (if shipped), export and how estimates work.
- Keywords: use natural, relevant language in the description. Do not stuff unrelated terms or competitors' brand names.
- Localization: localize the listing and screenshots only where the translated app experience is usable. A translated listing without translated in-app content can damage trust.
- Privacy: do not imply “local only” if any sensitive information is sent to a cloud service or SDK.

### Screenshot plan

Use screenshots that show actual app screens, not aspirational mockups that differ from the released app.

1. Today screen: what the user can do at a glance.
2. Calendar: confirmed records visually distinguished from estimates.
3. Fast logging: the low-friction action.
4. Insights: sample-size and estimate caveats visible.
5. Privacy controls: local use and optional sync, if implemented.
6. Settings/reminders: user control and lock-screen privacy.

Avoid fake user reviews, fabricated awards, medical approval badges, overconfident prediction claims, or screenshots containing real personal data.

## 4. Store assets and conversion experiments

- Prepare an app icon that remains recognizable at small sizes and avoids overused clinical-looking symbols.
- Use legible screenshot headlines, limited text, and consistent palette.
- Ensure every screenshot corresponds to a shipped feature.
- Use store listing experiments when available; change one meaningful variable at a time.
- Track listing views, acquisition source, install conversion, uninstalls and retention at aggregate level where available.
- Compare performance across meaningful periods and traffic sources rather than declaring success from small samples.
- Do not optimize click-through at the expense of misleading users; poor expectation-setting can create uninstalls and negative reviews.

## 5. Quality and retention work before SEO/ASO work

Prioritize the following before spending significant effort on keywords or paid acquisition:

1. Logging must be fast, forgiving and reliable.
2. Calendar states and prediction uncertainty must be clear.
3. No critical flow should require connectivity.
4. App startup and common navigation must be responsive on mid/low-range devices.
5. Accessibility and localization should be solid.
6. Privacy promises must match the actual data flow.
7. Reminders should be reliable within their stated timing behavior and never duplicate unnecessarily.
8. Export, correction, local deletion and (if shipped) account/cloud deletion must work.

## 6. Review strategy

Ask for feedback only after a genuine, non-sensitive moment of value and never only when the user appears satisfied. Do not gate help or problem reporting behind a rating request. Do not expose health information in the rating prompt. Reply to reviews with no private account or health details and route support issues through an appropriate channel.

## 7. Ranking: a concrete plan rather than a hope

No tactic guarantees a ranking. What follows is the actionable plan that the redesign is designed to support, with the measurable signal each item should move.

### 7.1 The ranking levers actually in our control

In rough order of leverage for this product:

1. **Retention and crash-free sessions.** Strongest and slowest-moving signal. Over-notification, forced flows and ad load all damage it directly — which is why `13` §9 and `15` §11.3 exist.
2. **Store-page conversion.** The gap between listing views and installs.
3. **Review volume and rating.** Driven by whether expectations set in the listing match the experience.
4. **Accessibility and reliability.** Affects both rating and Play quality signals.
5. **Relevance.** Keyword and category matching — the smallest lever, and the one most often over-invested in.

### 7.2 Positioning

The app is ad-supported and privacy-protective in a specific, verifiable way. That is a genuine differentiator, and it is stronger than generic "100% private" claims because it survives scrutiny.

> **Private by design, accurate about what it estimates.**
> CycleCare keeps your cycle data on your phone and never sends it to advertisers — and tells you when it is estimating rather than knowing.

Only use this if the final build upholds it. The first clause is verified by the ad-request invariant (`16` §7) and must remain so; the second is delivered by `11`. Do not claim "local-only" while ad SDKs transmit device data (`16` §4).

### 7.3 Metadata

| Field | Limit | Guidance |
|---|---|---|
| App name | 30 characters | Short, memorable, relevant. Avoid "best", "100%", "doctor approved" |
| Short description | 80 characters | Main benefit plus the one defensible differentiator |
| Full description | 4,000 characters | Logging, calendar, reminders, how estimates work and their limits, export and deletion, ad disclosure, sync if shipped |
| Keywords | No field — use natural language in the description | Do not stuff unrelated terms or competitors' brand names |

Description copy must state plainly: predictions are estimates, not contraception or diagnosis; the app is ad-supported; health data never reaches the ad SDK; data can be exported and deleted at any time.

Localise the listing **only** where the in-app experience is genuinely localised. The app currently has **zero** localisation (`strings.xml` contains only `app_name`), so shipping a localised listing today would damage trust. Localisation is a prerequisite, not an afterthought.

### 7.4 Screenshot plan

Use real screenshots of the shipped app. Each must correspond to a feature that exists in the build being reviewed.

1. **Today** — status and the single next action, with the estimate labelled honestly.
2. **Calendar** — confirmed records visibly distinct from estimates (dashed border, different fill — `14` §6).
3. **Logging** — the low-friction one-tap path.
4. **Insights** — sample size and estimate caveats visible, not hidden.
5. **Privacy** — lock-screen masking, app lock, export and deletion.
6. **Reminders** — user control over lead time, send time and quiet hours (`13` §14).

Never fabricate reviews or awards, never use a medical approval badge, never claim clinical accuracy, and never include real personal data in a screenshot.

### 7.5 Experiment discipline

- Use store listing experiments where available; change **one** variable at a time.
- Record experiment period, traffic mix and every concurrent change. A listing change during a seasonal traffic swing proves nothing.
- Compare against a meaningful baseline period, not against last week.
- Never optimise click-through at the expense of accurate expectation-setting. Promising "100% accurate predictions" and then shipping honest estimates produces uninstalls and one-star reviews, which costs far more than the click gained.

### 7.6 Metrics to watch

Store console reports plus privacy-reviewed internal metrics. Nothing here may include cycle content.

| Metric | Why |
|---|---|
| Listing views → install conversion | Primary page-level signal |
| Crash-free users and sessions | Quality signal |
| First successful log completion | Onboarding effectiveness (`03` §5.1) |
| Retention by install cohort | The strongest ranking input |
| Ad-free purchase rate | Indirect signal that ad load is too high |
| Notification disable rate | `13` §3 — rising disable means the scoring model is wrong |
| Review themes | Qualitative; read every one |
| Accessibility and support issue counts | `14` §9 |

Do not judge growth using cycle regularity, symptoms, fertility timing or any health content. Do not claim improved ranking from an install increase or a small rating move without a controlled comparison.
