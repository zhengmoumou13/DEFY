package com.workhub.app.ui.ai

import org.junit.Assert.*
import org.junit.Test

class DietPlanPresentationTest {
    private fun parse(content: String) = dietPlanPresentation(content.trimIndent(), parsePlanItems(content.trimIndent(), "diet"))
    private val fixture get() = javaClass.getResource("/plans/dense-diet-plan.md")!!.readText()

    @Test fun densePlanShowsThreeRequiredMealsAndOptionalGuidanceWithoutExtraTasks() {
        val plan = parse(fixture)
        assertEquals(5, plan.meals.size)
        assertEquals(listOf("早餐", "午餐", "晚餐"), plan.meals.filter { it.item != null }.map { it.label })
        assertEquals("1800–2000 kcal", plan.targets.first { it.first == "热量" }.second)
        assertEquals("2000–2500 ml，分次饮用", plan.targets.first { it.first == "饮水" }.second)
        assertEquals(4, plan.meals.first { it.label == "午餐" }.foods.size)
    }

    @Test fun foodStatePortionsAndGuidanceStaySeparateAndComplete() {
        val meal = parse(fixture).meals.first { it.label == "午餐" }
        assertEquals("米饭（熟重）200 g", meal.foods.first())
        assertTrue(meal.foods.contains("橄榄油10 g"))
        assertTrue(meal.details.any { it.contains("约 580 kcal") })
        assertTrue(meal.details.any { it.contains("彻底烹熟") })
        assertTrue(meal.foods.none { it.startsWith("营养") || it.startsWith("备餐") })
    }

    @Test fun displayProjectionRetainsExistingActionKeysAndLabels() {
        val actions = parsePlanItems(fixture, "diet")
        val meals = dietPlanPresentation(fixture, actions).meals
        assertEquals(actions, meals.mapNotNull { it.item })
    }

    @Test fun numberedMealHeadingsAndOptionalHeadingStayReadable() {
        val plan = parse("""
            ## 早餐
            - 燕麦40g
            - 牛奶250ml
            - 热量：约300kcal
            ## 加餐（可选）
            - 香蕉100g
            ## 执行提醒
            - 按需补水
        """)
        assertEquals(listOf("燕麦40g", "牛奶250ml"), plan.meals[0].foods)
        assertEquals(listOf("热量：约300kcal"), plan.meals[0].details)
        assertNull(plan.meals[1].item)
        assertTrue(plan.meals[1].foods.any { it.contains("香蕉") })
        assertEquals(1, plan.meals.count { it.item != null })
    }

    @Test fun parenthesizedIngredientsAndAlternativesAreNeverAddedAsTwoRequiredFoods() {
        val meal = dietMealPresentation("午餐", "米饭（熟重，白米+糙米）150g + 鸡肉或鱼肉100g；替换：按蛋白质等量", null)
        assertEquals(listOf("米饭（熟重，白米+糙米）150g", "鸡肉或鱼肉100g"), meal.foods)
        assertEquals(listOf("替换：按蛋白质等量"), meal.details)
    }

    @Test fun tableFormatAndCommaNutritionDoNotBecomeExtraFoodRows() {
        val table = parse("| 餐次 | 食物 | 营养 |\n| --- | --- | --- |\n| 早餐 | 燕麦40g + 鸡蛋1个 | 热量300kcal |")
        assertEquals(listOf("燕麦40g", "鸡蛋1个"), table.meals.single().foods)
        assertEquals(listOf("热量300kcal"), table.meals.single().details)
        val meal = dietMealPresentation("早餐", "燕麦40g，约300kcal，蛋白质12g", null)
        assertEquals(listOf("燕麦40g"), meal.foods)
        assertEquals(2, meal.details.size)
    }

    @Test fun longTermExecutionReminderIsNotPartOfDinner() {
        val plan = parse("""
            ## 第2天
            - 早餐：燕麦50g
            - 晚餐：米饭150g + 豆腐100g
            - 今日执行重点：记录饮食，不按单日体重增减份量
        """)
        assertEquals(listOf("米饭150g", "豆腐100g"), plan.meals.last().foods)
    }

    @Test fun unknownFreeTextDoesNotInventMealsOrGoals() {
        val plan = parse("今天请根据实际情况安排饮食，先补充过敏信息。")
        assertTrue(plan.meals.isEmpty())
        assertTrue(plan.targets.isEmpty())
    }

    @Test fun wholeMarkdownWrapperRendersTheSameMealsAndKeepsStableKeys() {
        val wrapped = "```markdown\n$fixture\n```"
        assertEquals(parse(fixture), parse(wrapped))
        assertEquals(parsePlanItems(fixture, "diet"), parsePlanItems(wrapped, "diet"))
    }
}
