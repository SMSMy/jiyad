package com.junkfood.seal.simple

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import com.junkfood.seal.download.DownloaderV2
import com.junkfood.seal.download.Task
import com.junkfood.seal.util.CONVERT_MP3
import com.junkfood.seal.util.DownloadUtil
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختبارات الوحدة الخاصة بوصلة الضبط المسبق وحارس yt-dlp لجياد (المرحلة 1)
 */
class JiyadPresetTest {

    @Test
    fun testVideoPreferences() {
        val prefs = JiyadPreset.createVideoPreferences()
        assertEquals(SimpleConfig.VIDEO_FORMAT, prefs.formatIdString)
        assertTrue(prefs.mergeAudioStream)
        assertFalse(prefs.extractAudio)
        assertFalse(prefs.aria2c)
        assertEquals(DownloadUtil.OUTPUT_TEMPLATE_DEFAULT, prefs.outputTemplate)
    }

    @Test
    fun testAudioPreferences() {
        val prefs = JiyadPreset.createAudioPreferences()
        assertTrue(prefs.extractAudio)
        assertTrue(prefs.convertAudio)
        assertEquals(CONVERT_MP3, prefs.audioConvertFormat)
        assertEquals("", prefs.formatIdString)
        assertFalse(prefs.aria2c)
        assertEquals(DownloadUtil.OUTPUT_TEMPLATE_DEFAULT, prefs.outputTemplate)
    }

    @Test
    fun testResolvePresetDirectories_cleanInstall() {
        var writtenVideo: String? = null
        var writtenAudio: String? = null
        var markedApplied = false

        val (finalVideo, finalAudio) = JiyadPreset.resolvePresetDirectories(
            isApplied = false,
            hasVideoKey = false,
            hasAudioKey = false,
            existingVideoPath = null,
            existingAudioPath = null,
            defaultVideoPath = "/sdcard/Download/Jiyad",
            defaultAudioPath = "/sdcard/Download/Jiyad/Audio",
            onWriteVideo = { writtenVideo = it },
            onWriteAudio = { writtenAudio = it },
            onMarkApplied = { markedApplied = true },
        )

        assertEquals("/sdcard/Download/Jiyad", writtenVideo)
        assertEquals("/sdcard/Download/Jiyad/Audio", writtenAudio)
        assertTrue(markedApplied)
        assertEquals("/sdcard/Download/Jiyad", finalVideo)
        assertEquals("/sdcard/Download/Jiyad/Audio", finalAudio)
    }

    @Test
    fun testResolvePresetDirectories_preserveExistingUserCustomization() {
        var writtenVideo: String? = null
        var writtenAudio: String? = null
        var markedApplied = false

        val (finalVideo, finalAudio) = JiyadPreset.resolvePresetDirectories(
            isApplied = false,
            hasVideoKey = true,
            hasAudioKey = true,
            existingVideoPath = "/storage/emulated/0/CustomVideo",
            existingAudioPath = "/storage/emulated/0/CustomAudio",
            defaultVideoPath = "/sdcard/Download/Jiyad",
            defaultAudioPath = "/sdcard/Download/Jiyad/Audio",
            onWriteVideo = { writtenVideo = it },
            onWriteAudio = { writtenAudio = it },
            onMarkApplied = { markedApplied = true },
        )

        // يجب عدم الكتابة فوق تخصيص المستخدم القائم
        assertEquals(null, writtenVideo)
        assertEquals(null, writtenAudio)
        assertTrue(markedApplied)
        assertEquals("/storage/emulated/0/CustomVideo", finalVideo)
        assertEquals("/storage/emulated/0/CustomAudio", finalAudio)
    }

    @Test
    fun testResolvePresetDirectories_alreadyApplied() {
        var writtenVideo: String? = null
        var writtenAudio: String? = null
        var markedApplied = false

        val (finalVideo, finalAudio) = JiyadPreset.resolvePresetDirectories(
            isApplied = true,
            hasVideoKey = true,
            hasAudioKey = true,
            existingVideoPath = "/my/video",
            existingAudioPath = "/my/audio",
            defaultVideoPath = "/sdcard/Download/Jiyad",
            defaultAudioPath = "/sdcard/Download/Jiyad/Audio",
            onWriteVideo = { writtenVideo = it },
            onWriteAudio = { writtenAudio = it },
            onMarkApplied = { markedApplied = true },
        )

        assertEquals(null, writtenVideo)
        assertEquals(null, writtenAudio)
        assertFalse(markedApplied)
        assertEquals("/my/video", finalVideo)
        assertEquals("/my/audio", finalAudio)
    }

    private class TestDownloader(
        private val map: SnapshotStateMap<Task, Task.State> = mutableStateMapOf()
    ) : DownloaderV2 {
        override fun getTaskStateMap(): SnapshotStateMap<Task, Task.State> = map
        override fun cancel(task: Task): Boolean = false
        override fun restart(task: Task) {}
        override fun enqueue(task: Task) {}
        override fun enqueue(task: Task, state: Task.State) {}
        override fun remove(task: Task): Boolean = true
    }

    @Test
    fun testYtDlpGuard_allFourActiveStates_postponeUpdate() {
        val activeStates: List<Task.DownloadState> = listOf(
            Task.DownloadState.Idle,
            Task.DownloadState.FetchingInfo(job = Job(), taskId = "task-fetch"),
            Task.DownloadState.ReadyWithInfo,
            Task.DownloadState.Running(
                job = Job(),
                taskId = "task-run",
                progress = 0.5f,
                progressText = "50%"
            )
        )

        for (state in activeStates) {
            // التحقق من أن الحالة تعتبر مانعة بشكل منفرد (D16)
            assertTrue(
                "State ${state::class.simpleName} must be recognized as blocking",
                JiyadPreset.isBlockingDownloadState(state)
            )

            // التحقق من أن DownloaderV2 يسجل وجود مهمة نشطة مانعة
            val map = mutableStateMapOf<Task, Task.State>()
            val downloader = TestDownloader(map)
            val task = Task(
                url = "https://example.com/test_${state::class.simpleName}",
                preferences = JiyadPreset.createVideoPreferences()
            )
            map[task] = Task.State(downloadState = state, videoInfo = null, viewState = Task.ViewState())

            assertTrue(
                "isAnyTaskActive must be true for ${state::class.simpleName}",
                JiyadPreset.isAnyTaskActive(downloader)
            )
            assertTrue(
                "isAnyTaskRunning must be true for ${state::class.simpleName}",
                JiyadPreset.isAnyTaskRunning(downloader)
            )
        }
    }

    @Test
    fun testYtDlpGuard_terminalStates_allowUpdate() {
        val terminalStates: List<Task.DownloadState> = listOf(
            Task.DownloadState.Completed(filePath = "/path/to/downloaded.mp4"),
            Task.DownloadState.Canceled(action = Task.RestartableAction.Download),
            Task.DownloadState.Error(
                throwable = RuntimeException("Network fail"),
                action = Task.RestartableAction.Download
            )
        )

        for (state in terminalStates) {
            // التحقق من أن الحالة لا تعتبر مانعة (D16)
            assertFalse(
                "State ${state::class.simpleName} must NOT be blocking",
                JiyadPreset.isBlockingDownloadState(state)
            )

            // التحقق من أن DownloaderV2 لا يمنع التحديث إذا كانت المهمة منتهية
            val map = mutableStateMapOf<Task, Task.State>()
            val downloader = TestDownloader(map)
            val task = Task(
                url = "https://example.com/test_${state::class.simpleName}",
                preferences = JiyadPreset.createVideoPreferences()
            )
            map[task] = Task.State(downloadState = state, videoInfo = null, viewState = Task.ViewState())

            assertFalse(
                "isAnyTaskActive must be false for ${state::class.simpleName}",
                JiyadPreset.isAnyTaskActive(downloader)
            )
            assertFalse(
                "isAnyTaskRunning must be false for ${state::class.simpleName}",
                JiyadPreset.isAnyTaskRunning(downloader)
            )
        }
    }

    @Test
    fun testSafeUpdateYtDlp_downloadInProgress_postponesUpdateAndLogsReason() = runBlocking {
        val map = mutableStateMapOf<Task, Task.State>()
        val downloader = TestDownloader(map)

        // محاكاة بدء تنزيل من الواجهة المبسطة
        val task = Task(
            url = "https://example.com/active_video_download",
            preferences = JiyadPreset.createVideoPreferences()
        )
        map[task] = Task.State(
            downloadState = Task.DownloadState.Running(
                job = Job(),
                taskId = task.id,
                progress = 0.35f,
                progressText = "35%"
            ),
            videoInfo = null,
            viewState = Task.ViewState()
        )

        var updaterCalled = false
        var capturedLog: String? = null

        // استدعاء فحص التحديث
        val result = JiyadPreset.safeUpdateYtDlp(
            downloader = downloader,
            logPoster = { capturedLog = it },
            updater = {
                updaterCalled = true
                YoutubeDL.UpdateStatus.DONE
            }
        )

        // التحقق من عدم بدء التحديث (إرجاع null وعدم استدعاء الدالة المحدثة)
        assertNull(result)
        assertFalse(updaterCalled)

        // التحقق من تسجيل سطر اللوج مع سبب التأجيل وتفاصيل المهمة المانعة
        assertTrue(capturedLog != null)
        assertTrue(capturedLog!!.contains("yt-dlp update postponed"))
        assertTrue(capturedLog!!.contains("active_video_download"))
        assertTrue(capturedLog!!.contains("Running"))
    }

    @Test
    fun testSafeUpdateYtDlp_terminalTasksOnly_allowsUpdate() = runBlocking {
        val map = mutableStateMapOf<Task, Task.State>()
        val downloader = TestDownloader(map)

        // مهام مكتملة فقط
        val task = Task(
            url = "https://example.com/finished_video",
            preferences = JiyadPreset.createVideoPreferences()
        )
        map[task] = Task.State(
            downloadState = Task.DownloadState.Completed(filePath = "/storage/video.mp4"),
            videoInfo = null,
            viewState = Task.ViewState()
        )

        var updaterCalled = false
        var capturedLog: String? = null

        val result = JiyadPreset.safeUpdateYtDlp(
            downloader = downloader,
            logPoster = { capturedLog = it },
            updater = {
                updaterCalled = true
                YoutubeDL.UpdateStatus.DONE
            }
        )

        assertEquals(YoutubeDL.UpdateStatus.DONE, result)
        assertTrue(updaterCalled)
        assertNull(capturedLog)
    }

    @Test
    fun testIsEligibleUrl() {
        assertTrue(JiyadPreset.isEligibleUrl("https://youtu.be/dQw4w9WgXcQ"))
        assertFalse(JiyadPreset.isEligibleUrl(""))
        assertFalse(JiyadPreset.isEligibleUrl("   "))
        assertFalse(JiyadPreset.isEligibleUrl("https://example.com\nhttps://other.com"))
        assertFalse(JiyadPreset.isEligibleUrl("https://example.com with some text"))
    }

    @Test
    fun testHistoryMigration_singleFileInDir_correctsPath() {
        val tempDir = java.io.File.createTempFile("jiyad_test_dir", "").apply {
            delete()
            mkdirs()
        }
        val mediaFile = java.io.File(tempDir, "video.mp4").apply { createNewFile() }

        val decision = JiyadPreset.evaluateHistoryItemMigration(
            videoPath = tempDir.absolutePath,
            extractor = "Jiyad",
            listFiles = { listOf(mediaFile) }
        )

        assertTrue(decision.needsCorrection)
        assertEquals(mediaFile.absolutePath, decision.newPath)
        assertFalse(decision.isUnavailable)

        mediaFile.delete()
        tempDir.delete()
    }

    @Test
    fun testHistoryMigration_multipleFilesInDir_marksUnavailable() {
        val tempDir = java.io.File.createTempFile("jiyad_test_dir_multi", "").apply {
            delete()
            mkdirs()
        }
        val media1 = java.io.File(tempDir, "video1.mp4").apply { createNewFile() }
        val media2 = java.io.File(tempDir, "video2.mp4").apply { createNewFile() }

        val decision = JiyadPreset.evaluateHistoryItemMigration(
            videoPath = tempDir.absolutePath,
            extractor = "Jiyad",
            listFiles = { listOf(media1, media2) }
        )

        assertFalse(decision.needsCorrection)
        assertTrue(decision.isUnavailable)

        media1.delete()
        media2.delete()
        tempDir.delete()
    }

    @Test
    fun testHistoryMigration_emptyDir_marksUnavailable() {
        val tempDir = java.io.File.createTempFile("jiyad_test_dir_empty", "").apply {
            delete()
            mkdirs()
        }

        val decision = JiyadPreset.evaluateHistoryItemMigration(
            videoPath = tempDir.absolutePath,
            extractor = "Jiyad",
            listFiles = { emptyList() }
        )

        assertFalse(decision.needsCorrection)
        assertTrue(decision.isUnavailable)

        tempDir.delete()
    }
}
