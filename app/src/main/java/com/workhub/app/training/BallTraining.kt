package com.workhub.app.training

import com.workhub.app.data.BallTrainingRecordEntity
import java.time.LocalDate
import java.util.Locale

enum class BallDrill(
    val key: String,
    val label: String,
    val subtitle: String,
    val positions: List<String>,
    val suggestion: String,
    val cues: String,
    val tracksShots: Boolean = true
) {
    MID_RANGE(
        "mid_range", "中投", "定点、接球与急停跳投",
        listOf("左底角", "左45度", "罚球线", "右45度", "右底角", "多点综合"),
        "从定点建立手型，再练移动接球、运一步急停和变向投篮。",
        "双脚落稳后出手，保持相同的抬球路径；急停投篮先降低速度，注意落地平衡。"
    ),
    THREE_POINT(
        "three_point", "三分", "接球、跑位与运球投射",
        listOf("左底角", "左侧翼", "弧顶", "右侧翼", "右底角", "多点综合"),
        "从接球出手到转移跑位，按能力逐步加入运球急停。",
        "先从近距离找手感，再后退到三分线；腿部发力与出手连贯，动作变形时缩短距离。"
    ),
    FREE_THROW(
        "free_throw", "罚球", "固定节奏与出手一致性",
        listOf("罚球线"),
        "每组 10 次，重复固定的准备动作与呼吸节奏。",
        "每次使用一致的站位、运球次数和瞄准点，出手后保持随挥，组间短暂休息。"
    ),
    FINISHING(
        "finishing", "上篮终结", "左右手、反手与抛投",
        listOf("左手上篮", "右手上篮", "反手上篮", "抛投", "篮下打板"),
        "左右手各 10 次，从低速脚步练习逐步过渡到实战速度。",
        "先保证步点和持球稳定；弱侧手单独记录，落地留出空间，避免疲劳下连续冲框。"
    ),
    HANDLING(
        "handling", "运控", "原地控球与行进间变向",
        listOf("原地双手", "体前变向", "胯下运球", "背后运球", "行进间运球"),
        "每组 30–60 秒，组间休息；按实际分钟和秒数记录，不含休息。",
        "降低重心、抬头观察，左右手均衡练习；先保持控球稳定，再提高移动速度。",
        tracksShots = false
    );

    companion object {
        fun fromKey(key: String): BallDrill? = entries.firstOrNull { it.key == key }
    }
}

data class BallTrainingSummary(
    val groups: Int,
    val days: Int,
    val attempts: Long,
    val made: Long,
    val minutes: Long,
    val seconds: Long = 0
) {
    val totalSeconds: Long get() = minutes * 60 + seconds
    val durationLabel: String get() = formatBallDuration(totalSeconds)
    val accuracy: String
        get() = if (attempts == 0L) "—" else String.format(Locale.CHINA, "%.1f%%", made * 100.0 / attempts)
}

fun summarizeBallTraining(records: List<BallTrainingRecordEntity>): BallTrainingSummary {
    val shots = records.filter { BallDrill.fromKey(it.category)?.tracksShots == true }
    return BallTrainingSummary(
        groups = records.size,
        days = records.map { it.date }.distinct().size,
        attempts = shots.sumOf { it.attempts.toLong() },
        made = shots.sumOf { it.made.toLong() },
        minutes = records.sumOf { it.totalDurationSeconds() } / 60,
        seconds = records.sumOf { it.totalDurationSeconds() } % 60
    )
}

fun BallTrainingRecordEntity.totalDurationSeconds(): Long = durationMinutes * 60L + durationSeconds

fun formatBallDuration(seconds: Long): String = when {
    seconds == 0L -> "0 分钟"
    seconds < 60 -> "$seconds 秒"
    seconds % 60 == 0L -> "${seconds / 60} 分钟"
    else -> "${seconds / 60} 分 ${seconds % 60} 秒"
}

fun validateBallRecord(record: BallTrainingRecordEntity, today: LocalDate = LocalDate.now()): String? {
    val drill = BallDrill.fromKey(record.category) ?: return "请选择训练分类"
    val date = runCatching { LocalDate.parse(record.date) }.getOrNull() ?: return "请选择有效日期"
    return when {
        record.movementKey.isNotBlank() && BallMoves.find(record.movementKey)?.category != record.category -> "请选择当前分类的训练动作"
        (record.planId == null) != record.planItemId.isBlank() -> "计划关联不完整，请重新打开记录页"
        record.planId != null && (record.planId <= 0 || record.planItemId.toIntOrNull() !in 0..5 || record.movementKey.isBlank()) -> "计划关联无效，请重新打开记录页"
        date.isAfter(today) -> "训练记录不能晚于今天"
        record.position !in drill.positions -> "请选择当前分类的点位或动作"
        record.durationMinutes !in 0..600 -> "时长请填写 0–600 分钟"
        record.durationSeconds !in 0..59 || record.totalDurationSeconds() > 36000 -> "秒数请填写 0–59，训练时长不能超过 600 分钟"
        record.note.length > 500 -> "备注请控制在 500 字以内"
        drill.tracksShots && record.attempts !in 1..9999 -> "出手次数请填写 1–9999"
        drill.tracksShots && record.made !in 0..record.attempts -> "命中次数不能小于 0 或超过出手次数"
        !drill.tracksShots && (record.attempts != 0 || record.made != 0) -> "运控只记录时长，不记录投篮次数"
        !drill.tracksShots && record.totalDurationSeconds() == 0L -> "请填写实际运控时长（至少 1 秒）"
        else -> null
    }
}
