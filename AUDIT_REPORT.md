# ADERA SMS — DEEP PRE-PRODUCTION CODE, SECURITY & LOGIC AUDIT REPORT

**Audit Date:** August 17, 2026  
**Auditor Role:** Principal Software Engineer, Security Engineer, Backend Architect & QA Lead  
**Scope:** Entire repository (`com.adera.sms` Android native codebase, build configurations, CI/CD pipelines, and dependencies)  
**Status:** Diagnostic Audit Report Only — No Source Code Modified  

---

# 1. EXECUTIVE SUMMARY

An exhaustive, hostile pre-production code, security, and logic audit of the **Adera SMS** Android application was conducted. The codebase consists of an on-device Kotlin application built using Jetpack Compose, Material 3, Room SQLite, WorkManager, Android Telephony APIs, and Firebase SDKs (Crashlytics, Analytics, Remote Config, In-App Messaging, and Cloud Messaging).

### Key Assessment Metrics:
* **Total Findings:** 12
* **Critical Findings:** 1
* **High Findings:** 3
* **Medium Findings:** 4
* **Low Findings:** 2
* **Informational Findings:** 2
* **Most Dangerous Issue:** **SEC-001** (Complete base64 production release signing key and plaintext keystore passwords stored in the repository root).
* **Biggest Reliability Risk:** **LOG-001** (API 31+ CallLog resolution lacks timestamp filtering, causing auto-replies to be sent to arbitrary past callers on race/mismatch).
* **Biggest Business-Logic Risk:** **CON-001** (Non-atomic check-then-act pattern allows duplicate SMS dispatch and daily cap bypass on simultaneous incoming calls).
* **Biggest Security Risk:** **REL-001** (Navigation route crash on unencoded URLs during forced update) and **SEC-001** (Compromised app signing credentials).
* **Production Readiness Assessment:** **NOT READY**

The application exhibits clean architecture and good adherence to Android modern UI practices, but contains critical security exposure in build credentials and high-risk concurrency/data-routing edge cases that must be addressed before deployment to real users.

---

# 2. ARCHITECTURE SUMMARY & SYSTEM RECONSTRUCTION

```
                             ┌────────────────────────────────────────────────────────┐
                             │                     ANDROID OS                         │
                             │  ┌───────────────────────┐  ┌───────────────────────┐  │
                             │  │   TelephonyManager    │  │       CallLog         │  │
                             │  │ (PhoneState/Callback) │  │   (ContentProvider)   │  │
                             │  └───────────┬───────────┘  └───────────▲───────────┘  │
                             └──────────────┼──────────────────────────┼──────────────┘
                                            │ Call state event         │ Query missed number
                                            ▼                          │ (API 31+ fallback)
┌──────────────────────────────────────────────────────────────────────┴───────────────────────────┐
│ ADERA SMS APPLICATION LAYER                                                                      │
│                                                                                                  │
│  ┌────────────────────────────────────────────────────────────────────────────────────────────┐  │
│  │ CallMonitorService (Foreground Service - START_STICKY)                                     │  │
│  │                                                                                            │  │
│  │  State Machine: RINGING ──(no offhook)──> IDLE (Missed Call Detected)                      │  │
│  │                                                                                            │  │
│  │  Gate Checks (in-memory / Room):                                                           │  │
│  │   1. Settings: autoReplyEnabled == true                                                    │  │
│  │   2. Quiet Hours: isWithinQuietHours(settings) == false                                    │  │
│  │   3. Cooldown: isInCooldown(sha256(number)) == false (10 min window)                       │  │
│  │   4. Daily Cap: countSentSince(24h) < dailyCap (RemoteConfig default 15)                   │  │
│  │   5. Template: getDefaultTemplate() != null                                                │  │
│  │                                                                                            │  │
│  │  Writes: CallLogEntry(status = PENDING)                                                    │  │
│  └────────────────────────────────────────┬───────────────────────────────────────────────────┘  │
│                                           │ Enqueues OneTimeWorkRequest                          │
│                                           ▼                                                      │
│  ┌────────────────────────────────────────────────────────────────────────────────────────────┐  │
│  │ WorkManager: SmsSenderWorker (CoroutineWorker with 1 Linear Retry @ 30s)                   │  │
│  │                                                                                            │  │
│  │  1. Resolves SmsManager for subId                                                          │  │
│  │  2. Appends mandatory "\n\nBy Adera SMS" signature                                         │  │
│  │  3. Registers dynamic BroadcastReceiver (com.adera.sms.SMS_SENT_<UUID>)                    │  │
│  │  4. Dispatches smsManager.sendTextMessage(...)                                             │  │
│  │  5. Updates CallLogEntry -> SENT or FAILED                                                 │  │
│  │  6. Dispatches Firebase Analytics event (autoreply_sent)                                   │  │
│  └────────────────────────────────────────┬───────────────────────────────────────────────────┘  │
│                                           │                                                      │
│                                           ▼                                                      │
│  ┌────────────────────────────────────────────────────────────────────────────────────────────┐  │
│  │ Local Persistence (Room SQLite - 100% On-Device)                                           │  │
│  │  - AppSettings (id=1, master toggle, quiet hours, consent, heartbeat)                       │  │
│  │  - MessageTemplate (preset & custom templates, single isDefault=1)                         │  │
│  │  - CallLogEntry (callerNumber, callerNumberHash, timestamp, simSlot, status)               │  │
│  └────────────────────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                                  │
│  ┌────────────────────────────────────────────────────────────────────────────────────────────┐  │
│  │ Presentation Layer (Jetpack Compose + Material 3)                                          │  │
│  │  MainActivity -> NavHost: Onboarding, Home, TemplateEditor, Recents (ActivityLog),         │  │
│  │                           Settings, ForceUpdate                                            │  │
│  └────────────────────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                                  │
│  ┌──────────────────────────────────────────────┐  ┌──────────────────────────────────────────┐  │
│  │ Firebase Integrations                        │  │ Update Checker                           │  │
│  │  - Analytics (mandatory, non-PII events)     │  │  - HTTP GET version.json (Vercel)        │  │
│  │  - Crashlytics (breadcrumbs, device state)   │  │  - Sideload APK redirect / ForceUpdate   │  │
│  │  - In-App Messaging (cap / template limit)   │  └──────────────────────────────────────────┘  │
│  │  - Cloud Messaging (FCM token / push alerts) │                                                │
│  │  - Remote Config (daily_send_cap, limits)    │                                                │
│  └──────────────────────────────────────────────┘                                                │
└──────────────────────────────────────────────────────────────────────────────────────────────────┘
```

* **Frontend Architecture**: Single-Activity application (`MainActivity.kt`) hosting a Jetpack Compose `NavHost`. UI components observe unidirectional state streams (`StateFlow`) exposed by ViewModels, which query local Room DAOs.
* **Backend / Server Architecture**: **Zero backend infrastructure.** The app operates 100% on-device. SMS dispatching is done through Android's baseband telephony stack using `android.telephony.SmsManager`.
* **Database**: Local SQLite database via Room (`AppDatabase.kt`, version 4). Entities: `AppSettings`, `MessageTemplate`, `CallLogEntry`.
* **Call Detection**: `CallMonitorService` declared as a foreground service with `specialUse` type and `START_STICKY`. Registers per-subscription `TelephonyCallback` (API 31+) or `PhoneStateListener` (API 26–30).
* **SMS Gateway / Sending**: Background task dispatched via `WorkManager` (`SmsSenderWorker`), configured with 1 retry (30s linear backoff).
* **Update Mechanism**: Static JSON endpoint on Vercel (`https://adera-sms.vercel.app/downloads/version.json`) checked via `HttpURLConnection` in `UpdateChecker.kt`.

---

# 3. COMPLETE DATA-FLOW MAP

```
[ INCOMING VOICE CALL ]
       │
       ▼
[ TelephonyManager State: CALL_STATE_RINGING ]
       │ ──> Captures subId; caches direct phoneNumber (API < 31)
       ▼
[ Call state transition: CALL_STATE_IDLE ] (without prior OFFHOOK)
       │
       ▼
[ Step 1: Resolve Caller Number ]
       ├─ If API < 31: Use direct phoneNumber from listener
       └─ If API 31+: Register ContentObserver on CallLog.Calls.CONTENT_URI (5000ms timeout)
                      Query top MISSED call entry from OS CallLog
       │
       ▼
[ Step 2: Settings & Master Toggle Check ]
       ├─ Query AppSettings (id=1)
       └─ If autoReplyEnabled == false ──> Abort (silent exit)
       │
       ▼
[ Step 3: Quiet Hours Evaluation ]
       ├─ Calculate current time in minutes since midnight
       └─ If within quiet hours range:
             ├── Write CallLogEntry(status = SUPPRESSED_QUIET_HOURS)
             └── Abort
       │
       ▼
[ Step 4: Per-Number Cooldown Check ]
       ├─ Hash callerNumber with SHA-256
       ├─ Query CallLogDao.getRecentByNumberHash(hash, since = now - 10 min)
       └─ If entry exists with status IN ('SENT', 'PENDING'):
             ├── Write CallLogEntry(status = SUPPRESSED_COOLDOWN)
             └── Abort
       │
       ▼
[ Step 5: 24-Hour Daily Send Cap Check ]
       ├─ Read daily_send_cap from Firebase Remote Config (default: 15)
       ├─ Query CallLogDao.countSentSince(now - 24 hours)
       └─ If sentCount >= dailyCap:
             ├── Write CallLogEntry(status = DAILY_LIMIT_REACHED)
             ├── Trigger FirebaseInAppMessaging event "daily_cap_reached"
             └── Abort
       │
       ▼
[ Step 6: Active Template Resolution ]
       ├─ Query TemplateDao.getDefaultTemplate()
       └─ If null ──> Log error, Abort
       │
       ▼
[ Step 7: Local Audit Trail Insertion ]
       ├─ Insert CallLogEntry(status = PENDING)
       └─ Retrieve generated row logId
       │
       ▼
[ Step 8: Background Worker Enqueue ]
       ├─ Build SmsSenderWorker OneTimeWorkRequest with inputData (callerNumber, subId, templateText, logId)
       └─ WorkManager.enqueue(request)
       │
       ▼
[ Step 9: WorkManager Execution (SmsSenderWorker) ]
       ├─ Validate callerNumber, templateText, and subscriptionId != INVALID_SUBSCRIPTION_ID
       ├─ Append mandatory signature: "\n\nBy Adera SMS"
       ├─ Resolve SmsManager for subscriptionId
       ├─ Create unique PendingIntent with action "com.adera.sms.SMS_SENT_<UUID>"
       ├─ Register dynamic BroadcastReceiver (RECEIVER_NOT_EXPORTED on API 33+)
       ├─ Call smsManager.sendTextMessage(...)
       └─ Await broadcast result:
             ├─ If RESULT_OK:
             │     ├── Update CallLogEntry(logId, status = SENT)
             │     ├── AnalyticsManager.autoReplySent()
             │     └── Result.success()
             ├─ If error & runAttemptCount < 1:
             │     └── Result.retry() (Backoff 30s linear)
             └─ If error & runAttemptCount >= 1:
                   ├── Update CallLogEntry(logId, status = FAILED)
                   └── Result.failure()
```

---

# 4. FINDINGS SUMMARY TABLE

| ID | Severity | Confidence | Category | Location | Short Description |
|---|---|---|---|---|---|
| **SEC-001** | **CRITICAL** | CONFIRMED | Secrets / Supply-Chain | `keystore.properties:13-26` | Full Production Signing Keystore Base64 & Passwords Stored in Local Workspace |
| **LOG-001** | **HIGH** | CONFIRMED | Logic / Concurrency | `CallMonitorService.kt:351-362, 442-524` | API 31+ CallLog Query Lacks Timestamp Filter; Sends Auto-Reply to Wrong Caller on Race/Mismatch |
| **REL-001** | **HIGH** | CONFIRMED | Navigation / Reliability | `Screen.kt:10-12`, `NavGraph.kt:91, 98-105` | Unencoded URL in Navigation Compose Path Crashes App on Forced Update |
| **CON-001** | **HIGH** | CONFIRMED | Concurrency / Race | `CallMonitorService.kt:383-428` | Check-Then-Act Race Condition Allows Duplicate SMS and Daily Cap Bypass on Simultaneous Calls |
| **REL-002** | **MEDIUM** | CONFIRMED | Reliability / Data Loss | `SettingsViewModel.kt:59-65` | `clearAllData()` Deletes Message Templates Without Re-seeding; Permanently Breaks Auto-Reply |
| **LOG-002** | **MEDIUM** | CONFIRMED | Logic / Incomplete Feature | `TemplateEditorScreen.kt:75-116, 169-182` | "New Template" Button Hardcoded to Dead-End "Coming Soon" Dialog; Orphan Backend Logic in ViewModel |
| **REL-003** | **MEDIUM** | CONFIRMED | SMS / Reliability | `SmsSenderWorker.kt:98, 148` | Multi-Segment SMS Dispatched via `sendTextMessage()` Instead of `sendMultipartTextMessage()` |
| **REL-004** | **MEDIUM** | CONFIRMED | Concurrency / Lifecycle | `SmsSenderWorker.kt:104-153`, `CallLogDao.kt:61-66` | Missing Timeout on Sent PendingIntent Broadcast Causes Indefinite Worker Hang; Pending Cleanup DAO Never Invoked |
| **TEL-001** | **MEDIUM** | HIGH CONFIDENCE | Telephony / Dual-SIM | `CallMonitorService.kt:176-177, 233-254` | Telephony Callback Not Re-registered on SIM State Changes / Hot-Swap (Missing `OnSubscriptionsChangedListener`) |
| **SEC-002** | **LOW** | CONFIRMED | Security / Integrity | `UpdateChecker.kt:38-84` | Sideload Update Endpoint Fetches APK URL Without Checksum / Signature Hash Validation |
| **PRV-001** | **LOW** | CONFIRMED | Privacy / Documentation | `CallLogEntry.kt:9-25` | Unencrypted Plaintext Caller Phone Numbers Persisted in Local SQLite Contrary to Class Docstring |
| **CFG-001** | **INFO** | CONFIRMED | Configuration / Tooling | `app/build.gradle.kts:80-91` | Android Gradle Lint Failures Suppressed (`warningsAsErrors = false`, `abortOnError = false`) |

---

# 5. DETAILED AUDIT FINDINGS

---

## CRITICAL FINDINGS

### SEC-001: Full Production Signing Keystore Base64 & Passwords Stored in Local Workspace

* **Finding ID**: SEC-001
* **Category**: Security / Supply-Chain
* **Severity**: **CRITICAL**
* **Confidence**: **CONFIRMED**
* **Exact Location**: `keystore.properties:13-26`
* **Vulnerable/Problematic Code Path**:
  ```properties
  storeFile=keystore.jks
  storePassword=AderaSMS2026Store
  keyAlias=adera
  keyPassword=AderaSMS2026Key

  # SIGNING_KEY_BASE64:
  /u3+7QAAAAIAAAABAAAAAQAFYWRlcmEAAAGf2shreQAABQIwggT... [Full Base64 Keystore Block]
  ```
* **Why It Is a Problem**:
  The file `keystore.properties` contains the plaintext keystore password, private key alias, key password, and the **entire base64-encoded binary Java Keystore (`.jks`)**. Although `.gitignore` mentions `keystore.properties`, the file is present in the workspace. Any developer, workstation compromise, or accidental commit exposes the developer's release signing key.
* **Reproduction Scenario**:
  1. An attacker extracts the `SIGNING_KEY_BASE64` string from `keystore.properties`.
  2. Runs `echo "<base64>" | base64 -d > stolen.jks`.
  3. Executes `apksigner sign --ks stolen.jks --ks-pass pass:AderaSMS2026Store --ks-key-alias adera --key-pass pass:AderaSMS2026Key malicious.apk`.
  4. The malicious APK successfully installs over existing user installations as an in-place update without signature validation failure.
* **Impact**:
  Complete compromise of application identity. An attacker can distribute malicious trojanized updates that overwrite legitimate user installations and gain full access to call logs, SMS permissions, and device storage.
* **Recommended Direction**:
  Immediately rotate the production signing certificate before public release. Delete `keystore.properties` from all local environments and developer machines. Store production keystores strictly in a secure cloud secret manager (e.g. GitHub Actions Encrypted Secrets or Google Cloud KMS) and never on local disk.

---

## HIGH FINDINGS

### LOG-001: API 31+ CallLog Query Lacks Timestamp Filter; Sends Auto-Reply to Wrong Caller on Race/Mismatch

* **Finding ID**: LOG-001
* **Category**: Logic / Concurrency
* **Severity**: **HIGH**
* **Confidence**: **CONFIRMED**
* **Exact Location**: `CallMonitorService.kt:351-362`, `CallMonitorService.kt:442-524`
* **Vulnerable/Problematic Code Path**:
  ```kotlin
  // In queryCallLogForMissedNumber():
  val args = Bundle().apply {
      putInt(ContentResolver.QUERY_ARG_LIMIT, 1)
      putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(CallLog.Calls.DATE))
      putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
      putString(ContentResolver.QUERY_ARG_SQL_SELECTION, "${CallLog.Calls.TYPE} = ?")
      putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf(CallLog.Calls.MISSED_TYPE.toString()))
  }
  resolver.query(CallLog.Calls.CONTENT_URI, arrayOf(CallLog.Calls.NUMBER), args, null)
  ```
* **Why It Is a Problem**:
  On Android 12+ (API 31+), `TelephonyCallback` does not supply the caller number. The service registers a `ContentObserver` on `CallLog.Calls.CONTENT_URI` to read the incoming missed call from the system `CallLog`.
  However, the query simply selects `TYPE = MISSED_TYPE` sorted by `DATE DESC LIMIT 1` with **no timestamp constraint** (`DATE >= callStartTime`).
  If a `ContentObserver` event fires due to contact sync, an incoming SMS, or another call before the telephony provider finishes writing the current call, the query reads the **previous historical missed call**.
* **Reproduction Scenario**:
  1. User has a historical missed call from Contact A at 10:00 AM.
  2. At 2:00 PM, Contact B calls and hangs up (missed call).
  3. `CallMonitorService` transitions `CALL_STATE_RINGING` -> `CALL_STATE_IDLE` and registers the `ContentObserver`.
  4. A background sync or push touches the `CallLog` provider before the OS writes Contact B's log.
  5. `onChange()` fires, executes `TYPE = MISSED_TYPE ORDER BY DATE DESC LIMIT 1`, and retrieves Contact A.
  6. The app sends an SMS auto-reply to Contact A instead of Contact B.
* **Impact**:
  Severe privacy violation and messaging failure. Users inadvertently broadcast their auto-replies to arbitrary past contacts, creating confusion and unexpected carrier SMS charges.
* **Recommended Direction**:
  Capture `callStartTime = System.currentTimeMillis() - buffer` when entering `CALL_STATE_RINGING`. In `queryCallLogForMissedNumber()`, enforce selection: `${CallLog.Calls.TYPE} = ? AND ${CallLog.Calls.DATE} >= ?` with `arrayOf(CallLog.Calls.MISSED_TYPE.toString(), callStartTime.toString())`.

---

### REL-001: Unencoded URL in Navigation Compose Path Crashes App on Forced Update

* **Finding ID**: REL-001
* **Category**: Navigation / Reliability
* **Severity**: **HIGH**
* **Confidence**: **CONFIRMED**
* **Exact Location**: `Screen.kt:10-12`, `NavGraph.kt:91, 98-105`
* **Vulnerable/Problematic Code Path**:
  ```kotlin
  // Screen.kt:
  object ForceUpdate : Screen("force_update/{downloadUrl}") {
      fun buildRoute(downloadUrl: String) = "force_update/$downloadUrl"
  }

  // NavGraph.kt:
  onForceUpdate = { url ->
      navController.navigate(Screen.ForceUpdate.buildRoute(url)) {
          popUpTo(0) { inclusive = true }
      }
  }
  ```
* **Why It Is a Problem**:
  `downloadUrl` is a full URL string (e.g. `https://adera-sms.vercel.app/downloads/AderaSMS.apk`). Jetpack Navigation decomposes destination paths using standard `/` delimiters. When `buildRoute()` constructs `"force_update/https://adera-sms.vercel.app/downloads/AderaSMS.apk"`, NavController attempts to parse `https:`, `adera-sms.vercel.app`, and `downloads` as separate path segments rather than a single parameter string.
* **Reproduction Scenario**:
  1. App performs an update check.
  2. Installed version code is lower than `minSupportedVersionCode`.
  3. `SettingsScreen` invokes `onForceUpdate("https://adera-sms.vercel.app/downloads/AderaSMS.apk")`.
  4. `navController.navigate("force_update/https://adera-sms.vercel.app/downloads/AderaSMS.apk")` executes.
  5. NavController throws `IllegalArgumentException: Navigation destination that matches request NavDeepLinkRequest cannot be found`.
  6. The application crashes immediately.
* **Impact**:
  Fatal app crash when a forced update is required, completely blocking the user from viewing the update screen or downloading the required APK.
* **Recommended Direction**:
  Encode the URL parameter using `java.net.URLEncoder.encode(downloadUrl, StandardCharsets.UTF_8.toString())` when building the route, and decode it upon retrieval, or pass the download URL through a ViewModel rather than raw navigation path parameters.

---

### CON-001: Check-Then-Act Race Condition Allows Duplicate SMS and Daily Cap Bypass on Simultaneous Calls

* **Finding ID**: CON-001
* **Category**: Concurrency / Race Condition
* **Severity**: **HIGH**
* **Confidence**: **CONFIRMED**
* **Exact Location**: `CallMonitorService.kt:383-428`
* **Vulnerable/Problematic Code Path**:
  ```kotlin
  // Step 3: cooldown check
  val hash = sha256(callerNumber)
  if (isInCooldown(hash)) { ... return }

  // Step 4: daily cap check
  val sentCount = database.callLogDao().countSentSince(since24h)
  if (sentCount >= dailyCap) { ... return }

  // Step 6: insert PENDING
  val logId = database.callLogDao().insertEntry(
      CallLogEntry(..., status = CallStatus.PENDING)
  )
  val request = SmsSenderWorker.buildRequest(...)
  WorkManager.getInstance(applicationContext).enqueue(request)
  ```
* **Why It Is a Problem**:
  `processMissedCall` is launched in a non-synchronized coroutine (`serviceScope.launch { ... }`) for each missed call event. The sequence `READ (cooldown/cap) -> CHECK -> WRITE (insert PENDING)` is non-atomic.
  If two missed call events are processed concurrently (e.g. dual-SIM receiving simultaneous calls, or rapid successive rings within milliseconds):
  1. Coroutine A checks `isInCooldown()` -> evaluates to `false`.
  2. Coroutine B checks `isInCooldown()` before Coroutine A inserts `PENDING` -> evaluates to `false`.
  3. Both coroutines insert a `PENDING` record and enqueue `SmsSenderWorker`.
  4. Two identical SMS messages are dispatched to the caller.
  5. If `sentCount == dailyCap - 1`, multiple simultaneous calls will all pass the cap check and exceed the quota.
* **Reproduction Scenario**:
  1. A caller rings SIM 1 and SIM 2 simultaneously or disconnects and reconnects in rapid succession (< 200ms).
  2. Two `onStateChange` callbacks trigger two concurrent `processMissedCall` coroutines.
  3. Both execute DB read queries before either writes.
  4. Both pass validation and enqueue `SmsSenderWorker`.
  5. The caller receives two SMS messages back-to-back within seconds.
* **Impact**:
  Duplicate SMS dispatching, carrier spam penalties, balance exhaustion, and bypass of daily send limits.
* **Recommended Direction**:
  Use a Kotlin `Mutex` or a Room database transaction (`@Transaction` with unique constraints or atomic status updates) to ensure only one missed call per number hash can enter the evaluation/enqueue pipeline at any given moment.

---

## MEDIUM FINDINGS

### REL-002: `clearAllData()` Deletes Message Templates Without Re-seeding; Permanently Breaks Auto-Reply

* **Finding ID**: REL-002
* **Category**: Reliability / Data Loss
* **Severity**: **MEDIUM**
* **Confidence**: **CONFIRMED**
* **Exact Location**: `SettingsViewModel.kt:59-65`
* **Vulnerable/Problematic Code Path**:
  ```kotlin
  fun clearAllData() {
      viewModelScope.launch(Dispatchers.IO) {
          db.clearAllTables()
          // Re-seed settings row so the app is not permanently broken
          db.settingsDao().upsertSettings(AppSettings())
      }
  }
  ```
* **Why It Is a Problem**:
  `db.clearAllTables()` wipes the `message_templates` table. While `AppSettings()` is re-inserted, `seedDatabaseIfNeeded()` in `AderaSmsApplication` only runs during `Application.onCreate()`.
  After clearing data:
  1. `message_templates` remains completely empty.
  2. `TemplateDao.getDefaultTemplate()` returns `null`.
  3. `CallMonitorService.kt` (line 408) fails with `"No default template — cannot send SMS"`.
  4. All future missed call auto-replies fail silently until the app process is completely killed and restarted.
  5. `CallMonitorService` is not stopped when clearing data, leaving the active foreground service running with an invalidated database state.
* **Reproduction Scenario**:
  1. User goes to Settings -> Privacy and Data -> Clear All Data -> Confirms.
  2. User turns auto-reply back ON in Home screen.
  3. A call is missed.
  4. `CallMonitorService` logs `"No default template — cannot send SMS"` and drops the event with no user notification or log entry.
* **Impact**:
  Total failure of core auto-reply functionality after user performs standard data clear action.
* **Recommended Direction**:
  In `clearAllData()`, explicitly call `db.templateDao().insertAll(buildPresetTemplates())` after wiping tables, and explicitly stop `CallMonitorService`.

---

### LOG-002: "New Template" Button Hardcoded to Dead-End "Coming Soon" Dialog; Orphan Backend Logic in ViewModel

* **Finding ID**: LOG-002
* **Category**: Logic / Incomplete Features
* **Severity**: **MEDIUM**
* **Confidence**: **CONFIRMED**
* **Exact Location**: `TemplateEditorScreen.kt:75-116`, `TemplateEditorScreen.kt:169-182`
* **Vulnerable/Problematic Code Path**:
  ```kotlin
  // TemplateEditorScreen.kt:
  Button(
      onClick = {
          // Item 16: Always show "Coming soon" — do not open edit sheet for new templates
          showComingSoonDialog = true
      },
      ...
  ) {
      Text("New Template", ...)
  }
  ```
* **Why It Is a Problem**:
  `TemplateViewModel.kt` (lines 39-62) implements `saveCustomTemplate(text, language)` with Remote Config limit checks (`RC_KEY_FREE_TEMPLATE_LIMIT`), Firebase In-App Messaging triggers (`template_limit_hit`), and analytics tracking. Furthermore, `EditTemplateSheet` in `TemplateEditorScreen.kt` (lines 218-230) contains code to invoke `saveCustomTemplate` when `templateToEdit == null`.
  However, the UI button has been hardcoded to intercept all clicks and display a "Coming Soon" dialog. Users are unable to add custom templates despite full architectural support existing in the ViewModel and DAO.
* **Impact**:
  Degraded user experience, dead code in ViewModel and UI, and contradiction with marketing/README claims of customizable templates.
* **Recommended Direction**:
  Align UI and ViewModel: either wire the button to open `EditTemplateSheet(template = null)` or remove dead ViewModel/analytics pathways.

---

### REL-003: Multi-Segment SMS Dispatched via `sendTextMessage()` Instead of `sendMultipartTextMessage()`

* **Finding ID**: REL-003
* **Category**: SMS / Carrier Reliability
* **Severity**: **MEDIUM**
* **Confidence**: **CONFIRMED**
* **Exact Location**: `SmsSenderWorker.kt:98`, `SmsSenderWorker.kt:148`
* **Vulnerable/Problematic Code Path**:
  ```kotlin
  val fullMessage = templateText + SIGNATURE // SIGNATURE = "\n\nBy Adera SMS"
  ...
  smsManager.sendTextMessage(callerNumber, null, fullMessage, sentIntent, null)
  ```
* **Why It Is a Problem**:
  In `TemplateEditorScreen.kt`, the UI calculates multi-part SMS segments (`smsSegmentInfo`) and allows users to type messages that span 2 or 3 SMS segments (especially common with Amharic Ethiopic script, which uses UTF-16 / UCS-2 encoding where a single segment is capped at 70 characters).
  However, `SmsSenderWorker.kt` dispatches the SMS using `smsManager.sendTextMessage()`.
  On many Android OEM telephony implementations and carrier networks, calling `sendTextMessage()` with a payload exceeding 160 GSM-7 or 70 UCS-2 characters throws `IllegalArgumentException: Message too long` or is silently truncated/dropped by the radio interface layer.
* **Reproduction Scenario**:
  1. User creates or selects an Amharic template of 75 characters.
  2. With signature `\n\nBy Adera SMS` (15 characters), total length is 90 UTF-16 characters (> 70 character single-segment limit).
  3. A missed call occurs.
  4. `SmsSenderWorker` passes 90 UCS-2 characters to `sendTextMessage()`.
  5. `sendTextMessage()` throws `IllegalArgumentException` or carrier rejects it, resulting in delivery failure.
* **Impact**:
  Auto-reply silently fails or crashes for longer templates and native Amharic text.
* **Recommended Direction**:
  Use `smsManager.divideMessage(fullMessage)`: if `parts.size > 1`, invoke `smsManager.sendMultipartTextMessage(callerNumber, null, parts, sentIntents, null)`.

---

### REL-004: Missing Timeout on Sent PendingIntent Broadcast Causes Indefinite Worker Hang

* **Finding ID**: REL-004
* **Category**: Concurrency / Lifecycle
* **Severity**: **MEDIUM**
* **Confidence**: **CONFIRMED**
* **Exact Location**: `SmsSenderWorker.kt:104-153`, `CallLogDao.kt:61-66`
* **Vulnerable/Problematic Code Path**:
  ```kotlin
  suspendCancellableCoroutine { continuation ->
      val intentAction = "com.adera.sms.SMS_SENT_${UUID.randomUUID()}"
      ...
      val receiver = object : BroadcastReceiver() {
          override fun onReceive(context: Context, intent: Intent) {
              ...
              if (continuation.isActive) continuation.resume(...)
          }
      }
      ...
      smsManager.sendTextMessage(...)
  }
  ```
* **Why It Is a Problem**:
  `suspendCancellableCoroutine` suspends until `receiver.onReceive()` is called by the OS telephony stack. If the baseband radio crashes, the device enters airplane mode mid-send, or an OEM RIL bug fails to broadcast the PendingIntent, `onReceive` **never fires**.
  The worker remains suspended indefinitely until WorkManager's 10-minute maximum execution window kills it. During this time:
  1. The `CallLogEntry` remains stuck in `PENDING` state.
  2. Because cooldown checks look for `status IN ('SENT', 'PENDING')`, cooldown remains locked against that caller.
  3. `CallLogDao` contains a maintenance method `markStuckPendingAsFailed(cutoffMs)`, but **this method is never called anywhere in the entire application**.
* **Impact**:
  Resource leakage, blocked WorkManager threads, and orphaned `PENDING` entries in the activity log.
* **Recommended Direction**:
  Wrap the `suspendCancellableCoroutine` block with `kotlinx.coroutines.withTimeoutOrNull(60_000L)`. If timed out, unregister the receiver, mark `CallStatus.FAILED`, and return `Result.failure()`. Call `markStuckPendingAsFailed()` on application launch.

---

### TEL-001: Telephony Callback Not Re-registered on SIM State Changes / Hot-Swap

* **Finding ID**: TEL-001
* **Category**: Telephony / Dual-SIM
* **Severity**: **MEDIUM**
* **Confidence**: **HIGH CONFIDENCE**
* **Exact Location**: `CallMonitorService.kt:176-177`, `CallMonitorService.kt:233-254`
* **Vulnerable/Problematic Code Path**:
  ```kotlin
  override fun onCreate() {
      ...
      val subIds = activeSubscriptionIds()
      registerListeners(subIds)
      ...
  }
  ```
* **Why It Is a Problem**:
  `registerListeners()` queries `SubscriptionManager.activeSubscriptionInfoList` exactly once during `Service.onCreate()`.
  If the user toggles a SIM card, inserts/removes a SIM in dual-SIM slots, disables airplane mode, or switches carrier defaults while the background service is running, `CallMonitorService` never receives an event to re-evaluate active SIM subscriptions.
  The service remains bound to dead subscription IDs or fails to bind to newly activated SIMs until the service process is explicitly killed and recreated.
* **Impact**:
  Auto-reply stops functioning silently on dual-SIM devices following carrier switching or SIM hot-swapping.
* **Recommended Direction**:
  Register `SubscriptionManager.OnSubscriptionsChangedListener` inside `CallMonitorService` to trigger `unregisterListeners()` and `registerListeners(newSubIds)` whenever subscription configurations change.

---

## LOW & INFORMATIONAL FINDINGS

### SEC-002: Sideload Update Endpoint Fetches APK URL Without Checksum / Signature Hash Validation

* **Finding ID**: SEC-002
* **Category**: Security / Integrity
* **Severity**: **LOW**
* **Confidence**: **CONFIRMED**
* **Exact Location**: `UpdateChecker.kt:38-84`
* **Description**: `UpdateChecker` parses `downloadUrl` from Vercel and directly launches `Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))`. There is no hash/SHA-256 field in `version.json` to verify package integrity before download or prompt. Android OS package installer will block mismatched signing keys, but lack of pre-verification allows malicious redirects if the endpoint DNS is spoofed.

---

### PRV-001: Unencrypted Plaintext Caller Phone Numbers Persisted in Local SQLite Contrary to Class Docstring

* **Finding ID**: PRV-001
* **Category**: Privacy / Documentation
* **Severity**: **LOW**
* **Confidence**: **CONFIRMED**
* **Exact Location**: `CallLogEntry.kt:9-25`
* **Description**: Header comment in `CallLogEntry.kt` states: *"The full phone number exists only transiently in memory... and is never written to disk in plain form"*. However, line 24 explicitly states: *"Spec A 12.2 reverted, numbers are now stored and visible"* and saves `callerNumber: String` directly into Room SQLite table `call_log_entries`. The database is stored unencrypted in the app data directory.

---

### CFG-001: Android Gradle Lint Failures Suppressed in Build Configuration

* **Finding ID**: CFG-001
* **Category**: Configuration / Tooling
* **Severity**: **INFO**
* **Confidence**: **CONFIRMED**
* **Exact Location**: `app/build.gradle.kts:80-91`
* **Description**: `app/build.gradle.kts` explicitly sets `warningsAsErrors = false` and `abortOnError = false`, and disables `NullSafeMutableLiveData`. While documented as a workaround for Kotlin 2.1.0 lifecycle-lint detector incompatibility, it allows severe build lint regressions to pass unnoticed in CI/CD.

---

# 6. BUSINESS-LOGIC RISKS

1. **Missed-Call Definition Boundary**:
   The state machine detects a missed call if `s.isRinging && !s.isOffHook` when `CALL_STATE_IDLE` is received. When a user actively rejects a call by pressing "Decline" or the hardware power button, Android transitions `RINGING -> IDLE` (never enters `OFFHOOK`). Thus, **manually rejected calls are treated as missed calls** and trigger an auto-reply SMS.
2. **Quiet Hours Overnight Evaluation**:
   `isWithinQuietHours()` handles overnight intervals (e.g. 22:00 to 06:00) using `now >= start || now < end`. In `QuietHoursSheet.kt`, if start == end (e.g. 00:00), the app stores 0,0 which is correctly treated as disabled. If a user selects 00:00 to 07:00, `start` is 0 and `end` is 420 (`start < end`), which correctly falls into same-day evaluation. Logic holds across midnight.
3. **Phone Number Formatting Inconsistency**:
   The cooldown hash is computed via `sha256(callerNumber)`. If a caller rings once with international format (`+251911234567`) and next with national format (`0911234567`), the SHA-256 hashes will differ, **bypassing the 10-minute cooldown window**.

---

# 7. SECURITY ATTACK SURFACE

| Attack Surface | Exposure Level | Protection Mechanism | Vulnerability / Risk |
|---|---|---|---|
| **Exported Components** | 2 Components: `MainActivity`, `BootReceiver` | System permissions & Intent filters | `BootReceiver` is protected by `android.permission.RECEIVE_BOOT_COMPLETED`. `MainActivity` is standard launcher. |
| **Non-Exported Services** | `CallMonitorService`, `AderaFirebaseMessagingService` | `exported="false"` | Protected from cross-app invocation. |
| **FileProvider** | `${applicationId}.fileprovider` | `exported="false"`, `grantUriPermissions="true"` | Path limited to cache directory (`cache_files`). Used for APK sharing. |
| **Network Attack Surface** | `UpdateChecker` HTTP GET to Vercel | HTTPS / TLS | No cert pinning; no SHA-256 integrity hash verification in payload. |
| **Database Storage** | Local SQLite (`adera_sms.db`) | OS Sandbox (`allowBackup="false"`) | Database is unencrypted; readable on rooted devices. |
| **Keystore Material** | Local root file `keystore.properties` | None (in file system) | **CRITICAL**: Contains complete private key & passwords in plaintext/base64. |

---

# 8. FAILURE MATRIX

| Failure Scenario | Current Behavior | Production Risk | Evidence |
|---|---|---|---|
| **Database Unavailable / Locked (Direct Boot)** | `ServiceStartWorker.doWork()` catches Exception and returns `null` settings, starting service defensively. | **LOW** — Handled gracefully; starts service defensively. | `ServiceStartWorker.kt:49-55` |
| **CallLog Provider Timeout (> 5s on API 31+)** | `withTimeoutOrNull(5_000L)` expires; returns `null`; logs warning and aborts. | **MEDIUM** — Missed call is dropped without SMS reply or audit log. | `CallMonitorService.kt:353-361` |
| **SMS Radio Failure / Out of Credit** | `BroadcastReceiver` in worker catches error `resultCode != RESULT_OK`. Retries once after 30s. Marks `FAILED`. | **LOW** — Properly retried and marked in database. | `SmsSenderWorker.kt:123-132` |
| **Multi-part / Unicode Template (> 70 chars)** | `smsManager.sendTextMessage()` called on multi-segment message. | **HIGH** — Radio error / `IllegalArgumentException`; SMS fails to deliver. | `SmsSenderWorker.kt:148` |
| **Process Crash / System Kill** | Service is `START_STICKY`. WorkManager persists pending requests in internal DB. | **LOW** — Resilient architecture for process death. | `CallMonitorService.kt:202` |
| **SIM Card Removed / Hot-Swapped** | Worker checks `subId == INVALID_SUBSCRIPTION_ID` and marks `FAILED`. Service listeners retain old subIds. | **MEDIUM** — Service stops picking up calls on new SIM until restart. | `CallMonitorService.kt:233` |
| **Forced Update Triggered** | NavController navigates to unencoded `downloadUrl`. | **HIGH** — App crashes immediately with navigation routing exception. | `NavGraph.kt:91` |

---

# 9. DATA-INTEGRITY RISKS

1. **Orphan `PENDING` Records**:
   If `SmsSenderWorker` is terminated by the OS or the broadcast receiver hangs, `CallLogEntry` remains with `status = PENDING`. The method `markStuckPendingAsFailed()` exists in `CallLogDao.kt:66` but is **never invoked anywhere in the codebase**.
2. **Template Wiping on Data Clear**:
   Invoking `clearAllData()` deletes all rows in `message_templates` without re-seeding default templates, permanently corrupting the active template foreign constraint/assumption.

---

# 10. DUPLICATE-SMS ANALYSIS

### Can Adera SMS send the same SMS more than once for a single missed call?

**YES.** There are two specific conditions under which duplicate SMS messages are sent:

1. **Concurrent / Rapid Call Race Condition**:
   When two missed call events trigger within milliseconds (e.g. dual-SIM or rapid redial), `CallMonitorService.processMissedCall` launches concurrent coroutines. Because the cooldown check (`isInCooldown`) and the database write (`insertEntry(PENDING)`) are not synchronized via a Mutex or database transaction, both coroutines read an empty cooldown state, both insert `PENDING`, and both enqueue `SmsSenderWorker`. The caller receives **two identical SMS messages**.
2. **Phone Number Format Divergence**:
   If an initial call arrives with international format (e.g. `+251911234567`) and a second call arrives seconds later in national format (`0911234567`), the `sha256()` hashes differ. The cooldown check fails to match, and a duplicate SMS is dispatched.

---

# 11. CROSS-ACCOUNT / CROSS-USER ACCESS ANALYSIS

### Can User A access or manipulate User B's data?

**NO.** The application has no server backend, no remote multi-tenancy, and no shared cloud database. All data resides entirely inside the Android application sandbox (`/data/data/com.adera.sms/databases/adera_sms.db`). Cross-user isolation is enforced by the Linux kernel UID isolation model of the Android operating system.

---

# 12. WEBHOOK SECURITY ANALYSIS

* **Can a fake webhook be submitted?** N/A — No webhook endpoints exist.
* **Can a legitimate webhook be replayed?** N/A — No incoming HTTP endpoints exist.
* **Is signature verification present?** N/A — App is 100% on-device.

---

# 13. SMS COST-ABUSE ANALYSIS

### Can an attacker intentionally cause Adera SMS to send large numbers of SMS messages and create financial damage?

**LIMITED BY DAILY CAP, BUT EXPLOITABLE VIA SIMULATED CALL FLOODING:**
1. **Remote Triggering**: An external party can call the user's phone number and immediately hang up. If auto-reply is ON, an SMS is sent from the user's carrier plan to the caller.
2. **Cooldown Protection**: A single number is capped to 1 SMS every 10 minutes.
3. **Daily Cap**: Global sending is capped at 15 SMS per 24 hours (`RC_KEY_DAILY_SEND_CAP`).
4. **Abuse Scenario**: An attacker using a pool of rotating phone numbers (or VoIP caller ID spoofing) can ring the user's device 15 times, exhausting the user's daily auto-reply quota in minutes and draining carrier SMS balance. However, the damage is strictly capped at `daily_send_cap` (15 messages by default).

---

# 14. PRODUCTION-READINESS ASSESSMENT

### **NOT READY**

**Justification based strictly on audited code:**
1. **Critical Secret Leak (SEC-001)**: The production release signing key base64 and keystore passwords are stored directly in `keystore.properties` in the workspace root.
2. **Fatal Navigation Crash (REL-001)**: Triggering a forced update crashes the app due to unencoded URL path parameters in Compose Navigation.
3. **API 31+ Caller Mismatch (LOG-001)**: The CallLog resolver lacks timestamp constraints and can auto-reply to the wrong caller.
4. **Multi-Segment Message Truncation (REL-003)**: Amharic and long messages dispatched via `sendTextMessage()` fail or throw exceptions.
5. **Data-Loss Trap (REL-002)**: Clearing data wipes all templates without re-seeding, breaking future auto-replies.

---

# 15. AUDIT LIMITATIONS

* **No Physical Device / Radio Hardware Attached**: Telephony callback timing and OEM RIL broadcast latency (especially on MediaTek Tecno/Infinix chipsets) could not be physically timed in this environment.
* **Remote Config / Firebase Console State**: Remote Config live default values and Firebase In-App Messaging live campaign states on the Google Cloud console could not be queried; analysis was based on local SDK integration and fallback constants.
* **Live Network Sideload Endpoint**: Live HTTP response from `https://adera-sms.vercel.app/downloads/version.json` was analyzed based on repository specification and mock files.
