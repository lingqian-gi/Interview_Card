package com.caj.interviewcards.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.caj.interviewcards.data.Note
import com.caj.interviewcards.data.Recording
import com.caj.interviewcards.recorder.RecordingService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 录音页：开会/口述录音，支持分类、关联笔记、播放、导出。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingScreen(rvm: RecordingViewModel = viewModel()) {
    val context = LocalContext.current
    val list by rvm.recordings.collectAsState()
    val categories by rvm.categories.collectAsState()
    val notes by rvm.notes.collectAsState()
    val recState by RecordingService.state.collectAsState()
    val playingId by rvm.playingId.collectAsState()
    val position by rvm.positionMs.collectAsState()
    val duration by rvm.durationMs.collectAsState()

    var filter by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Recording?>(null) }
    var exporting by remember { mutableStateOf<Recording?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf("") }

    LaunchedEffect(categories) {
        if (filter.isNotEmpty() && filter != "__none__" && filter !in categories) filter = ""
    }

    val shown = remember(list, filter) {
        when (filter) {
            "" -> list
            "__none__" -> list.filter { it.category.isBlank() }
            else -> list.filter { it.category == filter }
        }
    }

    val df = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    // ★ 权限：录音（含通知）
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val micOk = result[Manifest.permission.RECORD_AUDIO] ?: false
        if (micOk) {
            RecordingService.start(context)
        } else {
            permissionDenied = true
        }
    }

    fun requestAndStart() {
        val needMic = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) != PackageManager.PERMISSION_GRANTED
        val needNotif = Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED

        val perms = buildList {
            if (needMic) add(Manifest.permission.RECORD_AUDIO)
            if (needNotif) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (perms.isEmpty()) RecordingService.start(context)
        else permLauncher.launch(perms.toTypedArray())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("录音", fontWeight = FontWeight.Bold) },
                actions = {
                    if (list.isNotEmpty()) {
                        IconButton(onClick = { confirmClear = true }) {
                            Icon(Icons.Default.DeleteSweep, "清空")
                        }
                    }
                },
            )
        },
        // ★ 底部录音按钮（录音中变红 + 计时）
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    if (recState.recording) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .background(Color(0xFFE53935), CircleShape)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    RecordingViewModel.fmtDuration(recState.elapsedMs),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    "录音中（息屏也会继续）",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { RecordingService.cancel(context) }) {
                                Text("取消", color = MaterialTheme.colorScheme.error)
                            }
                            Spacer(Modifier.width(4.dp))
                            Button(
                                onClick = { RecordingService.stop(context) },
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            ) {
                                Icon(Icons.Default.Stop, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("停止", maxLines = 1)
                            }
                        }
                    } else {
                        Button(
                            onClick = { requestAndStart() },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(26.dp),
                        ) {
                            Icon(Icons.Default.Mic, null)
                            Spacer(Modifier.width(8.dp))
                            Text("开始录音", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {

            // 分类筛选
            if (list.isNotEmpty() && categories.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = filter == "",
                        onClick = { filter = "" },
                        label = { Text("全部 ${list.size}") },
                    )
                    val noneCount = list.count { it.category.isBlank() }
                    if (noneCount > 0) {
                        FilterChip(
                            selected = filter == "__none__",
                            onClick = { filter = if (filter == "__none__") "" else "__none__" },
                            label = { Text("未分类 $noneCount") },
                        )
                    }
                    categories.sortedByDescending { rvm.countOf(it) }.forEach { c ->
                        FilterChip(
                            selected = filter == c,
                            onClick = { filter = if (filter == c) "" else c },
                            label = { Text("${c} ${rvm.countOf(c)}") },
                        )
                    }
                }
            }

            if (list.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Mic, null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("还没有录音", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "点下方「开始录音」，开会时放着即可",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else if (shown.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("该分类下暂无录音", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "共 ${shown.size} 条 · 占用 ${RecordingViewModel.fmtSize(list.sumOf { it.sizeBytes })}",
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                    items(shown, key = { it.id }) { r ->
                        RecordingRow(
                            item = r,
                            timeText = df.format(Date(r.createdAt)),
                            isPlaying = playingId == r.id,
                            position = if (playingId == r.id) position else 0,
                            duration = if (playingId == r.id && duration > 0) duration else r.durationMs,
                            noteTitleOf = { id -> notes.firstOrNull { it.id == id }?.question ?: "" },
                            onPlay = { rvm.togglePlay(r) },
                            onSeek = { rvm.seekTo(it) },
                            onClick = { editing = r },
                            onExport = { exporting = r },
                        )
                    }
                }
            }
        }
    }

    // 编辑对话框
    editing?.let { e ->
        RecordingEditorDialog(
            initial = e,
            categories = categories,
            notes = notes,
            onDismiss = { editing = null },
            onSave = { rvm.update(it); editing = null },
            onDelete = { rvm.delete(e.id); rvm.stopPlay(); editing = null },
        )
    }

    // 导出对话框
    exporting?.let { item ->
        ExportRecordingDialog(
            item = item,
            noteTitle = notes.firstOrNull { it.id == item.noteId }?.question ?: "",
            onDismiss = { exporting = null },
            onToast = { toast = it },
            fileProvider = { f ->
                FileProvider.getUriForFile(context, context.packageName + ".fileprovider", f)
            },
            file = rvm.fileOf(item.id),
        )
    }

    // 清空确认
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空全部录音？") },
            text = {
                Text(
                    "将删除全部 ${list.size} 条录音（含音频文件），不可恢复。\n\n"
                        + "建议先把重要的导出或分享出去。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    rvm.stopPlay(); rvm.clearAll(); confirmClear = false
                }) { Text("清空", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } },
        )
    }

    // 权限被拒
    if (permissionDenied) {
        AlertDialog(
            onDismissRequest = { permissionDenied = false },
            title = { Text("需要麦克风权限") },
            text = { Text("录音需要麦克风权限。请在「设置 → 应用 → 面试卡片 → 权限」中开启「麦克风」。") },
            confirmButton = {
                TextButton(onClick = {
                    permissionDenied = false
                    runCatching {
                        context.startActivity(
                            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .setData(Uri.parse("package:" + context.packageName))
                        )
                    }
                }) { Text("去设置") }
            },
            dismissButton = { TextButton(onClick = { permissionDenied = false }) { Text("取消") } },
        )
    }

    // 提示
    if (toast.isNotEmpty()) {
        LaunchedEffect(toast) {
            kotlinx.coroutines.delay(1800)
            toast = ""
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Snackbar(Modifier.padding(16.dp)) { Text(toast) }
        }
    }
}

/** 单条录音：播放控制 + 信息 */
@Composable
private fun RecordingRow(
    item: Recording,
    timeText: String,
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    noteTitleOf: (String) -> String,
    onPlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onClick: () -> Unit,
    onExport: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 播放按钮
            FilledTonalIconButton(onClick = onPlay, modifier = Modifier.size(44.dp)) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    if (isPlaying) "暂停" else "播放",
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).clickable(onClick = onClick)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        timeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        " · ${RecordingViewModel.fmtDuration(item.durationMs)} · ${RecordingViewModel.fmtSize(item.sizeBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (item.category.isNotBlank() || item.noteId.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (item.category.isNotBlank()) {
                            Box(
                                Modifier
                                    .background(colorOf(item.category), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    item.category,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                )
                            }
                        }
                        if (item.noteId.isNotBlank()) {
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Default.Link, null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(2.dp))
                            Text(
                                noteTitleOf(item.noteId).ifBlank { "已关联笔记" },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (item.remark.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        item.remark,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onExport) {
                Icon(Icons.Default.IosShare, "导出", modifier = Modifier.size(20.dp))
            }
        }

        // 进度条（仅播放中显示）
        if (isPlaying) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    RecordingViewModel.fmtDuration(position),
                    style = MaterialTheme.typography.labelSmall,
                )
                Slider(
                    value = if (duration > 0) position.toFloat() / duration else 0f,
                    onValueChange = { onSeek((it * duration).toLong()) },
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                Text(
                    RecordingViewModel.fmtDuration(item.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
    HorizontalDivider(thickness = 0.5.dp)
}

/** 编辑：标题 / 分类 / 关联笔记 / 备注 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordingEditorDialog(
    initial: Recording,
    categories: List<String>,
    notes: List<Note>,
    onDismiss: () -> Unit,
    onSave: (Recording) -> Unit,
    onDelete: () -> Unit,
) {
    var title by remember { mutableStateOf(initial.title) }
    var cat by remember { mutableStateOf(initial.category) }
    var noteId by remember { mutableStateOf(initial.noteId) }
    var remark by remember { mutableStateOf(initial.remark) }
    var pickNote by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑录音") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("标题") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))

                Text("分类", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    FilterChip(
                        selected = cat.isBlank(),
                        onClick = { cat = "" },
                        label = { Text("未分类") },
                    )
                    categories.forEach { c ->
                        Spacer(Modifier.width(6.dp))
                        FilterChip(
                            selected = cat == c,
                            onClick = { cat = if (cat == c) "" else c },
                            label = { Text(c) },
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = notes.firstOrNull { it.id == noteId }?.question ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("关联随手记") },
                    placeholder = { Text("未关联") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        Row {
                            if (noteId.isNotBlank()) {
                                IconButton(onClick = { noteId = "" }) {
                                    Icon(Icons.Default.Close, "清除", modifier = Modifier.size(18.dp))
                                }
                            }
                            IconButton(onClick = { pickNote = true }) {
                                Icon(Icons.Default.Search, "选择", modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                )

                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = remark,
                    onValueChange = { remark = it },
                    label = { Text("备注（可选）") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        initial.copy(
                            title = title.trim().ifBlank { initial.title },
                            category = cat,
                            noteId = noteId,
                            remark = remark.trim(),
                        )
                    )
                }
            ) { Text("保存") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { confirmDelete = true }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )

    if (pickNote) {
        NotePickerDialog(
            notes = notes,
            onDismiss = { pickNote = false },
            onPick = { noteId = it; pickNote = false },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除这条录音？") },
            text = { Text("音频文件也会一并删除，不可恢复。") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            },
        )
    }
}

/** 选择一条随手记来关联 */
@Composable
private fun NotePickerDialog(
    notes: List<Note>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    var q by remember { mutableStateOf("") }
    val filtered = remember(notes, q) {
        if (q.isBlank()) notes
        else notes.filter { it.question.contains(q, true) || it.category.contains(q, true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("关联到随手记") },
        text = {
            Column {
                OutlinedTextField(
                    value = q,
                    onValueChange = { q = it },
                    label = { Text("搜索笔记") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                if (notes.isEmpty()) {
                    Text(
                        "还没有随手记，先去「笔记」页记一条",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                        filtered.forEach { n ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable { onPick(n.id) }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (n.category.isNotBlank()) {
                                    Box(
                                        Modifier
                                            .background(colorOf(n.category), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            n.category,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                        )
                                    }
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text(
                                    n.question,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            HorizontalDivider(thickness = 0.5.dp)
                        }
                        if (filtered.isEmpty()) {
                            Text(
                                "没有匹配的笔记",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

/** 导出：分享 / 存文件 / 复制信息 */
@Composable
private fun ExportRecordingDialog(
    item: Recording,
    noteTitle: String,
    onDismiss: () -> Unit,
    onToast: (String) -> Unit,
    fileProvider: (java.io.File) -> Uri,
    file: java.io.File,
) {
    val context = LocalContext.current
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("audio/mp4")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    file.inputStream().use { it.copyTo(os) }
                }
                onToast("已保存到文件")
            }.onFailure { onToast("保存失败：${it.message}") }
        }
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导出录音") },
        text = {
            Column {
                Text(item.title, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${RecordingViewModel.fmtDuration(item.durationMs)} · ${RecordingViewModel.fmtSize(item.sizeBytes)}（M4A/AAC）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (item.category.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "分类：${item.category}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (noteTitle.isNotBlank()) {
                    Text(
                        "关联笔记：$noteTitle",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "★ 转文字建议：把音频分享/保存后，交给豆包等支持音频转写的工具处理。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // 分享音频文件
                runCatching {
                    val uri = fileProvider(file)
                    val i = Intent(Intent.ACTION_SEND).apply {
                        type = "audio/mp4"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, item.title)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(i, "分享录音"))
                }.onFailure { onToast("分享失败：${it.message}") }
                onDismiss()
            }) { Text("分享音频") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    // 复制文字信息（便于在豆包等工具里补充上下文）
                    val sb = StringBuilder()
                    sb.append(item.title).append("\n")
                    sb.append("时长：").append(RecordingViewModel.fmtDuration(item.durationMs)).append("\n")
                    if (item.category.isNotBlank()) sb.append("分类：").append(item.category).append("\n")
                    if (noteTitle.isNotBlank()) sb.append("关联笔记：").append(noteTitle).append("\n")
                    if (item.remark.isNotBlank()) sb.append("备注：").append(item.remark).append("\n")
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("录音信息", sb.toString()))
                    onToast("信息已复制")
                    onDismiss()
                }) { Text("复制信息") }
                TextButton(onClick = {
                    saveLauncher.launch(file.name.ifBlank { "recording.m4a" })
                }) { Text("存为文件") }
            }
        },
    )
}
