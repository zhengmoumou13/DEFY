package com.workhub.app.nutrition

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object FoodCatalogue {
    @Volatile private var cached: List<CompositionFood>? = null
    suspend fun load(context: Context): List<CompositionFood> = cached ?: withContext(Dispatchers.IO) {
        cached ?: context.assets.open("nutrition/foods.json").bufferedReader().use {
            FoodComposition.decode(it.readText())
        }.also { cached = it }
    }
}
