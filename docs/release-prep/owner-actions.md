# CycleCare — Play Console Preparation (Owner Actions)

This is the evidence the repository can produce. The submission itself
requires the owner's account and cannot be done from here.

## 1. Data safety form — answers from the verified implementation

Source: `scripts/guard-privacy-manifest.sh` (merged manifest, 2026-10-23),
`scripts/guard-no-ad-targeting.sh`, `app/src/main/AndroidManifest.xml`,
`docs/privacy-policy.html`, and `core/security` for the export cipher.

| Question | Answer | Evidence |
|---|---|---|
| Does the app collect or share user data? | Yes | AdMob + UMP transmit off-device. `guard-no-ad-targeting.sh`: OK. |
| Encrypted in transit? | Yes | `network_security_config.xml`: `cleartextTrafficPermitted="false"`. |
| Data deletion mechanism? | Yes — local reset | `AppResetRepository.resetAllLocalData()` wipes Room + DataStore + exports + alarms. |
| Account deletion? | Not applicable | No account, no login, no server. |
| Health data collected/shared? | No | `cyclecare.db` is on-device only; `cloud-backup` excluded; no HTTP client in code. |
| Personal info collected? | No | No name/email/phone/address field anywhere. |
| Files collected? | No | `.ccbackup` is user-initiated via `ACTION_SEND`; not auto-transmitted. |
| Approximate location | Collected & shared | AdMob derives it from IP. |
| App interactions | Collected & shared | Ad impressions/clicks. |
| Crash logs | Collected & shared | AdMob SDK. |
| Diagnostics | Collected & shared | AdMob SDK. |
| Device or other IDs | Collected & shared | `AD_ID`, App Set ID. |

## 2. Advertising ID declaration

Declare **YES**. The app requests `AD_ID` (transitive via `play-services-ads`).
Purposes: Advertising or marketing, Analytics.
Justification: AdMob banner + interstitials.

## 3. Ads declaration

Declare **YES**, the app contains ads.
- Banner on Today and Insights only.
- Interstitials gated by the eight conditions in `15` §11.3.
- No ad on Calendar, Log, Settings or any detail screen.

## 4. Health declaration

Declare as a menstrual-cycle / symptom / prediction tracker.
Health data is **not** shared with third parties for health purposes.

## 5. Permissions declaration

- `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, `INTERNET`, `ACCESS_NETWORK_STATE` — standard.
- `SCHEDULE_EXACT_ALARM` — special-access. Justification: user-selected exact times for PeriodPrep and LatePeriod reminders, delivered via `setExactAndAllowWhileIdle`. The app is fully functional without it (Tier 2 fallback).

## 6. Target API / content rating / audience

- Target API 36, min 26 — satisfies current requirement.
- Content rating: complete the IARC questionnaire. Utility/health tracker, no user-to-user communication, not directed at children.
- Target audience: 13+ (or 18+). Not designed for children under 13.

## 7. Privacy policy URL

Deploy `docs/privacy-policy.html` (effective 2026-10-19) to a public HTTPS URL.
Verify it displays the exact text including the AdMob/UMP disclosures.
Set the URL in: App content → Privacy policy.

## 8. Support channel

`support@cyclecare.app` must exist and be monitored before launch.

## 9. Store listing

- Truthful description; no cloud sync, no medical-grade accuracy, no unsupported predictions.
- Screenshots: use the 97 captured images (12 screens × 4 widths × 2 schemes, plus privacy at 360dp light).
- Localization checklist: strings are already in `strings.xml`; translate those.

## 10. Device matrix and Doze

- Physical-device Doze run: `19-doze-verification.md` is the execution sheet.
- Device matrix: fresh install, upgrade from v2, permission denial, backup/restore, process death, RTL, 320dp.
- Foldable and manual TalkBack: not claimable without hardware and a person.

## 11. What the repository already produces

- Signed AAB: `app/build/outputs/bundle/release/app-release.aab`
  - SHA-256: `f43ff998797f29806fd356dd68e51b803e2a555e10ca18048c17ef4304e4f275`
  - Signer: `CN=CycleCare Upload`
  - Validation: bundletool structural pass; jarsigner verified
- Release workflow: `.github/workflows/release.yml` (tag + dispatch, protected `release` environment, 4 secrets)
- Version: `versionCode` 7, `versionName` 1.0

## 12. Owner actions required before submission

1. Create the protected `release` environment in GitHub and add the four signing secrets.
2. Set the `CYCLECARE_ADVISORY_FEED` repository variable.
3. Deploy `docs/privacy-policy.html` to a public HTTPS URL.
4. Provision `support@cyclecare.app`.
5. Run the physical-device Doze test and record it in `19-doze-verification.md`.
6. Complete the Play Console declarations and store listing.
7. Execute the device matrix.
8. Perform the manual TalkBack pass.
9. Visual sign-off on the red/white/black palette.
