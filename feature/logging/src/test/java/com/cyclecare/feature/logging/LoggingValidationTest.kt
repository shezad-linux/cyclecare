package com.cyclecare.feature.logging

import androidx.lifecycle.SavedStateHandle
import com.cyclecare.core.domain.CycleRepository
import com.cyclecare.core.domain.PredictionRepository
import com.cyclecare.core.domain.ProfileRepository
import com.cyclecare.core.domain.SymptomRepository
import com.cyclecare.core.model.CycleRecord
import com.cyclecare.core.model.PredictionSnapshot
import com.cyclecare.core.model.SymptomLog
import com.cyclecare.core.model.UserProfile
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoggingValidationTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun notesLimit_allowsFiveHundredCharacters() {
        val notes = "a".repeat(MAX_SYMPTOM_NOTE_LENGTH)

        val state = buildLoggingUiState(
            today = LocalDate.of(2026, 3, 16),
            profile = null,
            prediction = null,
            todayEntry = null,
            editor = LoggingEditorState(notes = notes),
            saveMessage = null,
            isSaving = false,
        )

        assertThat(state.notesError).isNull()
        assertThat(state.isSaveEnabled).isTrue()
    }

    @Test
    fun notesLimit_rejectsFiveHundredAndOneCharacters() {
        val notes = "a".repeat(MAX_SYMPTOM_NOTE_LENGTH + 1)

        val state = buildLoggingUiState(
            today = LocalDate.of(2026, 3, 16),
            profile = null,
            prediction = null,
            todayEntry = null,
            editor = LoggingEditorState(notes = notes),
            saveMessage = null,
            isSaving = false,
        )

        assertThat(state.notesError).isEqualTo("Notes can be up to 500 characters.")
        assertThat(state.isSaveEnabled).isFalse()
    }

    @Test
    fun saveGuard_ignoresRepeatedTapsWhileSaveIsInFlight() = runTest(dispatcher.scheduler) {
        val symptomRepository = BlockingSymptomRepository()
        val viewModel = LoggingViewModel(
            symptomRepository = symptomRepository,
            profileRepository = EmptyProfileRepository(),
            cycleRepository = EmptyCycleRepository(),
            predictionRepository = EmptyPredictionRepository(),
            savedStateHandle = SavedStateHandle(),
        )

        launch { viewModel.save() }
        runCurrent()
        launch { viewModel.save() }
        runCurrent()

        assertThat(symptomRepository.writeAttempts).isEqualTo(1)

        symptomRepository.release()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isSaving).isFalse()
        assertThat(symptomRepository.writeAttempts).isEqualTo(1)
    }
}

private class EmptyProfileRepository : ProfileRepository {
    private val state = MutableStateFlow<UserProfile?>(null)

    override fun observeProfile(): Flow<UserProfile?> = state.asStateFlow()

    override suspend fun saveProfile(profile: UserProfile) {
        state.value = profile
    }

    override suspend fun updateRemindersEnabled(enabled: Boolean) = Unit
}

private class EmptyCycleRepository : CycleRepository {
    private val state = MutableStateFlow<List<CycleRecord>>(emptyList())

    override fun observeCycles(): Flow<List<CycleRecord>> = state.asStateFlow()

    override suspend fun upsertCycle(record: CycleRecord) {
        state.value = state.value.filterNot { it.id == record.id } + record
    }

    override suspend fun deleteCycle(id: String) {
        state.value = state.value.filterNot { it.id == id }
    }
}

private class EmptyPredictionRepository : PredictionRepository {
    private val state = MutableStateFlow<PredictionSnapshot?>(null)

    override fun observePrediction(): Flow<PredictionSnapshot?> = state.asStateFlow()
}

private class BlockingSymptomRepository : SymptomRepository {
    private val state = MutableStateFlow<List<SymptomLog>>(emptyList())
    private val gate = CompletableDeferred<Unit>()
    var writeAttempts: Int = 0
        private set

    override fun observeSymptoms(): Flow<List<SymptomLog>> = state.asStateFlow()

    override fun observeSymptomForDate(date: LocalDate): Flow<SymptomLog?> =
        state.asStateFlow().map { logs -> logs.firstOrNull { it.date == date } }

    override suspend fun upsertSymptom(log: SymptomLog) {
        writeAttempts += 1
        gate.await()
        state.value = state.value.filterNot { it.id == log.id } + log
    }

    fun release() {
        gate.complete(Unit)
    }
}
