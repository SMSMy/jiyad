package com.junkfood.seal.simple

import android.content.Context
import android.os.Environment
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * محرك التحميل المبسط لجياد
 * يستخدم YoutubeDL مباشرة - بدون Koin, Room, أو WorkManager
 */
object JiyadDownloader {

    private const val TAG = "JiyadDownloader"

    /**
     * تحميل فيديو
     */
    suspend fun downloadVideo(
        context: Context,
        url: String,
        onProgress: (Float, String) -> Unit,
        onComplete: (Result<String>) -> Unit
    ) {
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
                for (s in request.buildCommand()) Log.d(TAG, s)

                YoutubeDL.getInstance().execute(
                    request = request,
                    processId = url,
                ) { progress, _, text ->
                    onProgress(progress, text)
                }

                withContext(Dispatchers.Main) {
                    onComplete(Result.success(downloadPath))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Video download failed", e)
                withContext(Dispatchers.Main) {
                    onComplete(Result.failure(e))
                }
            }
        }
    }

    /**
     * تحميل صوت (MP3)
     */
    suspend fun downloadAudio(
        context: Context,
        url: String,
        onProgress: (Float, String) -> Unit,
        onComplete: (Result<String>) -> Unit
    ) {
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
                for (s in request.buildCommand()) Log.d(TAG, s)

                YoutubeDL.getInstance().execute(
                    request = request,
                    processId = url,
                ) { progress, _, text ->
                    onProgress(progress, text)
                }

                withContext(Dispatchers.Main) {
                    onComplete(Result.success(downloadPath))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Audio download failed", e)
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
}
