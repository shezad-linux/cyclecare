# CycleCare — Google Play Data Safety Compliance Audit

**Audit Date:** 2026-10-23  
**Auditor:** Senior Android Privacy Engineer & Google Play Policy Compliance Auditor  
**Application ID:** `com.cyclecare.tracker`  
**Target Release:** Version Code `7`, Version Name `1.0`  
**Repository Baseline:** Branch `main` (commit `ba0d859453d397ee20a10396af001c0c1c41bf21`)  
**Target Delivery Path:** `play-console/data-safety/`  
**Status:** **AUDIT COMPLETE — EVIDENCE-BASED DISCLOSURE SPECIFICATION PRODUCED**

---

## 1. Executive Summary & Regulatory Context

This audit evaluates the privacy architecture, local storage, external network transmissions, permissions, and third-party SDK behavior of CycleCare (`com.cyclecare.tracker`) to establish an evidence-based Data safety declaration for Google Play Console.

The app's Play Console currently displays the compliance warning:
> **“We found issues with your Data safety form”**  
> *(Flow steps: 1. Overview $\rightarrow$ 2. Data collection and security $\rightarrow$ 3. Data types $\rightarrow$ 4. Data usage and handling $\rightarrow$ 5. Preview)*

### Key Findings at a Glance:

1. **Local-First Menstrual Health Invariant Verified:**  
   CycleCare is implemented as a local-first application. Menstrual cycle start/end dates, cycle lengths, symptom logs (mood, pain, energy, discharge, medication, notes), user profile data (age band, baseline cycle lengths, goals), and prediction calculations are stored exclusively on-device in a local Room database (`cyclecare.db`) and DataStore preferences. **Zero health, symptom, or user profile records are transmitted off-device.** Under Google Play Data safety rules, this data is **NOT collected** and **NOT shared**.

2. **Zero First-Party Networking:**  
   The application codebase contains zero first-party HTTP/network clients (`HttpURLConnection`, OkHttp, Retrofit, Ktor, WebViews, or plain sockets). The application operates no backend server, requires no user account, and implements no remote authentication.

3. **Active Third-Party SDK Data Transmission (AdMob & UMP):**  
   The release runtime classpath bundles Google Mobile Ads (`play-services-ads:25.2.0`) and Google User Messaging Platform (`user-messaging-platform:4.0.0`). These SDKs automatically collect and share specific data types off-device over HTTPS for advertising, measurement, and fraud prevention:
   - **Location:** Approximate location (derived from IP address).
   - **App Activity:** App interactions (ad views, clicks, taps, video interactions, consent interactions).
   - **App Info and Performance:** Diagnostics and Crash logs (SDK performance metrics, ad loading latency, error codes).
   - **Device or Other IDs:** Advertising ID (`AD_ID`), App Set ID, and device/network identifiers.

4. **Zero Analytics or Crash Reporting SDKs:**  
   Neither Firebase Analytics (`firebase-analytics`) nor Firebase Crashlytics (`firebase-crashlytics`) is bundled in the release build. Crashlytics was deliberately excised, and `createCrashReporter()` resolves to `NoOpCrashReporter` (`app/src/main/java/com/cyclecare/app/Diagnostics.kt:89`).

5. **Strict Health Isolation from Ad SDK:**  
   All ad requests are constructed via `AdRequest.Builder().build()` with no keywords, targeting parameters, custom targeting, or publisher-provided identifiers (`app/src/main/java/com/cyclecare/app/Ads.kt:452`). This invariant is enforced in CI via `scripts/guard-no-ad-targeting.sh`. Menstrual health records never reach the ad network.

6. **Root Cause of the Play Console Warning:**  
   In earlier planning and unsubmitted drafts, developers of local-first apps frequently select "No, my app does not collect or share any user data" because they operate no backend. However, Google Play Console's automated APK/AAB analyzer inspects the merged release manifest, detecting `com.google.android.gms.permission.AD_ID`, `ACCESS_ADSERVICES_*` permissions, and the Mobile Ads SDK components. Declaring "no data collected" or omitting AdMob's data types creates a direct contradiction with the binary, triggering the Data safety form error. In addition, repository records (`CycleCare-Redesign-Docs/22-phase-completion-plan.md:964, 1116-1120`) confirm that the Play Data safety questionnaire remains unsubmitted (Blocker B4).

---

## 2. Technical Evidence & Repository Verification

### 2.1 Manifest & Permissions Analysis

The merged release manifest was generated via `./gradlew :app:processReleaseMainManifest` and inspected at:  
`app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml`

#### Manifest Permissions & Purpose Verification:

| Permission | Source Manifest | Purpose in Shipped App | Data Safety Impact |
|---|---|---|---|
| `android.permission.INTERNET` | `app/src/main/AndroidManifest.xml:8` | Network communication required by Google Mobile Ads and UMP SDKs. | Enables SDK off-device transmission. |
| `android.permission.ACCESS_NETWORK_STATE` | `app/src/main/AndroidManifest.xml:9` | AdMob SDK checks network connectivity before requesting ads. | Used internally by AdMob SDK. |
| `android.permission.POST_NOTIFICATIONS` | `app/src/main/AndroidManifest.xml:5` | Android 13+ (API 33+) runtime permission to post cycle and wellness reminders. | Local OS permission; no data collected. |
| `android.permission.RECEIVE_BOOT_COMPLETED` | `app/src/main/AndroidManifest.xml:6` | Reschedules reminder alarms in `ReminderRescheduleReceiver` after device reboot. | Local OS permission; no data collected. |
| `android.permission.SCHEDULE_EXACT_ALARM` | `app/src/main/AndroidManifest.xml:7` | Delivers time-sensitive reminders at exact user-selected times via `AlarmManager.setExactAndAllowWhileIdle`. | Special access permission; requires separate Play declaration. |
| `android.permission.USE_BIOMETRIC` | Transitive (`androidx.biometric:1.1.0`) | Authenticates local app-lock PIN/biometrics in `AppLockGate.kt`. | Handled purely on-device by Android OS biometric prompt; zero biometric data stored or transmitted. |
| `android.permission.USE_FINGERPRINT` | Transitive (`androidx.biometric:1.1.0`) | Legacy fallback for biometric authentication. | Purely local OS processing. |
| `android.permission.WAKE_LOCK` | Transitive (`androidx.work:work-runtime:2.10.0`) | Keeps CPU awake momentarily for WorkManager tasks and alarm broadcasts. | Purely local OS processing. |
| `android.permission.FOREGROUND_SERVICE` | Transitive (`androidx.work:work-runtime:2.10.0`) | WorkManager expedited job execution. | Purely local OS processing. |
| `com.google.android.gms.permission.AD_ID` | Transitive (`play-services-ads:25.2.0`) | Allows AdMob to access the Google Advertising ID on Android 13+. | **Must be declared as Device or other IDs collected/shared.** |
| `android.permission.ACCESS_ADSERVICES_AD_ID` | Transitive (`ads-adservices:1.0.0-beta05`) | Android Privacy Sandbox Attribution & Ad ID access. | Handled under Advertising ID disclosure. |
| `android.permission.ACCESS_ADSERVICES_ATTRIBUTION` | Transitive (`ads-adservices:1.0.0-beta05`) | Android Privacy Sandbox Attribution reporting. | Handled under Ad interaction disclosure. |
| `android.permission.ACCESS_ADSERVICES_TOPICS` | Transitive (`ads-adservices:1.0.0-beta05`) | Android Privacy Sandbox Topics API. | Handled under Ad interaction disclosure. |

*Critical Observation:* CycleCare requests **no** location permissions (`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`), **no** camera permissions, **no** microphone permissions, **no** contacts permissions, and **no** storage/media permissions.

---

### 2.2 Resolved Release Dependency Tree

The release runtime dependency tree was extracted via `./gradlew -q :app:dependencies --configuration releaseRuntimeClasspath`:

#### Embedded Third-Party SDKs:
1. **Google Mobile Ads (AdMob):** `com.google.android.gms:play-services-ads:25.2.0`
   - Transitive components:
     - `com.google.android.gms:play-services-ads-api:25.2.0`
     - `com.google.android.gms:play-services-ads-identifier:18.0.0`
     - `com.google.android.gms:play-services-appset:16.0.1`
     - `com.google.android.gms:play-services-basement:18.9.0`
     - `com.google.android.gms:play-services-tasks:18.2.0`
     - `com.google.android.gms:play-services-measurement-sdk-api:20.1.2` *(API interfaces only; measurement base)*
     - `androidx.privacysandbox.ads:ads-adservices:1.0.0-beta05`
     - `androidx.privacysandbox.ads:ads-adservices-java:1.0.0-beta05`
     - `androidx.webkit:webkit:1.12.1`
     - `androidx.browser:browser:1.8.0`
2. **Google User Messaging Platform (UMP):** `com.google.android.ump:user-messaging-platform:4.0.0`
   - Provides GDPR/EEA and US state consent management UI and stores consent strings in `SharedPreferences`.
3. **AndroidX Architecture Components:**
   - `androidx.room:room-runtime:2.7.1` (local SQLite database)
   - `androidx.datastore:datastore-preferences:1.1.1` (local key-value store)
   - `androidx.work:work-runtime-ktx:2.10.0` (local background task scheduler)
   - `androidx.biometric:biometric:1.1.0` (local biometric authentication)
   - `androidx.compose.*:1.7.5` / Material3 (UI framework)

#### Confirmed Absent SDKs:
- **Firebase Analytics (`com.google.firebase:firebase-analytics`):** ABSENT.
- **Firebase Crashlytics (`com.google.firebase:firebase-crashlytics`):** ABSENT.
- **Third-Party Trackers (AppsFlyer, Adjust, Facebook SDK, Mixpanel, Sentry):** ABSENT.

---

### 2.3 Network Architecture & Security Verification

1. **Cleartext Traffic Policy:**  
   `app/src/main/res/xml/network_security_config.xml:27` declares:
   ```xml
   <network-security-config>
       <base-config cleartextTrafficPermitted="false">
           <trust-anchors>
               <certificates src="system" />
           </trust-anchors>
       </base-config>
   </network-security-config>
   ```
   Cleartext HTTP communication is strictly blocked across all domains and build types. All traffic generated by AdMob and UMP uses HTTPS/TLS.

2. **First-Party Network Code:**  
   Full-text ripgrep verification over `app/`, `core/`, and `feature/` confirmed:
   - Zero occurrences of `java.net.HttpURLConnection`
   - Zero occurrences of OkHttp, Retrofit, Apache HttpClient, or Ktor
   - Zero occurrences of `android.webkit.WebView`
   - Zero background network workers or sync adapters

---

### 2.4 Data Storage, Cloud Backup & Transfer Rules

1. **Cloud Backup Excluded:**  
   `app/src/main/res/xml/data_extraction_rules.xml:26-36` excludes all storage domains from cloud backup on Android 12+ (API 31+):
   ```xml
   <cloud-backup>
       <exclude domain="root" path="." />
       <exclude domain="file" path="." />
       <exclude domain="database" path="." />
       <exclude domain="sharedpref" path="." />
       <exclude domain="external" path="." />
       <exclude domain="device_root" path="." />
       <exclude domain="device_file" path="." />
       <exclude domain="device_database" path="." />
       <exclude domain="device_sharedpref" path="." />
   </cloud-backup>
   ```
   On API 26–30, `app/src/main/res/xml/backup_rules.xml:25-31` similarly excludes all domains. Health data is never backed up to Google Drive.

2. **Device-to-Device Transfer:**  
   `data_extraction_rules.xml:60-63` enables direct cable/Wi-Fi transfer of `database/cyclecare.db` and `file/datastore/` exclusively during user migration to a new device on Android 12+.

3. **Local Encrypted Export & Import:**  
   Export files (`.ccbackup`) are encrypted locally with AES-256-GCM using keys derived via PBKDF2 with SHA-256 (100,000 iterations) from a user-supplied passphrase (`core/security/src/main/java/com/cyclecare/core/security/AesGcmPayloadCipher.kt:20-55`). File transfer occurs only when the user explicitly triggers an Android system share intent (`Intent.ACTION_SEND` with `FileProvider` in `feature/export/src/main/java/com/cyclecare/feature/export/ExportScreen.kt:508-522`).

4. **Data Deletion / Reset Functionality:**  
   `LocalAppResetRepository.resetAllLocalData()` (`core/data/src/main/java/com/cyclecare/core/data/AppResetRepository.kt:27-46`) allows the user to immediately and completely wipe:
   - All Room database tables (`database.clearAllTables()`)
   - Export directory (`filesDir/exports`)
   - User DataStore preferences (`preferences.clear()`)
   - Notification permissions cache
   - All armed reminder alarms

---

### 2.5 Health Data Isolation & Ad Targeting Guards

1. **Zero Ad Targeting Invariant:**  
   Ad requests in `app/src/main/java/com/cyclecare/app/Ads.kt:452` are constructed using:
   ```kotlin
   val adRequest = AdRequest.Builder().build()
   ```
   No custom targeting parameters, keywords, user IDs, or content URLs are attached.

2. **Automated CI Enforcement:**  
   `scripts/guard-no-ad-targeting.sh:29` checks the entire application module for forbidden ad-targeting APIs:
   `FORBIDDEN_APIS='setCustomTargeting|addKeyword|setPublisherProvidedId|requestCustomAssets|customTargeting|CategoryExclusion|setLocation|categoryExclusions'`  
   The script passes with zero matches.

3. **Consent Gate:**  
   `app/src/main/java/com/cyclecare/app/GoogleMobileAdsConsentManager.kt:80-102` enforces that `MobileAds.initialize()` and ad loading are deferred until UMP consent is obtained (`canRequestAds == true`).

4. **Notification Privacy & Lock-Screen Redaction:**  
   `app/src/main/java/com/cyclecare/app/ReminderDelivery.kt:270-297` enforces that reminder notifications set `NotificationCompat.VISIBILITY_PRIVATE` and provide a `setPublicVersion()` that redacts cycle details on the lock screen.

---

## 3. Third-Party SDK Data Safety Analysis (AdMob & UMP)

According to Google’s official developer guidance for Google Mobile Ads SDK on Android ([Google Play Data Disclosure Guide](https://developers.google.com/admob/android/privacy/play-data-disclosure)), the SDK automatically collects and transmits data off-device.

### Detailed SDK Data Mapping:

```
+----------------------------------------------------------------------------------------------------+
| Data Category                | Data Type            | Collected | Shared | Ephemeral | Required    |
+----------------------------------------------------------------------------------------------------+
| Location                     | Approximate location | Yes       | Yes    | No        | Required    |
| App activity                 | App interactions     | Yes       | Yes    | No        | Required    |
| App info and performance     | Crash logs           | Yes       | Yes    | No        | Required    |
| App info and performance     | Diagnostics          | Yes       | Yes    | No        | Required    |
| Device or other IDs          | Device or other IDs  | Yes       | Yes    | No        | Required    |
+----------------------------------------------------------------------------------------------------+
```

### Purpose Justifications:
- **Approximate Location:** Inferred from the user's IP address to serve geographically relevant ads, comply with regional ad regulations, and prevent ad fraud.
- **App Interactions:** Measures ad impressions, click-through rates, video views, and ad lifecycle interactions.
- **Crash Logs & Diagnostics:** Evaluates SDK initialization time, ad load latency, rendering crashes, and network failure diagnostics.
- **Device or Other IDs:** Accesses Google Advertising ID (`AD_ID`), App Set ID, and hardware identifiers for frequency capping, fraud prevention, and personalized/non-personalized ad delivery.

---

## 4. Google Play Data Safety Questionnaire: Complete Form Mapping

The following table defines the defensible, compliant declarations for CycleCare across all sections of the Google Play Console Data safety form.

### Section 1: Overview & Data Collection / Security

| Questionnaire Prompt | Compliant Declaration | Technical Evidence & Regulatory Justification |
|---|---|---|
| **Does your app collect or share any of the required user data types?** | **Yes** | Required because AdMob and UMP SDKs transmit telemetry, ad IDs, and approximate location off-device. |
| **Is all of the user data collected by your app encrypted in transit?** | **Yes** | Verified. `network_security_config.xml:27` disallows cleartext (`cleartextTrafficPermitted="false"`). All AdMob and UMP endpoints communicate exclusively over TLS/HTTPS. |
| **Do you provide a way for users to request that their data be deleted?** | **Yes** *(Local Reset)* / **No** *(Account deletion not applicable)* | CycleCare provides an in-app "Reset All Local Data" mechanism (`AppResetRepository.kt:27`) that deletes all user records locally. However, CycleCare has **no user accounts**; therefore, Play Console's URL-based Account Deletion policy does not apply. For ad telemetry, data is retained according to Google's standard retention policies. |

---

### Section 2: Data Types & Categories

```
+--------------------------------------------------------------------------------------------------------------------+
| Google Play Data Category    | Play Data Type       | Status       | Handling Details                              |
+--------------------------------------------------------------------------------------------------------------------+
| Location                     | Approximate location | COLLECTED    | Collected & shared by AdMob via IP address.   |
|                              | Precise location     | NOT HANDLED  | No GPS or network location requested.         |
| Personal info                | All types            | NOT HANDLED  | No name, email, phone, or address collected.  |
| Financial info               | All types            | NOT HANDLED  | No payment or purchase processing in app.     |
| Health and fitness           | Health info          | NOT HANDLED* | Stays strictly on-device in Room SQLite.       |
|                              | Fitness info         | NOT HANDLED  | Not collected or shared.                      |
| Messages                     | All types            | NOT HANDLED  | Not collected or shared.                      |
| Photos and videos            | All types            | NOT HANDLED  | Not collected or shared.                      |
| Audio files                  | All types            | NOT HANDLED  | Not collected or shared.                      |
| Files and docs               | All types            | NOT HANDLED* | Exports are user-initiated via system share.  |
| Calendar                     | All types            | NOT HANDLED  | Not collected or shared.                      |
| Contacts                     | All types            | NOT HANDLED  | Not collected or shared.                      |
| App activity                 | App interactions     | COLLECTED    | Collected & shared by AdMob (views/clicks).   |
|                              | In-app search        | NOT HANDLED  | Not collected or shared.                      |
|                              | Installed apps       | NOT HANDLED  | Not collected or shared.                      |
|                              | Other user content   | NOT HANDLED  | Not collected or shared.                      |
| App info and performance     | Crash logs           | COLLECTED    | Collected & shared by AdMob SDK.              |
|                              | Diagnostics          | COLLECTED    | Collected & shared by AdMob SDK.              |
|                              | Other performance    | NOT HANDLED  | Not collected or shared.                      |
| Device or other IDs          | Device or other IDs  | COLLECTED    | Collected & shared by AdMob (`AD_ID`, AppSet).|
+--------------------------------------------------------------------------------------------------------------------+
```

*\*Important Policy Clarification on Health and Fitness:*  
Google Play Data Safety rules define "collected" as transmitting data off the device. Because cycle records, symptom logs, and predictions are stored purely in on-device SQLite and are never transmitted to any server, they must **NOT** be declared as "collected" or "shared" in the Data safety questionnaire. Declaring health data as collected would trigger false requirements for medical backend encryption and HIPAA/BPA compliance. (Note: CycleCare is declared separately under Google Play's dedicated **Health apps declaration** as a menstrual tracking app).

---

### Section 3: Data Usage and Handling (Per-Data-Type Declarations)

#### 1. Approximate Location (Location $\rightarrow$ Approximate location)
- **Collection:** Yes
- **Sharing:** Yes (shared with Google AdMob and ad demand partners)
- **Ephemeral Processing:** No (processed and logged by ad servers)
- **Data Required or Optional:** Required (automatic for ad requests)
- **Purposes:**
  1. *Advertising or marketing*
  2. *Analytics*
  3. *Fraud prevention, security, and compliance*

#### 2. App Interactions (App activity $\rightarrow$ App interactions)
- **Collection:** Yes
- **Sharing:** Yes (shared with Google AdMob and ad demand partners)
- **Ephemeral Processing:** No
- **Data Required or Optional:** Required
- **Purposes:**
  1. *Advertising or marketing*
  2. *Analytics*
  3. *Fraud prevention, security, and compliance*

#### 3. Crash Logs (App info and performance $\rightarrow$ Crash logs)
- **Collection:** Yes
- **Sharing:** Yes (shared with Google AdMob)
- **Ephemeral Processing:** No
- **Data Required or Optional:** Required
- **Purposes:**
  1. *Analytics*
  2. *Fraud prevention, security, and compliance*

#### 4. Diagnostics (App info and performance $\rightarrow$ Diagnostics)
- **Collection:** Yes
- **Sharing:** Yes (shared with Google AdMob)
- **Ephemeral Processing:** No
- **Data Required or Optional:** Required
- **Purposes:**
  1. *Analytics*
  2. *Fraud prevention, security, and compliance*

#### 5. Device or Other IDs (Device or other IDs $\rightarrow$ Device or other IDs)
- **Collection:** Yes
- **Sharing:** Yes (shared with Google AdMob and ad demand partners)
- **Ephemeral Processing:** No
- **Data Required or Optional:** Required
- **Purposes:**
  1. *Advertising or marketing*
  2. *Analytics*
  3. *Fraud prevention, security, and compliance*

---

## 5. Privacy Policy Alignment (`docs/privacy-policy.html`)

The published privacy policy in `docs/privacy-policy.html` (effective date: 19 October 2026, commit `9e0d8384`) was reviewed for consistency:

1. **AdMob & UMP Disclosures (§2, §4, §9):**  
   The policy accurately states that CycleCare embeds Google AdMob and UMP, which read the device advertising identifier and collect device, network, and usage data for advertising. **Matches Data safety declaration.**
2. **Local Health Data (§2, §3, §4):**  
   The policy explicitly states that cycle history, symptoms, mood, notes, and medications stay strictly on-device in a local database and are not sent to any server. **Matches Data safety declaration.**
3. **Backup & Device Transfer (§3, §9):**  
   The policy correctly discloses that cloud backup is disabled, but device-to-device transfer is enabled on Android 12+. **Matches Data safety declaration.**
4. **Permissions (§5, §9):**  
   The policy enumerates `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, and `SCHEDULE_EXACT_ALARM`. **Matches merged manifest.**
5. **No Account Required (§2):**  
   The policy confirms no account or login exists. **Matches Play Console account deletion exemption.**
6. **Support Channel (§10):**  
   Names `support@cyclecare.app`. Must be actively monitored by the owner before launch.

---

## 6. Official Play Console CSV Export & Import Verification

The official Google Play Console Data safety CSV export was ingested, archived, and updated:
- **Export Source:** `play-console/data-safety/data-safety-export.csv` (783 lines, 217 unique question IDs).
- **Import Output:** `play-console/data-safety/data-safety-import.csv` (783 lines, exactly 64 modified rows).

### Summary of Discrepancies Resolved in `data-safety-import.csv`:
1. **Removed False Personal Info:** Cleared `PSL_NAME` (L24) and all 4 usage response rows (L62, L64, L66, L67). CycleCare has no accounts or name fields.
2. **Removed False Health Info Collection:** Cleared `PSL_HEALTH` (L48) and all 4 usage response rows (L518, L520, L522, L523). Health data is local-only SQLite storage, not collected off-device.
3. **Corrected Deletion Commitment:** Replaced false `DATA_DELETION_NO_AUTO_DELETED` (L14) with `DATA_DELETION_YES` (L12) and `PSL_DATA_DELETION_URL` set to `https://cyclecare.app/privacy` (L15) (linking to local data reset / privacy contact guidance).
4. **Populated 5 Missing AdMob Data Types:**
   - `PSL_APPROX_LOCATION` (L37) + 10 usage/purpose rows (L309-L325).
   - `PSL_USER_INTERACTION` (L56) + 10 usage/purpose rows (L670-L686).
   - `PSL_CRASH_LOGS` (L52) + 8 usage/purpose rows (L594-L609).
   - `PSL_PERFORMANCE_DIAGNOSTICS` (L53) + 8 usage/purpose rows (L613-L628).
   - `PSL_DEVICE_ID` (L61) + 10 usage/purpose rows (L765-L781).
5. **Structural Integrity:** Verified with 100% question ID, response ID, requirement, and label parity against the official Google Play Console export template.

