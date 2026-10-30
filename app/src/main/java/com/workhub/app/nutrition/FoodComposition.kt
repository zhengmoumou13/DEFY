package com.workhub.app.nutrition

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Locale

@Serializable
data class FoodNutrients(val calories: Double, val protein: Double, val carbs: Double, val fat: Double) {
    fun valid() = listOf(calories, protein, carbs, fat).all { it.isFinite() && it >= 0 }
    operator fun times(factor: Double) = FoodNutrients(calories * factor, protein * factor, carbs * factor, fat * factor)
    operator fun plus(other: FoodNutrients) = FoodNutrients(calories + other.calories, protein + other.protein, carbs + other.carbs, fat + other.fat)
    operator fun minus(other: FoodNutrients) = FoodNutrients(calories - other.calories, protein - other.protein, carbs - other.carbs, fat - other.fat)
    fun value(nutrient: FoodNutrient) = when (nutrient) {
        FoodNutrient.CARBS -> carbs
        FoodNutrient.PROTEIN -> protein
        FoodNutrient.FAT -> fat
        FoodNutrient.ENERGY -> calories
    }
    companion object { val ZERO = FoodNutrients(0.0, 0.0, 0.0, 0.0) }
}

enum class FoodNutrient(val label: String, val unit: String) {
    CARBS("碳水", "g"), PROTEIN("蛋白质", "g"), FAT("脂肪", "g"), ENERGY("热量", "kcal")
}

@Serializable
data class CompositionFood(
    val id: String, val name: String, val state: String, val group: String,
    val sourceName: String, val per100g: FoodNutrients
) {
    val isCustom get() = id.startsWith("custom-")
    val sourceUrl get() = if (isCustom) "" else "https://fdc.nal.usda.gov/food-details/$id/nutrients"
}

@Serializable
data class FoodPortion(val foodId: String, val grams: Double)

object FoodComposition {
    val json = Json { ignoreUnknownKeys = true }
    fun decode(source: String): List<CompositionFood> = json.decodeFromString<List<CompositionFood>>(source).also { foods ->
        require(foods.isNotEmpty() && foods.map { it.id }.distinct().size == foods.size)
        require(foods.all { it.id.isNotBlank() && it.name.isNotBlank() && it.state.isNotBlank() && it.per100g.valid() })
    }
    /** No household-unit or raw-to-cooked weight guesses: every portion is edible grams. */
    fun forWeight(food: CompositionFood, grams: Double): FoodNutrients? =
        if (grams.isFinite() && grams in 0.0..10000.0 && food.per100g.valid()) food.per100g * (grams / 100.0) else null

    fun gramsFor(food: CompositionFood, nutrient: FoodNutrient, target: Double): Double? {
        val concentration = food.per100g.value(nutrient)
        if (!food.per100g.valid() || !target.isFinite() || target < 0 || concentration <= 0) return null
        return (target / concentration * 100).takeIf { it.isFinite() && it in 0.0..10000.0 }
    }
    fun equivalentGrams(from: CompositionFood, grams: Double, to: CompositionFood, nutrient: FoodNutrient): Double? {
        val target = forWeight(from, grams)?.value(nutrient) ?: return null
        if (target <= 0) return null
        return gramsFor(to, nutrient, target)
    }
    fun total(portions: List<FoodPortion>, foods: List<CompositionFood>): FoodNutrients? {
        val byId = foods.associateBy { it.id }
        var result = FoodNutrients.ZERO
        for (portion in portions) {
            val food = byId[portion.foodId] ?: return null
            result += forWeight(food, portion.grams) ?: return null
        }
        return result
    }
    fun number(value: Double): String = String.format(Locale.ROOT, "%.1f", value).removeSuffix(".0")
    fun input(value: String): Double? = value.trim().toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
    fun energyKcal(value: Double, kilojoules: Boolean): Double? = value.takeIf { it.isFinite() && it >= 0 }?.let { if (kilojoules) it / 4.184 else it }
    fun validLabel(name: String, n: FoodNutrients) = name.trim().isNotEmpty() && n.valid() &&
        n.calories <= 1000 && listOf(n.protein, n.carbs, n.fat).all { it <= 100 } && n.protein + n.carbs + n.fat <= 101
}
