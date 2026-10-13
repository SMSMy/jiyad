package com.junkfood.seal.simple

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.junkfood.seal.R
import com.junkfood.seal.ui.page.AppEntry
import com.junkfood.seal.ui.page.downloadv2.configure.DownloadDialogViewModel

/**
 * حاوية التبديل ونقطة العودة بين الوضع المبسط والوضع المتقدم لجياد (المرحلة 3).
 * نُقلت إلى حزمة simple/ لحصر التعديل على MainActivity.kt ضمن حدود القائمة البيضاء (≤ 18 سطراً).
 */
@Composable
fun JiyadRoot(
    dialogViewModel: DownloadDialogViewModel,
    sharedUrl: String,
) {
    var uiMode by remember { mutableStateOf(JiyadPreset.getUiMode()) }

    if (uiMode == JiyadPreset.UI_MODE_ADVANCED) {
        Box(modifier = Modifier.fillMaxSize()) {
            AppEntry(dialogViewModel = dialogViewModel)
            ExtendedFloatingActionButton(
                text = { Text(stringResource(R.string.jiyad_switch_to_simple), fontSize = 16.sp) },
                icon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) },
                onClick = {
                    JiyadPreset.setUiMode(JiyadPreset.UI_MODE_SIMPLE)
                    uiMode = JiyadPreset.UI_MODE_SIMPLE
                },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp),
            )
        }
    } else {
        SimpleHomeScreen(
            initialUrl = sharedUrl,
            onSwitchToAdvanced = {
                JiyadPreset.setUiMode(JiyadPreset.UI_MODE_ADVANCED)
                uiMode = JiyadPreset.UI_MODE_ADVANCED
            }
        )
    }
}
