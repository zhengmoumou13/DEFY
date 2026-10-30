package com.workhub.app.gym

/** Original procedural drawings; no upstream images, poses or animation files are used. */
enum class OriginalDemo(val viewLabel: String) {
    SQUAT("侧面"), RDL("侧面"), ROW("侧面"), BENCH("侧面"), INCLINE("侧面"),
    CURL("侧面"), HAMMER("侧面"), LATERAL("正面"), PRESS("正面"), PUSHUP("侧面");
    companion object {
        // Exact movement names only. Never substitute a similar-looking movement for an unknown exercise.
        fun forExercise(exercise: GymExercise): OriginalDemo? = when (exercise.englishName.lowercase()) {
            "barbell full squat" -> SQUAT
            "barbell romanian deadlift" -> RDL
            "barbell bent over row" -> ROW
            "barbell bench press" -> BENCH
            "barbell incline bench press" -> INCLINE
            "barbell curl" -> CURL
            "dumbbell hammer curl" -> HAMMER
            "dumbbell lateral raise" -> LATERAL
            "dumbbell standing overhead press" -> PRESS
            "push-up" -> PUSHUP
            else -> null
        }
    }
}
