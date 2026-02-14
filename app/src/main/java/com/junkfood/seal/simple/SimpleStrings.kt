package com.junkfood.seal.simple

import android.content.Context
import android.content.SharedPreferences
import java.util.Locale

/**
 * نظام الترجمة البسيط لجياد
 * Simple localization system for Jiyad
 */
object SimpleStrings {

    private const val PREFS_NAME = "jiyad_prefs"
    private const val KEY_LANGUAGE = "app_language"

    enum class Language(val code: String, val label: String) {
        ARABIC("ar", "عربي"),
        ENGLISH("en", "EN")
    }

    // النصوص العربية
    private val AR = mapOf(
        "app_title" to "جياد",
        "subtitle" to "تحميل سهل وسريع",
        "url_label" to "ضع رابط الفيديو هنا",
        "paste" to "لصق",
        "download_video" to "تحميل فيديو 🎬",
        "download_audio" to "تحميل صوت 🎵",
        "error_enter_url" to "❌ أدخل الرابط أولاً",
        "downloading" to "⏳ جاري التحميل...",
        "download_success" to "✅ تم التحميل بنجاح!",
        "download_failed" to "❌ فشل التحميل:",
        "unknown_error" to "خطأ غير معروف",
        "storage_permission" to "يجب السماح بإذن التخزين",
        "update_available" to "تحديث جديد متوفر! 🎉",
        "new_version" to "الإصدار الجديد:",
        "update_now" to "تحديث الآن",
        "later" to "لاحقاً"
    )

    // النصوص الانجليزية
    private val EN = mapOf(
        "app_title" to "Jiyad",
        "subtitle" to "Easy & Fast Download",
        "url_label" to "Paste video link here",
        "paste" to "Paste",
        "download_video" to "Download Video 🎬",
        "download_audio" to "Download Audio 🎵",
        "error_enter_url" to "❌ Enter URL first",
        "downloading" to "⏳ Downloading...",
        "download_success" to "✅ Download complete!",
        "download_failed" to "❌ Download failed:",
        "unknown_error" to "Unknown error",
        "storage_permission" to "Storage permission is required",
        "update_available" to "New Update Available! 🎉",
        "new_version" to "New version:",
        "update_now" to "Update Now",
        "later" to "Later"
    )

    /**
     * تحديد اللغة الافتراضية بناءً على لغة الجهاز
     */
    fun getDefaultLanguage(): Language {
        val deviceLang = Locale.getDefault().language
        return if (deviceLang == "ar") Language.ARABIC else Language.ENGLISH
    }

    /**
     * حفظ اللغة المختارة
     */
    fun saveLanguage(context: Context, language: Language) {
        getPrefs(context).edit().putString(KEY_LANGUAGE, language.code).apply()
    }

    /**
     * استرجاع اللغة المحفوظة أو الافتراضية
     */
    fun getSavedLanguage(context: Context): Language {
        val saved = getPrefs(context).getString(KEY_LANGUAGE, null)
        return when (saved) {
            "ar" -> Language.ARABIC
            "en" -> Language.ENGLISH
            else -> getDefaultLanguage()
        }
    }

    /**
     * جلب نص مترجم
     */
    fun get(key: String, language: Language): String {
        val strings = if (language == Language.ARABIC) AR else EN
        return strings[key] ?: key
    }

    /**
     * تبديل اللغة بين العربية والانجليزية
     */
    fun toggle(current: Language): Language {
        return if (current == Language.ARABIC) Language.ENGLISH else Language.ARABIC
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
