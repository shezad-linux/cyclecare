# Product Requirements

> ## ⚠️ PARTIALLY SUPERSEDED — 17 October 2026
>
> **Current requirements: `CycleCare-Redesign-Docs/09-product-requirements.md`.**
>
> This v1 PRD is accurate about user needs and non-goals but is now contradicted on three points by decisions taken during the redesign:
>
> | This document says | Current decision |
> |---|---|
> | "Cloud sync in v1" is a non-goal | Sync **is** in scope — opt-in client-side encrypted sync (`17-sync-and-key-management.md`) |
> | "Keep the app fully free… without paid-access flows" | A **one-time purchase for an ad-free experience** ships alongside ads (`16-monetization-and-ads.md`). Export and data deletion remain free, always |
> | Estimates described as a v1 goal, unspecified in form | Predictions now emit a **calibrated range** with a measured coverage target, not a point date (`11`, `12`) |
>
> Its non-goals remain binding and are restated in the current PRD: no diagnosis, no contraception claims, no remote AI analysis of health records, no social features, and no monetisation of reproductive-health data.

# Product Requirements

## Product Positioning

CycleCare is a privacy-first wellness tracker for menstrual cycles, symptoms, and routine care. It is not a medical device and does not provide contraception, diagnosis, or treatment guidance.

## Primary Users

- Teens and adults starting cycle tracking
- Users managing PMS and symptom patterns
- Users who want period and fertile-window estimates without cloud dependence

## v1 Goals

- Complete onboarding without account creation
- Log cycles and daily symptoms offline
- Surface period, ovulation, and fertile-window estimates
- Deliver reminders, privacy lock, full insights, and local export
- Keep the app fully free and local-first without paid-access flows; ads may appear in non-blocking surfaces

## Success Criteria

- A first-time user can complete onboarding and see useful predictions in under 3 minutes
- All core flows remain available offline after install
- Sensitive screens do not expose health data in lock-screen previews
- Every shipped feature remains available without account creation or purchase state
- Ads and ad privacy consent do not receive cycle history, symptoms, notes, profile details, or exported reports

## Non-Goals

- Real-time social or partner collaboration
- Clinical recommendations
- Cloud sync in v1
- Paid-access monetization experiments in v1
