package com.workhub.app.gym

import com.workhub.app.data.*
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class OriginalDemoTest {
    private val catalogue = GymCodec.json.decodeFromString<List<GymExercise>>(File("src/main/assets/opengym/exercises.json").readText())
    @Test fun everyDrawingHasAnExactCatalogueMatch() {
        assertEquals(OriginalDemo.entries.toSet(), catalogue.mapNotNull { OriginalDemo.forExercise(it) }.toSet())
        assertEquals(OriginalDemo.BENCH, OriginalDemo.forExercise(catalogue.first { it.id == "0025" }))
        assertEquals(OriginalDemo.RDL, OriginalDemo.forExercise(catalogue.first { it.id == "0085" }))
    }
    @Test fun unknownVariantsNeverReceiveAnApproximateDemo() {
        val base = catalogue.first { it.id == "0025" }
        assertNull(OriginalDemo.forExercise(base.copy(englishName = "barbell bench press wide grip")))
        assertNull(OriginalDemo.forExercise(base.copy(englishName = "dumbbell biceps curl")))
        assertNull(OriginalDemo.forExercise(base.copy(englishName = "unknown")))
    }
    @Test fun mediaAssociationAndAttributionSurviveBackup() {
        val snapshot = AppSnapshot(files = listOf(FileRecordEntity(8, "我的演示", "files/demo.mp4", 40, "video/mp4")),
            gymMedia = listOf(GymMediaEntity("0025", 8, "本人拍摄")))
        val restored = GymCodec.json.decodeFromString<AppSnapshot>(GymCodec.json.encodeToString(snapshot))
        assertEquals(snapshot, restored)
        assertEquals(restored.files.single().id, restored.gymMedia.single().fileId)
        assertTrue(GymCodec.json.decodeFromString<AppSnapshot>("{}").gymMedia.isEmpty())
    }
}
