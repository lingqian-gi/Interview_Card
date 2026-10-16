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
import kotlinx.serialization.json.Json

/** 一条随手记 */
@Serializable
data class Note(
    val id: String,                 // 时间戳字符串，天然唯一
    val question: String,           // ★ 问题 / 想法（必填）
    val detail: String = "",        // 补充说明（可选）
    val tag: String = "",           // 标签（可选）
    val createdAt: Long = 0L,       // 创建时间（毫秒）
    val updatedAt: Long = 0L,
)

private val Context.noteStore by preferencesDataStore(name = "interview_notes")

/**
 * 随手记存储：整份列表序列化成 JSON 存进 DataStore（简单可靠，笔记量小）。
 *   - 用 Flow 暴露，UI 自动刷新
 *   - 支持增 / 改 / 删 / 导出
 */
class NoteStore(private val context: Context) {

    private val KEY_NOTES = stringPreferencesKey("notes_json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

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

    suspend fun add(question: String, detail: String, tag: String): Note {
        val now = System.currentTimeMillis()
        val note = Note(
            id = now.toString(),
            question = question.trim(),
            detail = detail.trim(),
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

    private suspend fun notesOnce(): List<Note> {
        val raw = context.noteStore.data.first()[KEY_NOTES] ?: "[]"
        return runCatching {
            json.decodeFromString(ListSerializer(Note.serializer()), raw)
        }.getOrElse { emptyList() }
    }
}
