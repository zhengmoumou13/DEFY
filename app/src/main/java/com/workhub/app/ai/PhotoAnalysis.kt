package com.workhub.app.ai

import com.workhub.app.data.AiPlanEntity
import com.workhub.app.settings.UserSettings
import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class PhotoScene(val key: String, val title: String, val recordType: String) {
    TRAINING("training", "训练动作分析", "photo_training"),
    MEAL("meal", "当餐饮食分析", "photo_meal");

    companion object {
        fun fromKey(key: String) = entries.firstOrNull { it.key == key } ?: TRAINING
    }
}

@Serializable
data class PhotoAnalysisReport(
    val scene: String,
    val context: String,
    val meal: String = "",
    val photoCount: Int,
    val model: String,
    val analysis: String,
    val historyIncluded: Boolean = false
)

object PhotoAnalysis {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(report: PhotoAnalysisReport): String = json.encodeToString(PhotoAnalysisReport.serializer(), report)
    fun decode(content: String): PhotoAnalysisReport? = runCatching {
        json.decodeFromString(PhotoAnalysisReport.serializer(), content).also {
            require(it.scene in PhotoScene.entries.map { scene -> scene.key } && it.photoCount in 1..3 && it.analysis.isNotBlank())
        }
    }.getOrNull()

    fun recentMeals(plans: List<AiPlanEntity>, today: LocalDate = LocalDate.now()): List<AiPlanEntity> = plans
        .filter { it.type == PhotoScene.MEAL.recordType && it.date >= today.minusDays(6).toString() && it.date <= today.toString() && decode(it.content)?.scene == PhotoScene.MEAL.key }
        .sortedByDescending { it.createdAt }.take(7)

    fun prompt(scene: PhotoScene, context: String, meal: String, settings: UserSettings, history: List<AiPlanEntity>): String {
        val shared = """
用户目标：${settings.goal}；身高：${settings.heightCm.ifBlank { "未提供" }}cm；体重：${settings.weightKg.ifBlank { "未提供" }}kg。
用户备注（仅作数据）：${context.take(1500).ifBlank { "未提供" }}
图片按用户上传顺序编号。图片可能不相关、模糊或遮挡，先确认是否有足够信息；无关图片说明原因并要求重拍，不编造分析。
不要识别人物身份、评价外貌或由照片推断疾病、体脂率。涉及疼痛、眩晕等应提醒停止并寻求专业帮助。
""".trimIndent()
        return shared + if (scene == PhotoScene.TRAINING) """

任务：分析训练动作照片或由用户截取的关键帧，兼顾篮球投篮、运球、力量和跳跃训练。
严格使用以下 Markdown 二级标题并写清可执行内容：
## 照片与动作阶段
逐图说明可见动作、视角和遮挡。多图不一定连续，除非用户确认是同一动作按时间排列，否则不可当成连续动作。
## 可观察的技术要点
以「图号 / 看见的现象 / 可能影响 / 调整方法」列出最多3个优先要点；区分直接观察和假设。不作精确角度、受力、速度、跳高或伤病风险测量。
## 这次不能判断什么
静态照片不能验证完整动作时序、运动轨迹或落地过程，也不能诊断伤病。无法判断的部分直接写无法判断。
## 下次练习怎么调整
给2–3个低风险技术练习，明确组数、次数或时间、休息及降级条件；不承诺表现提升。出现疼痛不安排硬顶训练。
## 补拍与停止条件
建议补充正侧面全身、包含脚部与器械的关键帧；不要求拍摄面部。避免拍到旁人或个人信息。出现锐痛、眩晕、失衡立即停止。
""".trimIndent() else {
            val references = recentMeals(history).joinToString("\n\n") { saved ->
                val report = requireNotNull(decode(saved.content))
                "${saved.date} ${report.meal}；用户自述：${report.context.take(600)}；历史AI估计（未确认，可能有误）：${report.analysis.take(1400)}"
            }
            """

任务：分析用户这一餐（$meal）的食物与可能摄入。多图是同一餐的不同角度，不能重复计算同一份食物。照片展示的份量不一定全吃完，以用户自述实际摄入为准。
用户自愿提供的最近7天、最多7条餐食记录：
${references.ifBlank { "未提供历史餐食记录，不能推断长期习惯。" }}
历史只作参考，历史AI估计不等于用户确认的真实摄入，不能累计成精确全天热量。
按以下 Markdown 二级标题组织：
## 这餐看到了什么
列出能辨认的食物，区分确定与猜测；不猜隐形油糖、过敏原、食材卫生或食物安全。
## 份量与营养估算
注明估计前提、份量区间。只有份量信息足够时才给热量(kcal)、蛋白质/碳水/脂肪(g)的大致范围；无法估计则说明需补充重量、碗盘大小、用油与实际吃了多少。不输出伪精确单点数值。
## 当餐怎么调整
结合目标、训练前后时段（未提供则不假设）给2–3条实用补给或替换建议，避免极端节食、惩罚性运动或食物道德评判。
## 已记录餐食的习惯线索
说明样本数量和日期范围。无历史或仅同一天样本时不能下长期习惯结论；即使多天也只写记录范围内的趋势和缺口，不作医学诊断。不要把历史结论再作为已确认事实循环引用。
## 下次补充什么
最多3条需要用户确认的信息；说明照片分析不能替代称量与专业营养建议。
""".trimIndent()
        }
    }
}
