package com.junkfood.seal.simple

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.junkfood.seal.App
import com.junkfood.seal.download.DownloaderV2
import com.junkfood.seal.download.Task
import com.junkfood.seal.util.PreferenceUtil
import com.junkfood.seal.util.matchUrlFromSharedText
import com.junkfood.seal.util.setLanguage
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/**
 * نشاط التحميل السريع - يبدأ التحميل فوراً كفيديو عند مشاركة الرابط عبر DownloaderV2 الموحد
 * (مؤقت حتى نقل فلاتر المشاركة في المانيفست في المرحلة 3.4)
 */
class SimpleQuickDownloadActivity : ComponentActivity(), KoinComponent {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT < 33) {
            runBlocking { setLanguage(PreferenceUtil.getLocaleFromPreference()) }
        }

        val url = extractUrl(intent)

        if (url.isBlank()) {
            finish()
            return
        }

        // بدء التحميل فوراً كفيديو عبر DownloaderV2 الموحد
        App.startService()
        JiyadPreset.initPresetAndSyncMemory()
        val downloader: DownloaderV2 = get()
        downloader.enqueue(Task(url = url, preferences = JiyadPreset.createVideoPreferences()))

        Toast.makeText(this, "⏳ جاري التحميل...", Toast.LENGTH_SHORT).show()

        finish()
    }

    private fun extractUrl(intent: Intent?): String {
        if (intent == null) return ""
        return when (intent.action) {
            Intent.ACTION_SEND -> {
                intent.getStringExtra(Intent.EXTRA_TEXT)?.let { sharedContent ->
                    matchUrlFromSharedText(sharedContent)
                } ?: ""
            }
            Intent.ACTION_VIEW -> {
                intent.dataString ?: ""
            }
            else -> ""
        }
    }
}

