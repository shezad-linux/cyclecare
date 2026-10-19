package com.cyclecare.feature.logging

import androidx.lifecycle.SavedStateHandle
import com.cyclecare.core.domain.CycleRepository
import com.cyclecare.core.domain.PredictionRepository
import com.cyclecare.core.domain.ProfileRepository
import com.cyclecare.core.domain.SymptomRepository
import com.cyclecare.core.domain.ads.InMemoryDraftTracker
import com.cyclecare.core.model.CycleRecord
import com.cyclecare.core.model.Mood
import com.cyclecare.core.model.PredictionSnapshot
import com.cyclecare.core.model.SymptomLog
import com.cyclecare.core.model.UserProfile
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Spec 15 §11.3 condition 4: "No unsaved draft anywhere in the app."
 *
 * The gate can only honour that if the logging editor actually reports its
 * state, so this covers the reporting, not just the comparison.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LoggingDraftReportingTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class EmptyProfileRepository : ProfileRepository {
        override fun observeProfile(): Flow<UserProfile?> = MutableStateFlow(null)
        override suspend fun saveProfile(profile: UserProfile) = Unit
        override suspend fun updateRemindersEnabled(enabled: Boolean) = Unit
    }

    private class EmptyCycleRepository : CycleRepository {
        override fun observeCycles(): Flow<List<CycleRecord>> = MutableStateFlow(emptyList())
        override suspend fun upsertCycle(record: CycleRecord) = Unit
        override suspend fun deleteCycle(id: String) = Unit
    }

    private class EmptyPredictionRepository : PredictionRepository {
        override fun observePrediction(): Flow<PredictionSnapshot?> = MutableStateFlow(null)
    }

    private class FixedSymptomRepository(
        private val symptoms: MutableStateFlow<List<SymptomLog>>,
    ) : SymptomRepository {
        override fun observeSymptoms(): Flow<List<SymptomLog>> = symptoms
        override fun observeSymptomForDate(date: LocalDate): Flow<SymptomLog?> =
            symptoms.map { list -> list.firstOrNull { it.date == date } }
        override suspend fun upsertSymptom(log: SymptomLog) {
            symptoms.value = symptoms.value.filterNot { it.id == log.id } + log
        }
    }

    @Test
    fun aFreshEditorReportsNoDraft() = runTest(dispatcher.scheduler) {
        val tracker = InMemoryDraftTracker()
        val viewModel = LoggingViewModel(
            symptomRepository = FixedSymptomRepository(MutableStateFlow(emptyList())),
            profileRepository = EmptyProfileRepository(),
            cycleRepository = EmptyCycleRepository(),
            predictionRepository = EmptyPredictionRepository(),
            savedStateHandle = SavedStateHandle(),
            draftTracker = tracker,
        )
        advanceUntilIdle()

        assertThat(tracker.hasUnsavedDraft.value).isFalse()
    }

    @Test
    fun editingAFieldReportsADraft() = runTest(dispatcher.scheduler) {
        val tracker = InMemoryDraftTracker()
        val viewModel = LoggingViewModel(
            symptomRepository = FixedSymptomRepository(MutableStateFlow(emptyList())),
            profileRepository = EmptyProfileRepository(),
            cycleRepository = EmptyCycleRepository(),
            predictionRepository = EmptyPredictionRepository(),
            savedStateHandle = SavedStateHandle(),
            draftTracker = tracker,
        )
        advanceUntilIdle()

        viewModel.selectMood(Mood.Happy)
        advanceUntilIdle()

        assertThat(tracker.hasUnsavedDraft.value).isTrue()
    }

    @Test
    fun savingClearsTheDraft() = runTest(dispatcher.scheduler) {
        val tracker = InMemoryDraftTracker()
        val symptoms = MutableStateFlow(emptyList<SymptomLog>())
        val viewModel = LoggingViewModel(
            symptomRepository = FixedSymptomRepository(symptoms),
            profileRepository = EmptyProfileRepository(),
            cycleRepository = EmptyCycleRepository(),
            predictionRepository = EmptyPredictionRepository(),
            savedStateHandle = SavedStateHandle(),
            draftTracker = tracker,
        )
        advanceUntilIdle()

        viewModel.selectMood(Mood.Happy)
        advanceUntilIdle()
        assertThat(tracker.hasUnsavedDraft.value).isTrue()

        viewModel.save()
        advanceUntilIdle()

        // The entry is now stored, so the editor matches what is saved and
        // the interstitial is no longer suppressed.
        assertThat(tracker.hasUnsavedDraft.value).isFalse()
    }
}
