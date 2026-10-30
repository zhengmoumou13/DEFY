package com.workhub.app.nutrition

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class FoodCompositionTest {
    private val foods = FoodComposition.decode(listOf(File("src/main/assets/nutrition/foods.json"), File("app/src/main/assets/nutrition/foods.json")).first { it.exists() }.readText())
    private val rice = foods.first { it.id == "169757" }
    private val potato = foods.first { it.id == "170440" }
    private val oil = foods.first { it.id == "171413" }

    @Test fun allBundledFoodsHaveCompleteTraceableReferenceValues() {
        assertEquals(28, foods.size)
        assertEquals(foods.size, foods.map { it.id }.distinct().size)
        assertTrue(foods.all { it.per100g.valid() && it.sourceName.isNotBlank() && it.state.isNotBlank() && it.sourceUrl.endsWith("/nutrients") })
        assertEquals(130.0, rice.per100g.calories, 0.0)
        assertEquals(28.17, rice.per100g.carbs, 0.0)
    }

    @Test fun weightScalesAllNutrientsWithoutEarlyRounding() {
        val result = FoodComposition.forWeight(rice, 150.5)!!
        assertEquals(195.65, result.calories, 0.000001)
        assertEquals(28.17 * 1.505, result.carbs, 0.000001)
        assertEquals(2.69 * 1.505, result.protein, 0.000001)
        assertEquals(0.28 * 1.505, result.fat, 0.000001)
    }

    @Test fun targetToPortionRoundTripsEnergyAndMacros() {
        FoodNutrient.entries.forEach { nutrient ->
            val portion = FoodComposition.gramsFor(rice, nutrient, rice.per100g.value(nutrient) * 2.3)!!
            assertEquals(230.0, portion, 0.000001)
            assertEquals(rice.per100g.value(nutrient) * 2.3, FoodComposition.forWeight(rice, portion)!!.value(nutrient), 0.000001)
        }
    }

    @Test fun replacementMatchesOnlyTheSelectedNutrient() {
        val grams = FoodComposition.equivalentGrams(rice, 200.0, potato, FoodNutrient.CARBS)!!
        val before = FoodComposition.forWeight(rice, 200.0)!!
        val after = FoodComposition.forWeight(potato, grams)!!
        assertEquals(before.carbs, after.carbs, 0.000001)
        assertNotEquals(before.protein, after.protein, 0.01)
        assertNotEquals(before.calories, after.calories, 0.01)
    }

    @Test fun zeroConcentrationsNeverDivideByZeroOrSuggestZeroWeightSwap() {
        assertNull(FoodComposition.gramsFor(oil, FoodNutrient.CARBS, 30.0))
        assertNull(FoodComposition.gramsFor(oil, FoodNutrient.CARBS, 0.0))
        assertNull(FoodComposition.equivalentGrams(oil, 10.0, rice, FoodNutrient.CARBS))
        assertNull(FoodComposition.equivalentGrams(rice, 100.0, oil, FoodNutrient.PROTEIN))
        assertEquals(0.0, FoodComposition.gramsFor(rice, FoodNutrient.CARBS, 0.0)!!, 0.0)
    }

    @Test fun invalidAndOversizedInputsReturnNoResult() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0, 10001.0).forEach { assertNull(FoodComposition.forWeight(rice, it)) }
        listOf(Double.NaN, Double.NEGATIVE_INFINITY, -1.0, 1e100).forEach { assertNull(FoodComposition.gramsFor(rice, FoodNutrient.PROTEIN, it)) }
        listOf("", "-2", "NaN", "Infinity", "1e999", "100g").forEach { assertNull(FoodComposition.input(it)) }
    }

    @Test fun aggregateIncludesOilAndEveryMacroAndRejectsUnknownIngredients() {
        val total = FoodComposition.total(listOf(FoodPortion(rice.id, 100.0), FoodPortion(oil.id, 10.0)), foods)!!
        assertEquals(218.4, total.calories, 0.000001)
        assertEquals(10.28, total.fat, 0.000001)
        assertNull(FoodComposition.total(listOf(FoodPortion("missing", 100.0)), foods))
        assertNull(FoodComposition.total(listOf(FoodPortion(rice.id, -10.0)), foods))
    }

    @Test fun rawAndCookedRiceAreSeparateReferencesNotCookingYield() {
        val raw = foods.first { it.id == "169756" }
        assertTrue(raw.state.startsWith("生重"))
        assertTrue(rice.state.startsWith("熟重"))
        assertEquals(365.0, FoodComposition.forWeight(raw, 100.0)!!.calories, 0.0)
        assertEquals(130.0, FoodComposition.forWeight(rice, 100.0)!!.calories, 0.0)
    }

    @Test fun incompleteReferenceDataIsRejectedInsteadOfTreatingMissingAsZero() {
        assertTrue(runCatching { FoodComposition.decode("""[{"id":"1","name":"x","state":"raw","group":"x","sourceName":"x","per100g":{"calories":100,"protein":1,"carbs":2}}]""") }.isFailure)
        assertTrue(runCatching { FoodComposition.decode("[]") }.isFailure)
    }

    @Test fun customLabelsValidatePer100GramValuesAndCarryTheirOwnSource() {
        assertTrue(FoodComposition.validLabel("自用面包", FoodNutrients(250.0, 10.0, 40.0, 5.0)))
        assertFalse(FoodComposition.validLabel("", FoodNutrients(250.0, 10.0, 40.0, 5.0)))
        assertFalse(FoodComposition.validLabel("bad", FoodNutrients(100.0, 60.0, 60.0, 0.0)))
        assertFalse(FoodComposition.validLabel("bad", FoodNutrients(100.0, 1.0, -1.0, 0.0)))
        val custom = CompositionFood("custom-test", "bread", "label", "custom", "user label", FoodNutrients(250.0, 10.0, 40.0, 5.0))
        assertTrue(custom.isCustom)
        assertEquals("", custom.sourceUrl)
        assertEquals(125.0, FoodComposition.forWeight(custom, 50.0)!!.calories, 0.0)
    }

    @Test fun labelEnergyConvertsKilojoulesWithoutChangingAlreadyKcalValues() {
        assertEquals(100.0, FoodComposition.energyKcal(418.4, true)!!, 0.000001)
        assertEquals(418.4, FoodComposition.energyKcal(418.4, false)!!, 0.000001)
        assertNull(FoodComposition.energyKcal(Double.NaN, true))
        assertNull(FoodComposition.energyKcal(-1.0, true))
    }
}
