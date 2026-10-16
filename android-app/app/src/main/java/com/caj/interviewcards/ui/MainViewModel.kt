package com.caj.interviewcards.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.caj.interviewcards.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 当前浏览模式 */
enum class BrowseMode { SEQUENTIAL, RANDOM, UNKNOWN_ONLY, STARRED, DUE }

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val progress = ProgressStore(app)

    private val _books = MutableStateFlow<List<BookInfo>>(emptyList())
    val books: StateFlow<List<BookInfo>> = _books.asStateFlow()

    private val _currentBookId = MutableStateFlow("")
    val currentBookId: StateFlow<String> = _currentBookId.asStateFlow()

    private val _cards = MutableStateFlow<List<Card>>(emptyList())
    val cards: StateFlow<List<Card>> = _cards.asStateFlow()

    /** ★ 全部题目（目录页用，来自轻量版 cards.json） */
    private val _allCards = MutableStateFlow<List<Card>>(emptyList())
    val allCards: StateFlow<List<Card>> = _allCards.asStateFlow()

    private val _index = MutableStateFlow(0)
    val index: StateFlow<Int> = _index.asStateFlow()

    private val _mode = MutableStateFlow(BrowseMode.SEQUENTIAL)
    val mode: StateFlow<BrowseMode> = _mode.asStateFlow()

    private val _dataSource = MutableStateFlow("内置")
    val dataSource: StateFlow<String> = _dataSource.asStateFlow()

    // ★ 因为 DataStore 是 Flow，这里用可变镜像供 UI 读取
    private val _knownSet = MutableStateFlow<Set<String>>(emptySet())
    val knownSet: StateFlow<Set<String>> = _knownSet.asStateFlow()
    private val _unknownSet = MutableStateFlow<Set<String>>(emptySet())
    val unknownSet: StateFlow<Set<String>> = _unknownSet.asStateFlow()

    // ★ 新增：收藏 / 练习计数 / 今日统计 / 累计
    private val _starredSet = MutableStateFlow<Set<String>>(emptySet())
    val starredSet: StateFlow<Set<String>> = _starredSet.asStateFlow()

    private val _practiceMap = MutableStateFlow<Map<String, Int>>(emptyMap())
    val practiceMap: StateFlow<Map<String, Int>> = _practiceMap.asStateFlow()

    private val _todayCount = MutableStateFlow(0)
    val todayCount: StateFlow<Int> = _todayCount.asStateFlow()

    private val _reviewTotal = MutableStateFlow(0)
    val reviewTotal: StateFlow<Int> = _reviewTotal.asStateFlow()

    private var pendingLastCard = ""
    private var pendingLastBook = ""
    private var restoreAttempted = false

    init {
        viewModelScope.launch { progress.known.collect { _knownSet.value = it } }
        viewModelScope.launch { progress.unknown.collect { _unknownSet.value = it } }
        viewModelScope.launch { progress.starred.collect { _starredSet.value = it } }
        viewModelScope.launch { progress.practice.collect { _practiceMap.value = it } }
        viewModelScope.launch { progress.todayCount.collect { _todayCount.value = it } }
        viewModelScope.launch { progress.reviewTotal.collect { _reviewTotal.value = it } }
        viewModelScope.launch {
            // 先读取"上次位置"，等数据加载完再恢复
            pendingLastCard = progress.lastCardId.first()
            pendingLastBook = progress.lastBookId.first()
            loadIndex()
        }
    }

    private fun loadIndex() {
        viewModelScope.launch {
            // ★ 先加载"全部题目"（目录页数据源，轻量版一次读全）
            CardRepository.loadLight(getApplication())?.let { bundle ->
                _allCards.value = bundle.cards
            }
            val idx = CardRepository.loadIndex(getApplication())
            val bookList = if (idx != null && idx.books.isNotEmpty()) {
                _dataSource.value = CardRepository.lastSource
                idx.books
            } else {
                CardRepository.loadLight(getApplication())?.let { bundle ->
                    _dataSource.value = CardRepository.lastSource
                    bundle.books
                } ?: emptyList()
            }
            _books.value = bookList
            if (bookList.isNotEmpty()) {
                // ★ 恢复上次所在分册（没有则用第一个）
                val startBook = if (!restoreAttempted && bookList.any { it.id == pendingLastBook })
                    pendingLastBook else bookList.first().id
                loadBook(startBook, restoreCardId = if (!restoreAttempted) pendingLastCard else "")
                restoreAttempted = true
            }
        }
    }

    fun loadBook(bookId: String, restoreCardId: String = "") {
        viewModelScope.launch {
            var cards = CardRepository.loadBook(getApplication(), bookId)
            if (cards == null) {
                // 分册文件缺失 → 从轻量版过滤
                cards = CardRepository.loadLight(getApplication())
                    ?.cards?.filter { it.book == bookId } ?: emptyList()
            }
            _currentBookId.value = bookId
            _cards.value = cards
            // ★ 若有"续看"目标题，定位到它；否则回到第一张
            val i = if (restoreCardId.isNotEmpty()) cards.indexOfFirst { it.id == restoreCardId } else -1
            _index.value = if (i >= 0) i else 0
        }
    }

    /** 重新加载（用户手动同步后点"刷新"） */
    fun reload() {
        loadIndex()
    }

    fun next() {
        val list = visibleCards()
        if (list.isEmpty()) return
        _index.value = (_index.value + 1) % list.size
        saveLastPosition()
    }

    fun prev() {
        val list = visibleCards()
        if (list.isEmpty()) return
        _index.value = (_index.value - 1 + list.size) % list.size
        saveLastPosition()
    }

    /** 保存"上次复习位置" */
    private fun saveLastPosition() {
        val c = currentCard() ?: return
        viewModelScope.launch { progress.saveLast(c.id, c.book) }
    }

    fun jumpTo(cardId: String) {
        val list = visibleCards()
        val i = list.indexOfFirst { it.id == cardId }
        if (i >= 0) _index.value = i
    }

    /**
     * ★ 从目录页跳转到指定题：
     *   若该题不在当前分册，先切换分册；然后切到顺序模式并定位。
     */
    fun openCard(card: Card) {
        viewModelScope.launch {
            if (card.book != _currentBookId.value) {
                var cards = CardRepository.loadBook(getApplication(), card.book)
                if (cards == null) {
                    cards = CardRepository.loadLight(getApplication())
                        ?.cards?.filter { it.book == card.book } ?: emptyList()
                }
                _currentBookId.value = card.book
                _cards.value = cards
            }
            _mode.value = BrowseMode.SEQUENTIAL
            val list = _cards.value
            val i = list.indexOfFirst { it.id == card.id }
            _index.value = if (i >= 0) i else 0
        }
    }

    fun setMode(m: BrowseMode) {
        _mode.value = m
        _index.value = 0
    }

    /** 随机抽一张（在全部卡片里抽） */
    fun randomAll() {
        val list = _cards.value
        if (list.isEmpty()) return
        _index.value = (0 until list.size).random()
    }

    fun markKnown(cardId: String) = viewModelScope.launch { progress.markKnown(cardId) }
    fun markUnknown(cardId: String) = viewModelScope.launch { progress.markUnknown(cardId) }
    fun clearMark(cardId: String) = viewModelScope.launch { progress.clearMark(cardId) }
    fun resetAll() = viewModelScope.launch { progress.resetAll() }
    fun resetProgressOnly() = viewModelScope.launch { progress.resetProgressOnly() }
    fun clearStars() = viewModelScope.launch { progress.clearStars() }
    fun resetEverything() = viewModelScope.launch { progress.resetEverything() }

    /** ★ 收藏 / 取消收藏 */
    fun toggleStar(cardId: String) = viewModelScope.launch { progress.toggleStar(cardId) }

    /** ★ 间隔重复：不熟的题"再练一次"，连续 3 次答对自动转掌握 */
    fun practiceCorrect(cardId: String) = viewModelScope.launch { progress.practiceCorrect(cardId) }

    /** 某题的连续答对次数 */
    fun practiceCount(cardId: String): Int = _practiceMap.value[cardId] ?: 0

    /** 当前模式下的可见卡片列表 */
    fun visibleCards(): List<Card> {
        val all = _cards.value
        return when (_mode.value) {
            BrowseMode.SEQUENTIAL -> all
            BrowseMode.RANDOM -> all.shuffled()
            BrowseMode.UNKNOWN_ONLY -> all.filter { it.id in _unknownSet.value }
            BrowseMode.STARRED -> all.filter { it.id in _starredSet.value }
            BrowseMode.DUE -> all.filter { it.id in _unknownSet.value }
                .sortedBy { _practiceMap.value[it.id] ?: 0 }   // 练得少的优先
        }
    }

    fun currentCard(): Card? {
        val list = visibleCards()
        val i = _index.value
        return if (i in list.indices) list[i] else null
    }
}
