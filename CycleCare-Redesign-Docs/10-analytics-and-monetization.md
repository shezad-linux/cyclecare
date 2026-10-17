# 10 — Privacy-Compatible Analytics and Monetization

> **Amended 17 October 2026.** Advertising is **retained** in CycleCare. The previous recommendation of an ad-free MVP is superseded by **`16-monetization-and-ads.md`**, which specifies the truthful disclosure posture, consent requirements, Play declarations, placement rules and the ad-free purchase. Read that document alongside this one.
>
> What has **not** changed: health data is never used for monetisation. Advertising may exist; *profiling users by reproductive health* may not.

## 1. Product principle

CycleCare should earn trust by providing a reliable tracking tool, not by monetizing intimate health data. Avoid business models that need to profile users based on cycle dates, mood, symptoms, fertility estimates, medication, or free-text notes.

Advertising is present, and that is an accepted trade-off. The invariant is that **health data never reaches the ad system**: no targeting, no keywords, no publisher-provided identifier, no contextual hints, no custom assets. This is verified in code today (`Ads.kt:104, 163, 174, 400` — all three request paths use a bare `AdRequest.Builder()`), and `16` §7 makes it a structural invariant with a CI guard so a future developer cannot quietly break it.

Establish retention and usability through the product itself. Do not collect a detailed reproductive-health event stream for growth purposes.

## 2. Analytics policy

### Default position

- No telemetry event includes period dates, cycle length, symptom types, mood, discharge, medication, notes, fertility window, estimated next period or a user-specific health profile.
- No free-text event properties.
- No health payloads in URLs, breadcrumbs, crash metadata or remote config.
- No persistent user identifier is required for core use.
- If non-essential product analytics are used, consider opt-in and disclose purpose, data fields and retention.
- Operational diagnostics should be minimal, controlled and easy to inspect.

### Candidate events, only after privacy review

| Event | Allowed fields | Prohibited fields |
|---|---|---|
| `onboarding_completed` | App version, language if necessary | Health answers, period date, age or personal notes |
| `log_flow_completed` | Coarse success/failure outcome only if justified | Log type detail, symptom values, selected date |
| `export_completed` | Success/failure category | Export content, report range or health values |
| `sync_finished` | Duration bucket, result category, app version | Record type, decrypted payload, cycle information |
| `app_error` | Allowlisted error code, app version | Exception text with user content, tokens, local paths containing personal information |

Even a coarse event such as “log completed” can reveal engagement with a reproductive-health app. Evaluate whether the event is necessary and appropriate; the table is not an instruction to collect every event.

## 3. Technical controls

- Use a fixed event allowlist; reject arbitrary maps and user-provided strings.
- Redact sensitive exception details before sending diagnostics.
- Keep test fixtures with synthetic health values and search outbound payloads for these canary values.
- Disable automatic screen-name/screenshot capture for private screens unless there is a reviewed reason.
- Review SDK upgrades because default data collection can change.
- Provide a clear telemetry setting where applicable and implement the state, not only the toggle UI.
- Never send raw logs to a remote LLM or analytics service to generate insights.

## 4. Monetization options

### Option A — Premium subscription

Potential paid features include encrypted multi-device sync, advanced export formats, custom reminders, themes or extended charting. Users must still be able to view, export and delete their own data without an unfair paywall.

**Benefits:** Recurring revenue and an incentive to improve the product.  
**Costs/risks:** Subscription expectations, billing implementation, support, churn, and the need to deliver continuing value. Cloud sync is not free to maintain forever.

### Option B — One-time purchase

A paid upgrade can unlock non-essential convenience/personalization features.

**Benefits:** Simple user proposition; no recurring charge.  
**Costs/risks:** Lower recurring revenue to support storage, support and maintenance; future cost increases are hard to match to revenue.

### Option C — Contextual ads after a dedicated review

If ads are considered later, evaluate whether the ad SDK's data collection and consent flow are compatible with the product's explicit privacy promises. Do not target ads based on health data. Avoid interstitials that interrupt logging, reminders, exports, or sensitive insight reading.

**Benefits:** Can reduce the initial price barrier.  
**Costs/risks:** SDK telemetry and tracking may conflict with a privacy-first position; low fill/eCPM can produce little revenue while adding complexity, performance overhead and disclosure obligations.

### Recommendation

Advertising ships as specified in `16`. Alongside it, ship a **one-time purchase for an ad-free experience** — not a subscription, because there is no recurring service cost to justify one for this feature, and one-time avoids the churn and billing-expectation problems below.

Rules:

- Local use remains fully functional with ads present. Users are never blocked from logging, viewing, exporting or deleting their data.
- **Export and data deletion are never paywalled**, under any model.
- The ad-free purchase removes every ad surface including interstitials.
- Interstitial load is capped aggressively (`15` §11.3). Ad load and notification load compound fatigue, so the two budgets are reviewed together even though they are measured separately.
- Interstitial is the dominant uninstall driver. Gating failures must **drop** the ad, not defer it.
- Declining ad consent must never be compensated for with more ads or higher frequency.

Honest assessment of the trade-off: ads fund the product, and they also cost users who will not accept advertising in a cycle tracker, plus review sentiment and some retention. That is why the ad-free purchase must be real rather than nominal.

## 5. Growth strategy

- Improve first-log completion and common-action friction.
- Make the calendar and uncertainty states easy to understand.
- Provide dependable reminders with private preview defaults.
- Improve accessibility and localization.
- Publish an accurate, clear Play listing and test screenshots/descriptions.
- Respond to support issues, data-loss reports and privacy concerns quickly.
- Use user research and aggregate metrics; don't build growth tactics around health anxiety or predictions of pregnancy risk.

## 6. Privacy review gate for any new SDK or revenue feature

Before adding an ad, analytics, AI, crash, attribution, experimentation or remote configuration SDK, document:

1. What data the SDK collects automatically.
2. Whether it uses identifiers, IP addresses, device attributes or cross-app signals.
3. Whether request/content values can include health-derived information.
4. Data retention, sharing and deletion terms.
5. Consent and opt-out behavior.
6. Performance/battery impact.
7. Required privacy policy and Play Console updates.
8. A repeatable network/log test proving what is sent.

If these questions cannot be answered, do not add the SDK to the production build.
