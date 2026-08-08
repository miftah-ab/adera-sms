package com.adera.sms.update

import android.content.Context
import android.util.Log
import com.adera.sms.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Lightweight in-app update checker (spec §12.6).
 *
 * Fetches [VERSION_ENDPOINT] (a static JSON file on GitHub Pages), compares
 * [latestVersionCode] / [minSupportedVersionCode] against the installed
 * [BuildConfig.VERSION_CODE], and returns an [UpdateStatus].
 *
 * IMPORTANT: Call this only from a background coroutine; it makes a blocking
 * HTTP request. Never call it from [CallMonitorService] — the core loop must
 * not have network dependencies (spec §12.4).
 *
 * Version endpoint JSON shape (spec §12.6):
 * {
 *   "latestVersionCode": 2,
 *   "minSupportedVersionCode": 1,
 *   "downloadUrl": "https://github.com/.../releases/latest/AderaSMS.apk",
 *   "releaseNotes": "Fixed battery detection on Tecno devices",
 *   "disableCoreService": false
 * }
 */
object UpdateChecker {

    private const val TAG = "AderaSMS"

    /**
     * Static version.json hosted on Vercel.
     * Also update update-endpoint/version.json's downloadUrl to match.
     */
    const val VERSION_ENDPOINT =
        "https://adera-sms.vercel.app/downloads/version.json"

    private const val TIMEOUT_MS = 10_000

    fun check(context: Context): UpdateStatus {
        return try {
            val json = fetchJson(VERSION_ENDPOINT)
            val info = parseVersionInfo(json)
            val installed = BuildConfig.VERSION_CODE

            when {
                installed < info.minSupportedVersionCode ->
                    UpdateStatus.ForceUpdate(info)
                installed < info.latestVersionCode ->
                    UpdateStatus.UpdateAvailable(info)
                else ->
                    UpdateStatus.UpToDate
            }
        } catch (e: Exception) {
            Log.w(TAG, "Update check failed: ${e.message}")
            UpdateStatus.Error(e.message ?: "Unknown error")
        }
    }

    private fun fetchJson(urlString: String): String {
        val conn = URL(urlString).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.requestMethod = "GET"
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun parseVersionInfo(json: String): VersionInfo {
        val obj = JSONObject(json)
        return VersionInfo(
            latestVersionCode      = obj.getInt("latestVersionCode"),
            minSupportedVersionCode = obj.getInt("minSupportedVersionCode"),
            downloadUrl            = obj.getString("downloadUrl"),
            releaseNotes           = obj.optString("releaseNotes", ""),
            disableCoreService     = obj.optBoolean("disableCoreService", false)
        )
    }
}

data class VersionInfo(
    val latestVersionCode: Int,
    val minSupportedVersionCode: Int,
    val downloadUrl: String,
    val releaseNotes: String,
    val disableCoreService: Boolean
)

sealed class UpdateStatus {
    /** Installed version is current — no action needed. */
    object UpToDate : UpdateStatus()

    /** A newer version exists but the current one still works — show dismissible banner. */
    data class UpdateAvailable(val info: VersionInfo) : UpdateStatus()

    /**
     * Installed version is below [VersionInfo.minSupportedVersionCode].
     * Show a full-screen blocking update screen (spec §12.6).
     * Core service keeps running unless [VersionInfo.disableCoreService] is true.
     */
    data class ForceUpdate(val info: VersionInfo) : UpdateStatus()

    /** Network unavailable or JSON malformed — treat as no-op, try again next launch. */
    data class Error(val message: String) : UpdateStatus()
}
