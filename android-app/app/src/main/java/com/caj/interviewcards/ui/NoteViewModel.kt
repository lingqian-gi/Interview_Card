package com.caj.interviewcards.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.caj.interviewcards.data.Note
import com.caj.interviewcards.data.NoteStore
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class NoteViewModel(app: Application) : AndroidViewModel(app) {

    private val store = NoteStore(app)

    val notes: StateFlow<List<Note>> = store.notes.let { flow ->
        // 转成 StateFlow 供 Compose 使用
        val state = kotlinx.coroutines.flow.MutableStateFlow<List<Note>>(emptyList())
        viewModelScope.launch { flow.collect { state.value = it } }
        state
    }

    fun add(question: String, detail: String, tag: String) {
        if (question.isBlank()) return
        viewModelScope.launch { store.add(question, detail, tag) }
    }

    fun update(note: Note) = viewModelScope.launch { store.update(note) }

    fun delete(id: String) = viewModelScope.launch { store.delete(id) }

    fun clearAll() = viewModelScope.launch { store.clearAll() }

    /** ★ 导出为 Markdown（题库友好格式，可直接喂 AI 加题） */
    fun exportMarkdown(): String {
        val list = notes.value
        if (list.isEmpty()) return "# 待整理笔记\n\n（暂无笔记）\n"
        val df = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        val dateOnly = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val sb = StringBuilder()
        sb.append("# 待整理笔记（").append(dateOnly.format(java.util.Date())).append(" 导出，共 ")
            .append(list.size).append(" 条）\n\n")
        sb.append("> 说明：这是日常随手记的问题/想法，可作为面试题库的补充素材。\n\n")
        sb.append("---\n\n")
        list.sortedBy { it.createdAt }.forEachIndexed { i, n ->
            sb.append("### ").append(i + 1).append(". ")
                .append("[").append(df.format(java.util.Date(n.createdAt))).append("]")
            if (n.tag.isNotBlank()) sb.append(" [标签: ").append(n.tag).append("]")
            sb.append("\n\n")
            sb.append("**问题/想法**：").append(n.question).append("\n\n")
            if (n.detail.isNotBlank()) {
                sb.append("**补充**：").append(n.detail).append("\n\n")
            }
        }
        return sb.toString()
    }

    /** 导出为纯文本（备用） */
    fun exportPlainText(): String {
        val list = notes.value
        if (list.isEmpty()) return "（暂无笔记）"
        val df = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        return list.sortedBy { it.createdAt }.joinToString("\n\n") { n ->
            buildString {
                append("[").append(df.format(java.util.Date(n.createdAt))).append("]")
                if (n.tag.isNotBlank()) append(" (").append(n.tag).append(")")
                append("\n").append(n.question)
                if (n.detail.isNotBlank()) append("\n").append(n.detail)
            }
        }
    }
}
