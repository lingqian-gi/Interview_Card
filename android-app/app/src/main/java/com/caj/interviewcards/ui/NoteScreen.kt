package com.caj.interviewcards.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.caj.interviewcards.data.Note
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 随手记：记录日常问题/想法，支持分类、标签与一键导出。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteScreen(nvm: NoteViewModel = viewModel()) {
    val notes by nvm.notes.collectAsState()
    val categories by nvm.categories.collectAsState()
    val context = LocalContext.current

    var editing by remember { mutableStateOf<Note?>(null) }
    var creating by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var managingCategories by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf("") }

    // ★ 当前筛选分类："" = 全部，"__none__" = 未分类，其他 = 具体分类
    var filter by remember { mutableStateOf("") }

    // 分类被删除后，若当前正筛选它，回落到「全部」
    LaunchedEffect(categories) {
        if (filter.isNotEmpty() && filter != NONE_CATEGORY && filter !in categories) filter = ""
    }

    val shown = remember(notes, filter) {
        when (filter) {
            "" -> notes
            NONE_CATEGORY -> notes.filter { it.category.isBlank() }
            else -> notes.filter { it.category == filter }
        }
    }

    val df = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(nvm.exportMarkdown(filter).toByteArray(Charsets.UTF_8))
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
                    IconButton(onClick = { managingCategories = true }) {
                        Icon(Icons.Default.Category, "分类管理")
                    }
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
        Column(Modifier.padding(padding).fillMaxSize()) {

            // ★ 分类筛选 chip 行
            if (categories.isNotEmpty()) {
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
                        label = { Text("全部 ${notes.size}") },
                    )
                    // ★「未分类」紧跟「全部」（老笔记/未归类时最常用）
                    val noneCount = notes.count { it.category.isBlank() }
                    if (noneCount > 0) {
                        FilterChip(
                            selected = filter == NONE_CATEGORY,
                            onClick = { filter = if (filter == NONE_CATEGORY) "" else NONE_CATEGORY },
                            label = { Text("未分类 $noneCount") },
                        )
                    }
                    // 分类按「有内容的排前面」排序，空的沉到后面
                    categories.sortedByDescending { nvm.countOf(it) }.forEach { c ->
                        FilterChip(
                            selected = filter == c,
                            onClick = { filter = if (filter == c) "" else c },
                            label = { Text("${c} ${nvm.countOf(c)}") },
                        )
                    }
                }
            }

            if (notes.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
            } else if (shown.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "该分类下暂无笔记",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 88.dp),
                ) {
                    item {
                        Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    if (filter.isBlank()) "共 ${notes.size} 条"
                                    else "共 ${shown.size} 条" + (if (filter == NONE_CATEGORY) "（未分类）" else "（$filter）"),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = { exporting = true }) {
                                    Icon(Icons.Default.IosShare, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("导出")
                                }
                            }
                        }
                    }
                    items(shown, key = { it.id }) { n ->
                        NoteRow(n, df.format(Date(n.createdAt))) { editing = n }
                    }
                }
            }
        }
    }

    // ─── 新建 / 编辑对话框 ───
    if (creating || editing != null) {
        NoteEditorDialog(
            initial = editing,
            categories = categories,
            defaultCategory = if (filter.isNotEmpty() && filter != NONE_CATEGORY) filter else "",
            onDismiss = { creating = false; editing = null },
            onSave = { q, d, cat, t ->
                if (editing != null) {
                    nvm.update(editing!!.copy(question = q, detail = d, category = cat, tag = t))
                } else {
                    nvm.add(q, d, cat, t)
                }
                creating = false; editing = null
            },
            onDelete = editing?.let { e -> { nvm.delete(e.id); editing = null } },
            onOpenCategoryManager = { managingCategories = true },
        )
    }

    // ─── 分类管理 ───
    if (managingCategories) {
        CategoryManagerDialog(
            categories = categories,
            noteCountOf = { c -> nvm.countOf(c) },
            onDismiss = { managingCategories = false },
            onAdd = { nvm.addCategory(it) },
            onRemove = { nvm.removeCategory(it) },
            onRename = { o, n -> nvm.renameCategory(o, n) },
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
        val md = nvm.exportMarkdown(filter)
        val scopeText = when {
            filter.isEmpty() -> "全部 ${notes.size} 条"
            filter == NONE_CATEGORY -> "未分类 ${shown.size} 条"
            else -> "$filter ${shown.size} 条"
        }
        AlertDialog(
            onDismissRequest = { exporting = false },
            title = { Text("导出笔记（$scopeText）") },
            text = {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        "导出为 Markdown，格式对齐面试题库，可直接交给 AI 加题。"
                            + if (filter.isEmpty()) "" else "\n\n★ 当前仅导出「$scopeText」，如需导出全部请先切到「全部」。",
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
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "面试随手记")
                            putExtra(Intent.EXTRA_TEXT, md)
                        }
                        context.startActivity(Intent.createChooser(intent, "分享笔记"))
                        exporting = false
                    }) { Text("分享") }
                    TextButton(onClick = {
                        saveLauncher.launch(
                            "面试随手记_" + SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date()) + ".md"
                        )
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

/** 「未分类」筛选的哨兵值（避免与真实分类名冲突） */
private const val NONE_CATEGORY = "\u0000NONE"

/** 分类标签的统一配色（按名称哈希取一组柔和色） */
private val CATEGORY_COLORS = listOf(
    Color(0xFF1E88E5), Color(0xFF43A047), Color(0xFFFB8C00),
    Color(0xFF8E24AA), Color(0xFF00897B), Color(0xFFE53935),
    Color(0xFF5E35B1), Color(0xFF6D4C41),
)

private fun colorOf(category: String): Color =
    CATEGORY_COLORS[Math.floorMod(category.hashCode(), CATEGORY_COLORS.size)]

@Composable
private fun NoteRow(note: Note, timeText: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                timeText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (note.category.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                // ★ 分类：实心色块
                Box(
                    Modifier
                        .background(colorOf(note.category), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        note.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            if (note.tag.isNotBlank()) {
                Spacer(Modifier.width(6.dp))
                // 标签：浅色描边
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoteEditorDialog(
    initial: Note?,
    categories: List<String>,
    defaultCategory: String,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit,
    onDelete: (() -> Unit)?,
    onOpenCategoryManager: () -> Unit,
) {
    var q by remember { mutableStateOf(initial?.question ?: "") }
    var d by remember { mutableStateOf(initial?.detail ?: "") }
    var cat by remember { mutableStateOf(initial?.category ?: defaultCategory) }
    var t by remember { mutableStateOf(initial?.tag ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "记一条" else "编辑") },
        text = {
            Column(
                Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = q,
                    onValueChange = { q = it },
                    label = { Text("问题 / 想法（必填）") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                Spacer(Modifier.height(10.dp))

                // ★ 分类（单选 chip）
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "分类",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = onOpenCategoryManager,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    ) {
                        Icon(Icons.Default.Settings, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("管理", style = MaterialTheme.typography.labelSmall)
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = cat.isBlank(),
                        onClick = { cat = "" },
                        label = { Text("未分类") },
                    )
                    categories.forEach { c ->
                        FilterChip(
                            selected = cat == c,
                            onClick = { cat = if (cat == c) "" else c },
                            label = { Text(c) },
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
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
                onClick = { onSave(q, d, cat, t) },
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

/** ★ 分类管理：新增 / 重命名 / 删除 */
@Composable
private fun CategoryManagerDialog(
    categories: List<String>,
    noteCountOf: (String) -> Int,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onRename: (String, String) -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("分类管理") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("新分类名") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onAdd(newName); newName = "" },
                        enabled = newName.isNotBlank(),
                    ) { Text("添加") }
                }
                Spacer(Modifier.height(10.dp))
                HorizontalDivider()
                Column(
                    Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())
                ) {
                    categories.forEach { c ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .background(colorOf(c), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(c, style = MaterialTheme.typography.labelSmall, color = Color.White)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${noteCountOf(c)} 条",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.weight(1f))
                            IconButton(
                                onClick = { renaming = c; renameText = c },
                                modifier = Modifier.size(32.dp),
                            ) { Icon(Icons.Default.Edit, "重命名", modifier = Modifier.size(16.dp)) }
                            IconButton(
                                onClick = { deleting = c },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    Icons.Default.Delete, "删除",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                    if (categories.isEmpty()) {
                        Text(
                            "还没有分类，先在上面添加一个",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
    )

    // 重命名
    renaming?.let { old ->
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("重命名分类") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("新名称") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { onRename(old, renameText); renaming = null },
                    enabled = renameText.isNotBlank() && renameText != old,
                ) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("取消") } },
        )
    }

    // 删除确认
    deleting?.let { name ->
        val cnt = noteCountOf(name)
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除分类「$name」？") },
            text = {
                Text(
                    if (cnt > 0) "该分类下有 $cnt 条笔记，删除分类后这些笔记会变成「未分类」，笔记本身不会丢失。"
                    else "该分类下没有笔记，可直接删除。"
                )
            },
            confirmButton = {
                TextButton(onClick = { onRemove(name); deleting = null }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
    }
}
