package com.caj.interviewcards.ui

import android.app.Application
import android.media.MediaPlayer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.caj.interviewcards.data.NoteStore
import com.caj.interviewcards.data.Recording
import com.caj.interviewcards.data.RecordingStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

class RecordingViewModel(app: Application) : AndroidViewModel(app) {

    private val store = RecordingStore(app)
    private val noteStore = NoteStore(app)

    val recordings: StateFlow<List<Recording>> = store.recordings.let { flow ->
        val s = MutableStateFlow<List<Recording>>(emptyList())
        viewModelScope.launch { flow.collect { s.value = it } }
        s
    }

    /** 分类复用随手记的分类 */
    val categories: StateFlow<List<String>> = noteStore.categories.let { flow ->
        val s = MutableStateFlow<List<String>>(emptyList())
        viewModelScope.launch { flow.collect { s.value = it } }
        s
    }

    /** 随手记（用于「关联笔记」选择） */
    val notes = noteStore.notes.let { flow ->
        val s = MutableStateFlow<List<com.caj.interviewcards.data.Note>>(emptyList())
        viewModelScope.launch { flow.collect { s.value = it } }
        s
    }

    init {
        // 启动时对账，清掉「元信息在、文件没了」的僵尸记录
        viewModelScope.launch { store.reconcile() }
    }

    fun fileOf(id: String): File = store.fileOf(id)

    fun update(rec: Recording) = viewModelScope.launch { store.update(rec) }

    fun delete(id: String) = viewModelScope.launch { store.delete(id) }

    fun clearAll() = viewModelScope.launch { store.clearAll() }

    fun totalSize(): Long = store.totalSize()

    fun countOf(category: String): Int = recordings.value.count { it.category == category }

    // ─── ★ 播放器 ───

    private var player: MediaPlayer? = null
    private var tickJob: Job? = null

    private val _playingId = MutableStateFlow("")
    val playingId: StateFlow<String> = _playingId

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs

    /** 播放 / 暂停切换 */
    fun togglePlay(item: Recording) {
        val p = player
        if (p != null && _playingId.value == item.id) {
            if (p.isPlaying) p.pause() else p.start()
            return
        }
        releasePlayer()
        val f = store.fileOf(item.id)
        if (!f.exists()) return
        val mp = MediaPlayer()
        runCatching {
            mp.setDataSource(f.absolutePath)
            mp.prepare()
            mp.start()
        }.onFailure {
            runCatching { mp.release() }
            return
        }
        mp.setOnCompletionListener {
            _positionMs.value = 0L
            _playingId.value = ""
            tickJob?.cancel()
        }
        player = mp
        _playingId.value = item.id
        _durationMs.value = if (item.durationMs > 0) item.durationMs else mp.duration.toLong()
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (true) {
                player?.let { if (it.isPlaying) _positionMs.value = it.currentPosition.toLong() }
                delay(300)
            }
        }
    }

    /** 跳转到指定位置（拖动进度条 / 快退快进） */
    fun seekTo(ms: Long) {
        player?.let {
            it.seekTo(ms.toInt())
            _positionMs.value = ms
        }
    }

    fun stopPlay() = releasePlayer()

    private fun releasePlayer() {
        tickJob?.cancel()
        tickJob = null
        player?.let {
            runCatching { if (it.isPlaying) it.stop() }
            runCatching { it.release() }
        }
        player = null
        _playingId.value = ""
        _positionMs.value = 0L
    }

    override fun onCleared() {
        releasePlayer()
        super.onCleared()
    }

    companion object {
        /** 时长格式化 mm:ss（超过 1 小时显示 h:mm:ss） */
        fun fmtDuration(ms: Long): String {
            val total = ms / 1000
            val h = total / 3600
            val m = (total % 3600) / 60
            val s = total % 60
            return if (h > 0) String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, s)
            else String.format(java.util.Locale.US, "%02d:%02d", m, s)
        }

        /** 体积格式化 */
        fun fmtSize(bytes: Long): String = when {
            bytes >= 1024L * 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f GB", bytes / 1024.0 / 1024 / 1024)
            bytes >= 1024L * 1024 -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1024.0 / 1024)
            bytes >= 1024 -> String.format(java.util.Locale.US, "%d KB", bytes / 1024)
            else -> "$bytes B"
        }
    }
}
