package com.workhub.app.settings

import org.junit.Assert.*
import org.junit.Test

class ProfileValidationTest {
    @Test fun optionalBodyMeasurementsDoNotBlockOnboarding() {
        assertNull(ProfileValidation.error("", ""))
        assertNull(ProfileValidation.error("180", "75.5", "", ""))
    }
    @Test fun invalidNumbersCannotReachTrainingOrNutritionPrompts() {
        for (value in listOf("NaN", "Infinity", "-1", "abc", "0")) {
            assertNotNull(ProfileValidation.error(value, "75"))
            assertNotNull(ProfileValidation.error("180", value))
        }
    }
    @Test fun weeklyGoalCountsDaysNotArbitraryNumbers() {
        for (value in listOf("0", "8", "-3", "3.5", "NaN")) {
            assertNotNull(ProfileValidation.error("180", "75", "305", value))
        }
        for (day in 1..7) assertNull(ProfileValidation.error("180", "75", "305", day.toString()))
    }
    @Test fun rimHeightAndBodyBoundsAreValidated() {
        assertNotNull(ProfileValidation.error("251", "75"))
        assertNotNull(ProfileValidation.error("180", "401"))
        assertNotNull(ProfileValidation.error("180", "75", "0"))
        assertNull(ProfileValidation.error(" 180.5 ", " 75.5 ", " 305 ", " 4 "))
    }
}
