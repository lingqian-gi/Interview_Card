package com.caj.interviewcards.data

import android.content.Context
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 数据加载器 —— 方案 3：外部目录优先，内置 assets 兜底。
 *
 * 数据来源优先级：
 *   ① 外部目录：<externalRoot>/cards/  （用户 git pull 到这里 → 手动同步）
 *   ② 内置 assets：assets/cards/      （App 装机即用的兜底）
 *
 * externalRoot 默认为 /sdcard/interview-qa（环境变量可在设置里改）
 */
object CardRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /** 外部数据根目录（用户可覆盖） */
    var externalRoot: File = File("/sdcard/interview-qa")

    /** 记录上一次加载来自哪里（UI 上显示"数据来源"） */
    var lastSource: String = "内置"
        private set

    /** 是否有可用外部数据 */
    fun hasExternalData(): Boolean = externalCardFile("index.json").exists()

    private fun externalCardFile(name: String) = File(externalRoot, "cards/$name")

    // ─── 内部：读文件（外部优先 → assets 兜底）───
    private fun readAsset(context: Context, name: String): String? = try {
        context.assets.open("cards/$name").bufferedReader().use { it.readText() }
    } catch (e: Exception) {
        null
    }

    private fun readText(context: Context, name: String): Pair<String, String>? {
        // ① 外部目录优先
        val ext = externalCardFile(name)
        if (ext.exists()) {
            return try {
                lastSource = "外部目录"
                ext.readText() to "外部目录"
            } catch (e: Exception) {
                null
            }
        }
        // ② 内置 assets 兜底
        val asset = readAsset(context, name) ?: return null
        return asset to "内置"
    }

    /** 读索引（index.json），拿分册列表 */
    fun loadIndex(context: Context): CardsIndex? {
        val (text, source) = readText(context, "index.json") ?: return null
        lastSource = source
        return try {
            json.decodeFromString<CardsIndex>(text)
        } catch (e: Exception) {
            null
        }
    }

    /** 读某一分册（含全文），返回卡片列表 */
    fun loadBook(context: Context, bookId: String): List<Card>? {
        val (text, source) = readText(context, "$bookId.json") ?: return null
        lastSource = source
        return try {
            json.decodeFromString<BookBundle>(text).cards
        } catch (e: Exception) {
            null
        }
    }

    /** 读轻量版（cards.json，全部卡片不含全文）——作为分册文件缺失时的兜底 */
    fun loadLight(context: Context): CardBundle? {
        val (text, source) = readText(context, "cards.json") ?: return null
        lastSource = source
        return try {
            json.decodeFromString<CardBundle>(text)
        } catch (e: Exception) {
            null
        }
    }
}
