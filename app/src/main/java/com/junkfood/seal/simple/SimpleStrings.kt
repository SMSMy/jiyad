package com.junkfood.seal.simple

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.annotation.StringRes
import com.junkfood.seal.App.Companion.context
import com.junkfood.seal.R
import java.util.Locale

/**
 * نظام الترجمة والوصول لجياد (المرحلة 5)
 * يربط المفاتيح مباشرة بموارد strings.xml الرسمية لدعم TalkBack وتغيير خط النظام.
 */
object SimpleStrings {

    private const val PREFS_NAME = "jiyad_prefs"
    private const val KEY_LANGUAGE = "app_language"

    enum class Language(val code: String, val label: String) {
        ARABIC("ar", "عربي"),
        ENGLISH("en", "EN")
    }

    /**
     * خريطة ربط المفاتيح النصية بموارد strings.xml
     */
    val stringResourceMap: Map<String, Int> = mapOf(
        "app_title" to R.string.app_name,
        "subtitle" to R.string.jiyad_subtitle,
        "url_label" to R.string.jiyad_paste_url,
        "paste" to R.string.jiyad_paste,
        "clear" to R.string.jiyad_clear,
        "download_video" to R.string.jiyad_download_video,
        "desc_download_video" to R.string.jiyad_desc_download_video,
        "download_audio" to R.string.jiyad_download_audio,
        "desc_download_audio" to R.string.jiyad_desc_download_audio,
        "error_enter_url" to R.string.jiyad_error_enter_url,
        "downloading" to R.string.jiyad_downloading,
        "download_success" to R.string.jiyad_download_success,
        "download_failed" to R.string.jiyad_download_failed,
        "unknown_error" to R.string.jiyad_unknown_error,
        "storage_permission" to R.string.jiyad_storage_permission,
        "update_available" to R.string.jiyad_update_available,
        "new_version" to R.string.jiyad_new_version,
        "update_now" to R.string.jiyad_update_now,
        "later" to R.string.jiyad_later,
        "download_history" to R.string.jiyad_download_history,
        "no_downloads" to R.string.jiyad_no_downloads,
        "delete" to R.string.jiyad_delete,
        "open_file" to R.string.jiyad_open_file,
        "share_file" to R.string.jiyad_share_file,
        "file_unavailable" to R.string.jiyad_file_unavailable,
        "back" to R.string.jiyad_back,
        "more_options" to R.string.jiyad_more_options,
        "ytdlp_update" to R.string.jiyad_ytdlp_update,
        "ytdlp_updating" to R.string.jiyad_ytdlp_updating,
        "ytdlp_updated" to R.string.jiyad_ytdlp_updated,
        "ytdlp_update_failed" to R.string.jiyad_ytdlp_update_failed,
        "ytdlp_already_latest" to R.string.jiyad_ytdlp_already_latest,
        "ytdlp_update_postponed" to R.string.jiyad_ytdlp_update_postponed,
        "advanced_mode" to R.string.jiyad_advanced_mode,
        "update_engine" to R.string.jiyad_update_engine,
        "close" to R.string.jiyad_close,
        "converting" to R.string.jiyad_converting,
        "update_ready" to R.string.jiyad_update_ready,
        "update_tap_install" to R.string.jiyad_update_tap_install,
        "update_downloading" to R.string.jiyad_update_downloading,
        "update_download_failed" to R.string.jiyad_update_download_failed,
        "install_update" to R.string.jiyad_install_update,
        "retry" to R.string.jiyad_retry,
        "update_install_guide" to R.string.jiyad_update_install_guide,
        "switch_to_simple" to R.string.jiyad_switch_to_simple,
        "download_canceled" to R.string.jiyad_download_canceled,
        "fetching_info" to R.string.jiyad_fetching_info
    )

    fun getDefaultLanguage(): Language {
        val deviceLang = Locale.getDefault().language
        return if (deviceLang == "ar") Language.ARABIC else Language.ENGLISH
    }

    fun saveLanguage(context: Context, language: Language) {
        getPrefs(context).edit().putString(KEY_LANGUAGE, language.code).apply()
    }

    fun getSavedLanguage(context: Context): Language {
        val saved = getPrefs(context).getString(KEY_LANGUAGE, null)
        return when (saved) {
            "ar" -> Language.ARABIC
            "en" -> Language.ENGLISH
            else -> getDefaultLanguage()
        }
    }

    fun toggle(current: Language): Language {
        return if (current == Language.ARABIC) Language.ENGLISH else Language.ARABIC
    }

    /**
     * جلب النص من موارد strings.xml المترجمة
     */
    fun get(context: Context, key: String, language: Language = getSavedLanguage(context)): String {
        val resId = stringResourceMap[key] ?: return key
        return getLocalizedResource(context, resId, language)
    }

    /**
     * دالة التوافق مع الكود القائم
     */
    fun get(key: String, language: Language): String {
        return get(context, key, language)
    }

    fun getLocalizedResource(context: Context, @StringRes resId: Int, language: Language): String {
        return try {
            val config = Configuration(context.resources.configuration)
            config.setLocale(Locale(language.code))
            val localizedContext = context.createConfigurationContext(config)
            localizedContext.resources.getString(resId)
        } catch (_: Exception) {
            context.getString(resId)
        }
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
