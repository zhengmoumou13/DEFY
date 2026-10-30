package com.workhub.app.training

import com.workhub.app.data.BallTrainingRecordEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

@Serializable
data class BallPlan(
    val title: String,
    val focus: String,
    val level: String,
    val partner: String,
    val minutes: Int,
    val assessment: String,
    val warmup: List<String>,
    val exercises: List<BallPlanExercise>,
    val cooldown: List<String>,
    val stopRules: String
)

@Serializable
data class BallPlanExercise(
    val movementKey: String,
    val position: String,
    val sets: Int,
    val shotsPerSet: Int = 0,
    val workSeconds: Int = 0,
    val restSeconds: Int,
    val steps: List<String>,
    val cues: String,
    val progression: String
)

object BallPlanCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(plan: BallPlan): String = json.encodeToString(BallPlan.serializer(), plan)

    fun decode(content: String): BallPlan {
        val trimmed = content.trim().removePrefix("\uFEFF")
        val source = if (trimmed.startsWith("```")) trimmed.substringAfter('\n').substringBeforeLast("```").trim() else trimmed
        val plan = try {
            json.decodeFromString(BallPlan.serializer(), source)
        } catch (_: Exception) {
            throw IllegalArgumentException("计划格式不完整，请重新生成")
        }
        requireValidBallPlan(plan)
        return plan
    }
}

fun requireValidBallPlan(plan: BallPlan) {
    require(plan.title.isNotBlank() && plan.title.length <= 100) { "计划缺少有效标题" }
    require(plan.focus == "balanced" || BallDrill.fromKey(plan.focus) != null) { "计划训练方向无效" }
    require(plan.level in listOf("入门", "有基础", "进阶")) { "计划水平无效" }
    require(plan.partner in listOf("单人训练", "有陪练")) { "计划训练条件无效" }
    require(plan.minutes in listOf(30, 45, 60)) { "计划时长无效" }
    require(plan.exercises.size in 3..6) { "主训练需包含 3–6 个动作" }
    require(plan.assessment.isNotBlank() && plan.stopRules.isNotBlank()) { "计划缺少评估或停止条件" }
    require(plan.warmup.size in 1..6 && plan.warmup.all { it.isNotBlank() }) { "计划缺少热身" }
    require(plan.cooldown.size in 1..6 && plan.cooldown.all { it.isNotBlank() }) { "计划缺少放松安排" }
    require(plan.exercises.distinctBy { it.movementKey to it.position }.size == plan.exercises.size) { "计划包含重复动作与点位" }
    plan.exercises.forEach { exercise ->
        val move = BallMoves.find(exercise.movementKey) ?: throw IllegalArgumentException("计划包含无法识别的动作")
        val drill = requireNotNull(BallDrill.fromKey(move.category))
        require(exercise.position in drill.positions) { "动作与点位不匹配" }
        require(exercise.sets in 1..6 && exercise.restSeconds in 20..180) { "计划组数或休息时间不合理" }
        if (drill.tracksShots) {
            require(exercise.shotsPerSet in 1..20 && exercise.workSeconds == 0) { "投篮动作需明确每组出手数" }
        } else {
            require(exercise.workSeconds in 15..120 && exercise.shotsPerSet == 0) { "运控动作需按秒安排时长" }
        }
        require(exercise.steps.size in 2..6 && exercise.steps.all { it.isNotBlank() }) { "动作缺少详细执行步骤" }
        require(exercise.cues.isNotBlank() && exercise.progression.isNotBlank()) { "动作缺少要领或进阶条件" }
        require(plan.level != "入门" || move.key != "three_stepback") { "入门计划不应安排后撤步三分" }
    }
    if (plan.focus != "balanced") {
        require(plan.exercises.count { BallMoves.find(it.movementKey)?.category == plan.focus } >= (plan.exercises.size + 1) / 2) {
            "计划未围绕所选训练方向安排"
        }
    }
    if (plan.focus in listOf("balanced", "mid_range", "three_point")) {
        require(plan.exercises.filter {
            val move = BallMoves.find(it.movementKey)
            move?.dynamic == true && move.category in listOf("mid_range", "three_point")
        }.distinctBy { it.movementKey }.size >= 2) { "计划需包含至少两个不同的结合球投篮动作" }
    }
    val shots = plan.exercises.sumOf { it.sets * it.shotsPerSet }
    require(shots <= minOf(250, plan.minutes * 5)) { "计划出手总量超出本次训练范围" }
    val minimumSeconds = 8 * 60 + plan.exercises.sumOf {
        it.sets * (if (it.shotsPerSet > 0) it.shotsPerSet * (if (plan.partner == "单人训练") 12 else 6) else it.workSeconds) + (it.sets - 1) * it.restSeconds
    }
    require(minimumSeconds <= plan.minutes * 60) { "动作与休息总量超出所选时长" }
}

fun BallPlanExercise.prescription(): String = if (shotsPerSet > 0) {
    "$sets 组 × $shotsPerSet 次出手 · 组间休息 $restSeconds 秒"
} else {
    "$sets 组 × $workSeconds 秒 · 组间休息 $restSeconds 秒"
}

fun ballPlanProgress(planId: Long, itemId: String, exercise: BallPlanExercise, records: List<BallTrainingRecordEntity>, date: String = LocalDate.now().toString()): String {
    val linked = records.filter { it.planId == planId && it.planItemId == itemId && it.date == date }
    val summary = summarizeBallTraining(linked)
    return if (exercise.shotsPerSet > 0) {
        "当日已保存 ${summary.attempts}/${exercise.sets * exercise.shotsPerSet} 次出手 · 命中率 ${summary.accuracy}"
    } else {
        "当日已保存 ${summary.durationLabel} · 计划 ${formatBallDuration(exercise.sets * exercise.workSeconds.toLong())}"
    }
}
