package com.workhub.app.ui.ai

internal val mealLabels = setOf("早餐", "午餐", "晚餐", "加餐", "训练前", "训练后", "训练前后", "加餐与饮水", "饮水", "补水")
internal data class DietMealPresentation(val label: String, val foods: List<String>, val details: List<String>, val item: PlanCheckItem?)
internal data class DietPlanPresentation(val targets: List<Pair<String, String>>, val meals: List<DietMealPresentation>)

private fun dietPlain(text: String) = text.replace("**", "").replace("__", "").replace("`", "")
    .trim().trimStart('#').trim().replace(Regex("^(?:[-*+•]|\\d+[.)、])\\s*"), "").trim()
private val nutritionField = Regex("^(?:约?热量|能量|约?蛋白质|碳水(?:化合物)?|脂肪|营养(?:估算)?|合计|总计|说明|备注|替换|可替换|替代|建议|备餐|注意|约?\\s*\\d+(?:[-–～~]\\d+)?\\s*(?:kcal|千卡))", RegexOption.IGNORE_CASE)

/** Split only at top-level delimiters: commas and alternatives inside ingredient names stay intact. */
internal fun dietParts(value: String, foodSeparators: Boolean = false): List<String> {
    val parts = mutableListOf<String>()
    val part = StringBuilder()
    var depth = 0
    value.forEach { c ->
        if (c == '(' || c == '（' || c == '[') depth++
        if (c == ')' || c == '）' || c == ']') depth = (depth - 1).coerceAtLeast(0)
        if (depth == 0 && (c in "；;|｜\n" || foodSeparators && c in "+＋、")) {
            if (part.isNotBlank()) parts += part.toString().trim()
            part.clear()
        } else part.append(c)
    }
    if (part.isNotBlank()) parts += part.toString().trim()
    return parts
}

internal fun dietMealPresentation(label: String, body: String, item: PlanCheckItem?): DietMealPresentation {
    val foods = mutableListOf<String>()
    val details = mutableListOf<String>()
    val separated = body.replace(Regex("[，,]\\s*(?=(?:约?热量|能量|约?蛋白质|碳水|脂肪|营养|替换|可替换|说明|约?\\s*\\d+\\s*(?:kcal|千卡)))", RegexOption.IGNORE_CASE), "；")
    dietParts(separated).forEach { piece ->
        val text = dietPlain(piece)
        if (text.isNotBlank()) {
            if (nutritionField.containsMatchIn(text)) details += text
            else foods += dietParts(text, foodSeparators = true)
        }
    }
    return DietMealPresentation(label, foods, details, item)
}

internal fun dietPlanPresentation(content: String, items: List<PlanCheckItem>): DietPlanPresentation {
    val bodies = linkedMapOf<String, MutableList<String>>()
    val targets = linkedMapOf<String, String>()
    val targetNames = mapOf("热量" to "热量", "总热量" to "热量", "热量目标" to "热量", "蛋白质" to "蛋白质", "蛋白质目标" to "蛋白质", "碳水" to "碳水", "碳水化合物" to "碳水", "碳水目标" to "碳水", "脂肪" to "脂肪", "脂肪目标" to "脂肪", "饮水目标" to "饮水")
    var active: String? = null
    var section = ""
    var fence = false
    unwrapPlanMarkdown(content).lineSequence().forEach { raw ->
        val line = raw.trim()
        if (line.startsWith("```") || line.startsWith("~~~")) { fence = !fence; return@forEach }
        if (fence || line.isBlank()) return@forEach
        val text = dietPlain(line)
        val colon = text.indexOfFirst { it == ':' || it == '：' }
        val label = (if (colon >= 0) text.take(colon) else text).trim()
        val mealLabel = label.replace(Regex("[（(].*[）)]$"), "").trim()
        val heading = line.startsWith('#') || line.startsWith("**") && line.endsWith("**") && colon < 0
        when {
            heading && mealLabel in mealLabels -> {
                active = mealLabel
                bodies.getOrPut(mealLabel) { mutableListOf() }
                if (label != mealLabel) bodies.getValue(mealLabel) += label.removePrefix(mealLabel)
            }
            heading -> { section = text; active = null }
            colon >= 0 && mealLabel in mealLabels -> {
                active = mealLabel
                val qualifier = label.removePrefix(mealLabel)
                bodies.getOrPut(mealLabel) { mutableListOf() }.add(qualifier + text.drop(colon + 1).trim())
            }
            line.startsWith('|') -> {
                val cells = text.trim('|').split('|').map { it.trim() }
                if (cells.firstOrNull() in mealLabels) {
                    active = cells.first()
                    bodies.getOrPut(cells.first()) { mutableListOf() }.add(cells.drop(1).joinToString("｜"))
                }
            }
            colon >= 0 && label in setOf("今日执行重点", "当日类型", "执行提醒", "采用假设", "营养原则") -> active = null
            active != null -> bodies.getValue(active!!).add(text)
            colon >= 0 && (section.contains("营养") || section.isBlank()) -> targetNames[label]?.let { targets[it] = text.drop(colon + 1).trim() }
        }
    }
    // The existing action parser remains the authority for checkboxes/task conversion and stable keys.
    items.forEach { item ->
        val label = item.text.substringBefore('：').substringBefore(':')
        if (label in mealLabels && label !in bodies) bodies[label] = mutableListOf(item.text.drop(label.length + 1))
    }
    val meals = bodies.map { (label, values) ->
        val item = items.firstOrNull { it.text.substringBefore('：').substringBefore(':') == label }
        dietMealPresentation(label, values.joinToString("\n"), item)
    }
    return DietPlanPresentation(targets.toList(), meals)
}
