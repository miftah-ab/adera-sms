# Adera SMS — Complete Firebase Configuration Guide

This guide details all **required and optional Firebase Console configurations** for **Adera SMS**, with exact parameter keys, data types, event names, and setup instructions.

---

## 1. Firebase Remote Config (Dynamic Server Variables)

Go to **Firebase Console** → **Build** → **Remote Config** → **Add parameter**.

### Parameters to Add

| Parameter Key (Exact Variable Name) | Data Type | Default Value | Description / Purpose |
| :--- | :--- | :--- | :--- |
| `daily_send_cap` | **Number** | `15` | Maximum number of automated SMS replies sent across a rolling 24-hour window. Protects users against spam/carrier charges. |
| `free_template_limit` | **Number** | `6` | Maximum number of custom (user-created) templates permitted on the free tier. |

### How to Publish in Firebase Console
1. Click **Add parameter**.
2. Parameter key: `daily_send_cap` → Data type: `Number` → Default value: `15`.
3. Click **Add parameter** again.
4. Parameter key: `free_template_limit` → Data type: `Number` → Default value: `6`.
5. Click **Publish changes** in the top right banner.

> [!NOTE]
> **Safety Guard**: The app code has built-in fallbacks (`DEFAULT_DAILY_SEND_CAP = 15`, `DEFAULT_FREE_TEMPLATE_LIMIT = 6`). If Remote Config is offline, unconfigured, or returns `0`, the app automatically falls back to these defaults so auto-replies never stop.

---

## 2. Firebase In-App Messaging (Contextual Modal Prompts)

Go to **Firebase Console** → **Engage** → **In-App Messaging** → **Create campaign**.

The app fires the following **exact custom trigger events**:

### Trigger 1: `daily_cap_reached`
* **Trigger Event Name (Exact)**: `daily_cap_reached`
* **When It Fires in Code**: When an incoming missed call occurs after the user has already sent `daily_send_cap` replies in the last 24 hours.
* **Suggested In-App Message Setup**:
  * **Layout**: Modal or Card.
  * **Title**: `Daily Auto-Reply Limit Reached`
  * **Body**: `You've sent 15 automated replies today. To protect your SIM balance, auto-replies will resume in 24 hours.`
  * **Action**: Dismiss or View Settings.

### Trigger 2: `template_limit_hit`
* **Trigger Event Name (Exact)**: `template_limit_hit`
* **When It Fires in Code**: When a user attempts to create more custom templates than `free_template_limit` (6).
* **Suggested In-App Message Setup**:
  * **Layout**: Modal or Banner.
  * **Title**: `Template Limit Reached`
  * **Body**: `You have reached the limit of 6 custom templates. Edit existing templates or upgrade to Pro to unlock unlimited templates.`
  * **Button Text**: `Got it`

---

## 3. Firebase A/B Testing (Optional Remote Experiments)

Go to **Firebase Console** → **Engage** → **A/B Testing** → **Create experiment** → **Remote Config**.

### Example Experiment Setups:
1. **Optimize Daily Cap**:
   * **Target Key**: `daily_send_cap`
   * **Control (Variant A)**: `15`
   * **Variant B**: `25`
   * **Goal Metric**: User retention or daily active users (`app_open`).
2. **Optimize Template Creation Limit**:
   * **Target Key**: `free_template_limit`
   * **Control**: `6`
   * **Variant B**: `10`
   * **Goal Metric**: `template_edited` custom analytics event.

---

## 4. Firebase Cloud Messaging (Push Notifications)

Go to **Firebase Console** → **Engage** → **Messaging** → **New campaign** → **Notifications**.

* **FCM Receiver Service**: [`AderaFirebaseMessagingService.kt`](file:///c:/Users/26/Music/adera-sms/app/src/main/java/com/adera/sms/service/AderaFirebaseMessagingService.kt)
* **Topic / Broadcast**:
  * You can send targeted or broadcast notifications to all Adera SMS users directly from the Firebase Console (e.g., announcing updates, tips, or service notices).
  * The service processes background payloads and displays standard Android notifications.

---

## 5. Google Analytics Custom Events (Auto-Tracked in App)

These events are automatically recorded in **Firebase Console** → **Analytics** → **Events**:

| Event Name (Exact) | Trigger Point in App | Parameters Logged |
| :--- | :--- | :--- |
| `app_open` | When the app is launched | None |
| `onboarding_complete` | When user accepts terms & finishes consent | None |
| `auto_reply_toggle_changed` | When master toggle switch is flipped | `enabled` (`1` for ON, `0` for OFF) |
| `auto_reply_sent` | When an auto-reply SMS is successfully sent | None |
| `template_edited` | When a template is edited or saved | None |
| `diagnostic_check_run` | When diagnostic check is executed | `result` (`healthy` or `unhealthy`) |

---

## 6. Firebase Crashlytics Custom Keys

In **Firebase Console** → **Quality** → **Crashlytics**, crash logs and non-fatal breadcrumbs automatically include:

| Custom Key | Type | Description |
| :--- | :--- | :--- |
| `android_version` | `Integer` | Device SDK level (e.g. `34` for Android 14, `35` for Android 15). |
| `active_sim_count` | `Integer` | Number of active SIM subscriptions detected on device. |
| `call_monitor_breadcrumbs` | `String` | Chronological state transitions (`RINGING`, `OFFHOOK`, `IDLE`). |

---

## 7. Verification Checklist

- [x] Download `google-services.json` from Firebase Project Settings and place it in `app/google-services.json`.
- [x] Configure `daily_send_cap` (`15`) in Remote Config and click **Publish**.
- [x] Configure `free_template_limit` (`6`) in Remote Config and click **Publish**.
- [x] Create In-App Messaging campaigns for `daily_cap_reached` and `template_limit_hit`.
- [x] All app code includes automatic safe fallback defaults if Firebase is offline or uninitialized.
