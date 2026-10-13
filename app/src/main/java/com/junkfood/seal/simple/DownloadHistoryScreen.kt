package com.junkfood.seal.simple

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.junkfood.seal.database.objects.DownloadedVideoInfo
import com.junkfood.seal.util.DatabaseUtil
import com.junkfood.seal.util.FileUtil
import kotlinx.coroutines.launch
import java.io.File

/**
 * شاشة سجل التحميلات لتطبيق جياد (المرحلة 4)
 * تعتمد على FileUtil القياسي لفتح ومشاركة الملفات مباشرة في المشغل الافتراضي
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadHistoryScreen(
    lang: SimpleStrings.Language,
    onNavigateBack: () -> Unit
) {
    fun s(key: String) = SimpleStrings.get(key, lang)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // تشغيل ترحيل السجل لمرة واحدة في الخلفية (المرحلة 4.2)
    LaunchedEffect(Unit) {
        JiyadPreset.migrateHistoryIfNeeded()
    }

    val downloads by DatabaseUtil.getDownloadHistoryFlow()
        .collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        s("download_history"),
                        fontSize = 22.sp
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = s("back")
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (downloads.isEmpty()) {
            // لا يوجد تحميلات
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = s("no_downloads"),
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(
                    items = downloads.reversed(),
                    key = { it.id }
                ) { download ->
                    DownloadHistoryItem(
                        info = download,
                        lang = lang,
                        onOpenFile = {
                            FileUtil.openFile(path = download.videoPath) {
                                Toast.makeText(context, s("file_unavailable"), Toast.LENGTH_SHORT).show()
                            }
                        },
                        onShareFile = {
                            val shareIntent = FileUtil.createIntentForSharingFile(download.videoPath)
                            if (shareIntent != null) {
                                try {
                                    context.startActivity(Intent.createChooser(shareIntent, null).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    })
                                } catch (_: Exception) {
                                    Toast.makeText(context, s("download_failed"), Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, s("file_unavailable"), Toast.LENGTH_SHORT).show()
                            }
                        },
                        onDelete = {
                            scope.launch {
                                DatabaseUtil.deleteInfoById(download.id)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadHistoryItem(
    info: DownloadedVideoInfo,
    lang: SimpleStrings.Language,
    onOpenFile: () -> Unit,
    onShareFile: () -> Unit,
    onDelete: () -> Unit
) {
    fun s(key: String) = SimpleStrings.get(key, lang)
    val file = remember(info.videoPath) { File(info.videoPath) }
    val isFileAvailable = remember(info.videoPath) { file.exists() && file.isFile }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onOpenFile),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // أيقونة التشغيل السريع
            IconButton(
                onClick = onOpenFile,
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = s("open_file"),
                    tint = if (isFileAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // معلومات التحميل
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = info.videoTitle,
                    fontSize = 16.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (isFileAvailable) {
                    Text(
                        text = info.videoPath,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "⚠️ ${s("file_unavailable")}",
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { contentDescription = s("file_unavailable") }
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // أزرار المشاركة والحذف
            Row {
                IconButton(
                    onClick = onShareFile,
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = s("share_file"),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = s("delete"),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
