package com.afu.ledger.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.afu.ledger.data.backup.DataStats
import com.afu.ledger.ui.component.SettingDivider
import com.afu.ledger.ui.component.SettingGroup
import com.afu.ledger.ui.component.SettingRow

/**
 * 「数据管理」分组：让用户看得见自己的数据在哪、有多少，也能自己把它带走。
 *
 * 为什么单独成组：自动云备份不可控（依赖 ROM、账号、是否开启），
 * 真正可靠的「数据还在」是用户手里的一份文件。
 */
@Composable
fun DataManageSection(
    stats: DataStats,
    busy: Boolean,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit,
    onClearAllClick: () -> Unit,
) {
    var showClearConfirm by remember { mutableStateOf(false) }
    var showImportConfirm by remember { mutableStateOf(false) }

    SettingGroup {
        SettingRow(
            icon = Icons.Rounded.Folder,
            title = "数据管理",
            subtitle = "账本只存在本机；换机或重装前先导出一份",
            detail = buildString {
                append("${stats.transactions} 笔")
                if (stats.databaseBytes > 0) {
                    append(" · ")
                    append(formatBytes(stats.databaseBytes))
                }
            },
        )
        SettingDivider()
        SettingRow(
            icon = Icons.Rounded.Backup,
            title = "导出备份",
            subtitle = if (busy) "正在处理，请稍候…" else "生成 JSON 备份，保存到你选择的位置",
            onClick = { if (!busy) onExportClick() },
        )
        SettingDivider()
        SettingRow(
            icon = Icons.Rounded.Restore,
            title = "导入备份",
            subtitle = if (busy) "正在处理，请稍候…" else "用备份文件还原账本（会替换现有数据）",
            onClick = { if (!busy) showImportConfirm = true },
        )
        SettingDivider()
        SettingRow(
            icon = Icons.Rounded.DeleteForever,
            title = "清空全部数据",
            subtitle = "删除全部账单、账户与定时规则，不可撤销",
            iconTint = MaterialTheme.colorScheme.error,
            onClick = { if (!busy) showClearConfirm = true },
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清空全部数据？") },
            text = {
                Text(
                    "将删除全部 ${stats.transactions} 笔账单、${stats.accounts} 个账户、分类与定时规则，" +
                        "且无法撤销。建议先「导出备份」再清空。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirm = false
                        onClearAllClick()
                    },
                ) {
                    Text("确认清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("取消") }
            },
        )
    }

    if (showImportConfirm) {
        AlertDialog(
            onDismissRequest = { showImportConfirm = false },
            title = { Text("导入备份？") },
            text = {
                Text(
                    "导入会用备份文件完整替换当前账本（现有 ${stats.transactions} 笔账单将被清空）。" +
                        "如不确定，请先导出一份当前数据。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showImportConfirm = false
                        onImportClick()
                    },
                ) {
                    Text("选择备份文件")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportConfirm = false }) { Text("取消") }
            },
        )
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format("%.1f MB", bytes / 1024.0 / 1024.0)
    bytes >= 1024L -> String.format("%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}
