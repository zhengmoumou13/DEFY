package com.workhub.app.ui.ai

/** A display projection only. The saved plan, action keys and task extraction stay unchanged. */
internal data class FitnessPlanSection(val title: String, val content: String)

internal data class FitnessPlanPresentation(
    val goal: String?,
    val duration: String?,
    val intensity: String?,
    val sections: List<FitnessPlanSection>
)

internal data class FitnessExercisePresentation(
    val name: String,
    val dose: String?,
    val intensity: String?,
    val rest: String?,
    val details: List<String>
)

private fun displayText(text: String): String = text
    .replace("**", "").replace("__", "").replace("`", "")
    .replace(Regex("^\\s*(?:[-*+•]|\\d+[.)、])\\s*"), "")
    .trim()

private val sectionNames = setOf(
    "今日判断", "热身", "主训练", "训练总量", "跳跃总量", "起跳技术", "技术重点",
    "放松与恢复", "恢复任务", "停止条件", "完成标准"
)
private val effortPattern = Regex("RPE\\s*\\d+(?:\\s*[-–—~～至]\\s*\\d+)?", RegexOption.IGNORE_CASE)
private val durationPattern = Regex("(?:约\\s*)?\\d+(?:\\s*[-–—~～至]\\s*\\d+)?\\s*分钟")

internal fun fitnessPlanPresentation(content: String): FitnessPlanPresentation {
    val sections = mutableListOf<FitnessPlanSection>()
    var title = "计划依据"
    val body = mutableListOf<String>()
    var fence = false
    fun flush() {
        if (body.any { it.isNotBlank() }) sections += FitnessPlanSection(title, body.joinToString("\n").trim())
        body.clear()
    }
    // Markdown-wrapped model responses are still readable; inner code blocks are kept verbatim.
    val source = Regex("(?s)^```(?:markdown|md)\\s*\\n(.*)\\n```$")
        .matchEntire(content.trim())?.groupValues?.get(1) ?: content
    source.lineSequence().forEach { raw ->
        val line = raw.trim()
        if (line.startsWith("```") || line.startsWith("~~~")) fence = !fence
        val plain = displayText(line.trimStart('#').trim())
        val label = plain.substringBefore('：').substringBefore(':').trim().trimEnd('：', ':')
        val normalizedTitle = label.replace(Regex("\\s*[（(].*[）)]$"), "").trim()
        val isHeading = !fence && (line.startsWith('#') ||
            (line.startsWith("**") && line.endsWith("**") && !plain.contains('：') && !plain.contains(':')) ||
            normalizedTitle in sectionNames)
        if (isHeading) {
            flush()
            title = normalizedTitle
            label.removePrefix(normalizedTitle).trim().takeIf { it.isNotEmpty() }?.let { body += it }
            if (label != plain) body += plain.drop(label.length).trimStart('：', ':', ' ')
        } else {
            body += raw
        }
    }
    flush()
    // Read metrics only from labelled summary fields, never from an exercise's rest or dosage.
    val fields = source.lineSequence().map { displayText(it) }.mapNotNull { line ->
        val split = line.indexOfFirst { it == '：' || it == ':' }
        if (split > 0) line.take(split).trim() to line.drop(split + 1).trim() else null
    }.toList()
    fun value(vararg names: String) = fields.firstOrNull { it.first in names }?.second
    val goal = value("今日主目标", "主目标", "目标", "主题")
    val time = value("总时长", "时长", "预计时长", "强度与时长")
    val effort = value("整体强度", "强度", "强度与时长")
    return FitnessPlanPresentation(
        goal = goal?.substringBefore('（')?.substringBefore('(')?.trim()?.takeIf { it.isNotEmpty() },
        duration = time?.let { durationPattern.find(it)?.value },
        intensity = effort?.let { effortPattern.find(it)?.value },
        sections = sections
    )
}

internal fun fitnessExercisePresentation(item: PlanCheckItem): FitnessExercisePresentation {
    val cells = displayText(item.text).trim('|', '｜').split(Regex("[|｜]")).map { it.trim() }.filter { it.isNotEmpty() }
    val first = cells.firstOrNull().orEmpty()
    var name = first
    val details = mutableListOf<String>()
    // Keep equipment/height limits in the expandable detail, without shortening the stored action.
    val nameAndVariant = Regex("^(.+?)([（(].*[）)])$").matchEntire(first)
    if (nameAndVariant != null) {
        name = nameAndVariant.groupValues[1].trim()
        details += "动作要求：${nameAndVariant.groupValues[2].drop(1).dropLast(1)}"
    }
    var dose: String? = null
    var intensity: String? = null
    var rest: String? = null
    val dosePattern = Regex("\\d+(?:[-–—~～]\\d+)?\\s*(?:组)?\\s*[×xX*]\\s*\\d+(?:[-–—~～]\\d+)?\\s*(?:次|秒|分钟|米)?(?:\\s*[/／]\\s*(?:侧|腿|手))?")
    cells.drop(1).forEach { cell ->
        when {
            dose == null && dosePattern.matches(cell) -> dose = cell.replace(Regex("\\s*[×xX*]\\s*"), " × ")
            intensity == null && effortPattern.matches(cell) -> intensity = cell
            rest == null && Regex("^(?:组间(?:休息)?|休息)\\s*[:：]?\\s*\\d+(?:[-–—~～]\\d+)?\\s*(?:秒|分钟|min|s)$", RegexOption.IGNORE_CASE).matches(cell) -> rest = cell
            else -> details += cell
        }
    }
    // Unstructured legacy actions remain intact as their title; do not guess at missing units.
    details += item.notes
    return FitnessExercisePresentation(name, dose, intensity, rest, details.distinct())
}
