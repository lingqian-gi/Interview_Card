package com.caj.interviewcards.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.caj.interviewcards.data.Note
import com.caj.interviewcards.data.NoteStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class NoteViewModel(app: Application) : AndroidViewModel(app) {

    private val store = NoteStore(app)

    val notes: StateFlow<List<Note>> = store.notes.let { flow ->
        val state = MutableStateFlow<List<Note>>(emptyList())
        viewModelScope.launch { flow.collect { state.value = it } }
        state
    }

    /** ★ 分类列表（可自定义） */
    val categories: StateFlow<List<String>> = store.categories.let { flow ->
        val state = MutableStateFlow<List<String>>(emptyList())
        viewModelScope.launch { flow.collect { state.value = it } }
        state
    }

    fun add(question: String, detail: String, category: String, tag: String) {
        if (question.isBlank()) return
        viewModelScope.launch { store.add(question, detail, category, tag) }
    }

    fun update(note: Note) = viewModelScope.launch { store.update(note) }

    fun delete(id: String) = viewModelScope.launch { store.delete(id) }

    fun clearAll() = viewModelScope.launch { store.clearAll() }

    // ─── ★ 分类管理 ───

    fun addCategory(name: String) = viewModelScope.launch { store.addCategory(name) }

    fun removeCategory(name: String) = viewModelScope.launch { store.removeCategory(name) }

    fun renameCategory(old: String, new: String) =
        viewModelScope.launch { store.renameCategory(old, new) }

    /** 某分类下的笔记数 */
    fun countOf(category: String): Int =
        notes.value.count { it.category == category }

    /**
     * ★ 导出为 Markdown（题库友好格式，可直接喂 AI 加题）
     * @param filterCategory 非空则只导出该分类；空串 = 全部
     */
    fun exportMarkdown(filterCategory: String = ""): String {
        val all = notes.value
        val list = if (filterCategory.isBlank()) all
        else all.filter { it.category == filterCategory }
        if (list.isEmpty()) {
            return if (filterCategory.isBlank()) "# 待整理笔记\n\n（暂无笔记）\n"
            else "# 待整理笔记 · $filterCategory\n\n（该分类下暂无笔记）\n"
        }
        val df = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        val dateOnly = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val sb = StringBuilder()
        sb.append("# 待整理笔记")
        if (filterCategory.isNotBlank()) sb.append(" · ").append(filterCategory)
        sb.append("（").append(dateOnly.format(java.util.Date())).append(" 导出，共 ")
            .append(list.size).append(" 条）\n\n")
        sb.append("> 说明：这是日常随手记的问题/想法，可作为面试题库的补充素材。\n\n")
        sb.append("---\n\n")
        list.sortedBy { it.createdAt }.forEachIndexed { i, n ->
            sb.append("### ").append(i + 1).append(". ")
                .append("[").append(df.format(java.util.Date(n.createdAt))).append("]")
            if (n.category.isNotBlank()) sb.append(" [分类: ").append(n.category).append("]")
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
    fun exportPlainText(filterCategory: String = ""): String {
        val all = notes.value
        val list = if (filterCategory.isBlank()) all
        else all.filter { it.category == filterCategory }
        if (list.isEmpty()) return "（暂无笔记）"
        val df = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        return list.sortedBy { it.createdAt }.joinToString("\n\n") { n ->
            buildString {
                append("[").append(df.format(java.util.Date(n.createdAt))).append("]")
                if (n.category.isNotBlank()) append(" (").append(n.category).append(")")
                if (n.tag.isNotBlank()) append(" (").append(n.tag).append(")")
                append("\n").append(n.question)
                if (n.detail.isNotBlank()) append("\n").append(n.detail)
            }
        }
    }
}
