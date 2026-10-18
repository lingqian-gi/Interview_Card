package com.caj.interviewcards.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** 一条随手记 */
@Serializable
data class Note(
    val id: String,                 // 时间戳字符串，天然唯一
    val question: String,           // ★ 问题 / 想法（必填）
    val detail: String = "",        // 补充说明（可选）
    val category: String = "",      // ★ 分类（单选，空 = 未分类）
    val tag: String = "",           // 标签（自由输入，可选）
    val createdAt: Long = 0L,       // 创建时间（毫秒）
    val updatedAt: Long = 0L,
)

/** ★ 默认分类（首次使用时初始化，可增删） */
val DEFAULT_CATEGORIES = listOf("待查证", "待复习", "已理解", "灵感")

private val Context.noteStore by preferencesDataStore(name = "interview_notes")

/**
 * 随手记存储：整份列表序列化成 JSON 存进 DataStore（简单可靠，笔记量小）。
 *   - 用 Flow 暴露，UI 自动刷新
 *   - 支持增 / 改 / 删 / 导出
 *   - ★ 分类列表也持久化，支持自定义增删
 */
class NoteStore(private val context: Context) {

    private val KEY_NOTES = stringPreferencesKey("notes_json")
    private val KEY_CATEGORIES = stringPreferencesKey("note_categories_json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** 全部分类（若从未设置过，返回默认分类） */
    val categories: Flow<List<String>> = context.noteStore.data.map { prefs ->
        val raw = prefs[KEY_CATEGORIES]
        if (raw == null) DEFAULT_CATEGORIES
        else runCatching {
            json.decodeFromString(ListSerializer(String.serializer()), raw)
        }.getOrElse { DEFAULT_CATEGORIES }
    }

    /** 全部笔记（按创建时间倒序） */
    val notes: Flow<List<Note>> = context.noteStore.data.map { prefs ->
        val raw = prefs[KEY_NOTES] ?: "[]"
        runCatching {
            json.decodeFromString(ListSerializer(Note.serializer()), raw)
        }.getOrElse { emptyList() }
            .sortedByDescending { it.createdAt }
    }

    private suspend fun writeAll(list: List<Note>) {
        context.noteStore.edit { prefs ->
            prefs[KEY_NOTES] = json.encodeToString(ListSerializer(Note.serializer()), list)
        }
    }

    private suspend fun writeCategories(list: List<String>) {
        context.noteStore.edit { prefs ->
            prefs[KEY_CATEGORIES] = json.encodeToString(ListSerializer(String.serializer()), list)
        }
    }

    suspend fun add(
        question: String,
        detail: String,
        category: String,
        tag: String,
    ): Note {
        val now = System.currentTimeMillis()
        val note = Note(
            id = now.toString(),
            question = question.trim(),
            detail = detail.trim(),
            category = category.trim(),
            tag = tag.trim(),
            createdAt = now,
            updatedAt = now,
        )
        val cur = notesOnce()
        writeAll(cur + note)
        return note
    }

    suspend fun update(note: Note) {
        val cur = notesOnce().map {
            if (it.id == note.id) note.copy(updatedAt = System.currentTimeMillis()) else it
        }
        writeAll(cur)
    }

    suspend fun delete(id: String) {
        writeAll(notesOnce().filterNot { it.id == id })
    }

    suspend fun clearAll() {
        writeAll(emptyList())
    }

    // ─── ★ 分类管理 ───

    /** 新增一个分类（去重 + 去空） */
    suspend fun addCategory(name: String) {
        val n = name.trim()
        if (n.isEmpty()) return
        val cur = categoriesOnce()
        if (cur.any { it == n }) return
        writeCategories(cur + n)
    }

    /** 删除分类（只删分类本身，不动已使用该分类的笔记） */
    suspend fun removeCategory(name: String) {
        writeCategories(categoriesOnce().filterNot { it == name })
    }

    /** 重命名分类（同时更新所有已用该分类的笔记） */
    suspend fun renameCategory(old: String, new: String) {
        val n = new.trim()
        if (n.isEmpty() || n == old) return
        val cur = categoriesOnce()
        if (cur.any { it == n }) return
        writeCategories(cur.map { if (it == old) n else it })
        val notes = notesOnce().map { if (it.category == old) it.copy(category = n) else it }
        writeAll(notes)
    }

    private suspend fun categoriesOnce(): List<String> {
        val raw = context.noteStore.data.first()[KEY_CATEGORIES]
        return if (raw == null) DEFAULT_CATEGORIES
        else runCatching {
            json.decodeFromString(ListSerializer(String.serializer()), raw)
        }.getOrElse { DEFAULT_CATEGORIES }
    }

    private suspend fun notesOnce(): List<Note> {
        val raw = context.noteStore.data.first()[KEY_NOTES] ?: "[]"
        return runCatching {
            json.decodeFromString(ListSerializer(Note.serializer()), raw)
        }.getOrElse { emptyList() }
    }
}
