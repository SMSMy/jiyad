package com.junkfood.seal.simple

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.junkfood.seal.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * نظام التحديث التلقائي عبر GitHub Releases
 * OTA update system via GitHub Releases
 */
object SimpleAppUpdater {

    private const val TAG = "SimpleAppUpdater"

    // ⚠️ استبدل USERNAME و REPO باسم مستخدمك واسم المستودع
    private const val GITHUB_OWNER = "SMSMy"
    private const val GITHUB_REPO = "jiyad"
    private const val GITHUB_API_URL =
        "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"

    data class UpdateInfo(
        val hasUpdate: Boolean,
        val latestVersion: String = "",
        val downloadUrl: String = "",
        val releaseNotes: String = ""
    )

    /**
     * فحص التحديثات من GitHub
     */
    suspend fun checkForUpdate(): UpdateInfo = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Checking for updates from: $GITHUB_API_URL")
            val url = URL(GITHUB_API_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                connectTimeout = 10_000
                readTimeout = 10_000
            }

            val responseCode = connection.responseCode
            if (responseCode != 200) {
                Log.w(TAG, "GitHub API returned: $responseCode")
                return@withContext UpdateInfo(hasUpdate = false)
            }

            val response = connection.inputStream.bufferedReader().readText()
            val json = JSONObject(response)

            val tagName = json.optString("tag_name", "")
            val latestVersion = tagName.removePrefix("v")
            val currentVersion = BuildConfig.VERSION_NAME
            val releaseNotes = json.optString("body", "")

            // البحث عن رابط APK في الأصول
            var downloadUrl = ""
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk")) {
                        downloadUrl = asset.optString("browser_download_url", "")
                        break
                    }
                }
            }

            // إذا لم نجد APK، نستخدم صفحة الإصدار
            if (downloadUrl.isEmpty()) {
                downloadUrl = json.optString("html_url", "")
            }

            val hasUpdate = isNewerVersion(currentVersion, latestVersion)
            Log.d(TAG, "Current: $currentVersion, Latest: $latestVersion, HasUpdate: $hasUpdate")

            UpdateInfo(
                hasUpdate = hasUpdate,
                latestVersion = latestVersion,
                downloadUrl = downloadUrl,
                releaseNotes = releaseNotes
            )
        } catch (e: Exception) {
            Log.e(TAG, "Update check failed", e)
            UpdateInfo(hasUpdate = false)
        }
    }

    /**
     * مقارنة الإصدارات (1.0.0 < 1.0.1)
     */
    private fun isNewerVersion(current: String, latest: String): Boolean {
        if (current.isBlank() || latest.isBlank()) return false
        try {
            val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
            val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }

            val maxLen = maxOf(currentParts.size, latestParts.size)
            for (i in 0 until maxLen) {
                val c = currentParts.getOrElse(i) { 0 }
                val l = latestParts.getOrElse(i) { 0 }
                if (l > c) return true
                if (l < c) return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Version comparison failed", e)
        }
        return false
    }

    /**
     * فتح رابط التحميل في المتصفح
     */
    fun openDownloadLink(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open download link", e)
        }
    }
}
