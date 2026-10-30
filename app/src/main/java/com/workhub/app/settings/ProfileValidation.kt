package com.workhub.app.settings

/** User-entered profile values; missing height/weight are allowed until the user is ready. */
object ProfileValidation {
    fun error(height: String, weight: String, rim: String = "305", days: String = "4"): String? {
        fun valid(value: String, range: ClosedFloatingPointRange<Double>, optional: Boolean): Boolean {
            if (value.isBlank()) return optional
            val number = value.trim().toDoubleOrNull() ?: return false
            return number.isFinite() && number in range
        }
        return when {
            !valid(height, 50.0..250.0, true) -> "身高请输入 50–250 cm，或暂时留空"
            !valid(weight, 20.0..400.0, true) -> "体重请输入 20–400 kg，或暂时留空"
            !valid(rim.ifBlank { "305" }, 100.0..400.0, false) -> "篮筐高度请输入 100–400 cm"
            days.trim().ifBlank { "4" }.toIntOrNull() !in 1..7 -> "每周训练目标请输入 1–7 天"
            else -> null
        }
    }
}
