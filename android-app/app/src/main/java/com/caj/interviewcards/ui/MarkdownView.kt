package com.caj.interviewcards.ui

import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.linkify.LinkifyPlugin

/**
 * Markwon 的 Compose 封装：用 AndroidView 包一个 TextView，
 * 交给 Markwon 做 Markdown 渲染（支持表格 / 删除线 / 链接）。
 */
@Composable
fun MarkdownView(markdown: String, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            val textView = TextView(context).apply {
                setTextIsSelectable(true)
                textSize = 15f
            }
            val markwon = Markwon.builder(context)
                .usePlugin(TablePlugin.create(context))
                .usePlugin(StrikethroughPlugin.create())
                .usePlugin(LinkifyPlugin.create())
                .build()
            markwon.setMarkdown(textView, markdown)
            textView
        },
        update = { textView ->
            val markwon = Markwon.builder(textView.context)
                .usePlugin(TablePlugin.create(textView.context))
                .usePlugin(StrikethroughPlugin.create())
                .usePlugin(LinkifyPlugin.create())
                .build()
            markwon.setMarkdown(textView, markdown)
        }
    )
}
