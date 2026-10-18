# Trustify

### Trustify — Intelligent Caller Identification and False Spam Detection

> **Tagline:** *Your Calls. Your Privacy. Your Trust.*

Trustify is a privacy-first, on-device Android telephony and caller-identification application designed to protect users against one of the largest systemic problems in modern telecommunications: **legitimate callers incorrectly flagged and blocked as spam by crowd-sourced databases.**

---

## The False Spam Problem

In conventional crowd-sourced caller-ID applications, any caller who places a high volume of daily calls is quickly and indiscriminately reported as "spam". This causes severe disruption for:

* **E-Commerce & Courier Delivery Agents** (Amazon, Flipkart, Swiggy, Zomato, BlueDart, DTDC).
* **Ride-Hailing Drivers** (Uber, Ola, Rapido).
* **Bank & Healthcare Desks** (Customer support, verified appointment desks, emergency callbacks).
* **Job Recruiters & Hiring Managers**.
* **Technicians & Local Service Providers**.

When delivery associates call to drop off a parcel, users unknowingly ignore or auto-block the call because a crowd-sourced app marked it as spam. Delivery attempts fail, rides get canceled, and urgent notifications are missed.

---

## Core Trustify Principle

> **Your personal Trustify data stays on your device.**
> **A spam report does not automatically mean a caller is malicious, and an unknown number is not automatically safe.**

Trustify evaluates verifiable multi-signal evidence, cross-checks active booking dispatches, provides custom fullscreen in-call screens with hardware audio routing, and transparently distinguishes personal whitelist trust from certified corporate identity—**all locally on your device without cloud backends.**

---

## 100% Privacy-First Architecture

Unlike traditional caller ID apps, **Trustify has completely eliminated external cloud dependencies (such as Supabase or analytics backends) and revoked internet access (`INTERNET` permission removed)**:

* **Zero Cloud Data Transmission**: Personal contacts, call logs, screening history, and trusted numbers never leave your device.
* **No Contact Book Uploading**: Contact resolution executes strictly via the Android `ContactsContract` content provider on-device.
* **No Call Recording or Eavesdropping**: Trustify never records or listens to calls.
* **No Account Required**: No signup, phone number verification, or credentials required to use Trustify.
* **Encrypted On-Device Storage**: Local SQLite database with automated exclusion from cloud backups (`backup_rules.xml`).
* **Instant Offline Classification**: Real-time deterministic rules engine running in <15ms directly in `CallScreeningService` and `InCallService`.

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
       │ - Sub-15ms local decision    │              │ - Speaker & Mute Control     │
       └──────────────┬───────────────┘              │ - CPU & Proximity WakeLocks  │
                      │                              └──────────────┬───────────────┘
                      │                                             │
                      └──────────────────────┬──────────────────────┘
                                             ▼
                      ┌──────────────────────────────────────────────┐
                      │         CallClassificationEngine             │
                      │   (Deterministic Transparent Rules Engine)   │
                      └───────┬──────────────────────────────┬───────┘
                              │                              │
                              ▼                              ▼
      ┌───────────────────────────────────┐      ┌─────────────────────────────┐
      │       Verification Registry       │      │     Local SQLite Database   │
      │ - Delivery Provider (Mock Active) │      │ - Personal Trusted Callers  │
      │ - Ride Provider (Mock Active)     │      │ - Screening Activity Ledger │
      │ - Enterprise Registry (Local)     │      │ - Local Report Drafts       │
      │ - Offline Business Cache          │      │ - Encrypted Local Settings  │
      └───────────────────────────────────┘      └─────────────────────────────┘
                                  (100% On-Device • Zero Cloud Tracking)
```

---

## Key Features

### 1. Evidence-Based Caller Classifications
* **`VERIFIED LEGITIMATE`**: Supported by certified enterprise corporate records or independently verified active booking dispatches.
* **`LIKELY LEGITIMATE`**: Consistent positive history, user personal trust, or supporting booking context without full certificate authority.
* **`UNKNOWN`**: Insufficient evidence. Caution advised. The phone rings normally without blocking.
* **`POTENTIAL SPAM`**: Credible indicators of telemarketing or unsolicited robocalls based on local reputation data.
* **`HIGH-RISK ALERT`**: Confirmed identity spoofing, critical fraud, or malicious impersonation.

### 2. False Spam Recovery
When a delivery driver's number is flagged as spam on external databases, Trustify cross-references active order dispatches. If an active delivery is confirmed, Trustify recovers the call as **VERIFIED LEGITIMATE**, marks it with a **★ FALSE SPAM RECOVERED** badge, and displays verified enterprise credentials.

### 3. Fullscreen InCall Calling Interface
* Android `InCallService` integration for custom call screens.
* **Answer & Decline**: Real telephony actions directly through Android Telecom.
* **Hardware Audio Routing**: Official `CallAudioState` integration for earpiece, speakerphone, and mute controls.
* **Outgoing & Incoming Support**: Smooth transitions for dialing, connecting, ringing, and active calls.
* **CPU WakeLock & Proximity Sensor**: Keeps audio streaming continuously without dropping voice when the screen sleeps or is held to the ear.

### 4. Quick Reply / Send Message During Incoming Call
* **Three Immediate Options**: When a call arrives, choose **Accept**, **Decline**, or **Send Message**.
* **Predefined Quick Responses**: 6 one-tap professional replies:
  1. *"I'm busy right now. I'll call you back later."*
  2. *"I'm out of station at the moment."*
  3. *"I'll call you in 5 minutes."*
  4. *"I'm driving right now. I'll contact you when I arrive."*
  5. *"I can't talk right now. Please message me."*
  6. *"I'm in a meeting. I'll call you back when I'm free."*
* **Custom Message Composer**: "Write Your Own Message" with live character counter, GSM-7/UCS-2 SMS segment tracking, and one-handed ergonomic controls.
* **Real SMS Dispatch**: Uses Android's native `SmsManager` with multi-segment concatenation and delivery verification, with graceful fallback to Android messaging apps (`ACTION_SENDTO`) if permission is denied.
* **Lockscreen-Safe In-Window UI**: Smooth animated bottom sheet rendered directly in the calling window—never blocked by Android keyguard or window token restrictions.

### 5. Personal Trusted Callers vs. Public Identity
* Whitelist personal contacts, gate security, or local clinic numbers.
* Personal trust remains on your device and **never** falsely marks an unverified caller as a publicly verified business.

### 6. Instant Theme System
* Three immediate modes: **System Default**, **Light**, and **Dark**.
* High-contrast WCAG AA compliant palettes tailored for both sunlight readability and OLED power conservation.

---

## Technology Stack

* **Platform**: Android (minSdk 26, targetSdk 35, compileSdk 35).
* **Language**: Kotlin 2.0.21.
* **UI Toolkit**: Jetpack Compose & Material 3.
* **Architecture**: MVVM + Clean Architecture with Repositories.
* **Telephony APIs**: Android `InCallService`, `CallScreeningService`, `RoleManager` (API 29+), `TelecomManager`.
* **Local Persistence**: Android SQLite (`SQLiteOpenHelper`) & `SharedPreferences`.
* **Network Permissions**: **NONE** (Zero internet access for guaranteed privacy).
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
│       │   │   │   └── local/
│       │   │   │       ├── SafeCallDatabaseHelper.kt
│       │   │   │       ├── LocalRepositories.kt
│       │   │   │       └── PreferencesManager.kt
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

### 1. Set Java Home (PowerShell / Windows)
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
```

### 2. Run Automated Unit Tests
```bash
./gradlew testDebugUnitTest
```
*(Runs automated unit tests validating deterministic classification, contact lookup, audio state transitions, and normalization).*

### 3. Build Signed Release APK
```bash
./gradlew assembleRelease
```
The production signed APK will be output at:
`app/build/outputs/apk/release/app-release.apk`

### 4. Build Debug APK
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
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

### Method B: Sideloading (Sharing via WhatsApp, Telegram, or Drive)

1. Send the generated `app-release.apk` to the target Android phone.
2. Tap the APK file to install:
   * If Google Play Protect shows *"Unrecognized app"*, tap **More details > Install anyway**.
3. Open **Trustify**.

#### Important for Android 13, 14, and 15 (Restricted Settings):
Because Android 13+ restricts sideloaded apps from taking default phone app roles by default, follow these quick steps:
1. In the app, navigate to the **Settings** tab.
2. Tap **"Set Default"** under *Calling & Phone App Roles*.
3. If Android displays *"App was denied access to be default Phone app"*:
   * Tap **"1. Open App Info"** inside Trustify.
   * In the top-right corner of the App Info page, tap the **three vertical dots (⋮)**.
   * Tap **"Allow restricted settings"** and authenticate with your fingerprint or screen lock PIN.
   * Return to Trustify and tap **"2. Open Default Apps"** $\rightarrow$ Select **Trustify** as your default Phone app.

---

## Built-In Testing Scenarios

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

## License

This project is licensed under the Apache License 2.0.
