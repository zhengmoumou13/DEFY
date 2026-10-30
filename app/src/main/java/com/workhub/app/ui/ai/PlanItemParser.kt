package com.workhub.app.ui.ai

// Kept independent of Compose so generated-plan parsing can be regression tested.
data class PlanCheckItem(val key: String, val text: String, val notes: List<String> = emptyList())

internal fun unwrapPlanMarkdown(content: String): String =
    Regex("(?is)^```(?:markdown|md)\\s*\\n(.*)\\n```$").matchEntire(content.trim())?.groupValues?.get(1) ?: content

private val meals = setOf("早餐", "午餐", "晚餐", "加餐", "训练前", "训练后", "训练前后", "加餐与饮水", "饮水", "补水")
private val metadata = Regex("^(?:训练目标|训练目的|总计|合计|预计|要点|组数|次数|重量|落地要求|训练量|今日主目标|主目标|目标|主题|说明|备注|注意|提示|技术|动作要点|完成标准|停止条件|强度|总时长|时长|组间休息|休息|RPE|跳跃总量|总跳跃|总高质量|总触地|热量|约热量|总热量|蛋白质|碳水|脂肪|营养|替换|可替换|替代|原则|假设|建议|今日执行重点|当日类型)", RegexOption.IGNORE_CASE)
private val optional = Regex("^(?:无(?:需|训练|安排|加餐)?(?:[，。；、\\s]|$)|不需要|无需|休息日|可选|按需|如(?:果|需)|若|建议|注意|避免|不要|确保)")
// A per-set/side count describes the preceding exercise, not another exercise.
// Keep named unilateral exercises (e.g. 每侧单腿提踵) and 触地跳 valid.
private val fitnessDetail = Regex(
    "^(?:每(?:组|轮|次|项|个动作)|单组|(?:每侧|单侧)(?:各)?\\s*(?=[0-9一二三四五六七八九十])|(?:左右|两侧|双侧)(?:各|合计|共计)|触地(?:[：:]|次数|总量|时间|\\s*(?=[0-9]))|落地(?:次数|要求|时|后)|动作(?:提示|要点|节奏)|技术提示|发力要点|顶峰保持)"
)

private val dose = Regex("(?:\\d+(?:[.～~—–-]\\d+)?\\s*(?:组|次|秒|分钟|分|米|m\\b|s\\b)|\\d+\\s*[×xX*]\\s*\\d+)")
private val food = Regex("鸡|鸭|鱼|肉|虾|蛋|奶|酸奶|豆|米饭|杂粮|燕麦|面包|面条|馒头|土豆|红薯|蔬菜|青菜|西兰花|水果|香蕉|苹果|坚果|粥|沙拉|全麦|玉米|饮水|喝水|补水|\\d+\\s*(?:克|g\\b|ml\\b|毫升|杯|碗|份|个|片|掌|拳)", RegexOption.IGNORE_CASE)
private val bullet = Regex("^(?:[-*+•]|\\d+[.)、]|[一二三四五六七八九十]+[、.])\\s*")

private fun cleanPlanText(value: String): String = value
    .replace(Regex("\\[([^]]+)]\\([^)]*\\)"), "$1")
    .replace("**", "").replace("__", "").replace("`", "")
    .replace(Regex("^\\[[ xX]]\\s*"), "").trim()

private fun field(text: String): Pair<String, String>? {
    val index = text.indexOfFirst { it == '：' || it == ':' }
    return if (index > 0) text.substring(0, index).trim() to text.substring(index + 1).trim() else null
}

private fun fitnessAction(text: String): Boolean {
    val name = text.substringBefore('｜').substringBefore('|').substringBefore('：').substringBefore(':').trim()
    return name.isNotBlank() && !fitnessDetail.containsMatchIn(name) && !metadata.containsMatchIn(name) && !optional.containsMatchIn(name) &&
        !name.contains("总量") && !name.contains("总次数") &&
        !name.startsWith("每项") && !name.startsWith("每个动作") && !name.startsWith("只安排") &&
        !name.startsWith("动作｜") && dose.containsMatchIn(text) &&
        // A quantity or RPE on its own is a prescription, not an exercise.
        !Regex("^[\\d\\s×xX*.,～~—–-]+(?:组|次|秒|分钟|分|米)").containsMatchIn(name)
}

/** Conservative legacy-Markdown reader: a section is context, never proof of an action. */
fun parsePlanItems(content: String, type: String = ""): List<PlanCheckItem> {
    val actions = mutableListOf<String>()
    val notes = linkedMapOf<String, MutableList<String>>()
    var lastFitnessAction: String? = null
    val mealActions = linkedMapOf<String, MutableList<String>>()
    var section = ""
    var active: String? = null
    var fence: String? = null

    fun addFitness(text: String) {
        text.split(Regex("[；;]"))
            .map { it.trim().trimEnd('。') }
            .filter { it.isNotEmpty() }
            .forEach { part ->
                when {
                    fitnessDetail.containsMatchIn(part) -> lastFitnessAction?.let { parent ->
                        notes.getOrPut(parent) { mutableListOf() }.add(part)
                    }
                    fitnessAction(part) -> {
                        actions.add(part)
                        lastFitnessAction = part
                    }
                }
            }
    }
    fun addMeal(label: String, value: String) {
        val text = value.trim().trimEnd('。', '；', ';')
        if (text.isNotEmpty() && !metadata.containsMatchIn(text) && !optional.containsMatchIn(text) && food.containsMatchIn(text)) {
            mealActions.getOrPut(label) { mutableListOf() }.add(text)
        }
    }

    unwrapPlanMarkdown(content).lineSequence().forEach { raw ->
        val line = raw.trim()
        if (line.startsWith("```") || line.startsWith("~~~")) {
            val marker = line.take(3)
            if (fence == null) fence = marker else if (fence == marker) fence = null
            active = null
            lastFitnessAction = null
            return@forEach
        }
        if (fence != null) return@forEach
        if (line.isBlank()) { active = null; return@forEach }
        val heading = line.startsWith('#')
        val isList = bullet.containsMatchIn(line)
        var text = cleanPlanText(line.trimStart('#').trim().replace(bullet, ""))
        if (heading || (line.startsWith("**") && line.endsWith("**") && !text.contains('：') && !text.contains(':'))) {
            lastFitnessAction = null
            section = text.replace(Regex("[（(].*[）)]$"), "").trim()
            if (type == "diet" && section in meals && Regex("[（(].*(?:可选|按需|如需).*[）)]").containsMatchIn(text)) section = ""
            active = if (section == "主训练" || section in meals) section else null
            return@forEach
        }
        if (line.startsWith('|')) {
            val cells = text.trim('|').split('|').map { it.trim() }
            if (cells.all { it.matches(Regex("[:\\s-]*")) }) return@forEach
            text = cells.joinToString("｜")
            if (type == "diet" && cells.firstOrNull() in meals) {
                addMeal(cells.first(), cells.drop(1).joinToString("｜"))
                return@forEach
            }
        }
        val pair = field(text)
        when (type) {
            "fitness" -> {
                if (pair?.first == "主训练" || text == "主训练") {
                    active = "主训练"
                    lastFitnessAction = null
                    pair?.second?.let(::addFitness)
                } else if ((section == "主训练" || active == "主训练") && fitnessDetail.containsMatchIn(text)) {
                    addFitness(text)
                } else if (metadata.containsMatchIn(text) || pair?.first in setOf("热身", "起跳技术", "恢复任务", "放松与恢复")) {
                    active = null
                    lastFitnessAction = null
                } else if ((section == "主训练" && (isList || line.startsWith('|'))) || active == "主训练") {
                    addFitness(text)
                }
            }
            "diet" -> {
                if (pair != null && pair.first in meals) {
                    active = pair.first
                    addMeal(pair.first, pair.second)
                } else if (metadata.containsMatchIn(text) || pair != null) {
                    active = null
                } else {
                    val label = active ?: section.takeIf { it in meals }
                    if (label != null && (isList || active != null)) addMeal(label, text)
                }
            }
            else -> if (isList && text.length >= 4) actions.add(text)
        }
    }
    if (type == "diet") mealActions.forEach { (label, values) -> actions.add("$label：${values.distinct().joinToString("；")}") }
    return actions.distinct().map { PlanCheckItem(it.hashCode().toString(), it, notes[it].orEmpty().distinct()) }
}
