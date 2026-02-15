package com.junkfood.seal.simple

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.junkfood.seal.database.objects.DownloadedVideoInfo
import com.junkfood.seal.util.DatabaseUtil
import kotlinx.coroutines.launch
import java.io.File

/**
 * شاشة سجل التحميلات
 * Download History Screen
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
                    IconButton(onClick = onNavigateBack) {
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
                        onOpenFolder = {
                            try {
                                val file = File(download.videoPath)
                                val uri = Uri.parse(file.parentFile?.absolutePath ?: download.videoPath)
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, "resource/folder")
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                // إذا فشل فتح المجلد، لا نفعل شيئاً
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
    onOpenFolder: () -> Unit,
    onDelete: () -> Unit
) {
    fun s(key: String) = SimpleStrings.get(key, lang)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // معلومات التحميل
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = info.videoTitle,
                    fontSize = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = info.videoPath,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // أزرار
            Row {
                IconButton(onClick = onOpenFolder) {
                    Icon(
                        Icons.Default.FolderOpen,
                        contentDescription = s("open_folder"),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onDelete) {
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
