package com.caj.interviewcards.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.caj.interviewcards.data.Note
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 随手记：记录日常问题/想法，支持一键导出。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteScreen(nvm: NoteViewModel = viewModel()) {
    val notes by nvm.notes.collectAsState()
    val context = LocalContext.current

    var editing by remember { mutableStateOf<Note?>(null) }   // 非 null = 编辑中
    var creating by remember { mutableStateOf(false) }        // 新建
    var exporting by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf("") }

    val df = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    // 保存文件（SAF）
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(nvm.exportMarkdown().toByteArray(Charsets.UTF_8))
                }
                toast = "已导出到文件"
            }.onFailure { toast = "导出失败：${it.message}" }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("随手记", fontWeight = FontWeight.Bold) },
                actions = {
                    if (notes.isNotEmpty()) {
                        IconButton(onClick = { confirmClear = true }) {
                            Icon(Icons.Default.DeleteSweep, "清空")
                        }
                        IconButton(onClick = { exporting = true }) {
                            Icon(Icons.Default.IosShare, "导出")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("记一条") },
            )
        },
    ) { padding ->
        if (notes.isEmpty()) {
            Box(
                Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.EditNote, null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("还没有记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "复习时遇到疑问，点右下角「记一条」",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(bottom = 88.dp),
            ) {
                item {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("共 ${notes.size} 条", style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { exporting = true }) {
                                Icon(Icons.Default.IosShare, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("导出")
                            }
                        }
                    }
                }
                items(notes, key = { it.id }) { n ->
                    NoteRow(n, df.format(Date(n.createdAt))) {
                        editing = n
                    }
                }
            }
        }
    }

    // ─── 新建 / 编辑对话框 ───
    if (creating || editing != null) {
        NoteEditorDialog(
            initial = editing,
            onDismiss = { creating = false; editing = null },
            onSave = { q, d, t ->
                if (editing != null) {
                    nvm.update(editing!!.copy(question = q, detail = d, tag = t))
                } else {
                    nvm.add(q, d, t)
                }
                creating = false; editing = null
            },
            onDelete = editing?.let { e -> { nvm.delete(e.id); editing = null } },
        )
    }

    // ─── 清空确认 ───
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空全部笔记？") },
            text = { Text("将删除全部 ${notes.size} 条记录，不可恢复。建议先导出。") },
            confirmButton = {
                TextButton(onClick = { nvm.clearAll(); confirmClear = false }) {
                    Text("清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } },
        )
    }

    // ─── 导出选项 ───
    if (exporting) {
        val md = nvm.exportMarkdown()
        AlertDialog(
            onDismissRequest = { exporting = false },
            title = { Text("导出笔记（${notes.size} 条）") },
            text = {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        "导出为 Markdown，格式对齐面试题库，可直接交给 AI 加题。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            md.take(600) + if (md.length > 600) "\n…（完整内容见导出文件）" else "",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("笔记", md))
                    toast = "已复制到剪贴板"
                    exporting = false
                }) { Text("复制到剪贴板") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        // 分享
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "面试随手记")
                            putExtra(Intent.EXTRA_TEXT, md)
                        }
                        context.startActivity(Intent.createChooser(intent, "分享笔记"))
                        exporting = false
                    }) { Text("分享") }
                    TextButton(onClick = {
                        saveLauncher.launch("面试随手记_" + SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date()) + ".md")
                        exporting = false
                    }) { Text("存为文件") }
                }
            },
        )
    }

    // ─── 轻提示 ───
    if (toast.isNotEmpty()) {
        LaunchedEffect(toast) {
            kotlinx.coroutines.delay(1800)
            toast = ""
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Snackbar(modifier = Modifier.padding(16.dp)) { Text(toast) }
        }
    }
}

@Composable
private fun NoteRow(note: Note, timeText: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(timeText, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (note.tag.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = MaterialTheme.shapes.extraSmall,
                ) {
                    Text(
                        note.tag,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(note.question, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        if (note.detail.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                note.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
    HorizontalDivider(thickness = 0.5.dp)
}

@Composable
private fun NoteEditorDialog(
    initial: Note?,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var q by remember { mutableStateOf(initial?.question ?: "") }
    var d by remember { mutableStateOf(initial?.detail ?: "") }
    var t by remember { mutableStateOf(initial?.tag ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "记一条" else "编辑") },
        text = {
            Column {
                OutlinedTextField(
                    value = q,
                    onValueChange = { q = it },
                    label = { Text("问题 / 想法（必填）") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = d,
                    onValueChange = { d = it },
                    label = { Text("补充说明（可选）") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = t,
                    onValueChange = { t = it },
                    label = { Text("标签（可选，如 RTOS/网络）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(q, d, t) },
                enabled = q.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}
