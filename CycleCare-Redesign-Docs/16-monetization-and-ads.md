# 16 — Monetization and Advertising: Honest Posture with Ads Retained

**Status:** Product and compliance decision record. **Supersedes the ad-removal recommendations in `04-security-and-privacy.md` §8 and `10-analytics-and-monetization.md` §1.**
**Depends on:** `15` §11 (placement), `04` (privacy), `07` (Play declarations), `03` (no dark patterns).

## 1. Decision

Advertising stays in CycleCare. This document replaces the previous recommendation to remove AdMob entirely, and replaces the "publish local-only, add a premium option when justified" position in `10`.

That decision has consequences, and the rest of this document exists to make them explicit rather than to soften them.

**Retention and trust are the product.** Revenue is a means of funding it. When the two genuinely conflict, trust wins — which in practice means the ad-free path must be genuinely available, not a token.

## 2. What the code actually does (verified)

Three ad formats are implemented in `app/src/main/java/com/cyclecare/app/Ads.kt` (460 lines):

| Format | Ad unit | Placement |
|---|---|---|
| Adaptive banner | `ca-app-pub-2324135355814969/5000575657` | `CycleCareApp.kt:508` — above the bottom bar, **on every tab** |
| Native advanced | `ca-app-pub-2324135355814969/5327878717` | `CycleCareApp.kt:592` — Today only |
| Interstitial | `ca-app-pub-2324135355814969/3684915559` | `InterstitialAdManager`, 60-second cooldown |

Plus Google UMP (`GoogleMobileAdsConsentManager.kt`, 124 lines) for consent.

**The single most important verified fact:** all three request paths build a bare ad request with **no targeting parameters whatsoever**.

- Banner — `Ads.kt:104`: `adView.loadAd(AdRequest.Builder().build())`
- Native — `Ads.kt:161-163, 174`: `NativeAdOptions.Builder().build()` plus bare `AdRequest`
- Interstitial — `Ads.kt:400`: bare `AdRequest`

There is no `setCustomTargeting`, `addKeyword`, `setPublisherProvidedId`, `setLocation`, category exclusion, contextual hint or custom asset request anywhere in the codebase.

**Consequence, and this is a real strength:** no cycle date, symptom, mood, medication, note, age band, or profile value is ever passed to Google by the application. This is verifiable, not aspirational, and should be stated plainly to users and in the store listing.

It must be protected as an invariant. See §7.

## 3. What the ad SDKs still collect

Bearing no health data does not mean collecting nothing. The merged manifest for a release build includes, contributed by the ad SDKs:

| Permission | Meaning |
|---|---|
| `com.google.android.gms.permission.AD_ID` | Resettable Advertising ID — a persistent pseudonymous identifier |
| `ACCESS_ADSERVICES_AD_ID` | Access to that Advertising ID |
| `ACCESS_ADSERVICES_ATTRIBUTION` | Ad attribution measurement |
| `ACCESS_ADSERVICES_TOPICS` | Android Privacy Sandbox **Topics API** — ad interest topics inferred by the platform |

Plus, automatically and without the app passing anything:

- IP address, from which coarse location is inferred
- Device model, manufacturer, OS version, app version, language, screen size
- Ad impressions, clicks, and close events
- Install attribution — whether this install came from an ad
- Ad interaction history used for interest inference

**The uncomfortable truth that must be disclosed:** installing a menstrual cycle tracker is itself health-adjacent information. Even with zero custom targeting, Google's systems can infer sensitive interest categories from app category and ad-interaction patterns alone. A user who declines to share cycle data is still, by installing this app and serving ads, revealing that they use a menstrual tracker.

No privacy policy can make this disappear. It can only be stated.

## 4. Required privacy posture

The following statements are **true** and may be made:

- Cycle data, symptoms, medication, notes and profile settings are stored only on your device.
- We never send your cycle data, symptoms, or notes to Google or any advertising partner.
- We do not use your health information for ad targeting or ad personalisation.
- You can decline ad personalisation, or remove ads entirely.

The following statements are **false** and must not appear anywhere — in the policy, the store listing, the onboarding, or any screen:

- "Zero data leaves your device."
- "No tracking."
- "Completely private."
- "100% private."
- "Your data never leaves your phone" — untrue while ad SDKs run.
- Anything implying the app is ad-free.

This directly contradicts statements currently in the codebase's own `docs/privacy-policy.html`, which asserts "No analytics or tracking SDKs" (line 279) while embedding a user-tracking SDK. That line must be rewritten, not reworded.

## 5. Consent

UMP is implemented and must be requested correctly:

- The consent form is shown **before** any ad request, never on first launch without context.
- Consent state gates every ad surface — `canRequestAds` already does this correctly and must be preserved.
- Declining consent suppresses ad personalisation; it must not degrade the core app in any way.
- The **privacy options form** must remain reachable in Settings so consent can be changed at any time, as Google's policy requires.
- Consent state must be stored and honoured across app updates and process death.

Regional behaviour (EEA/UK opt-in, US state opt-out regimes) is handled by UMP and must not be reimplemented.

## 6. Play declarations

Must be completed accurately and kept in agreement with the code. Discrepancies between the Data safety form and actual SDK behaviour are a policy violation, not a formality.

- **Data safety** — declare advertising ID collection, device and app activity, and approximate location. Do **not** declare health and fitness data as collected by us, because we do not collect it; but note that the app is a health app, which the Health apps declaration covers separately.
- **Advertising ID** declaration — required, since `AD_ID` is in the merged manifest.
- **Ads** declaration — the app contains ads.
- **Health apps declaration** — required, including for testing tracks (`07` §2).
- Re-review all of these when adding any SDK, changing an ad format, or adding any data path.

**Never** enable ad personalisation, sensitive-interest categories, or any audience segment derived from app content. If an ad-targeting feature is ever proposed, it requires a fresh privacy review — and for a reproductive-health app the answer should effectively always be no.

## 7. Protecting the §2 invariant

"No health data in ad requests" is the strongest privacy claim this product makes and it is currently upheld by the absence of code rather than by a control. That is fragile. Make it structural:

1. Wrap ad-request construction in a single function that takes **no parameters capable of carrying user content**. Do not expose `AdRequest.Builder` to feature modules.
2. Add a unit test asserting that the builder is constructed with defaults only.
3. Add a source-level check in CI that fails the build if `setCustomTargeting`, `addKeyword`, `setPublisherProvidedId`, `setLocation` or `NativeAdOptions.requestCustomAssets` appears anywhere in the app module.
4. Review any future ad-SDK upgrade for changed default collection behaviour — SDK defaults change without notice.

Item 3 is the durable protection: a future developer adding targeting gets a failing build rather than a silent privacy regression.

## 8. Code hygiene

- Move ad unit IDs out of Kotlin source (`Ads.kt:47-52`) into Gradle properties or remote config. They are not secrets, but hardcoding them blocks account switching and forces source edits. Keep the existing `BuildConfig.DEBUG` test/production split.
- Replace the hardcoded 28/22/14dp corner radii (`Ads.kt:241, 298, 274`) with design tokens (`14` §5.2) so ads do not read as visibly foreign.
- Reserve ad container height to prevent layout shift (`15` §11.4).

## 9. Commercial reality

State this plainly rather than hoping it is not true.

Ads fund the product. They also cost: some users will refuse to use a cycle tracker that shows advertising; some will uninstall; review sentiment is measurably affected by ad load; and a meaningful segment of users would rather pay than see ads.

Therefore:

- **Ship a real ad-free option.** A one-time purchase is the right model here, not a subscription — there is no recurring service cost that a subscription justifies for this feature, and one-time purchase avoids the churn and billing-expectation problems documented in `10` §4.
- Ad-free must remove **all** ad surfaces, including the interstitial.
- Users must be able to view, export and delete all their data **without paying** (`10` §4). Never paywall export or deletion.
- The free tier must remain fully functional for logging, calendar, reminders, insights and export.

## 10. Frequency and fatigue

Interstitial load is the primary uninstall driver. `15` §11.3 already specifies conservative gating: minimum 3 sessions, minimum 10 minutes between interstitials, and dropping rather than deferring when conditions are unmet.

Additionally:

- Interstitial impressions capped per day.
- If consent is declined, do not substitute other ad formats or increase frequency to compensate.
- Reminder frequency is governed independently by `13` §9. Ad load and notification load compound fatigue; the two budgets must be considered together even though they are measured separately.

## 11. Acceptance criteria

1. No ad request carries user content of any kind, and CI fails the build if targeting APIs appear in the app module.
2. Privacy policy states §4's true claims and none of its false ones.
3. Store listing states the app is ad-supported and does not claim local-only.
4. UMP consent is resolved before any ad request; privacy options are reachable in Settings.
5. Data safety, Advertising ID, Ads and Health declarations match actual SDK behaviour.
6. No ad on Calendar, Log, Settings or detail screens; interstitial gating per `15` §11.3.
7. Ad-free purchase removes all ad surfaces.
8. Export and data deletion remain available without payment.
9. Ad unit IDs are not in Kotlin source; ad containers use design tokens and reserve height.
10. No ad appears within 30 minutes of a health record being logged.