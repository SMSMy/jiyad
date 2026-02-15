package com.junkfood.seal

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.mutableStateOf
import com.junkfood.seal.App.Companion.context
import com.junkfood.seal.simple.SimpleHomeScreen
import com.junkfood.seal.ui.common.LocalDarkTheme
import com.junkfood.seal.ui.common.SettingsProvider
import com.junkfood.seal.ui.theme.SealTheme
import com.junkfood.seal.util.PreferenceUtil
import com.junkfood.seal.util.matchUrlFromSharedText
import com.junkfood.seal.util.setLanguage
import kotlinx.coroutines.runBlocking
import org.koin.compose.KoinContext

class MainActivity : AppCompatActivity() {

    // حالة الرابط المشترك - قابلة للتحديث من onNewIntent
    private val sharedUrlState = mutableStateOf("")

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT < 33) {
            runBlocking { setLanguage(PreferenceUtil.getLocaleFromPreference()) }
        }
        enableEdgeToEdge()

        context = this.baseContext

        // استخراج الرابط من intent المشاركة
        sharedUrlState.value = extractSharedUrl(intent)

        setContent {
            KoinContext {
                val windowSizeClass = calculateWindowSizeClass(this)
                SettingsProvider(windowWidthSizeClass = windowSizeClass.widthSizeClass) {
                    SealTheme(
                        darkTheme = LocalDarkTheme.current.isDarkTheme(),
                        isHighContrastModeEnabled = LocalDarkTheme.current.isHighContrastModeEnabled,
                    ) {
                        SimpleHomeScreen(initialUrl = sharedUrlState.value)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // عند مشاركة رابط جديد أثناء تشغيل التطبيق
        val newUrl = extractSharedUrl(intent)
        if (newUrl.isNotBlank()) {
            sharedUrlState.value = newUrl
        }
    }

    private fun extractSharedUrl(intent: Intent?): String {
        if (intent == null) return ""
        return when (intent.action) {
            Intent.ACTION_SEND -> {
                intent.getStringExtra(Intent.EXTRA_TEXT)?.let { matchUrlFromSharedText(it) } ?: ""
            }
            Intent.ACTION_VIEW -> intent.dataString ?: ""
            else -> ""
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
