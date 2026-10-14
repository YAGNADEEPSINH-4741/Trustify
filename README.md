# SafeCall AI

### AI-Powered Legitimate Caller Identification & False Spam Detection for Android

SafeCall AI solves a widespread problem in modern telecommunications: **legitimate callers incorrectly flagged as spam by aggressive caller-identification databases.**

Common victims of false spam labelling:
* **E-Commerce & Courier Delivery Agents** (Amazon, Flipkart, Swiggy, Zomato, BlueDart, DTDC).
* **Cab & Auto Drivers** (Uber, Ola, Rapido).
* **Bank & Healthcare Representatives** (Customer support, verified appointment desks).
* **Job Recruiters & Hiring Managers**.
* **Technicians, Electricians, and Service Providers**.

When delivery agents make dozens or hundreds of calls a day, other caller-ID apps often crowd-source unverified spam flags against their numbers. Users ignore or block these calls, causing deliveries, rides, and appointments to fail.

---

## Core Principle

> **A spam report does not automatically mean a caller is malicious, and an unknown number is not automatically safe.**

SafeCall AI never relies on simple crowd votes or fabricated confidence percentages. It evaluates multi-signal verifiable evidence, cross-checks active dispatch associations, and communicates uncertainty with complete transparency.

---

## Architecture Overview

```
                      ┌──────────────────────────────────────────────┐
                      │          Android Telecom Subsystem           │
                      │         (Incoming Call Screening Event)      │
                      └──────────────────────┬───────────────────────┘
                                             │
                                             ▼
                      ┌──────────────────────────────────────────────┐
                      │          SafeCallScreeningService            │
                      │  - DisallowCall: false (rings normally)      │
                      │  - Posts rich False-Spam Recovery alert      │
                      └──────────────────────┬───────────────────────┘
                                             │
                                             ▼
                      ┌──────────────────────────────────────────────┐
                      │         CallClassificationEngine             │
                      │   (Deterministic Transparent Rules Engine)   │
                      └───────┬──────────────┬──────────────┬────────┘
                              │              │              │
                              ▼              ▼              ▼
     ┌────────────────────────────┐ ┌────────────────┐ ┌────────────────────────┐
     │   Verification Registry    │ │  Local SQLite  │ │   Supabase Backend     │
     │ - Delivery Provider (Mock) │ │ - Trusted List │ │ - verified_businesses  │
     │ - Ride Provider (Mock)     │ │ - Call History │ │ - number_reports       │
     │ - Business Provider (Mock) │ │ - Cached Orgs  │ │ - verification_events  │
     └────────────────────────────┘ └────────────────┘ └────────────────────────┘
```

---

## Key Features

### 1. Evidence-Based Caller Classifications
* **`VERIFIED LEGITIMATE`**: Supported by certified enterprise corporate records or independently verified active booking dispatches.
* **`LIKELY LEGITIMATE`**: Consistent positive history, user personal trust, or supporting booking context without full certificate authority.
* **`UNKNOWN`**: Insufficient evidence. Caution advised. The phone rings normally without blocking.
* **`POTENTIAL SPAM`**: Credible indicators of telemarketing or unsolicited robocalls.
* **`HIGH-RISK ALERT`**: Confirmed identity spoofing, critical fraud, or malicious impersonation.

### 2. False Spam Recovery
When a delivery driver's number is flagged as spam on external databases, SafeCall AI cross-references active order dispatches. If an active delivery is confirmed, SafeCall AI recovers the call as **VERIFIED LEGITIMATE**, marks it with a **★ FALSE SPAM RECOVERED** badge, and allows your phone to ring normally.

### 3. Modular Verification Architecture
Official provider interfaces allow plug-and-play integrations:
* `DeliveryVerificationProvider`: Integrates with e-commerce logistics APIs.
* `RideVerificationProvider`: Integrates with ride-hailing partner driver APIs.
* `BusinessVerificationProvider`: Integrates with corporate telecommunications registries.
* Includes explicitly labelled test-only mock providers for immediate local evaluation.

### 4. Zero-Compromise Privacy
* **No Contact Book Uploading**: Your personal contacts are never sent to a server.
* **No Call Audio Recording**: SafeCall AI never listens to or records phone calls.
* **100% Offline Capability**: Core rules engine and local whitelist work without internet.
* **Strict Separation of Trust**: Personal whitelist numbers are never converted into public business identities.

---

## Technology Stack

* **Platform**: Android (minSdk 26, targetSdk 35, compileSdk 35).
* **Language**: Kotlin 2.0.21.
* **UI Toolkit**: Jetpack Compose & Material 3.
* **Architecture**: MVVM + Clean Architecture with Repositories.
* **System APIs**: Android `CallScreeningService`, `RoleManager` (API 29+), Android Telecom framework.
* **Local Persistence**: Android SQLite (`SQLiteOpenHelper`) & `SharedPreferences`.
* **Backend**: Supabase (PostgreSQL, Row Level Security, PostgREST API).
* **Build System**: Gradle 8.13 with Version Catalog (`libs.versions.toml`) and Android Gradle Plugin 8.7.3.

---

## Project Structure

```
d:/spam/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/safecallai/app/
│       │   │   ├── MainActivity.kt
│       │   │   ├── SafeCallApplication.kt
│       │   │   ├── domain/
│       │   │   │   ├── engine/
│       │   │   │   │   ├── CallClassificationEngine.kt
│       │   │   │   │   └── PhoneNumberNormalizer.kt
│       │   │   │   └── models/
│       │   │   │       ├── CallerClassification.kt
│       │   │   │       ├── VerificationStatus.kt
│       │   │   │       ├── Evidence.kt
│       │   │   │       ├── Warning.kt
│       │   │   │       ├── ClassificationResult.kt
│       │   │   │       └── CallerProfile.kt
│       │   │   ├── verification/
│       │   │   │   ├── VerificationProviders.kt
│       │   │   │   ├── MockProviders.kt
│       │   │   │   └── VerificationRegistry.kt
│       │   │   ├── service/
│       │   │   │   ├── SafeCallScreeningService.kt
│       │   │   │   ├── CallScreeningRoleManager.kt
│       │   │   │   └── ScreeningNotificationHelper.kt
│       │   │   ├── data/
│       │   │   │   ├── local/
│       │   │   │   │   ├── SafeCallDatabaseHelper.kt
│       │   │   │   │   ├── LocalRepositories.kt
│       │   │   │   │   └── PreferencesManager.kt
│       │   │   │   ├── remote/
│       │   │   │   │   └── SupabaseClient.kt
│       │   │   │   └── repository/
│       │   │   │       └── SafeCallRepository.kt
│       │   │   └── ui/
│       │   │       ├── navigation/Screen.kt
│       │   │       ├── components/StatusBadges.kt
│       │   │       ├── viewmodels/SafeCallViewModel.kt
│       │   │       ├── screens/
│       │   │       │   ├── OnboardingScreen.kt
│       │   │       │   ├── DashboardScreen.kt
│       │   │       │   ├── CallerDetailsScreen.kt
│       │   │       │   ├── RecentActivityScreen.kt
│       │   │       │   ├── TrustedCallersScreen.kt
│       │   │       │   ├── ReportNumberScreen.kt
│       │   │       │   └── SettingsScreen.kt
│       │   │       └── theme/
│       │   │           ├── Color.kt
│       │   │           ├── Theme.kt
│       │   │           └── Type.kt
│       │   └── res/
│       └── test/java/com/safecallai/app/
│           ├── CallClassificationEngineTest.kt
│           └── PhoneNumberNormalizerTest.kt
├── supabase/
│   ├── .env.example
│   ├── sample_seed.sql
│   └── migrations/
│       ├── 20261013000000_safecall_schema.sql
│       └── 20261013000001_safecall_rls_policies.sql
├── gradle/
│   └── libs.versions.toml
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

---

## Build & Test Instructions

### Prerequisites
* JDK 21 (OpenJDK 21 or Android Studio JBR).
* Android SDK (API 34 or 35).

### 1. Run Automated Unit Tests
```bash
./gradlew testDebugUnitTest
```

### 2. Build Debug APK
```bash
./gradlew assembleDebug
```
The output APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## Testing Incoming Calls on a Physical Android Phone

Follow these steps to test call screening and false-spam recovery on a physical device:

### Step 1: Enable USB Debugging
1. On your Android phone, go to **Settings > About phone**.
2. Tap **Build number** 7 times to enable Developer options.
3. Go to **Settings > System > Developer options** and toggle on **USB debugging**.

### Step 2: Install SafeCall AI
Connect your phone via USB cable and run:
```powershell
& "C:\Users\YAGNADEEPSINH\AppData\Local\Android\Sdk\platform-tools\adb.exe" install -r app/build/outputs/apk/debug/app-debug.apk
```

### Step 3: Grant Call Screening Role
1. Open **SafeCall AI** on your device.
2. Complete the onboarding screen and tap **Grant Call Screening Role**.
3. Android will prompt: *"Set SafeCall AI as your default caller ID & spam app?"* -> Tap **Set as default**.
4. Alternatively, you can verify this manually under:
   **Settings > Apps > Default apps > Caller ID & spam app > Select SafeCall AI**.

### Step 4: Test Pre-Configured Scenarios

SafeCall AI includes built-in mock dispatches for real-time demonstration:

| Phone Number | Scenario | Expected Classification |
|---|---|---|
| `+91 98765 43210` | **Amazon Delivery Associate** (Spam on other apps, active dispatch in SafeCall) | **VERIFIED LEGITIMATE** (★ False Spam Recovered) |
| `+91 99887 76655` | **Uber Driver** (Active ride MH 02 CK 4589) | **VERIFIED LEGITIMATE** (Active Dispatch Linked) |
| `+91 1800 202 6161`| **HDFC Customer Support** | **VERIFIED LEGITIMATE** (Corporate Registered) |
| `+91 98765 00000` | **Lapsed Vendor** (Registration expired) | **LIKELY LEGITIMATE** (Expired Registry Warning) |
| `+91 99999 99999` | **Robocall Telemarketer** (45+ spam reports) | **HIGH-RISK ALERT** (High Volume Spam) |
| Any unknown number| **Unknown Caller** | **UNKNOWN CALLER** (Rings normally) |

You can test any of these numbers instantly using the **Test / Lookup Any Number** bar on the Dashboard!

---

## Connecting Supabase Backend

SafeCall AI functions completely offline by default. If you wish to connect a Supabase project:

1. **Apply Database Migrations:**
   Run the SQL scripts in order using the Supabase SQL Editor:
   - `supabase/migrations/20261013000000_safecall_schema.sql`
   - `supabase/migrations/20261013000001_safecall_rls_policies.sql`
   - `supabase/sample_seed.sql` (Optional seed data)

2. **Configure in the App:**
   - In SafeCall AI, open the **Settings** tab.
   - Enter your **Supabase Project URL** and **Public Anon Key**.
   - Tap **Save Configuration**. The app will now query the cloud database with graceful offline fallback.

---

## Implemented vs. Unimplemented Features

### Implemented & Ready:
- [x] Full Jetpack Compose & Material 3 UI (7 complete screens).
- [x] Deterministic rules-based Classification Engine.
- [x] Automated unit test suite with 10+ edge cases.
- [x] False-spam recovery system with active dispatch correlation.
- [x] Android `CallScreeningService` declaration and lifecycle handling.
- [x] `RoleManager` (API 29+) call-screening role integration.
- [x] Local SQLite persistence (`SafeCallDatabaseHelper`).
- [x] Multi-provider verification architecture (`DeliveryVerificationProvider`, `RideVerificationProvider`, `BusinessVerificationProvider`).
- [x] Labelled development mock providers.
- [x] Supabase PostgREST client with anonymous key support.
- [x] Complete Supabase SQL migrations with strict Row Level Security.
- [x] Offline mode support with local cache fallback.

### Future Official Integrations (Requires Partner API Credentials):
- [ ] Official production OAuth APIs with e-commerce portals (Amazon Logistics Partner API, Flipkart Logistics API, Swiggy Partner Connect). *The architecture and provider interfaces are fully established to plug these in.*
- [ ] Direct telecom STIR/SHAKEN carrier cryptographic attestation lookup (requires carrier-grade telecom integration).
