package com.caj.interviewcards.ui

import androidx.compose.ui.graphics.Color

/** 分类标签的统一配色（按名称哈希取一组柔和色，同名永远同色） */
private val CATEGORY_COLORS = listOf(
    Color(0xFF1E88E5), Color(0xFF43A047), Color(0xFFFB8C00),
    Color(0xFF8E24AA), Color(0xFF00897B), Color(0xFFE53935),
    Color(0xFF5E35B1), Color(0xFF6D4C41),
)

/** 根据分类名取一个稳定的颜色 */
fun colorOf(category: String): Color =
    CATEGORY_COLORS[Math.floorMod(category.hashCode(), CATEGORY_COLORS.size)]
