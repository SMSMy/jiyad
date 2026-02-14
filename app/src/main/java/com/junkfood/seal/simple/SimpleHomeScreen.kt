package com.junkfood.seal.simple

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
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
import coil.request.ImageRequest
import kotlinx.coroutines.launch

/**
 * واجهة جياد البسيطة - مصممة لكبار السن
 * Simple Jiyad UI - designed for elderly users
 */
@Composable
fun SimpleHomeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // اللغة الحالية
    var lang by remember { mutableStateOf(SimpleStrings.getSavedLanguage(context)) }
    fun s(key: String) = SimpleStrings.get(key, lang)

    var url by remember { mutableStateOf("") }
    var isDownloading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var statusMessage by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf("") }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<SimpleAppUpdater.UpdateInfo?>(null) }
    var videoInfo by remember { mutableStateOf<JiyadDownloader.VideoInfo?>(null) }
    var isFetchingInfo by remember { mutableStateOf(false) }
    var hasStoragePermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) true
            else ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    // طلب إذن التخزين
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasStoragePermission = granted
        if (!granted) {
            Toast.makeText(context, s("storage_permission"), Toast.LENGTH_LONG).show()
        }
    }

    // تحديث yt-dlp تلقائياً عند فتح التطبيق
    LaunchedEffect(Unit) {
        if (SimpleConfig.AUTO_UPDATE_YTDLP) {
            JiyadDownloader.updateYtDlp(context)
        }

        // فحص تحديث التطبيق من GitHub
        try {
            val info = SimpleAppUpdater.checkForUpdate()
            if (info.hasUpdate) {
                updateInfo = info
                showUpdateDialog = true
            }
        } catch (_: Exception) {}

        // طلب إذن التخزين إذا لزم الأمر
        if (!hasStoragePermission && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        // لصق تلقائي من الحافظة
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString() ?: ""
                if (text.contains("http://") || text.contains("https://")) {
                    url = text.trim()
                }
            }
        } catch (_: Exception) {}
    }

    // جلب معلومات الفيديو عند تغيير الرابط
    LaunchedEffect(url) {
        videoInfo = null
        if (url.isNotBlank() && (url.contains("http://") || url.contains("https://"))) {
            isFetchingInfo = true
            videoInfo = JiyadDownloader.fetchVideoInfo(url.trim())
            isFetchingInfo = false
        }
    }

    fun startDownload(isAudio: Boolean) {
        if (url.isBlank()) {
            statusMessage = s("error_enter_url")
            return
        }
        if (!hasStoragePermission && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        if (isDownloading) return

        isDownloading = true
        progress = 0f
        statusMessage = s("downloading")
        statusText = ""

        val downloadFn = if (isAudio) JiyadDownloader::downloadAudio else JiyadDownloader::downloadVideo

        scope.launch {
            downloadFn(
                context,
                url.trim(),
                { p, text ->
                    progress = p / 100f
                    statusText = text
                },
                { result ->
                    isDownloading = false
                    result.onSuccess {
                        statusMessage = s("download_success")
                        progress = 1f
                    }.onFailure { e ->
                        statusMessage = "${s("download_failed")} ${e.message?.take(100) ?: s("unknown_error")}"
                        progress = 0f
                    }
                }
            )
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // زر تبديل اللغة (في الأعلى)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                FilledTonalButton(
                    onClick = {
                        lang = SimpleStrings.toggle(lang)
                        SimpleStrings.saveLanguage(context, lang)
                    },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = if (lang == SimpleStrings.Language.ARABIC) "EN" else "عربي",
                        fontSize = 16.sp
                    )
                }
            }

            // عنوان التطبيق
            Text(
                text = s("app_title"),
                fontSize = 36.sp,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )

            Text(
                text = s("subtitle"),
                fontSize = 20.sp,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // حقل إدخال الرابط
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = {
                    Text(
                        s("url_label"),
                        fontSize = 18.sp
                    )
                },
                placeholder = {
                    Text(
                        "https://...",
                        fontSize = 16.sp
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                textStyle = LocalTextStyle.current.copy(fontSize = 20.sp),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                trailingIcon = {
                    IconButton(
                        onClick = {
                            try {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = clipboard.primaryClip
                                if (clip != null && clip.itemCount > 0) {
                                    url = clip.getItemAt(0).text?.toString()?.trim() ?: ""
                                }
                            } catch (_: Exception) {}
                        }
                    ) {
                        Icon(
                            Icons.Default.ContentPaste,
                            contentDescription = s("paste"),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            )

            // معاينة الفيديو (صورة مصغرة + عنوان)
            if (isFetchingInfo) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (videoInfo != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column {
                        if (videoInfo!!.thumbnail.isNotBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(videoInfo!!.thumbnail)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = videoInfo!!.title,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Text(
                            text = videoInfo!!.title,
                            fontSize = 18.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (videoInfo!!.duration > 0) {
                            val mins = videoInfo!!.duration / 60
                            val secs = videoInfo!!.duration % 60
                            Text(
                                text = "⏱ ${mins}:${"%02d".format(secs)}",
                                fontSize = 16.sp,
                                modifier = Modifier.padding(start = 12.dp, bottom = 12.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // زر تحميل فيديو
            Button(
                onClick = { startDownload(isAudio = false) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp),
                shape = RoundedCornerShape(20.dp),
                enabled = !isDownloading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = s("download_video"),
                    fontSize = 24.sp,
                    style = MaterialTheme.typography.titleLarge
                )
            }

            // زر تحميل صوت
            Button(
                onClick = { startDownload(isAudio = true) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp),
                shape = RoundedCornerShape(20.dp),
                enabled = !isDownloading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary
                )
            ) {
                Text(
                    text = s("download_audio"),
                    fontSize = 24.sp,
                    style = MaterialTheme.typography.titleLarge
                )
            }

            // شريط التقدم
            if (isDownloading) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (statusText.isNotEmpty()) {
                        Text(
                            text = statusText,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // رسالة الحالة
            if (statusMessage.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (statusMessage.startsWith("✅"))
                            MaterialTheme.colorScheme.primaryContainer
                        else if (statusMessage.startsWith("❌"))
                            MaterialTheme.colorScheme.errorContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = statusMessage,
                        fontSize = 22.sp,
                        textAlign = TextAlign.Center,
                        color = if (statusMessage.startsWith("✅"))
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else if (statusMessage.startsWith("❌"))
                            MaterialTheme.colorScheme.onErrorContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(20.dp)
                            .fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // نافذة التحديث المنبثقة
    if (showUpdateDialog && updateInfo != null) {
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = {
                Text(
                    text = s("update_available"),
                    fontSize = 24.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "${s("new_version")} ${updateInfo!!.latestVersion}",
                        fontSize = 20.sp
                    )
                    if (updateInfo!!.releaseNotes.isNotBlank()) {
                        Text(
                            text = updateInfo!!.releaseNotes.take(300),
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showUpdateDialog = false
                        SimpleAppUpdater.openDownloadLink(context, updateInfo!!.downloadUrl)
                    },
                    modifier = Modifier.height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(s("update_now"), fontSize = 20.sp)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showUpdateDialog = false },
                    modifier = Modifier.height(56.dp)
                ) {
                    Text(s("later"), fontSize = 18.sp)
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }
}
