package com.junkfood.seal.simple

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * الشاشة الرئيسية المبسطة لتطبيق جياد
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleHomeScreen(initialUrl: String = "") {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // الحالة
    var lang by remember { mutableStateOf(SimpleStrings.getSavedLanguage(context)) }
    fun s(key: String) = SimpleStrings.get(key, lang)

    var url by remember { mutableStateOf(initialUrl) }
    var isDownloading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var statusMessage by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf("") }

    // التنقل بين الشاشات
    var currentScreen by remember { mutableStateOf("home") }

    // حالة القوائم
    var showMoreMenu by remember { mutableStateOf(false) }
    var showYtDlpDialog by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }

    // معلومات الفيديو
    var videoTitle by remember { mutableStateOf("") }
    var videoThumbnail by remember { mutableStateOf("") }
    var isFetchingInfo by remember { mutableStateOf(false) }

    // تحديث التطبيق
    var updateInfo by remember { mutableStateOf<SimpleAppUpdater.UpdateInfo?>(null) }

    // مهمة جلب معلومات الفيديو (للإلغاء عند تغيير الرابط)
    var fetchJob by remember { mutableStateOf<Job?>(null) }

    // طلب إذن الإشعارات
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* يكمل بغض النظر عن النتيجة */ }

    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // جلب معلومات الفيديو تلقائياً عند تغيير الرابط
    fun fetchThumbnail(newUrl: String) {
        fetchJob?.cancel()
        if (newUrl.isBlank() || !newUrl.contains("http")) {
            videoTitle = ""
            videoThumbnail = ""
            isFetchingInfo = false
            return
        }
        fetchJob = scope.launch {
            delay(800) // انتظار قبل الجلب لتجنب طلبات كثيرة
            isFetchingInfo = true
            try {
                val info = JiyadDownloader.fetchVideoInfo(newUrl.trim())
                info?.let {
                    videoTitle = it.title
                    videoThumbnail = it.thumbnail
                }
            } catch (_: Exception) {}
            isFetchingInfo = false
        }
    }

    // تحديث initialUrl عند تغييره (من share intent جديد)
    LaunchedEffect(initialUrl) {
        if (initialUrl.isNotBlank()) {
            url = initialUrl
            fetchThumbnail(initialUrl)
        }
    }

    // التحقق من التحديثات + تحديث yt-dlp تلقائي
    LaunchedEffect(Unit) {
        // تحديث yt-dlp تلقائياً أولاً
        if (SimpleConfig.AUTO_UPDATE_YTDLP) {
            try {
                JiyadDownloader.updateYtDlp(context)
            } catch (_: Exception) {}
        }

        // تحديث التطبيق
        try {
            val info = SimpleAppUpdater.checkForUpdate()
            if (info.hasUpdate) {
                updateInfo = info
                showUpdateDialog = true
            }
        } catch (_: Exception) {}
    }

    // ربط Callbacks مع الـ Service
    DisposableEffect(Unit) {
        SimpleDownloadService.onProgressUpdate = { p, text ->
            progress = p
            statusText = text
        }
        SimpleDownloadService.onDownloadComplete = { result ->
            isDownloading = false
            result.onSuccess {
                statusMessage = "✅ ${s("download_success")}"
                progress = 1f
            }.onFailure { e ->
                statusMessage = "❌ ${s("download_failed")} ${e.message?.take(100) ?: s("unknown_error")}"
                progress = 0f
            }
        }
        onDispose {
            SimpleDownloadService.onProgressUpdate = null
            SimpleDownloadService.onDownloadComplete = null
        }
    }

    // بدء التحميل عبر Foreground Service
    fun startDownload(isAudio: Boolean) {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isBlank()) {
            statusMessage = s("error_enter_url")
            return
        }

        requestNotificationPermission()

        isDownloading = true
        progress = 0f
        statusMessage = s("downloading")
        statusText = ""

        // بدء التحميل عبر Service
        SimpleDownloadService.startDownload(context, trimmedUrl, isAudio)
    }

    // التنقل بين الشاشات
    when (currentScreen) {
        "history" -> {
            DownloadHistoryScreen(
                lang = lang,
                onNavigateBack = { currentScreen = "home" }
            )
            return
        }
    }

    // الشاشة الرئيسية
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                actions = {
                    // زر التحميلات
                    IconButton(onClick = { currentScreen = "history" }) {
                        Icon(Icons.Default.History, contentDescription = s("download_history"))
                    }
                    // القائمة المنسدلة
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = s("more_options"))
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            // تبديل اللغة
                            DropdownMenuItem(
                                text = { Text(SimpleStrings.toggle(lang).label, fontSize = 16.sp) },
                                onClick = {
                                    lang = SimpleStrings.toggle(lang)
                                    SimpleStrings.saveLanguage(context, lang)
                                    showMoreMenu = false
                                }
                            )
                            // تحديث المحرك
                            DropdownMenuItem(
                                text = { Text(s("update_engine"), fontSize = 16.sp) },
                                onClick = {
                                    showMoreMenu = false
                                    showYtDlpDialog = true
                                }
                            )
                            // الوضع المتقدم - يفتح واجهة Seal الأصلية
                            DropdownMenuItem(
                                text = { Text(s("advanced_mode"), fontSize = 16.sp) },
                                onClick = {
                                    showMoreMenu = false
                                    val trimmedUrl = url.trim()
                                    if (trimmedUrl.isNotBlank()) {
                                        val advancedIntent = Intent(context, com.junkfood.seal.QuickDownloadActivity::class.java).apply {
                                            action = Intent.ACTION_SEND
                                            putExtra(Intent.EXTRA_TEXT, trimmedUrl)
                                            type = "text/plain"
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        try {
                                            context.startActivity(advancedIntent)
                                        } catch (_: Exception) {}
                                    }
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // العنوان
            Text(
                text = s("app_title"),
                fontSize = 36.sp,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = s("subtitle"),
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            // مؤشر جلب المعلومات
            AnimatedVisibility(visible = isFetchingInfo) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
            }

            // صورة مصغرة
            AnimatedVisibility(visible = videoThumbnail.isNotBlank()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    AsyncImage(
                        model = videoThumbnail,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentScale = ContentScale.Crop
                    )
                    if (videoTitle.isNotBlank()) {
                        Text(
                            text = videoTitle,
                            modifier = Modifier.padding(12.dp),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // حقل الرابط
            OutlinedTextField(
                value = url,
                onValueChange = { newUrl ->
                    url = newUrl
                    fetchThumbnail(newUrl)
                },
                label = { Text(s("url_label"), fontSize = 16.sp) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
                trailingIcon = {
                    Row {
                        // زر مسح الرابط (X)
                        if (url.isNotBlank()) {
                            IconButton(onClick = {
                                url = ""
                                videoTitle = ""
                                videoThumbnail = ""
                                statusMessage = ""
                                fetchJob?.cancel()
                            }) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = s("clear"),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        // زر اللصق
                        TextButton(onClick = {
                            val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                            clipboard?.primaryClip?.getItemAt(0)?.text?.toString()?.let { clipText ->
                                url = clipText
                                fetchThumbnail(clipText)
                            }
                        }) {
                            Text(s("paste"), fontSize = 14.sp)
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // أزرار التحميل
            Button(
                onClick = { startDownload(false) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !isDownloading,
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(s("download_video"), fontSize = 20.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = { startDownload(true) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !isDownloading,
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(s("download_audio"), fontSize = 20.sp)
            }

            Spacer(modifier = Modifier.height(20.dp))

            // شريط التقدم
            AnimatedVisibility(visible = isDownloading) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val animatedProgress by animateFloatAsState(
                        targetValue = progress,
                        animationSpec = tween(300, easing = FastOutSlowInEasing),
                        label = "progress"
                    )

                    // إذا كان في مرحلة التحويل (99%) اعرض شريط غير محدد
                    if (statusText.contains("تحويل") || statusText.contains("MP3")) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = { animatedProgress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (statusText.contains("تحويل") || statusText.contains("MP3"))
                            "🔄 ${s("converting")}"
                        else "${(progress * 100).toInt()}%",
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (statusText.isNotBlank()) {
                        Text(
                            text = statusText,
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            // رسالة الحالة
            if (statusMessage.isNotBlank() && !isDownloading) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = statusMessage,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    color = if (statusMessage.startsWith("✅"))
                        MaterialTheme.colorScheme.primary
                    else if (statusMessage.startsWith("❌"))
                        MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // نافذة تحديث yt-dlp
    if (showYtDlpDialog) {
        YtDlpUpdateDialog(
            lang = lang,
            onDismiss = { showYtDlpDialog = false }
        )
    }

    // نافذة تحديث التطبيق (تحميل + تثبيت داخل التطبيق)
    if (showUpdateDialog && updateInfo != null) {
        InAppUpdateDialog(
            lang = lang,
            updateInfo = updateInfo!!,
            onDismiss = { showUpdateDialog = false }
        )
    }
}

/**
 * نافذة تحديث yt-dlp
 */
@Composable
private fun YtDlpUpdateDialog(
    lang: SimpleStrings.Language,
    onDismiss: () -> Unit
) {
    fun s(key: String) = SimpleStrings.get(key, lang)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isUpdating by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isUpdating) onDismiss() },
        title = { Text(s("ytdlp_update"), fontSize = 20.sp) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (isUpdating) {
                    CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                    Text(s("ytdlp_updating"), fontSize = 16.sp)
                } else if (resultMessage.isNotBlank()) {
                    Text(resultMessage, fontSize = 16.sp)
                } else {
                    Text(s("ytdlp_update"), fontSize = 16.sp)
                }
            }
        },
        confirmButton = {
            if (!isUpdating && resultMessage.isBlank()) {
                TextButton(onClick = {
                    isUpdating = true
                    scope.launch {
                        val updated = JiyadDownloader.updateYtDlp(context)
                        isUpdating = false
                        resultMessage = if (updated) s("ytdlp_updated") else s("ytdlp_already_latest")
                    }
                }) {
                    Text(s("update_now"), fontSize = 16.sp)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(s("close"), fontSize = 16.sp)
            }
        }
    )
}

/**
 * نافذة تحديث التطبيق داخل التطبيق (تحميل + تثبيت)
 */
@Composable
private fun InAppUpdateDialog(
    lang: SimpleStrings.Language,
    updateInfo: SimpleAppUpdater.UpdateInfo,
    onDismiss: () -> Unit
) {
    fun s(key: String) = SimpleStrings.get(key, lang)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var downloadProgress by remember { mutableIntStateOf(0) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var downloadedApk by remember { mutableStateOf<java.io.File?>(null) }
    var errorMessage by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isDownloadingUpdate) onDismiss() },
        title = { Text(s("update_available"), fontSize = 20.sp) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (errorMessage.isNotBlank()) {
                    // حالة الخطأ
                    Text("❌ $errorMessage", fontSize = 16.sp)
                } else if (downloadedApk != null) {
                    // التحميل اكتمل - جاهز للتثبيت
                    Text("✅ ${s("update_ready")}", fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(s("update_tap_install"), fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (isDownloadingUpdate) {
                    // جاري تحميل التحديث
                    Text("${s("update_downloading")} $downloadProgress%", fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { downloadProgress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                } else {
                    // عرض معلومات التحديث
                    Text(
                        "${s("new_version")} ${updateInfo.latestVersion}",
                        fontSize = 18.sp
                    )
                    if (updateInfo.releaseNotes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            updateInfo.releaseNotes.take(200),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        },
        confirmButton = {
            when {
                downloadedApk != null -> {
                    // زر التثبيت
                    TextButton(onClick = {
                        if (SimpleAppUpdater.canInstallApks(context)) {
                            SimpleAppUpdater.installApk(context, downloadedApk!!)
                        } else {
                            SimpleAppUpdater.requestInstallPermission(context)
                        }
                    }) {
                        Text(s("install_update"), fontSize = 16.sp)
                    }
                }
                errorMessage.isNotBlank() -> {
                    // إعادة المحاولة
                    TextButton(onClick = {
                        errorMessage = ""
                    }) {
                        Text(s("retry"), fontSize = 16.sp)
                    }
                }
                !isDownloadingUpdate -> {
                    // زر بدء التحديث
                    TextButton(onClick = {
                        isDownloadingUpdate = true
                        downloadProgress = 0
                        scope.launch {
                            val apk = SimpleAppUpdater.downloadApk(
                                context = context,
                                downloadUrl = updateInfo.downloadUrl,
                                onProgress = { downloadProgress = it }
                            )
                            isDownloadingUpdate = false
                            if (apk != null) {
                                downloadedApk = apk
                            } else {
                                errorMessage = s("update_download_failed")
                            }
                        }
                    }) {
                        Text(s("update_now"), fontSize = 16.sp)
                    }
                }
            }
        },
        dismissButton = {
            if (!isDownloadingUpdate) {
                TextButton(onClick = onDismiss) {
                    Text(s("later"), fontSize = 16.sp)
                }
            }
        }
    )
}
