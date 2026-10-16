package com.caj.interviewcards.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caj.interviewcards.data.Card

/**
 * 单张卡片：正面（问题）→ 点击翻转 → 背面（短答案 + 可展开全文）。
 */
@Composable
fun CardView(
    card: Card,
    isKnown: Boolean,
    isUnknown: Boolean,
    isStarred: Boolean,
    practiceCount: Int,
    onMarkKnown: () -> Unit,
    onMarkUnknown: () -> Unit,
    onToggleStar: () -> Unit,
    onPracticeCorrect: () -> Unit,
) {
    var flipped by remember(card.id) { mutableStateOf(false) }
    var showFull by remember(card.id) { mutableStateOf(false) }

    val rotation by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = tween(400),
        label = "flip"
    )

    Column(modifier = Modifier.fillMaxSize()) {

        // ─── 卡片主体 ───
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .graphicsLayer {
                    rotationY = rotation
                    cameraDistance = 12f * density
                }
                .clickable { flipped = !flipped },
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .graphicsLayer { if (rotation > 90f) rotationY = 180f }
            ) {
                // 顶部：题号 + 分册 + 状态 + ⭐
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            card.id,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        card.bookName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    if (isKnown) {
                        Icon(Icons.Default.CheckCircle, "已掌握", tint = Color(0xFF2E7D32))
                    }
                    if (isUnknown) {
                        Icon(Icons.Default.Error, "不熟", tint = MaterialTheme.colorScheme.error)
                    }
                    // ★ 收藏按钮
                    IconButton(onClick = onToggleStar) {
                        Icon(
                            if (isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "收藏",
                            tint = if (isStarred) Color(0xFFF9A825)
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                if (!flipped) {
                    // ─── 正面 ───
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = card.question,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 34.sp,
                        )
                    }
                    Text(
                        "点击卡片查看答案",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                } else {
                    // ─── 背面 ───
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            card.question,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            card.answer.ifBlank { "（本题无短答案，请展开全文）" },
                            style = MaterialTheme.typography.bodyLarge,
                            lineHeight = 26.sp,
                        )

                        if (showFull) {
                            Spacer(Modifier.height(16.dp))
                            HorizontalDivider()
                            Spacer(Modifier.height(12.dp))
                            if (!card.answerFull.isNullOrBlank()) {
                                MarkdownView(card.answerFull)
                            } else {
                                Text("（本题无完整正文）", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    // ★ 展开全文：整块区域可点（大点击目标）
                    if (!card.answerFull.isNullOrBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .clickable { showFull = !showFull },
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    if (showFull) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (showFull) "收起全文" else "展开完整正文",
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
            }
        }

        // ─── 底部操作栏 ───
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = { flipped = !flipped },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
            ) {
                Text(if (flipped) "看问题" else "看答案", maxLines = 1)
            }
            // ★ 再练一次（间隔重复）
            FilledTonalButton(
                onClick = { onPracticeCorrect(); flipped = false },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                ),
            ) {
                Text(if (practiceCount > 0) "再练($practiceCount)" else "再练", maxLines = 1)
            }
            FilledTonalButton(
                onClick = { onMarkUnknown(); flipped = false },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (isUnknown) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
            ) {
                Text("不熟", maxLines = 1)
            }
            Button(
                onClick = { onMarkKnown(); flipped = false },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isKnown) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                ),
            ) {
                Text("掌握", maxLines = 1)
            }
        }
    }
}
