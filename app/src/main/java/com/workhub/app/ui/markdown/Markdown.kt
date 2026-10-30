package com.workhub.app.ui.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * 轻量 Markdown 渲染：
 * 标题 / 无序列表 / 有序列表 / 表格 / 引用 / 分隔线 / 代码块 / 行内加粗斜体代码
 */
object Markdown {

    private fun normalize(content: String): String {
        val trimmed = content.trim()
        val wrappedMarkdown = Regex(
            pattern = """(?is)^```\s*(?:markdown|md)\s*\n(.*)\n```$"""
        ).matchEntire(trimmed)
        return wrappedMarkdown?.groupValues?.get(1)?.trim() ?: content
    }

    fun plainText(content: String): String = normalize(content)
        .replace(Regex("""(?m)^```\s*(?:markdown|md)?\s*$"""), "")
        .replace(Regex("""(?m)^#{1,6}\s+"""), "")
        .replace(Regex("""(?m)^[-*+]\s+\[[ xX]\]\s+"""), "")
        .replace(Regex("""(?m)^[-*+]\s+"""), "")
        .replace(Regex("""(?m)^\d+[.)]\s+"""), "")
        .replace(Regex("""\[([^]]+)]\([^)]+\)"""), "$1")
        .replace("**", "")
        .replace("~~", "")
        .replace("`", "")
        .replace("*", "")
        .trim()

    fun preview(content: String, maxLength: Int = 100): String =
        plainText(content).replace(Regex("""\s+"""), " ").take(maxLength)

    @Composable
    fun Render(content: String, modifier: Modifier = Modifier) {
        val lines = normalize(content).lines()
        val code = mutableListOf<String>()
        var inCode = false

        Column(modifier = modifier) {
            var i = 0
            while (i < lines.size) {
                val line = lines[i].trimEnd()
                val trimmed = line.trimStart()

                when {
                    // 代码块
                    trimmed.startsWith("```") -> {
                        if (inCode) {
                            CodeBlock(code)
                            code.clear()
                            inCode = false
                        } else {
                            inCode = true
                        }
                        i++
                    }
                    inCode -> {
                        code += line
                        i++
                    }
                    // 表格（以 | 开头，且含 | 分隔）
                    trimmed.startsWith("|") -> {
                        val tableLines = mutableListOf<String>()
                        while (i < lines.size && lines[i].trim().contains("|")) {
                            tableLines += lines[i].trim()
                            i++
                        }
                        Table(tableLines)
                    }
                    trimmed.startsWith("#### ") -> {
                        Heading(trimmed.removePrefix("#### ").trim(), MaterialTheme.typography.titleSmall)
                        i++
                    }
                    trimmed.startsWith("### ") -> {
                        Heading(trimmed.removePrefix("### ").trim(), MaterialTheme.typography.titleSmall)
                        i++
                    }
                    trimmed.startsWith("## ") -> {
                        Heading(trimmed.removePrefix("## ").trim(), MaterialTheme.typography.titleMedium)
                        i++
                    }
                    trimmed.startsWith("# ") -> {
                        Heading(trimmed.removePrefix("# ").trim(), MaterialTheme.typography.titleLarge)
                        i++
                    }
                    // 有序列表 1. 2. 3)
                    Regex("""^\d+[.)]\s""").containsMatchIn(trimmed) -> {
                        val text = trimmed.substringAfter(' ').trim().ifBlank { trimmed }
                        Numbered(trimmed.substringBefore(' ').trimEnd('.', ')'), text)
                        i++
                    }
                    Regex("""^[-*+]\s+\[[ xX]\]\s+""").containsMatchIn(trimmed) -> {
                        val checked = trimmed.contains(Regex("""\[[xX]\]"""))
                        val text = trimmed.replaceFirst(Regex("""^[-*+]\s+\[[ xX]\]\s+"""), "")
                        TaskItem(text, checked)
                        i++
                    }
                    Regex("""^[-*+]\s+""").containsMatchIn(trimmed) -> {
                        Bullet(trimmed.replaceFirst(Regex("""^[-*+]\s+"""), ""))
                        i++
                    }
                    Regex("""^[•‣◦]\s*""").containsMatchIn(trimmed) -> {
                        Bullet(trimmed.replaceFirst(Regex("""^[•‣◦]\s*"""), ""))
                        i++
                    }
                    trimmed.startsWith("> ") -> {
                        Quote(trimmed.removePrefix("> ").trim())
                        i++
                    }
                    // 分隔线 --- *** ___
                    trimmed.matches(Regex("""[-*_]{3,}""")) -> {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                        i++
                    }
                    line.isBlank() -> {
                        Spacer(Modifier.height(4.dp))
                        i++
                    }
                    else -> {
                        Paragraph(line)
                        i++
                    }
                }
            }
            if (inCode) CodeBlock(code)
        }
    }

    @Composable
    private fun Heading(text: String, style: androidx.compose.ui.text.TextStyle) {
        Text(
            inline(text),
            style = style,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp, bottom = 3.dp)
        )
    }

    @Composable
    private fun Paragraph(text: String) {
        Text(
            inline(text),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = 1.dp)
        )
    }

    @Composable
    private fun Bullet(text: String) {
        Row(Modifier.padding(vertical = 1.dp)) {
            Text("•  ", style = MaterialTheme.typography.bodyMedium)
            Text(
                inline(text),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
        }
    }

    @Composable
    private fun Numbered(number: String, text: String) {
        Row(Modifier.padding(vertical = 1.dp)) {
            Text(
                "$number.  ",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                inline(text),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
        }
    }

    @Composable
    private fun TaskItem(text: String, checked: Boolean) {
        Row(Modifier.padding(vertical = 1.dp)) {
            Text(
                if (checked) "☑  " else "☐  ",
                style = MaterialTheme.typography.bodyMedium,
                color = if (checked) MaterialTheme.colorScheme.tertiary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                inline(text),
                style = MaterialTheme.typography.bodyMedium,
                textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None,
                color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }
    }

    @Composable
    private fun Quote(text: String) {
        Text(
            inline(text),
            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                .padding(10.dp)
        )
    }

    @Composable
    private fun CodeBlock(lines: List<String>) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            lines.forEach { line ->
                Text(
                    line,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
        Spacer(Modifier.height(6.dp))
    }

    // ---------- 表格 ----------

    @Composable
    private fun Table(lines: List<String>) {
        val rows = lines.map { parseRow(it) }
        if (rows.isEmpty()) return
        val colCount = rows.maxOf { it.size }.coerceAtLeast(1)

        // 判断是否有表头：第二行是分隔行（--- / :---:）
        val hasHeader = rows.size >= 2 && isSeparatorRow(rows[1])
        val header = if (hasHeader) rows[0].normalize(colCount) else null
        val dataStart = if (hasHeader) 2 else 0
        val dataRows = rows.drop(dataStart).filter { !isSeparatorRow(it) }

        Column(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                .padding(horizontal = 0.dp)
        ) {
            header?.let {
                Row(Modifier.background(MaterialTheme.colorScheme.primaryContainer)) {
                    it.forEach { cell -> TableCell(cell, isHeader = true) }
                }
            }
            dataRows.forEachIndexed { idx, row ->
                val bg = if (idx % 2 == 0) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                else Color.Transparent
                Row(Modifier.background(bg)) {
                    row.normalize(colCount).forEach { cell -> TableCell(cell, isHeader = false) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    @Composable
    private fun androidx.compose.foundation.layout.RowScope.TableCell(
        text: String,
        isHeader: Boolean
    ) {
        Text(
            inline(text),
            style = if (isHeader) MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
            else MaterialTheme.typography.bodySmall,
            color = if (isHeader) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .width(110.dp)
                .padding(horizontal = 10.dp, vertical = 8.dp)
        )
    }

    private fun parseRow(line: String): List<String> {
        var s = line.trim()
        if (s.startsWith("|")) s = s.drop(1)
        if (s.endsWith("|")) s = s.dropLast(1)
        return s.split("|").map { it.trim() }
    }

    private fun isSeparatorRow(cells: List<String>): Boolean =
        cells.isNotEmpty() && cells.all { it.matches(Regex("""^:?-{3,}:?$""")) }

    private fun List<String>.normalize(n: Int): List<String> =
        if (size >= n) subList(0, n) else this + List(n - size) { "" }

    /** 行内格式：**加粗** *斜体* `代码` ~~删除线~~ [链接](url) */
    private fun inline(text: String): AnnotatedString {
        val regex = Regex("""(\*\*[^*]+\*\*|\*[^*]+\*|`[^`]+`|~~[^~]+~~|\[[^]]+\]\([^)]+\))""")
        return buildAnnotatedString {
            var last = 0
            for (m in regex.findAll(text)) {
                append(text.substring(last, m.range.first))
                val token = m.value
                when {
                    token.startsWith("**") ->
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(token.removeSurrounding("**"))
                        }
                    token.startsWith("`") ->
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = Color(0x22000000)
                            )
                        ) {
                            append(token.removeSurrounding("`"))
                        }
                    token.startsWith("~~") ->
                        withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                            append(token.removeSurrounding("~~"))
                        }
                    token.startsWith("[") -> {
                        val label = token.substringAfter('[').substringBefore(']')
                        withStyle(
                            SpanStyle(
                                textDecoration = TextDecoration.Underline
                            )
                        ) {
                            append(label)
                        }
                    }
                    else ->
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(token.removeSurrounding("*"))
                        }
                }
                last = m.range.last + 1
            }
            append(text.substring(last))
        }
    }
}
