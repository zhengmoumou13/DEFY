package com.workhub.app.ui.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanItemParserTest {
    private fun items(text: String, type: String) = parsePlanItems(text.trimIndent(), type).map { it.text }

    @Test fun dietReferenceMacrosAndReviewDatesAreNotTasks() {
        assertEquals(listOf("早餐：燕麦50g + 鸡蛋2个"), items("""
            ## 谭成义减脂饮食参考
            - 基准体重：85kg
            - 碳水：187g，蛋白质：119g，脂肪：68g
            - 热量：1836 kcal
            - 每10天复盘一次，不根据单日变化调节
            ## 今日安排
            - 早餐：燕麦50g + 鸡蛋2个
            ## 执行提醒
            - 来源：谭成义减脂视频
        """, "diet"))
    }

    @Test fun fitnessKeepsExercisesButNotMetadataInSameSection() {
        assertEquals(listOf("深蹲｜3×8｜RPE 7｜休息90秒", "提踵：3组，每组15次"), items("""
            ## 主训练
            - 深蹲｜3×8｜RPE 7｜休息90秒
            - 总高质量跳跃次数：24次
            - 组间休息：90秒
            - 强度：RPE 7
            - 技术提示：落地缓冲保持2秒
            - 提踵：3组，每组15次
            ## 停止条件
            - 疼痛超过3分钟就停止
        """, "fitness"))
    }

    @Test fun longTermIndentedActionsAreNotLost() {
        assertEquals(listOf("深蹲｜3×8｜RPE 7", "平板支撑｜3组×30秒"), items("""
            ## 第1天
            - **主题**：基础力量
            - **主训练**：
              - **深蹲**｜3×8｜RPE 7
              - 平板支撑｜3组×30秒
            - 完成标准：训练30分钟，动作稳定
            训练后休息10分钟，观察疲劳。
        """, "fitness"))
    }

    @Test fun proseAfterInlineActionDoesNotBecomeAnotherTask() {
        assertEquals(listOf("深蹲3组×8次", "提踵3组×15次"), items("""
            - 主训练：深蹲3组×8次；提踵3组×15次
            以上动作以质量为主，不追求疲劳。
            - 强度与时长：RPE 6，45分钟
            总计完成约45分钟训练。
        """, "fitness"))
    }

    @Test fun mealsIgnoreGoalsRemindersAndExecutionFocus() {
        assertEquals(listOf("早餐：燕麦40g + 牛奶250ml", "午餐：米饭1碗 + 鸡肉100g", "晚餐：鱼肉100g + 蔬菜200g"), items("""
            ## 今日营养目标
            - 蛋白质：120g
            ## 今日安排
            - **早餐：**燕麦40g + 牛奶250ml
            - 午餐：米饭1碗 + 鸡肉100g
            - 训练前后：无训练，无需额外补给
            - 晚餐：鱼肉100g + 蔬菜200g
            - 今日执行重点：保证蛋白质120g
            ## 执行提醒
            - 注意补足蛋白质120g
            - 鸡肉可以换成鱼肉100g
        """, "diet"))
    }

    @Test fun mealHeadingGroupsFoodsIntoOneTask() {
        assertEquals(listOf("早餐：燕麦40g；鸡蛋2个", "午餐：米饭1碗"), items("""
            ### 早餐
            - 燕麦40g
            - 鸡蛋2个
            - 热量：400千卡
            ### 午餐
            - 米饭1碗
            ### 营养说明
            - 蛋白质30g
        """, "diet"))
    }

    @Test fun optionalAndEmptyMealsAreNotRequiredTasks() {
        assertTrue(items("""
            - 早餐：无
            - 加餐：可选一杯牛奶
            - 训练前后：无需额外补给
            - 午餐：
            - 今日执行重点：注意营养均衡
        """, "diet").isEmpty())
    }

    @Test fun codeBlocksAndExplanationOnlyPlansProduceNoTasks() {
        assertTrue(items("""
            ## 今日判断
            - 总时长：45分钟
            ```
            ## 主训练
            - 深蹲3组
            ```
            ## 主训练
            - 动作｜组×次｜强度/RPE｜组间休息｜技术要点
            - 只安排3-6个主训练动作
            - 每项写3组训练的技术要点
        """, "fitness").isEmpty())
    }

    @Test fun supportsTablesWithoutConvertingHeadersOrSummary() {
        assertEquals(listOf("深蹲｜3×8｜7", "平板支撑｜30秒｜6"), items("""
            ## 主训练
            | 动作 | 组次 | RPE |
            | --- | --- | --- |
            | 深蹲 | 3×8 | 7 |
            | 平板支撑 | 30秒 | 6 |
            | 跳跃总量 | 24次 | - |
        """, "fitness"))
        assertEquals(listOf("早餐：燕麦40g｜牛奶250ml"), items("""
            | 餐次 | 主食 | 饮品 |
            | --- | --- | --- |
            | 早餐 | 燕麦40g | 牛奶250ml |
        """, "diet"))
    }

    @Test fun numberedCheckboxesAndBoldNamesNormalizeToStableKeys() {
        val plain = parsePlanItems("## 主训练\n- 深蹲3组×8次", "fitness")
        assertEquals(plain, parsePlanItems("## 主训练\n1. [ ] **深蹲**3组×8次", "fitness"))
    }

    @Test fun doesNotSilentlyDropSeventhValidExercise() {
        val content = "## 主训练\n" + (1..7).joinToString("\n") { "- 动作$it｜3组×8次" }
        assertEquals(7, items(content, "fitness").size)
    }

    @Test fun noStandaloneDoseOrIntensityAsExercise() {
        assertTrue(items("""
            ## 主训练
            - 3组×8次
            - RPE 7，休息90秒
            - 组数：3组
            - 落地要求：保持2秒
        """, "fitness").isEmpty())
    }

    @Test fun headingWithDurationKeepsOnlyActualExercises() {
        assertEquals(listOf("深蹲｜3×8"), items("## 3. 主训练（30分钟）\n- 深蹲｜3×8\n- 总时长：30分钟", "fitness"))
    }

    @Test fun screenshotContactCountIsNotASixthExercise() {
        val content = javaClass.getResource("/plans/touch-count-note.md")!!.readText()
        val result = parsePlanItems(content, "fitness")
        assertEquals(5, result.size)
        assertTrue(result.none { it.text.startsWith("每组触地") })
        assertEquals(listOf("每组触地 4 次，总触地 12 次"), result.single { it.text.startsWith("跳深") }.notes)
    }

    @Test fun countsAreAttachedAcrossCommonGeneratedFormats() {
        for (note in listOf("每组触地4次，总触地12次", "每侧 8 次，两侧合计 16 次", "触地次数：每组4次，总计12次", "触地：每组4次", "触地4次", "每侧各8次", "两侧合计16次")) {
            for (source in listOf(
                "## 主训练\n- 跳深｜3×4\n- $note\n- 卷腹｜3×12",
                "## 主训练\n- 跳深｜3×4\n  $note\n- 卷腹｜3×12",
                "- 主训练：跳深｜3×4；$note；卷腹｜3×12"
            )) {
                val result = parsePlanItems(source, "fitness")
                assertEquals(listOf("跳深｜3×4", "卷腹｜3×12"), result.map { it.text })
                assertEquals(listOf(note), result.first().notes)
                assertTrue(result.last().notes.isEmpty())
            }
        }
    }

    @Test fun tableNotesAreNotAnExtraExercise() {
        val result = parsePlanItems("## 主训练\n| 跳深 | 3×4 |\n| 每组触地 | 4次，总触地12次 |\n| 卷腹 | 3×12 |", "fitness")
        assertEquals(2, result.size)
        assertEquals(listOf("每组触地｜4次，总触地12次"), result.first().notes)
    }

    @Test fun notesDoNotChangeExerciseKeysOrReintroduceSavedNoteChecks() {
        val before = parsePlanItems("## 主训练\n- 跳深｜3×4", "fitness").single()
        val after = parsePlanItems("## 主训练\n- 跳深｜3×4\n- 每组触地4次，总触地12次", "fitness").single()
        assertEquals(before.key, after.key)
        val saved = mapOf(before.key to true, "每组触地4次，总触地12次".hashCode().toString() to true)
        assertEquals(1, listOf(after).count { saved[it.key] == true })
    }

    @Test fun notesCannotBecomeStandaloneTasksOrAttachAcrossSections() {
        assertTrue(items("## 主训练\n- 每组触地4次，总触地12次", "fitness").isEmpty())
        val result = parsePlanItems("## 主训练\n- 跳深｜3×4\n## 恢复\n- 每组触地4次\n## 主训练\n- 每组触地3次\n- 卷腹｜3×12", "fitness")
        assertEquals(2, result.size)
        assertTrue(result.all { it.notes.isEmpty() })
    }

    @Test fun countRelatedExerciseNamesRemainValid() {
        assertEquals(listOf("触地跳｜3×5", "每侧单腿提踵｜3组×10次"), items("## 主训练\n- 触地跳｜3×5\n- 每侧单腿提踵｜3组×10次", "fitness"))
    }

    @Test fun movementCuesAreSupportingTextInsteadOfChecks() {
        val result = parsePlanItems("## 主训练\n- 臀桥｜3×10\n- 动作要点：顶峰保持2秒\n- 每组休息75秒\n- 卷腹｜3×12", "fitness")
        assertEquals(2, result.size)
        assertEquals(listOf("动作要点：顶峰保持2秒", "每组休息75秒"), result.first().notes)
    }

    @Test fun studyParsingRemainsCompatible() {
        assertEquals(listOf("阅读第一章并总结", "完成课后练习"), items("- 阅读第一章并总结\n- 完成课后练习", "study"))
    }
}
