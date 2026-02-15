package com.junkfood.seal.simple

import android.content.Context
import android.os.Environment
import android.util.Log
import com.junkfood.seal.database.objects.DownloadedVideoInfo
import com.junkfood.seal.util.DatabaseUtil
import com.junkfood.seal.util.NotificationUtil
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.media.MediaScannerConnection
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * محرك التحميل المبسط لجياد
 * يستخدم YoutubeDL مباشرة مع إشعارات التقدم
 */
object JiyadDownloader {

    private const val TAG = "JiyadDownloader"
    private val notificationIdCounter = AtomicInteger(200)

    /**
     * تحميل فيديو مع إشعار
     */
    suspend fun downloadVideo(
        context: Context,
        url: String,
        onProgress: (Float, String) -> Unit,
        onComplete: (Result<String>) -> Unit
    ) {
        val notifId = notificationIdCounter.getAndIncrement()
        withContext(Dispatchers.IO) {
            try {
                val downloadPath = getDownloadPath()
                val request = YoutubeDLRequest(url).apply {
                    addOption("--no-mtime")
                    addOption("-f", SimpleConfig.VIDEO_FORMAT)
                    addOption("--merge-output-format", SimpleConfig.VIDEO_MERGE_FORMAT)
                    addOption("-P", downloadPath)
                    addOption("--no-playlist")
                    addOption("--embed-chapters")
                    if (SimpleConfig.USE_ARIA2C) {
                        addOption("--downloader", "libaria2c.so")
                    }
                    addOption("-o", "%(title).100s.%(ext)s")
                }

                Log.d(TAG, "Starting video download: $url")

                // إشعار بدء التحميل
                NotificationUtil.notifyProgress(
                    title = "جاري التحميل...",
                    notificationId = notifId,
                    progress = 0,
                    text = url.take(80)
                )

                var lastTitle = ""
                YoutubeDL.getInstance().execute(
                    request = request,
                    processId = url,
                ) { progress, _, text ->
                    onProgress(progress, text)
                    if (text.isNotBlank()) lastTitle = text
                    // تحديث الإشعار
                    NotificationUtil.notifyProgress(
                        title = "جاري التحميل...",
                        notificationId = notifId,
                        progress = progress.toInt().coerceIn(0, 100),
                        text = text
                    )
                }

                // إشعار اكتمال
                NotificationUtil.finishNotification(
                    notificationId = notifId,
                    title = "تم التحميل ✅",
                    text = lastTitle.ifBlank { "فيديو" }
                )

                // حفظ في قاعدة البيانات
                saveToHistory(url, lastTitle.ifBlank { "Video" }, downloadPath)

                // تحديث MediaStore ليظهر في الاستديو فوراً
                scanDownloadedFiles(context, downloadPath)

                withContext(Dispatchers.Main) {
                    onComplete(Result.success(downloadPath))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Video download failed", e)
                NotificationUtil.cancelNotification(notifId)
                withContext(Dispatchers.Main) {
                    onComplete(Result.failure(e))
                }
            }
        }
    }

    /**
     * تحميل صوت (MP3) مع إشعار
     */
    suspend fun downloadAudio(
        context: Context,
        url: String,
        onProgress: (Float, String) -> Unit,
        onComplete: (Result<String>) -> Unit
    ) {
        val notifId = notificationIdCounter.getAndIncrement()
        withContext(Dispatchers.IO) {
            try {
                val downloadPath = getDownloadPath()
                val request = YoutubeDLRequest(url).apply {
                    addOption("--no-mtime")
                    addOption("-f", SimpleConfig.AUDIO_FORMAT)
                    addOption("-x")
                    addOption("--audio-format", SimpleConfig.AUDIO_EXTRACT_FORMAT)
                    addOption("-P", downloadPath)
                    addOption("--no-playlist")
                    if (SimpleConfig.USE_ARIA2C) {
                        addOption("--downloader", "libaria2c.so")
                    }
                    addOption("-o", "%(title).100s.%(ext)s")
                }

                Log.d(TAG, "Starting audio download: $url")

                // إشعار بدء التحميل
                NotificationUtil.notifyProgress(
                    title = "جاري التحميل...",
                    notificationId = notifId,
                    progress = 0,
                    text = url.take(80)
                )

                var lastTitle = ""
                var conversionStarted = false
                YoutubeDL.getInstance().execute(
                    request = request,
                    processId = url,
                ) { progress, _, text ->
                    // عندما يصل التحميل إلى 100% ويبدأ التحويل
                    if (progress >= 99f && !conversionStarted) {
                        conversionStarted = true
                        onProgress(99f, "🔄 جاري التحويل إلى MP3...")
                        NotificationUtil.notifyProgress(
                            title = "جاري التحويل إلى MP3...",
                            notificationId = notifId,
                            progress = -1, // indeterminate
                            text = lastTitle.ifBlank { "تحويل الصوت" }
                        )
                    } else if (!conversionStarted) {
                        onProgress(progress, text)
                        NotificationUtil.notifyProgress(
                            title = "جاري التحميل...",
                            notificationId = notifId,
                            progress = progress.toInt().coerceIn(0, 100),
                            text = text
                        )
                    }
                    if (text.isNotBlank()) lastTitle = text
                }

                // إشعار اكتمال
                NotificationUtil.finishNotification(
                    notificationId = notifId,
                    title = "تم التحميل ✅",
                    text = lastTitle.ifBlank { "صوت" }
                )

                // حفظ في قاعدة البيانات
                saveToHistory(url, lastTitle.ifBlank { "Audio" }, downloadPath)

                // تحديث MediaStore ليظهر في الاستديو فوراً
                scanDownloadedFiles(context, downloadPath)

                withContext(Dispatchers.Main) {
                    onComplete(Result.success(downloadPath))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Audio download failed", e)
                NotificationUtil.cancelNotification(notifId)
                withContext(Dispatchers.Main) {
                    onComplete(Result.failure(e))
                }
            }
        }
    }

    /**
     * تحديث yt-dlp تلقائياً
     */
    suspend fun updateYtDlp(context: Context): Boolean =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Updating yt-dlp...")
                val status = YoutubeDL.getInstance()
                    .updateYoutubeDL(
                        appContext = context,
                        updateChannel = YoutubeDL.UpdateChannel.STABLE
                    )
                val updated = status == YoutubeDL.UpdateStatus.DONE
                Log.d(TAG, "yt-dlp update status: $status")
                updated
            } catch (e: Exception) {
                Log.e(TAG, "yt-dlp update failed", e)
                false
            }
        }

    /**
     * جلب معلومات الفيديو (العنوان + صورة مصغرة)
     */
    suspend fun fetchVideoInfo(url: String): VideoInfo? = withContext(Dispatchers.IO) {
        try {
            val request = YoutubeDLRequest(url).apply {
                addOption("--dump-json")
                addOption("--no-playlist")
                addOption("--no-download")
            }
            val response = YoutubeDL.getInstance().execute(request, null, null)
            val json = org.json.JSONObject(response.out)
            VideoInfo(
                title = json.optString("title", ""),
                thumbnail = json.optString("thumbnail", ""),
                duration = json.optLong("duration", 0)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch video info", e)
            null
        }
    }

    data class VideoInfo(
        val title: String,
        val thumbnail: String,
        val duration: Long
    )

    /**
     * حفظ التحميل في قاعدة البيانات
     */
    private fun saveToHistory(url: String, title: String, downloadPath: String) {
        try {
            DatabaseUtil.insertInfo(
                DownloadedVideoInfo(
                    id = 0,
                    videoTitle = title,
                    videoAuthor = "",
                    videoUrl = url,
                    thumbnailUrl = "",
                    videoPath = downloadPath,
                    extractor = "Jiyad"
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save download to history", e)
        }
    }

    /**
     * مسار التحميل: Downloads/جياد
     */
    private fun getDownloadPath(): String {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS
        )
        val jiyadDir = File(downloadsDir, SimpleConfig.DOWNLOAD_DIR_NAME)
        if (!jiyadDir.exists()) {
            jiyadDir.mkdirs()
        }
        return jiyadDir.absolutePath
    }

    /**
     * فحص الملفات المحملة لتحديث MediaStore (الظهور في الاستديو فوراً)
     */
    private fun scanDownloadedFiles(context: Context, directoryPath: String) {
        try {
            val dir = File(directoryPath)
            if (!dir.exists()) return

            val files = dir.listFiles() ?: return
            val filePaths = files.map { it.absolutePath }.toTypedArray()

            if (filePaths.isNotEmpty()) {
                MediaScannerConnection.scanFile(
                    context,
                    filePaths,
                    null
                ) { path, uri ->
                    Log.d(TAG, "Media scanned: $path -> $uri")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Media scan failed", e)
        }
    }
}
