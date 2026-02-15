package com.junkfood.seal.simple

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.junkfood.seal.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * نظام التحديث التلقائي عبر GitHub Releases
 * يحمل APK داخل التطبيق ويثبته مباشرة
 */
object SimpleAppUpdater {

    private const val TAG = "SimpleAppUpdater"

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
     * تحميل APK مع إظهار التقدم
     */
    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        onProgress: (Int) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Downloading APK from: $downloadUrl")
            val url = URL(downloadUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                connectTimeout = 30_000
                readTimeout = 30_000
                instanceFollowRedirects = true
            }

            // GitHub يعيد توجيه، نتبع التوجيه
            val responseCode = connection.responseCode
            if (responseCode != 200) {
                Log.e(TAG, "Download failed with code: $responseCode")
                return@withContext null
            }

            val contentLength = connection.contentLength
            val updateDir = File(context.cacheDir, "updates")
            if (!updateDir.exists()) updateDir.mkdirs()

            val apkFile = File(updateDir, "jiyad-update.apk")
            if (apkFile.exists()) apkFile.delete()

            val input = connection.inputStream
            val output = FileOutputStream(apkFile)
            val buffer = ByteArray(8192)
            var totalRead = 0L
            var bytesRead: Int

            while (input.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
                totalRead += bytesRead
                if (contentLength > 0) {
                    val progress = ((totalRead * 100) / contentLength).toInt()
                    withContext(Dispatchers.Main) {
                        onProgress(progress.coerceIn(0, 100))
                    }
                }
            }

            output.flush()
            output.close()
            input.close()
            connection.disconnect()

            Log.d(TAG, "APK downloaded: ${apkFile.absolutePath} (${apkFile.length()} bytes)")
            apkFile
        } catch (e: Exception) {
            Log.e(TAG, "APK download failed", e)
            null
        }
    }

    /**
     * تثبيت APK عبر FileProvider + Package Installer
     */
    fun installApk(context: Context, apkFile: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to install APK", e)
        }
    }

    /**
     * التحقق من إذن تثبيت التطبيقات غير المعروفة
     */
    fun canInstallApks(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true // أندرويد 7 وأقل لا يحتاج إذن خاص
        }
    }

    /**
     * فتح إعدادات إذن تثبيت التطبيقات غير المعروفة
     */
    fun requestInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    /**
     * فتح رابط التحميل في المتصفح (احتياطي)
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

    /**
     * مقارنة الإصدارات
     */
    internal fun isNewerVersion(current: String, latest: String): Boolean {
        if (current.isBlank() || latest.isBlank()) return false
        try {
            // إزالة أي suffix مثل -debug أو -alpha.5
            val cleanCurrent = current.split("-").first()
            val cleanLatest = latest.split("-").first()

            val currentParts = cleanCurrent.split(".").map { it.toIntOrNull() ?: 0 }
            val latestParts = cleanLatest.split(".").map { it.toIntOrNull() ?: 0 }

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
}
