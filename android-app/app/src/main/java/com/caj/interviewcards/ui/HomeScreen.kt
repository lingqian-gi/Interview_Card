package com.caj.interviewcards.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.caj.interviewcards.data.Card

/** 顶层界面：底部两个 Tab（卡片 / 目录） */
@Composable
fun HomeScreen(vm: MainViewModel = viewModel()) {
    var tab by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Default.Style, "卡片") },
                    label = { Text("卡片") },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Default.Menu, "目录") },
                    label = { Text("目录") },
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Icon(Icons.Default.EditNote, "笔记") },
                    label = { Text("笔记") },
                )
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                0 -> CardTab(vm)
                1 -> {
                    val allCards by vm.allCards.collectAsState()
                    val knownSet by vm.knownSet.collectAsState()
                    val starredSet by vm.starredSet.collectAsState()
                    CatalogScreen(
                        allCards = allCards,
                        knownSet = knownSet,
                        starredSet = starredSet,
                        onPick = { card: Card ->
                            vm.openCard(card)
                            tab = 0
                        }
                    )
                }
                else -> NoteScreen()
            }
        }
    }
}

/** 重置类型 */
private enum class ResetKind { PROGRESS, STARS, ALL }

/** 卡片浏览页 */
@Composable
private fun CardTab(vm: MainViewModel) {
    var menuOpen by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<ResetKind?>(null) }

    // ★ 重置确认对话框
    confirm?.let { kind ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("确认重置？") },
            text = {
                Text(
                    when (kind) {
                        ResetKind.PROGRESS -> "将清空「掌握 / 不熟 / 练习次数」和今日统计，收藏会保留。此操作不可撤销。"
                        ResetKind.STARS -> "将清空所有 ⭐ 收藏。此操作不可撤销。"
                        ResetKind.ALL -> "将清空「掌握 / 不熟 / 练习次数 / 收藏 / 统计」全部数据。此操作不可撤销。"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    when (kind) {
                        ResetKind.PROGRESS -> vm.resetProgressOnly()
                        ResetKind.STARS -> vm.clearStars()
                        ResetKind.ALL -> vm.resetEverything()
                    }
                    confirm = null
                }) { Text("确认重置", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = null }) { Text("取消") }
            },
        )
    }

    val books by vm.books.collectAsState()
    val currentBookId by vm.currentBookId.collectAsState()
    val mode by vm.mode.collectAsState()
    val index by vm.index.collectAsState()
    val dataSource by vm.dataSource.collectAsState()
    val knownSet by vm.knownSet.collectAsState()
    val unknownSet by vm.unknownSet.collectAsState()
    val starredSet by vm.starredSet.collectAsState()
    val todayCount by vm.todayCount.collectAsState()
    val reviewTotal by vm.reviewTotal.collectAsState()
    val practiceMap by vm.practiceMap.collectAsState()

    val card = remember(index, mode, vm.cards.value, knownSet, unknownSet, starredSet) { vm.currentCard() }
    val total = remember(index, mode, vm.cards.value, knownSet, unknownSet, starredSet) { vm.visibleCards().size }

    // 全局进度（基于全部 161 题）
    val allCount = vm.allCards.collectAsState().value.size
    val masteredCount = knownSet.size

    Column(modifier = Modifier.fillMaxSize()) {

        // ─── 进度条 + 统计 ───
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("掌握进度", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.width(8.dp))
                    LinearProgressIndicator(
                        progress = { if (allCount > 0) masteredCount.toFloat() / allCount else 0f },
                        modifier = Modifier.weight(1f).height(8.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "$masteredCount/$allCount",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "今日 $todayCount · 累计 $reviewTotal · 不熟 ${unknownSet.size} · ⭐ ${starredSet.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        dataSource,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    IconButton(onClick = { vm.reload() }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Refresh, "重新加载", modifier = Modifier.size(16.dp))
                    }
                    // ★ 更多操作（进度重置）
                    Box {
                        IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.MoreVert, "更多", modifier = Modifier.size(16.dp))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("重置复习进度") },
                                onClick = { menuOpen = false; confirm = ResetKind.PROGRESS },
                                leadingIcon = { Icon(Icons.Default.RestartAlt, null) },
                            )
                            DropdownMenuItem(
                                text = { Text("清空收藏") },
                                onClick = { menuOpen = false; confirm = ResetKind.STARS },
                                leadingIcon = { Icon(Icons.Default.StarBorder, null) },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("全部重置", color = MaterialTheme.colorScheme.error) },
                                onClick = { menuOpen = false; confirm = ResetKind.ALL },
                                leadingIcon = { Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
                            )
                        }
                    }
                }
            }
        }

        // ─── 分册选择 ───
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(books) { b ->
                FilterChip(
                    selected = b.id == currentBookId,
                    onClick = { vm.loadBook(b.id) },
                    label = { Text("${b.id} ${b.name}") },
                )
            }
        }

        // ─── 模式切换（可横向滚动）───
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = mode == BrowseMode.SEQUENTIAL,
                onClick = { vm.setMode(BrowseMode.SEQUENTIAL) },
                label = { Text("顺序") },
            )
            FilterChip(
                selected = mode == BrowseMode.RANDOM,
                onClick = { vm.setMode(BrowseMode.RANDOM) },
                label = { Text("随机") },
            )
            FilterChip(
                selected = mode == BrowseMode.UNKNOWN_ONLY,
                onClick = { vm.setMode(BrowseMode.UNKNOWN_ONLY) },
                label = { Text("不熟") },
            )
            FilterChip(
                selected = mode == BrowseMode.STARRED,
                onClick = { vm.setMode(BrowseMode.STARRED) },
                label = { Text("⭐收藏") },
            )
            FilterChip(
                selected = mode == BrowseMode.DUE,
                onClick = { vm.setMode(BrowseMode.DUE) },
                label = { Text("待复习") },
            )
            IconButton(onClick = { vm.randomAll() }) {
                Icon(Icons.Default.Shuffle, "随机抽题")
            }
        }

        // ─── 卡片 ───
        if (card == null) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    when (mode) {
                        BrowseMode.UNKNOWN_ONLY, BrowseMode.DUE -> "本册没有标记为「不熟」的题"
                        BrowseMode.STARRED -> "本册没有收藏的题（点卡片右上角 ⭐）"
                        else -> "暂无数据"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            CardView(
                card = card,
                isKnown = card.id in knownSet,
                isUnknown = card.id in unknownSet,
                isStarred = card.id in starredSet,
                practiceCount = practiceMap[card.id] ?: 0,
                onMarkKnown = { vm.markKnown(card.id); vm.next() },
                onMarkUnknown = { vm.markUnknown(card.id); vm.next() },
                onToggleStar = { vm.toggleStar(card.id) },
                onPracticeCorrect = { vm.practiceCorrect(card.id); vm.next() },
            )
        }

        // ─── 翻页 ───
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledIconButton(onClick = { vm.prev() }, enabled = total > 1) {
                Icon(Icons.Default.ChevronLeft, "上一张")
            }
            Text(
                if (total > 0) "${index + 1} / $total" else "0 / 0",
                style = MaterialTheme.typography.titleMedium,
            )
            FilledIconButton(onClick = { vm.next() }, enabled = total > 1) {
                Icon(Icons.Default.ChevronRight, "下一张")
            }
        }
    }
}
