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
import java.io.File

/**
 * 一条录音的元信息。
 * 音频本身以文件形式存放在 App 私有目录（filesDir/recordings/<id>.m4a），
 * 这里只存元信息（标题、时长、大小、分类、关联笔记等）。
 */
@Serializable
data class Recording(
    val id: String,                       // 文件基名（时间戳）
    val title: String = "",               // 标题（默认按时间生成，可改）
    val durationMs: Long = 0L,            // 时长（毫秒）
    val sizeBytes: Long = 0L,             // 文件大小
    val category: String = "",            // 分类（复用随手记分类）
    val noteId: String = "",              // ★ 关联的随手记 id（可空 = 未关联）
    val remark: String = "",              // 备注
    val createdAt: Long = 0L,
)

private val Context.recordingMetaStore by preferencesDataStore(name = "interview_recordings")

/**
 * 录音仓库：
 *   - 音频文件：filesDir/recordings/<id>.m4a
 *   - 元信息：DataStore(JSON 列表)
 */
class RecordingStore(private val context: Context) {

    private val KEY_RECORDINGS = stringPreferencesKey("recordings_json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** 录音文件目录 */
    val dir: File
        get() = File(context.filesDir, "recordings").apply { if (!exists()) mkdirs() }

    /** 新录音的目标文件（用时间戳做 id，天然唯一） */
    fun newFile(): Pair<String, File> {
        val id = System.currentTimeMillis().toString()
        return id to File(dir, "$id.m4a")
    }

    /** 某条录音的音频文件 */
    fun fileOf(id: String): File = File(dir, "$id.m4a")

    /** 全部录音（按创建时间倒序） */
    val recordings: Flow<List<Recording>> = context.recordingMetaStore.data.map { prefs ->
        val raw = prefs[KEY_RECORDINGS] ?: "[]"
        runCatching {
            json.decodeFromString(ListSerializer(Recording.serializer()), raw)
        }.getOrElse { emptyList() }
            .sortedByDescending { it.createdAt }
    }

    private suspend fun writeAll(list: List<Recording>) {
        context.recordingMetaStore.edit { prefs ->
            prefs[KEY_RECORDINGS] = json.encodeToString(ListSerializer(Recording.serializer()), list)
        }
    }

    private suspend fun once(): List<Recording> {
        val raw = context.recordingMetaStore.data.first()[KEY_RECORDINGS] ?: "[]"
        return runCatching {
            json.decodeFromString(ListSerializer(Recording.serializer()), raw)
        }.getOrElse { emptyList() }
    }

    /** 录音结束后登记 */
    suspend fun add(rec: Recording) {
        writeAll(once() + rec)
    }

    suspend fun update(rec: Recording) {
        writeAll(once().map { if (it.id == rec.id) rec else it })
    }

    /** 删除录音（同时删文件） */
    suspend fun delete(id: String) {
        writeAll(once().filterNot { it.id == id })
        runCatching { fileOf(id).delete() }
    }

    /** 清空全部录音 */
    suspend fun clearAll() {
        writeAll(emptyList())
        runCatching { dir.listFiles()?.forEach { it.delete() } }
    }

    /** ★ 启动时对账：清掉「元信息存在但文件没了」的僵尸记录 */
    suspend fun reconcile() {
        val exist = once().filter { fileOf(it.id).exists() }
        if (exist.size != once().size) writeAll(exist)
    }

    /** 某条笔记关联的录音 */
    suspend fun recordingsOfNote(noteId: String): List<Recording> =
        once().filter { it.noteId == noteId }

    /** 已占用的总空间（字节） */
    fun totalSize(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L
}
