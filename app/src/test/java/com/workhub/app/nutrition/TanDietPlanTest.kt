package com.workhub.app.nutrition

import com.workhub.app.ai.AiPrompts
import com.workhub.app.data.BackupSettings
import com.workhub.app.settings.UserSettings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class TanDietPlanTest {
    private val plan = DietPlanPreferences(true, "male", 0, "85", "2.2", "1.4", "0.8", "2026-10-30")
    @Test fun originalVideoExampleCalculatesNutrientsNotFoodWeight() {
        val t = TanDietPlan.target(plan)!!
        assertEquals(187.0, t.carbs, .001)
        assertEquals(119.0, t.protein, .001)
        assertEquals(68.0, t.fat, .001)
        assertEquals(1836, t.calories)
    }
    @Test fun femaleTableIsNotMaleTableOrBlanketThreeTimesCarbs() {
        val p = TanDietPlan.factors("female", 0)!!
        assertEquals(MacroFactors(2.0, 1.4, 1.0), p)
        assertEquals(MacroFactors(2.2, 1.6, 1.1), TanDietPlan.factors("female", 1))
        assertEquals(MacroFactors(2.5, 1.7, 1.1), TanDietPlan.factors("female", 2))
        assertEquals(MacroFactors(3.0, 1.8, 1.2), TanDietPlan.factors("female", 3))
    }
    @Test fun invalidOrIncompleteInputsNeverProduceTargets() {
        listOf(plan.copy(sex=""), plan.copy(activity=-1), plan.copy(weightKg="NaN"), plan.copy(weightKg="0"), plan.copy(weightKg="301"), plan.copy(carbsPerKg="Infinity"), plan.copy(fatPerKg="0"), plan.copy(proteinPerKg="-1")).forEach { assertNull(TanDietPlan.target(it)) }
    }
    @Test fun enabledReferenceAppliesToBothDietPromptsAndHonorsEdits() {
        val settings = UserSettings(dietPlan=plan.copy(carbsPerKg="2.4"))
        listOf(AiPrompts.diet(settings, ""), AiPrompts.longTermDiet(settings, 7, "", "")).forEach {
            assertTrue(it.contains(TanDietPlan.source))
            assertTrue(it.contains("碳水约 204 g"))
            assertTrue(it.contains("公式、原则和复盘说明不生成任务"))
        }
    }
    @Test fun disabledAndInvalidReferencesDoNotAffectPrompts() {
        assertEquals("", TanDietPlan.prompt(plan.copy(enabled=false)))
        assertEquals("", TanDietPlan.prompt(plan.copy(weightKg="")))
        assertFalse(AiPrompts.diet(UserSettings(), "").contains(TanDietPlan.source))
    }
    @Test fun oldBackupsKeepReferenceOffAndNewBackupsPreserveEdits() {
        assertFalse(Json.decodeFromString<BackupSettings>("{}").dietPlan.enabled)
        val backup = BackupSettings(dietPlan = plan)
        assertEquals(plan, Json.decodeFromString<BackupSettings>(Json.encodeToString(backup)).dietPlan)
    }
}
