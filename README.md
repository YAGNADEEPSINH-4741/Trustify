# Trustify (SafeCall AI)

### AI-Powered Legitimate Caller Identification, InCall Telephony & False Spam Detection for Android

Trustify (SafeCall AI) solves a critical problem in modern telecommunications: **legitimate callers incorrectly flagged as spam by aggressive caller-identification databases.**

Common victims of false spam labelling:
* **E-Commerce & Courier Delivery Agents** (Amazon, Flipkart, Swiggy, Zomato, BlueDart, DTDC).
* **Cab & Auto Drivers** (Uber, Ola, Rapido).
* **Bank & Healthcare Representatives** (Customer support, verified appointment desks).
* **Job Recruiters & Hiring Managers**.
* **Technicians, Electricians, and Service Providers**.

When delivery agents make dozens or hundreds of calls a day, conventional caller-ID apps crowd-source unverified spam flags against their numbers. Users ignore or block these calls, causing deliveries, rides, and time-critical appointments to fail.

---

## Core Principle

> **A spam report does not automatically mean a caller is malicious, and an unknown number is not automatically safe.**

Trustify evaluates multi-signal verifiable evidence, cross-checks active dispatch associations, provides custom fullscreen in-call screens with hardware audio routing, and communicates caller credibility with complete transparency.

---

## Architecture Overview

```
                      ┌──────────────────────────────────────────────┐
                      │          Android Telecom Subsystem           │
                      │         (Incoming & Outgoing Events)         │
                      └──────────────────────┬───────────────────────┘
                                             │
                      ┌──────────────────────┴───────────────────────┐
                      ▼                                              ▼
       ┌──────────────────────────────┐              ┌──────────────────────────────┐
       │   SafeCallScreeningService   │              │    SafeCallInCallService     │
       │ (CallScreeningService Role)  │              │     (Default Dialer Role)    │
       │ - Heads-up verification alert│              │ - Custom Fullscreen UI       │
       │ - Non-blocking ring pass     │              │ - Hardware Audio Route       │
       └──────────────┬───────────────┘              │ - Speaker & Mute Control     │
                      │                              └──────────────┬───────────────┘
                      │                                             │
                      └──────────────────────┬──────────────────────┘
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
When a delivery driver's number is flagged as spam on external databases, Trustify cross-references active order dispatches. If an active delivery is confirmed, Trustify recovers the call as **VERIFIED LEGITIMATE**, marks it with a **★ FALSE SPAM RECOVERED** badge, and displays verified enterprise credentials.

### 3. Fullscreen InCall Calling Interface
* Android `InCallService` integration for custom call screens.
* **Answer & Decline**: Real telephony actions directly through Android Telecom.
* **Hardware Audio Routing**: Official `CallAudioState` integration for earpiece, speakerphone, and mute controls.
* **Outgoing & Incoming Support**: Smooth transitions for dialing, connecting, ringing, and active calls.
* **CPU WakeLock & Proximity Sensor**: Keeps audio streaming continuously without dropping voice when the screen sleeps or is held to the ear.

### 4. Zero-Compromise Privacy
* **No Contact Book Uploading**: Personal contacts are queried locally on-device and never sent to a remote server.
* **No Call Audio Recording**: Trustify never records or listens to phone conversations.
* **100% Offline Capability**: Core rules engine and local whitelist work without internet.

---

## Technology Stack

* **Platform**: Android (minSdk 26, targetSdk 35, compileSdk 35).
* **Language**: Kotlin 2.0.21.
* **UI Toolkit**: Jetpack Compose & Material 3.
* **Architecture**: MVVM + Clean Architecture with Repositories.
* **Telephony APIs**: Android `InCallService`, `CallScreeningService`, `RoleManager` (API 29+), `TelecomManager`.
* **Local Persistence**: Android SQLite (`SQLiteOpenHelper`) & `SharedPreferences`.
* **Backend**: Supabase (PostgreSQL, Row Level Security, PostgREST API).
* **Build System**: Gradle 8.13 with Version Catalog (`libs.versions.toml`) and Android Gradle Plugin 8.7.3.
* **Signing**: Production RSA-2048 V1, V2, V3 & V4 APK Signing Scheme.

---

## Project Structure

```
Trustify/
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
│       │   │   │   │   ├── ContactLookupHelper.kt
│       │   │   │   │   └── PhoneNumberNormalizer.kt
│       │   │   │   └── models/
│       │   │   │       ├── CallerClassification.kt
│       │   │   │       ├── VerificationStatus.kt
│       │   │   │       ├── Evidence.kt
│       │   │   │       ├── Warning.kt
│       │   │   │       ├── ContactInfo.kt
│       │   │   │       └── ClassificationResult.kt
│       │   │   ├── service/
│       │   │   │   ├── SafeCallInCallService.kt
│       │   │   │   ├── SafeCallScreeningService.kt
│       │   │   │   ├── CallManager.kt
│       │   │   │   ├── DefaultDialerManager.kt
│       │   │   │   ├── CallScreeningRoleManager.kt
│       │   │   │   └── ScreeningNotificationHelper.kt
│       │   │   ├── verification/
│       │   │   │   ├── VerificationProviders.kt
│       │   │   │   ├── MockProviders.kt
│       │   │   │   └── VerificationRegistry.kt
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
│       │   │       ├── call/
│       │   │       │   ├── IncomingCallActivity.kt
│       │   │       │   └── IncomingCallScreen.kt
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
│           ├── CallManagerTest.kt
│           ├── PhoneNumberNormalizerTest.kt
│           ├── ThemeModeTest.kt
│           └── VerificationProviderTest.kt
├── supabase/
│   ├── .env.example
│   ├── sample_seed.sql
│   └── migrations/
├── safecall-release.jks
├── install_to_device.ps1
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

---

## How to Build the Project

### Prerequisites
* **JDK 21**: OpenJDK 21 or Android Studio JBR.
* **Android SDK**: Build tools 34.0.0+, Android SDK Platform 35.
* **Git** installed on your system.

### 1. Clone the Repository
```bash
git clone https://github.com/YAGNADEEPSINH-4741/Trustify.git
cd Trustify
```

### 2. Set Java Home (if on Windows / PowerShell)
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
```

### 3. Run Automated Unit Tests
```bash
./gradlew testDebugUnitTest
```
*(Runs all 26 unit tests verifying deterministic classification, contact lookup, audio state transitions, and normalization).*

### 4. Build Signed Release APK
```bash
./gradlew assembleRelease
```
The production signed APK will be output at:
`app/build/outputs/apk/release/app-release.apk`

### 5. Build Debug APK
```bash
./gradlew assembleDebug
```
The debug APK will be output at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## How to Install the App

### Method A: Automated Installation via USB (Recommended for Developers)

1. On your Android phone, enable **Developer Options** and **USB Debugging**:
   * Go to **Settings > About phone**.
   * Tap **Build number** 7 times rapidly.
   * Go to **Settings > System > Developer options** and enable **USB debugging**.
2. Connect your phone to your computer via USB cable.
3. Run the automated installer script:
   ```powershell
   .\install_to_device.ps1
   ```
   *The script automatically detects the connected phone, installs the release APK, and launches the app.*

Alternatively, install manually using ADB:
```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

---

### Method B: Sideloading (Sharing via WhatsApp, Telegram, or Drive)

1. Send the generated `app-release.apk` to the target Android phone.
2. Tap the APK file to install:
   * If Google Play Protect shows *"Unrecognized app"*, tap **More details > Install anyway**.
3. Open **Trustify (SafeCall AI)**.

#### Important for Android 13, 14, and 15 (Restricted Settings):
Because Android 13+ restricts sideloaded apps from taking default phone app roles by default, follow these quick steps:
1. In the app, navigate to the **Settings** tab.
2. Tap **"Set Default"** under *Calling & Phone App Roles*.
3. If Android displays *"App was denied access to be default Phone app"*:
   * Tap **"1. Open App Info"** inside Trustify.
   * In the top-right corner of the App Info page, tap the **three vertical dots (⋮)**.
   * Tap **"Allow restricted settings"** and authenticate with your fingerprint or screen lock PIN.
   * Return to Trustify and tap **"2. Open Default Apps"** $\rightarrow$ Select **SafeCall AI** as your default Phone app.

---

## Testing Scenarios

Trustify includes built-in mock dispatches for real-time demonstration:

| Phone Number | Scenario | Expected Classification |
|---|---|---|
| `+91 98765 43210` | **Amazon Delivery Associate** (Spam on other apps, active dispatch in Trustify) | **VERIFIED LEGITIMATE** (★ False Spam Recovered) |
| `+91 99887 76655` | **Uber Driver** (Active ride MH 02 CK 4589) | **VERIFIED LEGITIMATE** (Active Dispatch Linked) |
| `+91 1800 202 6161`| **HDFC Customer Support** | **VERIFIED LEGITIMATE** (Corporate Registered) |
| `+91 98765 00000` | **Lapsed Vendor** (Registration expired) | **LIKELY LEGITIMATE** (Expired Registry Warning) |
| `+91 99999 99999` | **Robocall Telemarketer** (45+ spam reports) | **HIGH-RISK ALERT** (High Volume Spam) |
| Any unknown number| **Unknown Caller** | **UNKNOWN CALLER** (Rings normally) |

You can test any of these numbers instantly using:
1. **Interactive Test Buttons** in the **Settings** tab.
2. **Instant Number Lookup** on the **Dashboard** screen.

---

## Connecting Supabase Cloud Backend (Optional)

Trustify operates 100% offline by default using on-device SQLite caches. To enable live cloud sync:

1. **Apply Database Migrations:**
   Run the SQL scripts in order using the Supabase SQL Editor:
   - `supabase/migrations/20261013000000_safecall_schema.sql`
   - `supabase/migrations/20261013000001_safecall_rls_policies.sql`
   - `supabase/sample_seed.sql` (Optional seed data)

2. **Configure in the App:**
   - In Trustify, open the **Settings** tab.
   - Enter your **Supabase Project URL** and **Public Anon Key**.
   - Tap **Save Configuration**. The app will now query the cloud database with graceful offline fallback.

---

## License

This project is licensed under the Apache License 2.0.
