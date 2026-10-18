package com.caj.interviewcards.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * 可长按拖拽排序的列表（无第三方依赖）。
 *
 * 原理：
 *   1. 用 LazyColumn + itemsIndexed 渲染；
 *   2. 长按某一行 → 记录「正在拖拽的索引」+ 该行的累计位移；
 *   3. 拖动时，把位移换算成「跨过了几个行高」，实时交换列表顺序；
 *   4. 松手 → 回调最终顺序（由调用方持久化）。
 *
 * 注意：为了简化，行高按「实测第一行高度」近似（分类行高度一致，够用）。
 */
@Composable
fun <T> DragReorderLazyColumn(
    items: List<T>,
    onReorder: (List<T>) -> Unit,
    modifier: Modifier = Modifier,
    keyOf: (T) -> Any,
    content: @Composable (item: T, isDragging: Boolean) -> Unit,
) {
    val list = remember(items) { items.toMutableStateList() }
    // 外部数据变化时同步（如新增/删除分类）
    LaunchedEffect(items) {
        if (list != items) {
            list.clear()
            list.addAll(items)
        }
    }

    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var rowHeight by remember { mutableFloatStateOf(1f) }

    val density = LocalDensity.current
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = modifier,
    ) {
        itemsIndexed(list, key = { _, item -> keyOf(item) }) { index, item ->
            val isDragging = index == draggingIndex
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer {
                        if (isDragging) {
                            translationY = dragOffset
                            alpha = 0.92f
                            shadowElevation = with(density) { 8.dp.toPx() }
                        }
                    }
                    .onSizeChanged { rowHeight = it.height.toFloat() }
                    .pointerInput(keyOf(item)) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggingIndex = index
                                dragOffset = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffset += dragAmount.y
                                // 跨过一行高度就交换位置
                                if (rowHeight > 0f) {
                                    val steps = (dragOffset / rowHeight).toInt()
                                    if (steps != 0) {
                                        val from = draggingIndex
                                        val to = (from + steps).coerceIn(0, list.lastIndex)
                                        if (from != to) {
                                            list.add(to, list.removeAt(from))
                                            draggingIndex = to
                                            dragOffset -= steps * rowHeight
                                        }
                                    }
                                }
                            },
                            onDragEnd = {
                                draggingIndex = -1
                                dragOffset = 0f
                                onReorder(list.toList())
                            },
                            onDragCancel = {
                                draggingIndex = -1
                                dragOffset = 0f
                            },
                        )
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                content(item, isDragging)
            }
        }
    }
}

/** 拖拽把手图标（提示用户可长按拖动） */
@Composable
fun DragHandle() {
    Icon(
        Icons.Default.DragHandle,
        contentDescription = "长按拖动排序",
        modifier = Modifier.size(20.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
