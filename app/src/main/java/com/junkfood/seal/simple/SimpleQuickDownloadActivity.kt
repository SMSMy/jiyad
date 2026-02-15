package com.junkfood.seal.simple

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.junkfood.seal.util.PreferenceUtil
import com.junkfood.seal.util.matchUrlFromSharedText
import com.junkfood.seal.util.setLanguage
import kotlinx.coroutines.runBlocking

/**
 * نشاط التحميل السريع - يبدأ التحميل فوراً كفيديو عند مشاركة الرابط
 * هذا النشاط يستخدم بدلاً من QuickDownloadActivity الأصلي
 */
class SimpleQuickDownloadActivity : ComponentActivity() {

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

        // بدء التحميل فوراً كفيديو
        Toast.makeText(this, "⏳ جاري التحميل...", Toast.LENGTH_SHORT).show()
        SimpleDownloadService.startDownload(this, url, isAudio = false)

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
