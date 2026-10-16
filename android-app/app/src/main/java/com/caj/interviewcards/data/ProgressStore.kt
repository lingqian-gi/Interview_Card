package com.caj.interviewcards.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "interview_progress")

/**
 * 进度持久化（DataStore）：
 *   - known / unknown     熟 / 不熟标记
 *   - starred             收藏
 *   - practiceCount       某题"连续答对"次数（间隔重复用）
 *   - lastCardId          上次复习到的题（续看）
 *   - lastBookId          上次所在分册
 *   - todayDate / todayCount  今日复习计数
 *   - reviewTotal         累计复习次数
 */
class ProgressStore(private val context: Context) {

    private val KEY_KNOWN = stringSetPreferencesKey("known")
    private val KEY_UNKNOWN = stringSetPreferencesKey("unknown")
    private val KEY_STARRED = stringSetPreferencesKey("starred")
    private val KEY_PRACTICE = stringSetPreferencesKey("practice")   // 存 "Q21:2" 形式
    private val KEY_LAST_CARD = stringPreferencesKey("lastCardId")
    private val KEY_LAST_BOOK = stringPreferencesKey("lastBookId")
    private val KEY_TODAY_DATE = stringPreferencesKey("todayDate")
    private val KEY_TODAY_COUNT = intPreferencesKey("todayCount")
    private val KEY_REVIEW_TOTAL = intPreferencesKey("reviewTotal")

    val known: Flow<Set<String>> = context.dataStore.data.map { it[KEY_KNOWN] ?: emptySet() }
    val unknown: Flow<Set<String>> = context.dataStore.data.map { it[KEY_UNKNOWN] ?: emptySet() }
    val starred: Flow<Set<String>> = context.dataStore.data.map { it[KEY_STARRED] ?: emptySet() }
    val practice: Flow<Map<String, Int>> = context.dataStore.data.map { prefs ->
        (prefs[KEY_PRACTICE] ?: emptySet()).mapNotNull { s ->
            val i = s.lastIndexOf(':')
            if (i <= 0) null else s.substring(0, i) to (s.substring(i + 1).toIntOrNull() ?: 0)
        }.toMap()
    }
    val lastCardId: Flow<String> = context.dataStore.data.map { it[KEY_LAST_CARD] ?: "" }
    val lastBookId: Flow<String> = context.dataStore.data.map { it[KEY_LAST_BOOK] ?: "" }
    val todayCount: Flow<Int> = context.dataStore.data.map { it[KEY_TODAY_COUNT] ?: 0 }
    val reviewTotal: Flow<Int> = context.dataStore.data.map { it[KEY_REVIEW_TOTAL] ?: 0 }

    // ─── 熟 / 不熟 ───
    suspend fun markKnown(cardId: String) {
        context.dataStore.edit { p ->
            p[KEY_KNOWN] = (p[KEY_KNOWN] ?: emptySet()) + cardId
            p[KEY_UNKNOWN] = (p[KEY_UNKNOWN] ?: emptySet()) - cardId
            p[KEY_PRACTICE] = bumpPractice(p[KEY_PRACTICE], cardId, 0)  // 掌握 → 清零
        }
        bumpToday()
    }

    suspend fun markUnknown(cardId: String) {
        context.dataStore.edit { p ->
            p[KEY_UNKNOWN] = (p[KEY_UNKNOWN] ?: emptySet()) + cardId
            p[KEY_KNOWN] = (p[KEY_KNOWN] ?: emptySet()) - cardId
        }
        bumpToday()
    }

    suspend fun clearMark(cardId: String) {
        context.dataStore.edit { p ->
            p[KEY_KNOWN] = (p[KEY_KNOWN] ?: emptySet()) - cardId
            p[KEY_UNKNOWN] = (p[KEY_UNKNOWN] ?: emptySet()) - cardId
        }
    }

    /** ★ 间隔重复：不熟的题"再练一次"，连续答对 3 次自动转掌握 */
    suspend fun practiceCorrect(cardId: String) {
        context.dataStore.edit { p ->
            val next = bumpPractice(p[KEY_PRACTICE], cardId, +1)
            p[KEY_PRACTICE] = next
            val count = next.firstOrNull { it.startsWith("$cardId:") }
                ?.substringAfter(':')?.toIntOrNull() ?: 0
            if (count >= 3) {
                p[KEY_KNOWN] = (p[KEY_KNOWN] ?: emptySet()) + cardId
                p[KEY_UNKNOWN] = (p[KEY_UNKNOWN] ?: emptySet()) - cardId
                p[KEY_PRACTICE] = next.filterNot { it.startsWith("$cardId:") }.toSet()
            }
        }
        bumpToday()
    }

    // ─── 收藏 ───
    suspend fun toggleStar(cardId: String) {
        context.dataStore.edit { p ->
            val cur = p[KEY_STARRED] ?: emptySet()
            p[KEY_STARRED] = if (cardId in cur) cur - cardId else cur + cardId
        }
    }

    // ─── 上次位置 ───
    suspend fun saveLast(cardId: String, bookId: String) {
        context.dataStore.edit { p ->
            p[KEY_LAST_CARD] = cardId
            p[KEY_LAST_BOOK] = bookId
        }
    }

    // ─── 今日计数 ───
    private suspend fun bumpToday() {
        val today = java.time.LocalDate.now().toString()
        context.dataStore.edit { p ->
            if (p[KEY_TODAY_DATE] == today) {
                p[KEY_TODAY_COUNT] = (p[KEY_TODAY_COUNT] ?: 0) + 1
            } else {
                p[KEY_TODAY_DATE] = today
                p[KEY_TODAY_COUNT] = 1
            }
            p[KEY_REVIEW_TOTAL] = (p[KEY_REVIEW_TOTAL] ?: 0) + 1
        }
    }

    suspend fun resetAll() {
        context.dataStore.edit { p ->
            p[KEY_KNOWN] = emptySet()
            p[KEY_UNKNOWN] = emptySet()
            p[KEY_PRACTICE] = emptySet()
        }
    }

    /** 只重置复习进度（掌握/不熟/练习/统计），保留收藏 */
    suspend fun resetProgressOnly() {
        context.dataStore.edit { p ->
            p[KEY_KNOWN] = emptySet()
            p[KEY_UNKNOWN] = emptySet()
            p[KEY_PRACTICE] = emptySet()
            p[KEY_TODAY_COUNT] = 0
            p[KEY_REVIEW_TOTAL] = 0
            p[KEY_LAST_CARD] = ""     // 同时清掉"续看位置"
            p[KEY_LAST_BOOK] = ""
        }
    }

    /** 只清空收藏 */
    suspend fun clearStars() {
        context.dataStore.edit { p -> p[KEY_STARRED] = emptySet() }
    }

    /** 完全重置（进度 + 收藏 + 位置 + 统计） */
    suspend fun resetEverything() {
        context.dataStore.edit { p ->
            p[KEY_KNOWN] = emptySet()
            p[KEY_UNKNOWN] = emptySet()
            p[KEY_PRACTICE] = emptySet()
            p[KEY_STARRED] = emptySet()
            p[KEY_TODAY_COUNT] = 0
            p[KEY_REVIEW_TOTAL] = 0
            p[KEY_LAST_CARD] = ""
            p[KEY_LAST_BOOK] = ""
        }
    }

    private fun bumpPractice(cur: Set<String>?, cardId: String, delta: Int): Set<String> {
        val set = cur ?: emptySet()
        val old = set.firstOrNull { it.startsWith("$cardId:") }
            ?.substringAfter(':')?.toIntOrNull() ?: 0
        val now = (old + delta).coerceAtLeast(0)
        return set.filterNot { it.startsWith("$cardId:") }.toSet() + "$cardId:$now"
    }
}
