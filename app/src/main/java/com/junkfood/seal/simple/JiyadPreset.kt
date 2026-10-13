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
import com.junkfood.seal.util.YT_DLP_AUTO_UPDATE
import com.junkfood.seal.util.DatabaseUtil
import com.junkfood.seal.util.UpdateUtil
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
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
        onWriteYtDlpAutoUpdate: (Boolean) -> Unit = {},
        onMarkApplied: () -> Unit,
    ): Pair<String, String> {
        if (!isApplied) {
            if (!hasVideoKey) {
                onWriteVideo(defaultVideoPath)
            }
            if (!hasAudioKey) {
                onWriteAudio(defaultAudioPath)
            }
            // تعطيل التحديث التلقائي غير المحمي في المنبع عند الضبط الأول لحماية المستخدم
            onWriteYtDlpAutoUpdate(false)
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
     * - كتابة YT_DLP_AUTO_UPDATE = false لمنع التحديث غير المحمي في المنبع
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
            onWriteYtDlpAutoUpdate = { YT_DLP_AUTO_UPDATE.updateBoolean(it) },
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
     * التحقق مما إذا كانت حالة المهمة تعتبر مانعة لتحديث yt-dlp (D16):
     * أي حالة ليست (Completed أو Canceled أو Error) تعتبر مانعة
     * وتشمل: Idle, FetchingInfo, ReadyWithInfo, Running.
     * السبب الهندسي: منع استبدال مكتبات بايثون أو محرك yt-dlp الثنائي أثناء تشغيل أو تجهيز
     * أي تنزيل لتفادي انهيار العملية (Crash/SIGSEGV) أو إفساد الملفات الجاري تحميلها.
     */
    fun isBlockingDownloadState(state: Task.DownloadState): Boolean {
        return when (state) {
            is Task.DownloadState.Completed,
            is Task.DownloadState.Canceled,
            is Task.DownloadState.Error -> false
            is Task.DownloadState.Idle,
            is Task.DownloadState.FetchingInfo,
            is Task.DownloadState.ReadyWithInfo,
            is Task.DownloadState.Running -> true
        }
    }

    /**
     * التحقق من وجود أي مهمة مانعة لتحديث yt-dlp في DownloaderV2 (D16):
     * يشمل كل المهام التي ليست في حالة نهائية (Idle, FetchingInfo, ReadyWithInfo, Running).
     */
    fun isAnyTaskActive(downloader: DownloaderV2): Boolean {
        return downloader.getTaskStateMap().values.any {
            isBlockingDownloadState(it.downloadState)
        }
    }

    /**
     * متوافق مع الاستدعاءات القائمة مع تحويله للفحص الشامل
     */
    fun isAnyTaskRunning(downloader: DownloaderV2): Boolean = isAnyTaskActive(downloader)

    /**
     * استضافة تحديث yt-dlp بحارس المهام الموحد (D16):
     * الفحص يتم مباشرة في اللحظة نفسها قبل استدعاء UpdateUtil.updateYtDlp().
     * إذا كان هناك أي تنزيل نشط أو قيد التجهيز (Idle/FetchingInfo/ReadyWithInfo/Running)،
     * يتم تأجيل التحديث فوراً وتسجيل تفاصيل المهام المانعة في اللوج.
     */
    suspend fun safeUpdateYtDlp(
        downloader: DownloaderV2,
        logPoster: (String) -> Unit = { Log.w(TAG, it) },
        updater: suspend () -> YoutubeDL.UpdateStatus? = { UpdateUtil.updateYtDlp() }
    ): YoutubeDL.UpdateStatus? {
        val blockingTasks = downloader.getTaskStateMap().entries.filter {
            isBlockingDownloadState(it.value.downloadState)
        }

        if (blockingTasks.isNotEmpty()) {
            val details = blockingTasks.joinToString { (task, state) ->
                "${task.url.take(60)} (State: ${state.downloadState::class.simpleName})"
            }
            val msg = "yt-dlp update postponed: active download/fetch tasks in progress: $details"
            logPoster(msg)
            return null
        }
        return updater()
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

    const val KEY_HISTORY_MIGRATED_V1 = "jiyad_history_fixed_v1"

    data class MigrationDecision(
        val needsCorrection: Boolean,
        val newPath: String?,
        val isUnavailable: Boolean
    )

    /**
     * تقييم ترحيل سجل التنزيل لمرة واحدة (المرحلة 4.2):
     * - الصفوف التي تحمل مسار مجلد أو extractor == "Jiyad" تُفحص:
     *   إذا وُجد ملف وحيد واضح في المجلد يُصحح المسار إليه، وإلا تُعلَّم «غير متاح» دون حذف السجل.
     */
    fun evaluateHistoryItemMigration(
        videoPath: String,
        extractor: String?,
        listFiles: (File) -> List<File>? = { it.listFiles()?.toList() }
    ): MigrationDecision {
        val file = File(videoPath)
        val isDir = file.isDirectory
        val isOldJiyad = extractor == "Jiyad" || isDir

        if (!isOldJiyad && file.exists() && file.isFile) {
            return MigrationDecision(needsCorrection = false, newPath = null, isUnavailable = false)
        }

        if (isOldJiyad) {
            val dir = if (isDir) file else file.parentFile
            if (dir != null && dir.exists() && dir.isDirectory) {
                val candidateFiles = listFiles(dir)?.filter { f ->
                    f.isFile && !f.name.startsWith(".") &&
                        (f.name.endsWith(".mp4", ignoreCase = true) ||
                         f.name.endsWith(".mp3", ignoreCase = true) ||
                         f.name.endsWith(".m4a", ignoreCase = true) ||
                         f.name.endsWith(".webm", ignoreCase = true) ||
                         f.name.endsWith(".mkv", ignoreCase = true))
                } ?: emptyList()

                if (candidateFiles.size == 1) {
                    return MigrationDecision(
                        needsCorrection = true,
                        newPath = candidateFiles[0].absolutePath,
                        isUnavailable = false
                    )
                }
            }
            return MigrationDecision(needsCorrection = false, newPath = null, isUnavailable = true)
        }

        val exists = file.exists() && file.isFile
        return MigrationDecision(needsCorrection = false, newPath = null, isUnavailable = !exists)
    }

    /**
     * تنفيذ ترحيل السجل لمرة واحدة في الخلفية (المرحلة 4.2)
     */
    suspend fun migrateHistoryIfNeeded() = withContext(Dispatchers.IO) {
        if (KEY_HISTORY_MIGRATED_V1.getBoolean(false)) return@withContext
        try {
            val list = DatabaseUtil.getDownloadHistoryFlow().first()
            for (info in list) {
                val decision = evaluateHistoryItemMigration(info.videoPath, info.extractor)
                if (decision.needsCorrection && decision.newPath != null) {
                    DatabaseUtil.deleteInfoById(info.id)
                    DatabaseUtil.insertInfo(info.copy(id = 0, videoPath = decision.newPath))
                }
            }
            KEY_HISTORY_MIGRATED_V1.updateBoolean(true)
            Log.d(TAG, "History migration completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to migrate history", e)
        }
    }
}
