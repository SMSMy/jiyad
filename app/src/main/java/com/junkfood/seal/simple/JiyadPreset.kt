package com.junkfood.seal.simple

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.junkfood.seal.App
import com.junkfood.seal.download.DownloaderV2
import com.junkfood.seal.download.Task
import com.junkfood.seal.util.CONVERT_MP3
import com.junkfood.seal.util.DownloadUtil
import com.junkfood.seal.util.AUDIO_DIRECTORY
import com.junkfood.seal.util.PreferenceUtil
import com.junkfood.seal.util.PreferenceUtil.getBoolean
import com.junkfood.seal.util.PreferenceUtil.getString
import com.junkfood.seal.util.PreferenceUtil.updateBoolean
import com.junkfood.seal.util.PreferenceUtil.updateString
import com.junkfood.seal.util.VIDEO_DIRECTORY
import com.junkfood.seal.util.UpdateUtil
import com.yausername.youtubedl_android.YoutubeDL
import org.koin.core.context.GlobalContext
import java.io.File

/**
 * وصلة الضبط المسبق ومزامنة الذاكرة الحية وحارس yt-dlp لجياد (المرحلة 1)
 */
object JiyadPreset {
    private const val TAG = "JiyadPreset"
    const val KEY_JIYAD_PRESET_APPLIED = "jiyad_preset_applied"
    const val KEY_JIYAD_UI_MODE = "jiyad_ui_mode"
    const val UI_MODE_SIMPLE = "simple"
    const val UI_MODE_ADVANCED = "advanced"

    /**
     * قراءة وضع الواجهة الحالي (الافتراضي simple إن كان غائباً) (المرحلة 3.1)
     */
    fun getUiMode(): String {
        val mode = KEY_JIYAD_UI_MODE.getString(UI_MODE_SIMPLE)
        return if (mode.isBlank()) UI_MODE_SIMPLE else mode
    }

    /**
     * كتابة وضع الواجهة عبر واجهة PreferenceUtil بلا لمس PreferenceUtil.kt (المرحلة 3.1)
     */
    fun setUiMode(mode: String) {
        KEY_JIYAD_UI_MODE.updateString(mode)
    }

    /**
     * ترجمة خيارات الفيديو إلى DownloadPreferences (المرحلة 1.1):
     * - formatIdString = SimpleConfig.VIDEO_FORMAT
     * - mergeAudioStream = true
     * - aria2c = false
     * - outputTemplate = "" (عدم لمس قالب المخرجات)
     */
    fun createVideoPreferences(): DownloadUtil.DownloadPreferences {
        return DownloadUtil.DownloadPreferences.EMPTY.copy(
            formatIdString = SimpleConfig.VIDEO_FORMAT,
            mergeAudioStream = true,
            extractAudio = false,
            aria2c = false,
            outputTemplate = "",
        )
    }

    /**
     * ترجمة خيارات الصوت إلى DownloadPreferences (المرحلة 1.1):
     * - extractAudio = true
     * - convertAudio = true مع تحويل MP3
     * - formatIdString = ""
     * - aria2c = false
     * - outputTemplate = "" (عدم لمس قالب المخرجات)
     */
    fun createAudioPreferences(): DownloadUtil.DownloadPreferences {
        return DownloadUtil.DownloadPreferences.EMPTY.copy(
            extractAudio = true,
            convertAudio = true,
            audioConvertFormat = CONVERT_MP3,
            formatIdString = "",
            aria2c = false,
            outputTemplate = "",
        )
    }

    /**
     * منطق فحص وتطبيق مسارات التنزيل النقي (D10):
     * يكتب في التخزين الدائم فقط إذا كان المفتاح غائباً، لحماية إعدادات المستخدمين المرقّين.
     */
    fun resolvePresetDirectories(
        isApplied: Boolean,
        hasVideoKey: Boolean,
        hasAudioKey: Boolean,
        existingVideoPath: String?,
        existingAudioPath: String?,
        defaultVideoPath: String,
        defaultAudioPath: String,
        onWriteVideo: (String) -> Unit,
        onWriteAudio: (String) -> Unit,
        onMarkApplied: () -> Unit,
    ): Pair<String, String> {
        if (!isApplied) {
            if (!hasVideoKey) {
                onWriteVideo(defaultVideoPath)
            }
            if (!hasAudioKey) {
                onWriteAudio(defaultAudioPath)
            }
            onMarkApplied()
        }

        val finalVideo = if (hasVideoKey && !existingVideoPath.isNullOrBlank()) existingVideoPath else defaultVideoPath
        val finalAudio = if (hasAudioKey && !existingAudioPath.isNullOrBlank()) existingAudioPath else defaultAudioPath

        return Pair(finalVideo, finalAudio)
    }

    /**
     * الضبط الأول ومزامنة الذاكرة الحية (D10):
     * - فحص مفتاح jiyad_preset_applied
     * - لكل من VIDEO_DIRECTORY و AUDIO_DIRECTORY: يكتب في MMKV فقط إذا كان المفتاح غائباً
     * - إسناد المسارات مباشرة للمتغيرين العامين في الذاكرة الحية:
     *     App.videoDownloadDir = videoDir.absolutePath
     *     App.audioDownloadDir = audioDir.absolutePath
     *   (تجنب استدعاء App.updateDownloadDir لأنها تشترط Uri لمنتقي SAF)
     */
    fun initPresetAndSyncMemory(): Pair<String, String> {
        val defaultVideoDir = runCatching {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            File(downloadsDir, SimpleConfig.DOWNLOAD_DIR_NAME)
        }.getOrElse { File("/sdcard/Download", SimpleConfig.DOWNLOAD_DIR_NAME) }

        val defaultAudioDir = File(defaultVideoDir, "Audio")

        val applied = KEY_JIYAD_PRESET_APPLIED.getBoolean(false)
        val hasVideo = PreferenceUtil.containsKey(VIDEO_DIRECTORY)
        val hasAudio = PreferenceUtil.containsKey(AUDIO_DIRECTORY)

        val existingVideo = if (hasVideo) VIDEO_DIRECTORY.getString(defaultVideoDir.absolutePath) else null
        val existingAudio = if (hasAudio) AUDIO_DIRECTORY.getString(defaultAudioDir.absolutePath) else null

        val (finalVideo, finalAudio) = resolvePresetDirectories(
            isApplied = applied,
            hasVideoKey = hasVideo,
            hasAudioKey = hasAudio,
            existingVideoPath = existingVideo,
            existingAudioPath = existingAudio,
            defaultVideoPath = defaultVideoDir.absolutePath,
            defaultAudioPath = defaultAudioDir.absolutePath,
            onWriteVideo = { VIDEO_DIRECTORY.updateString(it) },
            onWriteAudio = { AUDIO_DIRECTORY.updateString(it) },
            onMarkApplied = { KEY_JIYAD_PRESET_APPLIED.updateBoolean(true) }
        )

        // إسناد مباشر في الذاكرة الحية لضمان سريانها في الجلسة الأولى فوراً
        App.videoDownloadDir = finalVideo
        App.audioDownloadDir = finalAudio

        try {
            File(finalVideo).mkdirs()
            File(finalAudio).mkdirs()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create target directories", e)
        }

        return Pair(finalVideo, finalAudio)
    }

    /**
     * التحقق من وجود مهام تنزيل جارية في DownloaderV2
     */
    fun isAnyTaskRunning(downloader: DownloaderV2): Boolean {
        return downloader.getTaskStateMap().values.any {
            it.downloadState is Task.DownloadState.Running
        }
    }

    /**
     * استضافة تحديث yt-dlp بحارس المهام (D16):
     * إذا كان هناك أي تنزيل نشط، يتم تأجيل التحديث لحماية العملية الحالية.
     */
    suspend fun safeUpdateYtDlp(downloader: DownloaderV2): YoutubeDL.UpdateStatus? {
        if (isAnyTaskRunning(downloader)) {
            Log.w(TAG, "yt-dlp update postponed: active download in progress in DownloaderV2")
            return null
        }
        return UpdateUtil.updateYtDlp()
    }

    /**
     * فحص صحة الرابط للتنزيل السريع (رابط فردي صالح غير متعدد الأسطر) (المرحلة 3.4)
     */
    fun isEligibleUrl(url: String): Boolean {
        val trimmed = url.trim()
        return trimmed.isNotBlank() && !trimmed.contains('\n') && !trimmed.contains(' ')
    }

    /**
     * التحقق مما إذا كانت المشاركة مؤهلة للتنزيل الفوري في الوضع المبسط (D11 / المرحلة 3.4):
     * - الوضع مبسط
     * - الرابط صالح وفردي وغير متعدد الأسطر
     * - إذن الإشعارات ممنوح (على Android 13+)
     */
    fun isEligibleForQuickDownload(context: Context, url: String): Boolean {
        if (getUiMode() != UI_MODE_SIMPLE) return false
        if (!isEligibleUrl(url)) return false
        if (Build.VERSION.SDK_INT >= 33) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) return false
        }
        return true
    }

    /**
     * معالجة التنزيل السريع إذا توفرت الشروط (D11 / المرحلة 3.4):
     * إذا كان مؤهلاً: ينفذ التنزيل ويرجع true لإنهاء النشاط.
     * إذا لم يكن مؤهلاً: يرجع false ليسقط التطبيق تلقائياً لورقة Seal (ShowSheet) دون إنهاء صامت.
     */
    fun handleQuickDownloadIfEligible(context: Context, url: String): Boolean {
        if (!isEligibleForQuickDownload(context, url)) {
            return false
        }

        return try {
            App.startService()
            initPresetAndSyncMemory()
            val downloader: DownloaderV2 = GlobalContext.get().get()
            downloader.enqueue(Task(url = url.trim(), preferences = createVideoPreferences()))
            Toast.makeText(context, "⏳ جاري التحميل...", Toast.LENGTH_SHORT).show()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start quick download in simple mode", e)
            false
        }
    }
}
