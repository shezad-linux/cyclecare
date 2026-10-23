# CycleCare — Data Safety Unresolved Items & Owner Action Plan

**Audit Date:** 2026-10-23  
**Auditor:** Senior Android Privacy Engineer & Google Play Policy Compliance Auditor  
**Application ID:** `com.cyclecare.tracker`  
**Target Delivery Path:** `play-console/data-safety/data-safety-unresolved.md`  
**Status:** **IMPORT CSV PRODUCED — PENDING OWNER PLAY CONSOLE SUBMISSION & URL HOSTING**

---

## 1. Official Play Console CSV Export & Import CSV — [RESOLVED]

### Resolution Summary:
- The official Play Console export CSV was provided directly by the developer and archived at:
  `play-console/data-safety/data-safety-export.csv`
- Structural analysis confirmed exact schema alignment (783 total rows, 217 unique question IDs, 5 columns RFC 4180).
- Differential analysis identified the root causes of the Play Console warning:
  1. False declaration of Personal info (Name) (`PSL_NAME`).
  2. False declaration of Health info collection (`PSL_HEALTH`) (local Room SQLite storage is NOT collection under Google Play definitions).
  3. False 90-day automatic deletion claim (`DATA_DELETION_NO_AUTO_DELETED`).
  4. Complete omission of all 5 Google Mobile Ads SDK / UMP transmitted data types (`PSL_APPROX_LOCATION`, `PSL_USER_INTERACTION`, `PSL_CRASH_LOGS`, `PSL_PERFORMANCE_DIAGNOSTICS`, `PSL_DEVICE_ID`).
- All 64 discrepancy rows were corrected and verified 1:1, producing:
  `play-console/data-safety/data-safety-import.csv`
- Validation tests passed with zero structural errors. Ready for immediate import into Google Play Console.

---

## 2. Unresolved Regulatory & Console Declarations (Blocker B4)

The Data safety form warning in Play Console (`“We found issues with your Data safety form”`) is part of a broader set of unsubmitted Play Console declarations documented as Blocker B4 in `CycleCare-Redesign-Docs/24-final-production-readiness-audit.md:26, 325`:

| Declaration Item | Console Section | Status | Required Owner Action |
|---|---|---|---|
| **Data Safety Questionnaire** | App content $\rightarrow$ Data safety | **Ready to Import** | Import `data-safety-import.csv` via Play Console, verify Preview, and click Save. |
| **Advertising ID Declaration** | App content $\rightarrow$ Advertising ID | **Open** | Declare **YES**, the app uses the advertising ID. Check the boxes for **Advertising or marketing** and **Analytics**. |
| **Ads Declaration** | App content $\rightarrow$ Ads | **Open** | Declare **YES**, my app contains ads. (Banners on Today/Insights, interstitials per spec `15` §11.3). |
| **Health Apps Declaration** | App content $\rightarrow$ Health apps | **Open** | Declare as a health/wellness app tracking menstrual cycles and symptoms. Declare that health data is **not** shared with third parties. |
| **SCHEDULE_EXACT_ALARM Justification** | App content $\rightarrow$ Special app access | **Open** | Submit justification that exact alarms are required for user-scheduled cycle and wellness alerts delivered at the exact minute chosen by the user. |
| **Content Rating Questionnaire** | App content $\rightarrow$ Content rating | **Open** | Complete the IARC questionnaire. CycleCare is a utility/health tracker with no user-to-user communication. Not directed at children. |
| **Target Audience & Content** | App content $\rightarrow$ Target audience | **Open** | Declare target age group: 13+ (or 18+). The app is not designed for children under 13. |

---

## 3. Live Hosted Privacy Policy URL

### Finding:
- The repository contains an updated, legally accurate privacy policy at `docs/privacy-policy.html` (effective date 19 October 2026).
- However, Google Play Console requires a publicly accessible, live HTTPS URL for the privacy policy before release approval.
- An unhosted, local-only HTML file cannot be verified by Google Play reviewers or web crawlers.

### Required Owner Action:
1. Deploy `docs/privacy-policy.html` to a public web hosting service (e.g., GitHub Pages, Firebase Hosting, Cloudflare Pages, or the official domain `https://cyclecare.app/privacy`).
2. Verify that the live URL displays the exact text of `docs/privacy-policy.html` including the October 19, 2026 effective date and AdMob/UMP disclosures.
3. Update the Privacy policy URL in Google Play Console: **App content** $\rightarrow$ **Privacy policy**.

---

## 4. Support Email Verification (`support@cyclecare.app`)

### Finding:
- `docs/privacy-policy.html:403` and store listing documentation list `support@cyclecare.app` as the official support and privacy inquiry contact.
- `CycleCare-Redesign-Docs/24-final-production-readiness-audit.md:26` notes that the external inbox and operational monitoring for this address have not been independently confirmed.

### Required Owner Action:
1. Confirm that the mailbox `support@cyclecare.app` is provisioned, active, and regularly monitored for user privacy requests, data deletion inquiries, and compliance notices.
2. Ensure that emails sent to `support@cyclecare.app` receive responses within statutory response timeframes (e.g., GDPR 30-day requirement).

---

## 5. Data Deletion Mechanism Representation in Play Console

### Finding:
- CycleCare does not implement user accounts, logins, or remote server storage.
- All user-entered health data is stored locally on the device in Room SQLite.
- The app provides an in-app "Reset All Local Data" button (`SettingsRoute.kt:91` $\rightarrow$ `AppResetRepository.kt:27`) that immediately wipes all database rows, preferences, and exports.
- In Google Play Console, the Data Safety form asks:  
  *“Do you provide a way for users to request that their data be deleted?”*

### Owner Configuration Options:
- **Option A (Configured by Default in `data-safety-import.csv`):**  
  Answers **Yes**, with `https://cyclecare.app/privacy` provided as the deletion URL. The hosted privacy policy explains that uninstalling the app or using the in-app "Reset All Local Data" button completely deletes all personal data, and provides `support@cyclecare.app` for inquiries regarding third-party ad telemetry. Displays the "You can request that data be deleted" badge on the store listing.
- **Option B (Alternative):**  
  Answers **No**, with no URL required, because CycleCare has no user accounts and operates no remote server. To select Option B, change lines 12, 13, and 15 of `data-safety-import.csv` as detailed in `data-safety-validation.txt`.

---

## 6. Physical Device Doze Verification (Blocker B3 / P7-05)

### Finding:
- Notification alarm delivery under battery-saving Doze has been tested on emulator (`dumpsys deviceidle force-idle`), but spec `13` §17 requires physical-device demonstration under true battery-saving Doze prior to production sign-off.
- While this does not directly alter the Data safety declaration, it gates the final production release approval alongside Blocker B4.

### Required Owner Action:
- Execute the physical device Doze test run documented in `CycleCare-Redesign-Docs/19-doze-verification.md` and record the results.
