package com.junkfood.seal.simple

/**
 * إعدادات جياد الثابتة - لا حاجة لتغييرها من واجهة المستخدم
 * Hardcoded settings for Jiyad - no UI needed
 */
object SimpleConfig {
    // فيديو: جودة 720p بصيغة MP4
    const val VIDEO_FORMAT = "bestvideo[height<=720][ext=mp4]+bestaudio[ext=m4a]/bestvideo[height<=720]+bestaudio/best[height<=720]/best"

    // صوت: أفضل جودة
    const val AUDIO_FORMAT = "bestaudio/best"

    // صيغة دمج الفيديو (MP4 هي الأولوية)
    const val VIDEO_MERGE_FORMAT = "mp4"

    // مجلد التحميل
    const val DOWNLOAD_DIR_NAME = "جياد"

    // تحديث yt-dlp عند فتح التطبيق
    const val AUTO_UPDATE_YTDLP = true

    // استخدام aria2c للتحميل السريع
    const val USE_ARIA2C = true

    // استخراج الصوت كـ MP3
    const val AUDIO_EXTRACT_FORMAT = "mp3"
}
