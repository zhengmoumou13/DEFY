package com.workhub.app.gym

import com.workhub.app.data.*
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class GymDataTest {
    private fun asset(name: String) = File("src/main/assets/opengym/$name").readText()
    @Test fun catalogueAndStarterReferencesAreValid() {
        val exercises = GymCodec.json.decodeFromString<List<GymExercise>>(asset("exercises.json"))
        assertEquals(6554, exercises.size)
        val ids = exercises.map { it.id }.toSet()
        assertEquals(exercises.size, ids.size)
        assertTrue(exercises.all { it.name.isNotBlank() && it.instructions.isNotEmpty() })
        val starters = GymCodec.json.decodeFromString<List<GymRoutine>>(asset("starters.json"))
        assertEquals(13, starters.size)
        starters.forEach { routine ->
            assertTrue(routine.exercises.isNotEmpty())
            assertTrue(routine.exercises.all { it.exerciseId in ids && it.valid() })
            assertEquals(routine.exercises.size, routine.exercises.map { it.slotId }.toSet().size)
        }
    }
    @Test fun prescriptionsRejectInvalidNumbers() {
        val p = GymPrescription("0043")
        assertTrue(p.valid())
        assertFalse(p.copy(sets = 0).valid())
        assertFalse(p.copy(reps = -1).valid())
        assertFalse(p.copy(weightKg = Double.NaN).valid())
        assertFalse(p.copy(weightKg = Double.POSITIVE_INFINITY).valid())
        assertFalse(p.copy(restSeconds = 901).valid())
        assertFalse(p.copy(seconds = 0).valid())
    }
    @Test fun repeatedExercisesHaveIndependentSlotsAndSnapshotRoundTrips() {
        val first = GymPrescription("0043")
        val second = GymPrescription("0043")
        assertNotEquals(first.slotId, second.slotId)
        val routine = GymRoutine("下肢", listOf(first, second.copy(timed = true, seconds = 45)))
        assertEquals(routine, GymCodec.decode(GymCodec.encode(routine)))
        val snapshot = AppSnapshot(
            gymTemplates = listOf(GymTemplateEntity(8, routine.name, GymCodec.encode(routine))),
            gymSessions = listOf(GymSessionEntity("session", "2026-10-30", GymCodec.encode(routine))),
            workoutSets = listOf(WorkoutSetEntity(date = "2026-10-30", exercise = "深蹲", gymSessionId = "session", gymSlotKey = "${first.slotId}:0", reps = 8))
        )
        assertEquals(snapshot, GymCodec.json.decodeFromString<AppSnapshot>(GymCodec.json.encodeToString(snapshot)))
    }
    @Test fun olderBackupsStillLoadWithoutGymFields() {
        val snapshot = GymCodec.json.decodeFromString<AppSnapshot>("""{"tasks":[],"workoutSets":[{"id":1,"date":"2026-01-01","exercise":"深蹲","reps":8}]}""")
        assertTrue(snapshot.gymTemplates.isEmpty())
        assertTrue(snapshot.gymSessions.isEmpty())
        assertNull(snapshot.workoutSets.single().gymSessionId)
        assertEquals("", snapshot.workoutSets.single().gymSlotKey)
    }
}
