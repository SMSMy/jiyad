package com.junkfood.seal.simple

/**
 * إعدادات جياد الثابتة - معدلة للاستقرار
 */
object SimpleConfig {
    // فيديو: نطلب MP4 أولاً، إذا لم يوجد نأخذ الأفضل وندمجه لاحقاً
    // هذا الصيغة تضمن أفضل توافقية مع الهواتف القديمة
    const val VIDEO_FORMAT = "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best"

    // صوت: أفضل جودة متاحة
    const val AUDIO_FORMAT = "bestaudio/best"

    // صيغة دمج الفيديو (يجبر الناتج النهائي ليكون MP4 دائماً)
    const val VIDEO_MERGE_FORMAT = "mp4"

    // مجلد التحميل (يفضل الإنجليزية لضمان عدم حدوث مشاكل في بعض الهواتف القديمة)
    const val DOWNLOAD_DIR_NAME = "Jiyad" 

    // تحديث yt-dlp عند فتح التطبيق (ميزة ممتازة)
    const val AUTO_UPDATE_YTDLP = true

    // ⚠️ هام جداً: تعطيل aria2c لحل مشكلة 403 Forbidden
    const val USE_ARIA2C = false

    // استخراج الصوت كـ MP3 (الأكثر توافقاً مع السيارات والهواتف القديمة)
    const val AUDIO_EXTRACT_FORMAT = "mp3"
}