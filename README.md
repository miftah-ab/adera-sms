<div align="center">

<img src="https://img.shields.io/badge/Platform-Android%208.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white"/>
<img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white"/>
<img src="https://img.shields.io/github/actions/workflow/status/miftah-ab/adera-sms/build.yml?style=for-the-badge&label=CI%20Build&logo=github-actions&logoColor=white"/>
<img src="https://img.shields.io/badge/Version-1.0.3-F9A825?style=for-the-badge"/>
<img src="https://img.shields.io/badge/License-MIT-2E7D32?style=for-the-badge"/>

<br/><br/>

# 📵 Adera SMS

### *"No more 'sorry I missed your call' — send it before they ask."*
**"የጠፋ ጥሪ፣ ያመለጠ እድል"** · *A missed call, a missed opportunity*

A lightweight, offline-first Android app that automatically sends a customizable SMS reply  
when you miss a call — built for Ethiopian users, tested on budget devices.

[**⬇ Download APK**](https://github.com/miftah-ab/adera-sms/releases/latest) · [**📋 Report Bug**](https://github.com/miftah-ab/adera-sms/issues) · [**💡 Request Feature**](https://github.com/miftah-ab/adera-sms/issues)

</div>

---

## 📖 Table of Contents

- [Overview](#-overview)
- [Features](#-features)
- [Screenshots](#-screenshots)
- [Architecture](#-architecture)
- [Getting Started](#-getting-started)
  - [Download & Install](#download--install-sideload)
  - [Build from Source](#build-from-source)
  - [GitHub Actions CI/CD](#github-actions-cicd)
- [Configuration](#-configuration)
- [Privacy & Data Policy](#-privacy--data-policy)
- [Roadmap](#-roadmap)
- [Contributing](#-contributing)
- [License](#-license)

---

## 🌍 Overview

**Adera SMS** is a missed-call auto-reply application designed from the ground up for real-world conditions in Ethiopia and the broader East African market.

Unlike global competitors (Smarter, Auto Message), Adera SMS is built for:

| Reality | Adera SMS approach |
|---|---|
| Dual-SIM devices (Tecno, Infinix, Itel) | Per-SIM listener registered per subscription ID; strict SIM matching on send |
| Aggressive OEM battery killers | Foreground service + battery exemption guided setup on Home screen |
| Offline-first / no reliable internet | Core auto-reply loop has zero network dependencies |
| Sideload distribution (no Play Store) | GitHub Releases + in-app update checker (Vercel-hosted version.json) |
| Amharic + English | 3 English + 3 Amharic preset templates shipped out of the box |
| Short-code calls (700, 8000 etc.) | Automatically filtered — no SMS sent to carrier short-codes |
| Low-end hardware | Material 3 dark theme, system font, minimal memory footprint |

---

## ✨ Features

### Core (v1.0.3)

- **🔁 Automatic SMS Reply** — Sends a customizable message the moment a call is missed; no popup, no confirmation
- **📱 Dual-SIM Aware** — Registers one telephony listener per active subscription ID; SMS is sent via the exact SIM that received the missed call
- **🚫 Short-Code Filter** — Calls from carrier short-codes (< 7 digits) are silently ignored — no SMS attempt, nothing logged
- **⏰ Quiet Hours** — Configurable do-not-reply window stored as minutes-since-midnight; supports overnight wrap-around (e.g. 23:00–06:00)
- **🔄 10-Minute Cooldown** — Suppresses duplicate replies to the same caller within 10 minutes, keyed by SHA-256 hash of the number
- **📋 Template Library** — 3 English + 3 Amharic presets; up to 6 custom templates on the free tier (adjustable via Firebase Remote Config)
- **📊 Recents Log** — Full history with status chips: Sent · Failed · Quiet hrs · Cooldown · Pending · Limit Reached. Grouped by date, searchable by number, shows contact name if READ_CONTACTS is granted
- **🔒 Privacy-First** — Full phone numbers stored locally in SQLite; SHA-256 hash used for cooldown lookups only; no data ever leaves the device
- **🔋 Battery Survival** — `START_STICKY` foreground service + Android 14+ WorkManager expedited boot restart; Home screen guides battery exemption
- **⬆ In-App Updates** — Checks `adera-sms.vercel.app/downloads/version.json` on launch; shows soft banner for optional updates, full-screen blocking screen for forced updates; `disableCoreService` emergency kill-switch in JSON
- **📤 Share APK** — Send the APK to another device via Xender, SHAREit, or Bluetooth directly from Settings
- **📞 Contact Names** — Optional READ_CONTACTS permission resolves caller numbers to contact names in Recents and Home screen
- **🔥 Firebase Integration** — Crashlytics, Performance Monitoring, Analytics, Remote Config (daily cap + template limit), In-App Messaging (Pro upsell prompts), FCM (push notifications)
- **🌐 Works Offline** — Core auto-reply loop requires zero network. Firebase features degrade gracefully if offline.

### Planned (v1.1+)

- Per-contact custom reply rules
- Activity log export (CSV)
- SMS delivery report (read confirmation)
- Home screen widget for quick toggle
- WhatsApp Business fallback integration

---

## 📸 Screenshots

> *(Install the APK on a physical device and screenshots will be added here)*

| Onboarding | Home | Templates | Recents | Settings |
|---|---|---|---|---|
| Consent gate + Privacy Policy + Terms of Service | Master toggle + active template + recent activity | English & Amharic presets + custom editor | Date-grouped log, search, filter chips, contact names | Quiet hours, share APK, clear data, update check |

---

## 🏗 Architecture

```
adera-sms/
├── .github/workflows/build.yml           # CI/CD — debug on push, signed release APK on tag
├── app/
│   ├── build.gradle.kts                  # compileSdk=36, minSdk=26, versionCode=4 (1.0.3)
│   ├── google-services.json              # Firebase project config
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/com/adera/sms/
│           ├── AderaSmsApplication.kt    # Notification channels, DB seed (3+3 presets), Remote Config init
│           ├── MainActivity.kt           # Single activity; reads consentGiven → routes to Onboarding or Main
│           ├── analytics/
│           │   └── AnalyticsManager.kt   # Firebase Analytics wrapper (app_open, autoreply_sent, etc.)
│           ├── data/
│           │   ├── AppDatabase.kt        # Room singleton, schema v4, migrations 1→2→3→4
│           │   ├── Converters.kt         # CallStatus enum ↔ String type converter
│           │   ├── entity/
│           │   │   ├── AppSettings.kt    # Single-row settings (autoReply, quietHours, consent, heartbeat)
│           │   │   ├── CallLogEntry.kt   # Log row: callerNumber, hash, timestamp, simSlot, status
│           │   │   └── MessageTemplate.kt
│           │   └── dao/
│           │       ├── CallLogDao.kt     # CRUD + cooldown query + stuck-PENDING cleanup
│           │       ├── SettingsDao.kt    # observe/get/upsert, setAutoReplyEnabled, setQuietHours
│           │       └── TemplateDao.kt
│           ├── receiver/
│           │   └── BootReceiver.kt       # BOOT_COMPLETED + LOCKED_BOOT → schedules ServiceStartWorker
│           ├── service/
│           │   ├── AderaFirebaseMessagingService.kt  # FCM token + push notification display
│           │   ├── CallMonitorService.kt # Foreground service; TelephonyCallback (API31+) / PhoneStateListener (API26-30)
│           │   ├── ServiceStartWorker.kt # WorkManager expedited worker — starts CallMonitorService safely on Android 14+
│           │   └── SmsSenderWorker.kt    # Sends SMS via exact SIM, 1 retry, updates log SENT/FAILED
│           ├── update/
│           │   └── UpdateChecker.kt      # Fetches version.json from Vercel, returns UpdateStatus
│           └── ui/
│               ├── home/                 # HomeScreen + HomeViewModel (toggle, permissions, update status)
│               ├── templates/            # TemplateEditorScreen + TemplateViewModel (RC limit enforcement)
│               ├── activitylog/          # ActivityLogScreen (Recents) + ActivityLogViewModel
│               ├── settings/             # SettingsScreen, QuietHoursSheet, SettingsViewModel
│               ├── onboarding/           # OnboardingScreen (consent gate, Privacy Policy, Terms of Service)
│               ├── navigation/           # AderaNavGraph, Screen sealed class
│               ├── theme/                # Material 3 dark theme — Color, Type, Shape
│               └── update/               # ForceUpdateScreen (clears back stack, blocks navigation)
└── update-endpoint/
    └── version.json                      # Served from Vercel — source of truth for update checks
```

### Technology Stack

| Layer | Technology | Why |
|---|---|---|
| Language | Kotlin | Official Android language |
| UI | Jetpack Compose + Material 3 | Declarative, dark-mode-first |
| Database | Room (SQLite) v4 | Fully offline, zero backend |
| Background Work | WorkManager | Survives process kill; retry built-in |
| Call Detection | TelephonyCallback (API 31+) + PhoneStateListener (API 26–30) | Covers all target OS versions; dedicated executor (not mainExecutor) for Android 15 reliability |
| Navigation | Compose NavHost | Single-activity, type-safe routes |
| HTTP | HttpURLConnection + JSONObject | No Retrofit needed for one endpoint |
| Firebase | Analytics · Crashlytics · Performance · Remote Config · In-App Messaging · FCM | Observability, remote kill-switch, Pro upsell |
| CI/CD | GitHub Actions | Free, reproducible, tag-triggered signed releases |

### Call State Machine

```
Phone rings
     │
     ▼
 RINGING ─────────────────────── IDLE  ←── Missed call detected
     │                                          │
     ▼                                          ▼
 OFFHOOK ─────────────────────── IDLE      Gate 1: valid phone number? (≥7 digits)
  (answered — no reply)                    Gate 2: autoReplyEnabled?
                                           Gate 3: not in quiet hours?
                                           Gate 4: not in 10-min cooldown?
                                           Gate 5: below daily send cap? (default: 15/24h)
                                           Gate 6: default template exists?
                                                │
                                                ▼
                                     Insert PENDING log entry
                                     Enqueue SmsSenderWorker
                                                │
                                    Check SEND_SMS permission
                                    Send via exact SIM (subscriptionId)
                                    Append "\n\nBy Adera SMS" signature
                                                │
                                     Update log → SENT / FAILED
```

### Database Schema (v4)

| Table | Key columns |
|---|---|
| `app_settings` | `autoReplyEnabled`, `quietHoursStart/End` (min since midnight), `consentGiven`, `consentTimestamp`, `lastServiceHeartbeat` |
| `call_log_entries` | `callerNumber`, `callerNumberHash` (SHA-256), `timestamp`, `simSlot` (subscriptionId), `status` |
| `message_templates` | `text`, `language`, `isDefault`, `isPreset` |

**Call statuses:** `PENDING` · `SENT` · `FAILED` · `SUPPRESSED_QUIET_HOURS` · `SUPPRESSED_COOLDOWN` · `DAILY_LIMIT_REACHED`

---

## 🚀 Getting Started

### Download & Install (Sideload)

1. Go to [Releases](https://github.com/miftah-ab/adera-sms/releases/latest)
2. Download `app-release.apk`
3. On your Android phone: **Settings → Security → Install unknown apps** → allow your browser or Files app
4. Open the downloaded APK and tap **Install**
5. Accept the consent screen, then grant the 3 core permissions

**Minimum Android:** 8.0 Oreo (API 26)  
**Tested on:** Tecno Spark, Infinix Hot, Samsung Galaxy A-series, Xiaomi Redmi

---

### Build from Source

#### Prerequisites

| Tool | Version |
|---|---|
| Android Studio | Hedgehog 2023.1.1+ |
| JDK | 17 (Temurin recommended) |
| Android SDK | API 36 (compile), API 26 (min) |

#### Steps

```bash
# 1. Clone the repository
git clone https://github.com/miftah-ab/adera-sms.git
cd adera-sms

# 2. Open in Android Studio, or build from CLI:
./gradlew assembleDebug

# 3. Install on a connected physical device (telephony does not work on emulators)
./gradlew installDebug
```

> ⚠️ **Physical device required** — the telephony listener does not fire on emulators.  
> Test by calling your device from another phone while Adera SMS is running.

---

### GitHub Actions CI/CD

The pipeline in [`.github/workflows/build.yml`](.github/workflows/build.yml) runs automatically:

| Trigger | What happens |
|---|---|
| Push to `main` or `develop` | Debug APK built and uploaded as artifact |
| Pull request to `main` | Debug APK built (not published) |
| Tag `v*.*.*` (e.g. `v1.1.0`) | Signed release APK built + GitHub Release created |

#### Setting up signing (one-time)

```bash
# 1. Generate a keystore (keep this file SAFE — never commit it)
keytool -genkey -v -keystore keystore.jks -alias adera \
        -keyalg RSA -keysize 2048 -validity 10000

# 2. Base64-encode it
# macOS / Linux:
base64 keystore.jks | pbcopy

# Windows:
certutil -encode keystore.jks keystore.b64
# Copy the contents of keystore.b64 (excluding header/footer lines)
```

Add these **4 GitHub Actions secrets** under  
`Repository → Settings → Secrets and variables → Actions → New repository secret`:

| Secret name | Value |
|---|---|
| `SIGNING_KEY_BASE64` | Base64-encoded keystore.jks |
| `SIGNING_STORE_PASSWORD` | Keystore password |
| `SIGNING_KEY_ALIAS` | `adera` (or your alias) |
| `SIGNING_KEY_PASSWORD` | Key password |

#### Publishing a release

```bash
# 1. Bump versionCode and versionName in app/build.gradle.kts
# 2. Tag and push:
git tag v1.1.0
git push origin v1.1.0
# → CI builds, signs, and creates the GitHub Release automatically

# 3. Update update-endpoint/version.json with the new versionCode and re-deploy to Vercel
#    so existing users receive the in-app update notification
```

---

## ⚙️ Configuration

### Quiet Hours

Navigate to **Home → Quiet Hours** or **Settings → Quiet Hours**.  
Set a start and end time. Both same-day (e.g. 08:00–20:00) and overnight (e.g. 23:00–06:00) ranges are supported.  
Set start = end to disable (default is both = 0, disabled).

### Templates

Navigate to the **Templates** tab from the main screen.  
Select from 3 English or 3 Amharic presets, or tap **+** to write a custom message.  
The free tier allows up to **6 custom templates** (adjustable via Firebase Remote Config key `free_template_limit`).  
Messages over 160 characters will be sent as multi-part SMS.

### Update Endpoint

The update endpoint is hosted on Vercel:

```
https://adera-sms.vercel.app/downloads/version.json
```

Shape of `version.json`:

```json
{
  "latestVersionCode": 4,
  "minSupportedVersionCode": 1,
  "downloadUrl": "https://github.com/miftah-ab/adera-sms/releases/latest/download/app-release.apk",
  "releaseNotes": "Short-code filtering, stuck PENDING fix, explicit SMS permission guard",
  "disableCoreService": false
}
```

- **`latestVersionCode`** — shows a soft update banner to users below this
- **`minSupportedVersionCode`** — shows a full-screen blocking update to users below this
- **`disableCoreService`** — emergency kill-switch: set to `true` to stop the auto-reply service on all installed devices remotely *(currently parsed but not yet wired — see known issues)*

### Firebase Remote Config Keys

| Key | Default | Effect |
|---|---|---|
| `daily_send_cap` | `15` | Max auto-replies per 24-hour window |
| `free_template_limit` | `6` | Max custom (non-preset) templates on free tier |

---

## 🔒 Privacy & Data Policy

| Data | Stored | Where | Notes |
|---|---|---|---|
| Full phone number | ✅ Yes | Local SQLite only | Displayed in Recents and Home screen |
| SHA-256 hash of full number | ✅ Yes | Local SQLite only | Used only for 10-minute cooldown logic — never displayed |
| SMS template text | ✅ Yes | Local SQLite only | User-configured |
| Settings (toggle, quiet hours, consent) | ✅ Yes | Local SQLite only | `consentGiven` + `consentTimestamp` stored on acceptance |
| Analytics events | ✅ Yes | Firebase Analytics | Behavioral signals only: `app_open`, `autoreply_sent`, `toggle_changed`, `template_edited`. **No phone numbers. No message content.** |
| Crash reports | ✅ Yes | Firebase Crashlytics | Stack traces + device context keys (Android version, SIM count). No personal data. |
| Performance traces | ✅ Yes | Firebase Performance | Automatic traces only |
| FCM token | In memory | Firebase | Logged locally; not sent to any backend |

**The core auto-reply loop requires zero network.** Firebase features (analytics, Crashlytics, Remote Config, FCM) are the only network activity, and they degrade gracefully when offline.

The full Privacy Policy and Terms of Service are bundled in the app assets and shown on first launch.

---

## 📅 Roadmap

| Version | Milestone | Status |
|---|---|---|
| **v1.0** | Core loop · onboarding consent gate · template editor · Recents log · quiet hours · update checker | ✅ Complete |
| **v1.0.3** | Short-code filtering · stuck PENDING cleanup · explicit SEND_SMS permission guard · Firebase (Analytics, Crashlytics, Perf, Remote Config, FCM, In-App Messaging) | ✅ Complete |
| **v1.1** | Wire `disableCoreService` kill-switch · analytics opt-out toggle · per-contact rules · log export (CSV) | 🔜 Planned |
| **v1.2** | Home screen widget · WhatsApp fallback intent · delivery reports | 🔜 Planned |

---

## 🤝 Contributing

Contributions are welcome — especially:

- **Amharic translation review** — preset templates need verification by a native speaker
- **OEM-specific bug reports** on Tecno / Infinix / Itel devices (battery and SIM edge cases)
- **New language presets** (Oromo, Tigrinya, Somali) for a future release

### Contribution process

```bash
# 1. Fork the repo and create a branch
git checkout -b fix/your-fix-name

# 2. Build and test on a physical device
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk

# 3. Open a pull request — CI will build automatically
```

Please keep PRs focused. One fix per PR.  
Follow the existing code style: Kotlin official style guide, no Hilt, no Retrofit, no RxJava.

---

## 📄 License

```
MIT License

Copyright (c) 2026 Miftah

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

<div align="center">

**Built with ❤️ for Ethiopia**

*"No more 'sorry I missed your call' — send it before they ask."*

[⬆ Back to top](#-adera-sms)

</div>
