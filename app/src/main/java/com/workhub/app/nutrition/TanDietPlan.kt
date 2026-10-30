package com.workhub.app.nutrition

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable
data class DietPlanPreferences(
    val enabled: Boolean = false,
    val sex: String = "",
    val activity: Int = -1,
    val weightKg: String = "",
    val carbsPerKg: String = "",
    val proteinPerKg: String = "",
    val fatPerKg: String = "",
    val reviewedOn: String = ""
)

data class MacroFactors(val carbs: Double, val protein: Double, val fat: Double)
data class MacroTarget(val carbs: Double, val protein: Double, val fat: Double) {
    val calories: Int get() = (carbs * 4 + protein * 4 + fat * 9).roundToInt()
}

object TanDietPlan {
    const val source = "https://www.douyin.com/video/7618962209967965617"
    const val version = "2026-03-20 · 三个月生活化减脂"
    val activityLabels = listOf("每周 2–3 小时", "每周 4–5 小时", "每周 6–7 小时", "每周 8–9 小时")
    // Manually checked against the author's on-screen table, 01:28–06:04.
    fun factors(sex: String, activity: Int): MacroFactors? {
        if (sex !in listOf("male", "female") || activity !in 0..3) return null
        val carbs = if (sex == "female") listOf(2.0, 2.2, 2.5, 3.0) else listOf(2.2, 2.5, 3.0, 3.5)
        val fats = if (sex == "female") listOf(1.0, 1.1, 1.1, 1.2) else listOf(0.8, 0.9, 1.0, 1.0)
        return MacroFactors(carbs[activity], listOf(1.4, 1.6, 1.7, 1.8)[activity], fats[activity])
    }
    fun target(p: DietPlanPreferences): MacroTarget? {
        if (factors(p.sex, p.activity) == null) return null
        val w = p.weightKg.toDoubleOrNull()?.takeIf { it.isFinite() && it in 30.0..300.0 } ?: return null
        val c = p.carbsPerKg.toDoubleOrNull()?.takeIf { it.isFinite() && it in 1.0..6.0 } ?: return null
        val pro = p.proteinPerKg.toDoubleOrNull()?.takeIf { it.isFinite() && it in 0.8..2.5 } ?: return null
        val f = p.fatPerKg.toDoubleOrNull()?.takeIf { it.isFinite() && it in 0.5..1.5 } ?: return null
        return MacroTarget(w * c, w * pro, w * f)
    }
    fun prompt(p: DietPlanPreferences): String {
        if (!p.enabled) return ""
        val target = target(p) ?: return ""
        return """
用户启用的饮食参考：谭成义减脂饮食（App 整理，非官方产品）
出处：$source；版本：$version。
计算基准体重 ${p.weightKg} kg；${if (p.sex == "female") "女性" else "男性"}；${activityLabels[p.activity]}。
用户确认的系数：碳水 ${p.carbsPerKg}、蛋白质 ${p.proteinPerKg}、脂肪 ${p.fatPerKg} g/kg。
每日参考总量：碳水约 ${target.carbs.roundToInt()} g、蛋白质约 ${target.protein.roundToInt()} g、脂肪约 ${target.fat.roundToInt()} g，折算约 ${target.calories} kcal。
这是参考起点，不是已验证的能量缺口或医疗处方。若近期体重、活动量与此基准不符，提示用户复盘，不擅自更改保存值；疾病、孕期、未成年人或过敏等情况优先采用适用限制。
把目标分配为三餐和可选训练加餐，优先燕麦、米饭或薯类搭配蛋、鱼、禽肉、牛肉与蔬菜，允许等价替换。所有食材的碳水、蛋白质和脂肪都计入总量，油、坚果、水果也计入；区分生重和熟重，营养估算不要伪装为精确实测。
参考执行约 10 天后结合体重趋势、训练状态和饥饿感复盘，不按单日波动自动扣减碳水，不自动增加补剂或高盐规则。餐食才是可执行任务；总量、公式、原则和复盘说明不生成任务。
""".trimIndent()
    }
}
