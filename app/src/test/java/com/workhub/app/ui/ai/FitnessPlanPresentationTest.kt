package com.workhub.app.ui.ai

import org.junit.Assert.*
import org.junit.Test

class FitnessPlanPresentationTest {
    private val fixture get() = javaClass.getResource("/plans/dense-fitness-plan.md")!!.readText()

    @Test fun screenshotBecomesFiveExercisesWithSeparateSummaryAndGuidance() {
        val plan = fitnessPlanPresentation(fixture)
        assertEquals("低量爆发力激活 + 扣篮助跑节奏巩固", plan.goal)
        assertEquals("约 50 分钟", plan.duration)
        assertEquals("RPE 6–7", plan.intensity)
        assertEquals(listOf("今日判断", "热身", "主训练", "训练总量", "起跳技术", "放松与恢复", "停止条件"), plan.sections.map { it.title })
        val actions = parsePlanItems(fixture, "fitness")
        assertEquals(5, actions.size)
        val jump = fitnessExercisePresentation(actions[2])
        assertEquals("跳深", jump.name)
        assertEquals("3 × 4", jump.dose)
        assertEquals("RPE 6–7", jump.intensity)
        assertEquals("组间 120 秒", jump.rest)
        assertTrue(jump.details.contains("每组触地 4 次，总触地 12 次"))
        assertTrue(jump.details.any { it.contains("≤30 cm") })
    }

    @Test fun everySectionRetainsItsFullBodyIncludingSafetyAndMainTrainingNotes() {
        val sections = fitnessPlanPresentation(fixture).sections
        assertTrue(sections.first { it.title == "今日判断" }.content.contains("保留 2–3 次余力"))
        assertTrue(sections.first { it.title == "主训练" }.content.contains("助跑摸高今日不单独加练"))
        assertTrue(sections.first { it.title == "停止条件" }.content.contains("立即停止或降级训练"))
        assertTrue(sections.first { it.title == "放松与恢复" }.content.contains("避免影响爆发质量与恢复"))
    }

    @Test fun longTermInlineSectionsRemainReadableAndMetricsComeFromSummary() {
        val plan = fitnessPlanPresentation("""
            ## 第3天
            - **主题**：力量基础
            - 强度与时长：RPE 6，45–60 分钟
            - 热身：快走 5 分钟
            - 主训练：
              - 深蹲｜3×8｜RPE 7｜休息90秒
            - 技术重点：落地缓冲
            - 恢复任务：慢走 5 分钟
            - 完成标准：动作稳定
        """.trimIndent())
        assertEquals("力量基础", plan.goal)
        assertEquals("45–60 分钟", plan.duration)
        assertEquals("RPE 6", plan.intensity)
        assertEquals("快走 5 分钟", plan.sections.first { it.title == "热身" }.content)
        assertEquals("落地缓冲", plan.sections.first { it.title == "技术重点" }.content)
    }

    @Test fun exerciseDosageIsNeverMistakenForSessionTimeOrIntensity() {
        val plan = fitnessPlanPresentation("## 主训练\n- 深蹲｜3×8｜RPE 7｜休息 2 分钟")
        assertNull(plan.duration)
        assertNull(plan.intensity)
        assertNull(plan.goal)
    }

    @Test fun unfamiliarAndRecoveryFormatsKeepAllProseWithoutInventingActions() {
        val text = "今天休息。\n保持正常饮食。\n## 特别提醒\n如有不适先休息。"
        val plan = fitnessPlanPresentation(text)
        assertEquals("今天休息。\n保持正常饮食。", plan.sections[0].content)
        assertEquals("特别提醒", plan.sections[1].title)
        assertEquals("如有不适先休息。", plan.sections[1].content)
        assertTrue(parsePlanItems(text, "fitness").isEmpty())
    }

    @Test fun timedUnilateralAndUnknownPrescriptionFieldsAreNotLost() {
        val timed = fitnessExercisePresentation(PlanCheckItem("x", "平板支撑｜3组×30秒｜自重｜休息60秒｜保持呼吸"))
        assertEquals("3组 × 30秒", timed.dose)
        assertEquals(listOf("自重", "保持呼吸"), timed.details)
        val side = fitnessExercisePresentation(PlanCheckItem("y", "单腿提踵｜3×12/侧｜RPE 7（保留3次）｜组间休息：60秒"))
        assertEquals("3 × 12/侧", side.dose)
        assertTrue(side.details.contains("RPE 7（保留3次）"))
        assertEquals("组间休息：60秒", side.rest)
    }

    @Test fun legacyTextAndSavedEditsArePreservedWithoutChangingIdentity() {
        val original = PlanCheckItem("stable-key", "深蹲：3组，每组8次，慢下快上", listOf("每侧 8 次"))
        assertEquals(original.text, fitnessExercisePresentation(original).name)
        val edited = original.copy(text = "徒手深蹲｜2×8｜RPE 5｜休息90秒")
        val projection = fitnessExercisePresentation(edited)
        assertEquals("徒手深蹲", projection.name)
        assertEquals("2 × 8", projection.dose)
        assertEquals("stable-key", edited.key)
        assertEquals(listOf("每侧 8 次"), projection.details)
    }

    @Test fun repeatedAndUnknownHeadingsAreNotDropped() {
        val plan = fitnessPlanPresentation("**热身**\n快走\n## 热身（补充）\n绕踝\n## 器械替换\n使用弹力带")
        assertEquals(listOf("热身", "热身", "器械替换"), plan.sections.map { it.title })
        assertEquals(listOf("快走", "（补充）\n绕踝", "使用弹力带"), plan.sections.map { it.content })
    }

    @Test fun constraintsInSectionHeadingsRemainVisibleWhenExpanded() {
        val plan = fitnessPlanPresentation("## 热身（5分钟）\n快走\n## 主训练（只做轻重量）\n- 深蹲｜3×8")
        assertTrue(plan.sections[0].content.startsWith("（5分钟）"))
        assertTrue(plan.sections[1].content.startsWith("（只做轻重量）"))
    }
}
