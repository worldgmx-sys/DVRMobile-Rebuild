package com.longhorn.dvr.worldgm

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun AdvancedSettingsSection(status: DvrStatus, vm: DvrViewModel, modifier: Modifier = Modifier) {
    val advanced by vm.advanced.collectAsStateWithLifecycle()
    ElevatedCard(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row {
                Column(Modifier.weight(1f)) {
                    Text("SigmaStar S38 固件设置", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "仅使用原厂 Config.cgi。未读回的设置不代表已同步；实验性接口需要实机验证。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                IconButton(onClick = { vm.refreshAdvanced() }, enabled = status.isConnected && !advanced.loading) {
                    Icon(Icons.Default.Refresh, "刷新设置")
                }
            }
            if (advanced.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("可确认的实时设置：${advanced.sigmaValues.size} 项", style = MaterialTheme.typography.bodySmall)
            SigmaStarAdvancedControls(enabled = status.isConnected, vm = vm)
            advanced.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            advanced.lastRawResponse?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
