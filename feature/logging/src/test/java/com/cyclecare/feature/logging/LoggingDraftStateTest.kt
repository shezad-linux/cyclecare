package com.cyclecare.feature.logging

import com.cyclecare.core.model.DischargeType
import com.cyclecare.core.model.EnergyLevel
import com.cyclecare.core.model.Mood
import com.cyclecare.core.model.PainLevel
import com.cyclecare.core.model.SymptomLog
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

/**
 * Spec 15 §11.3 condition 4 depends on the logging editor correctly
 * reporting whether it holds unsaved changes.
 *
 * The comparison is against the stored entry rather than a snapshot taken
 * when the editor opened, so the same answer is produced after a ViewModel
 * recreation, when the restored editor would otherwise be compared against
 * itself.
 */
class LoggingDraftStateTest {

    private val date = LocalDate.of(2026, 10, 19)

    private fun entry(
        mood: Mood? = Mood.Stable,
        painLevel: PainLevel? = PainLevel.None,
        energyLevel: EnergyLevel? = EnergyLevel.Steady,
        dischargeType: DischargeType? = DischargeType.None,
        medication: String? = null,
        notes: String? = null,
    ) = SymptomLog(
        id = "entry-1",
        date = date,
        mood = mood,
        painLevel = painLevel,
        energyLevel = energyLevel,
        dischargeType = dischargeType,
        medication = medication,
        notes = notes,
    )

    @Test
    fun anEmptyEditorAgainstNoEntryIsNotADraft() {
        val editor = LoggingEditorState()
        assertThat(editor.hasUnsavedContentComparedTo(null)).isFalse()
    }

    @Test
    fun anEditorMatchingTheSavedEntryIsNotADraft() {
        val saved = entry()
        val editor = LoggingEditorState(
            existingEntryId = saved.id,
            mood = saved.mood,
            painLevel = saved.painLevel,
            energyLevel = saved.energyLevel,
            dischargeType = saved.dischargeType,
        )
        assertThat(editor.hasUnsavedContentComparedTo(saved)).isFalse()
    }

    @Test
    fun aChangedMoodIsADraft() {
        val saved = entry()
        val editor = LoggingEditorState(
            existingEntryId = saved.id,
            mood = Mood.Low,
            painLevel = saved.painLevel,
            energyLevel = saved.energyLevel,
            dischargeType = saved.dischargeType,
        )
        assertThat(editor.hasUnsavedContentComparedTo(saved)).isTrue()
    }

    @Test
    fun aChangedPainLevelIsADraft() {
        val saved = entry()
        val editor = LoggingEditorState(
            existingEntryId = saved.id,
            mood = saved.mood,
            painLevel = PainLevel.Strong,
            energyLevel = saved.energyLevel,
            dischargeType = saved.dischargeType,
        )
        assertThat(editor.hasUnsavedContentComparedTo(saved)).isTrue()
    }

    @Test
    fun aChangedMedicationIsADraft() {
        val saved = entry()
        val editor = LoggingEditorState(
            existingEntryId = saved.id,
            mood = saved.mood,
            painLevel = saved.painLevel,
            energyLevel = saved.energyLevel,
            dischargeType = saved.dischargeType,
            medication = "Ibuprofen",
        )
        assertThat(editor.hasUnsavedContentComparedTo(saved)).isTrue()
    }

    @Test
    fun aChangedNoteIsADraft() {
        val saved = entry()
        val editor = LoggingEditorState(
            existingEntryId = saved.id,
            mood = saved.mood,
            painLevel = saved.painLevel,
            energyLevel = saved.energyLevel,
            dischargeType = saved.dischargeType,
            notes = "Cramps eased by afternoon.",
        )
        assertThat(editor.hasUnsavedContentComparedTo(saved)).isTrue()
    }

    @Test
    fun typingIntoAnEmptyEditorIsADraft() {
        val editor = LoggingEditorState(mood = Mood.Happy)
        assertThat(editor.hasUnsavedContentComparedTo(null)).isTrue()
    }

    @Test
    fun typingNotesIntoAnEmptyEditorIsADraft() {
        val editor = LoggingEditorState(notes = "Some note")
        assertThat(editor.hasUnsavedContentComparedTo(null)).isTrue()
    }

    @Test
    fun expandingTheDetailsSectionIsNotADraft() {
        // The expansion flag is presentation, not content. Expanding the
        // details section must not suppress an interstitial.
        val saved = entry()
        val editor = LoggingEditorState(
            existingEntryId = saved.id,
            mood = saved.mood,
            painLevel = saved.painLevel,
            energyLevel = saved.energyLevel,
            dischargeType = saved.dischargeType,
            isDetailsExpanded = true,
        )
        assertThat(editor.hasUnsavedContentComparedTo(saved)).isFalse()
    }

    @Test
    fun clearingAFieldIsADraft() {
        val saved = entry(mood = Mood.Stable)
        val editor = LoggingEditorState(
            existingEntryId = saved.id,
            mood = null,
            painLevel = saved.painLevel,
            energyLevel = saved.energyLevel,
            dischargeType = saved.dischargeType,
        )
        assertThat(editor.hasUnsavedContentComparedTo(saved)).isTrue()
    }
}
