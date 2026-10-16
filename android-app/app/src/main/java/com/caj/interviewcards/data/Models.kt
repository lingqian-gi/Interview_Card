package com.caj.interviewcards.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 单张卡片 */
@Serializable
data class Card(
    val id: String,                        // 题号，如 Q141
    val book: String,                      // 分册 id，如 "05"
    @SerialName("bookName") val bookName: String = "",
    val proj: String = "?",                // 项目/主题分组键（由数据决定，如项目代号、通用基础等）
    val tags: List<String> = emptyList(),  // 主题标签
    val question: String,                  // ★ 卡片正面
    val answer: String = "",               // ★ 卡片背面（短答案）
    @SerialName("answerSource") val answerSource: String = "",
    @SerialName("answerFull") val answerFull: String? = null,  // 完整正文（Markdown，可空）
    val anchor: String = "",
)

/** 分册元信息 */
@Serializable
data class BookInfo(
    val id: String,
    val name: String,
    val count: Int = 0,
)

/** 轻量版（cards.json）：全部卡片，不含全文 */
@Serializable
data class CardBundle(
    val version: String = "",
    @SerialName("generatedAt") val generatedAt: String = "",
    val total: Int = 0,
    val books: List<BookInfo> = emptyList(),
    val cards: List<Card> = emptyList(),
)

/** 分册版（cards/01.json）：单册，含全文 */
@Serializable
data class BookBundle(
    val book: String = "",
    @SerialName("bookName") val bookName: String = "",
    val count: Int = 0,
    val cards: List<Card> = emptyList(),
)

/** 索引（cards/index.json） */
@Serializable
data class CardsIndex(
    val version: String = "",
    @SerialName("generatedAt") val generatedAt: String = "",
    val total: Int = 0,
    val books: List<BookInfo> = emptyList(),
)
