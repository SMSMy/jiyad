package com.junkfood.seal.simple

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import com.junkfood.seal.download.DownloaderV2
import com.junkfood.seal.download.Task
import com.junkfood.seal.util.CONVERT_MP3
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertEquals("", prefs.outputTemplate)
    }

    @Test
    fun testAudioPreferences() {
        val prefs = JiyadPreset.createAudioPreferences()
        assertTrue(prefs.extractAudio)
        assertTrue(prefs.convertAudio)
        assertEquals(CONVERT_MP3, prefs.audioConvertFormat)
        assertEquals("", prefs.formatIdString)
        assertFalse(prefs.aria2c)
        assertEquals("", prefs.outputTemplate)
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
    fun testYtDlpGuard_postponesWhenTaskIsRunning() {
        val map = mutableStateMapOf<Task, Task.State>()
        val downloader = TestDownloader(map)

        assertFalse(JiyadPreset.isAnyTaskRunning(downloader))

        val task = Task(
            url = "https://example.com/video",
            preferences = JiyadPreset.createVideoPreferences()
        )
        val runningState = Task.State(
            downloadState = Task.DownloadState.Running(
                job = Job(),
                taskId = task.id,
                progress = 0.5f,
                progressText = "50%"
            ),
            videoInfo = null,
            viewState = Task.ViewState()
        )
        map[task] = runningState

        assertTrue(JiyadPreset.isAnyTaskRunning(downloader))
    }

    @Test
    fun testYtDlpGuard_allowsWhenTaskCompletedOrIdle() {
        val map = mutableStateMapOf<Task, Task.State>()
        val downloader = TestDownloader(map)

        val task = Task(
            url = "https://example.com/video",
            preferences = JiyadPreset.createVideoPreferences()
        )
        val completedState = Task.State(
            downloadState = Task.DownloadState.Completed(filePath = "/path/to/video.mp4"),
            videoInfo = null,
            viewState = Task.ViewState()
        )
        map[task] = completedState

        assertFalse(JiyadPreset.isAnyTaskRunning(downloader))
    }
}
