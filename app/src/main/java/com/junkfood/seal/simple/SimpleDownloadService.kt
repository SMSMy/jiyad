package com.junkfood.seal.simple

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.junkfood.seal.MainActivity
import com.junkfood.seal.util.NotificationUtil
import kotlinx.coroutines.*

/**
 * خدمة التحميل في المقدمة (Foreground Service)
 * تضمن بقاء التحميل نشطاً حتى عند إغلاق الشاشة أو الخروج من التطبيق
 */
class SimpleDownloadService : Service() {

    private var serviceScope: CoroutineScope? = null
    private var activeDownloads = 0

    companion object {
        private const val TAG = "SimpleDownloadService"
        private const val SERVICE_NOTIFICATION_ID = 300

        const val ACTION_DOWNLOAD_VIDEO = "download_video"
        const val ACTION_DOWNLOAD_AUDIO = "download_audio"
        const val EXTRA_URL = "url"

        // حالة التحميل المُراقَبة من الـ UI
        var isDownloading = false
            private set
        var currentProgress = 0f
            private set
        var currentStatusText = ""
            private set
        var lastResult: Result<String>? = null
            private set

        // Callbacks للـ UI
        var onProgressUpdate: ((Float, String) -> Unit)? = null
        var onDownloadComplete: ((Result<String>) -> Unit)? = null

        fun startDownload(context: Context, url: String, isAudio: Boolean) {
            val intent = Intent(context, SimpleDownloadService::class.java).apply {
                action = if (isAudio) ACTION_DOWNLOAD_AUDIO else ACTION_DOWNLOAD_VIDEO
                putExtra(EXTRA_URL, url)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        Log.d(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // بدء Foreground فوراً لمنع القتل
        val pendingIntent = Intent(this, MainActivity::class.java).let { notifIntent ->
            PendingIntent.getActivity(this, 0, notifIntent, PendingIntent.FLAG_IMMUTABLE)
        }
        val notification = NotificationUtil.makeServiceNotification(
            pendingIntent,
            "جاري التحميل..."
        )
        startForeground(SERVICE_NOTIFICATION_ID, notification)

        val url = intent?.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
        val isAudio = intent.action == ACTION_DOWNLOAD_AUDIO

        activeDownloads++
        isDownloading = true
        currentProgress = 0f
        currentStatusText = ""
        lastResult = null

        serviceScope?.launch {
            val downloadFn = if (isAudio) JiyadDownloader::downloadAudio else JiyadDownloader::downloadVideo

            downloadFn(
                this@SimpleDownloadService,
                url,
                { progress, text ->
                    currentProgress = progress / 100f
                    currentStatusText = text
                    onProgressUpdate?.invoke(currentProgress, text)
                },
                { result ->
                    lastResult = result
                    activeDownloads--
                    onDownloadComplete?.invoke(result)

                    if (activeDownloads <= 0) {
                        isDownloading = false
                        activeDownloads = 0
                        stopSelfGracefully()
                    }
                }
            )
        }

        return START_NOT_STICKY
    }

    private fun stopSelfGracefully() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope?.cancel()
        serviceScope = null
        isDownloading = false
        activeDownloads = 0
        Log.d(TAG, "Service destroyed")
    }
}
