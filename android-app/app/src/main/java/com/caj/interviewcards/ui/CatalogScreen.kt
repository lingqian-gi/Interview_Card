package com.caj.interviewcards.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.caj.interviewcards.data.Card

/** 目录分组维度 */
enum class GroupBy { NONE, BOOK, PROJ, STARRED }

/**
 * 目录页：看"所有问题"。
 *  - 全部（161 题平铺，按题号）
 *  - 按分册分组
 *  - 按项目分组
 *  - ⭐收藏
 * 点击任一题 → 跳到卡片模式并定位。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    allCards: List<Card>,
    knownSet: Set<String>,
    starredSet: Set<String>,
    onPick: (Card) -> Unit,
) {
    var groupBy by remember { mutableStateOf(GroupBy.BOOK) }
    var query by remember { mutableStateOf("") }

    val base = remember(allCards, groupBy, starredSet) {
        if (groupBy == GroupBy.STARRED) allCards.filter { it.id in starredSet } else allCards
    }
    val filtered = remember(base, query) {
        if (query.isBlank()) base
        else base.filter {
            it.question.contains(query, true) ||
            it.id.contains(query, true) ||
            it.answer.contains(query, true) ||
            it.tags.any { t -> t.contains(query, true) }
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("问题目录", fontWeight = FontWeight.Bold) },
                    actions = {
                        Text(
                            "${filtered.size} 题",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                    }
                )
                // 搜索框
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    placeholder = { Text("搜索问题 / 题号（如 Q141、V4L2）") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Close, "清除")
                            }
                        }
                    },
                    singleLine = true,
                )
                // 分组方式
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = groupBy == GroupBy.BOOK,
                        onClick = { groupBy = GroupBy.BOOK },
                        label = { Text("按分册") },
                    )
                    FilterChip(
                        selected = groupBy == GroupBy.PROJ,
                        onClick = { groupBy = GroupBy.PROJ },
                        label = { Text("按项目") },
                    )
                    FilterChip(
                        selected = groupBy == GroupBy.NONE,
                        onClick = { groupBy = GroupBy.NONE },
                        label = { Text("全部") },
                    )
                    FilterChip(
                        selected = groupBy == GroupBy.STARRED,
                        onClick = { groupBy = GroupBy.STARRED },
                        label = { Text("⭐收藏") },
                    )
                }
            }
        }
    ) { padding ->
        val listState = rememberLazyListState()

        when (groupBy) {
            GroupBy.NONE, GroupBy.STARRED -> {
                LazyColumn(
                    modifier = Modifier.padding(padding).fillMaxSize(),
                    state = listState,
                ) {
                    if (filtered.isEmpty() && groupBy == GroupBy.STARRED) {
                        item {
                            Box(
                                Modifier.fillMaxWidth().padding(32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "还没有收藏的题（在卡片右上角点 ⭐）",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    items(filtered, key = { it.id }) { card ->
                        CatalogRow(card, card.id in knownSet, card.id in starredSet) { onPick(card) }
                    }
                }
            }
            else -> {
                // 分组：按分册 或 按项目
                // ★ 项目名不再硬编码：用该 proj 下实际出现的 bookName 兜底（数据驱动，软件通用）
                val projNames = remember(allCards) {
                    val m = LinkedHashMap<String, String>()
                    allCards.forEach { c ->
                        if (c.proj.isNotBlank() && c.proj != "?") {
                            val cur = m[c.proj]
                            if (cur.isNullOrBlank()) m[c.proj] = c.bookName
                        }
                    }
                    m
                }
                val groups = remember(filtered, groupBy, projNames) {
                    val m = LinkedHashMap<String, MutableList<Card>>()
                    filtered.forEach { c ->
                        val key = if (groupBy == GroupBy.BOOK)
                            "${c.book} ${c.bookName}"
                        else
                            projLabel(c.proj, projNames[c.proj] ?: "")
                        m.getOrPut(key) { mutableListOf() }.add(c)
                    }
                    m.entries.sortedBy { it.key }
                }

                LazyColumn(
                    modifier = Modifier.padding(padding).fillMaxSize(),
                    state = listState,
                ) {
                    groups.forEach { (groupName, cards) ->
                        item(key = "h_$groupName") {
                            GroupHeader(groupName, cards.size)
                        }
                        items(cards, key = { it.id }) { card ->
                            CatalogRow(card, card.id in knownSet, card.id in starredSet) { onPick(card) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 项目名友好化（数据驱动，不硬编码具体题库）。
 * 若 proj 与某分册同名，用分册名增强可读性；否则直接显示 proj 本身。
 */
private fun projLabel(p: String, bookNameOfProj: String): String =
    if (bookNameOfProj.isNotBlank() && bookNameOfProj != p) "$p · $bookNameOfProj" else p

@Composable
private fun GroupHeader(name: String, count: Int) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.weight(1f))
            Text("$count 题", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun CatalogRow(card: Card, isKnown: Boolean, isStarred: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isKnown) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = "已掌握",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
        } else if (isStarred) {
            Icon(
                Icons.Default.Star,
                contentDescription = "已收藏",
                tint = Color(0xFFF9A825),
                modifier = Modifier.size(16.dp),
            )
        } else {
            Spacer(Modifier.size(16.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(
            card.id,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.width(46.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            card.question,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
        )
    }
    HorizontalDivider(thickness = 0.5.dp)
}
