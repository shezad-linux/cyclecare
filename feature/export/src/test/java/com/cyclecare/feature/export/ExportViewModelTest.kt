package com.cyclecare.feature.export

import com.cyclecare.core.designsystem.text.UiText
import com.cyclecare.core.domain.ExportRepository
import com.cyclecare.core.domain.ImportResult
import com.cyclecare.core.model.ExportArtifact
import com.cyclecare.core.model.ExportFormat
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class ExportViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeExportRepository(
        private val onRequest: (ExportFormat) -> ExportArtifact = ::defaultArtifact,
        private val onBackup: (CharArray) -> ExportArtifact = { defaultArtifact(ExportFormat.EncryptedBackup) },
        private val onImport: (InputStream, CharArray) -> ImportResult = { _, _ -> ImportResult.Success(3, 5, true) },
    ) : ExportRepository {
        val requestedFormats = mutableListOf<ExportFormat>()
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun createReport(format: ExportFormat): ExportArtifact {
            requestedFormats += format
            gate?.await()
            return onRequest(format)
        }

        override suspend fun createEncryptedBackup(passphrase: CharArray): ExportArtifact {
            gate?.await()
            return onBackup(passphrase)
        }

        override suspend fun importEncryptedBackup(inputStream: InputStream, passphrase: CharArray): ImportResult {
            gate?.await()
            return onImport(inputStream, passphrase)
        }
    }

    @Test
    fun `initial state exposes idle status`() {
        assertThat(ExportUiState().status).isEqualTo(ExportStatus.Idle)
        assertThat(ExportUiState().isWorking).isFalse()
        assertThat(ExportUiState().lastArtifact).isNull()
    }

    @Test
    fun `createReport in flight exposes working status with format`() = runTest(dispatcher) {
        val repository = FakeExportRepository().apply { gate = CompletableDeferred() }
        val viewModel = ExportViewModel(repository)

        viewModel.createReport(ExportFormat.Pdf)
        runCurrent()

        assertThat(viewModel.uiState.value.isWorking).isTrue()
        assertThat(viewModel.uiState.value.status).isEqualTo(ExportStatus.Working(ExportFormat.Pdf))

        repository.gate?.complete(Unit)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.isWorking).isFalse()
        assertThat(viewModel.uiState.value.status).isEqualTo(ExportStatus.Success(defaultArtifact(ExportFormat.Pdf)))
    }

    @Test
    fun `createReport success exposes typed success status and artifact`() = runTest(dispatcher) {
        val repository = FakeExportRepository()
        val viewModel = ExportViewModel(repository)

        viewModel.createReport(ExportFormat.Pdf)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(repository.requestedFormats).containsExactly(ExportFormat.Pdf)
        assertThat(state.isWorking).isFalse()
        assertThat(state.lastArtifact).isEqualTo(defaultArtifact(ExportFormat.Pdf))
        assertThat(state.status).isEqualTo(ExportStatus.Success(defaultArtifact(ExportFormat.Pdf)))
    }

    @Test
    fun `createReport failure exposes error status and keeps lastArtifact`() = runTest(dispatcher) {
        val repository = FakeExportRepository(onRequest = { throw IllegalStateException("boom") })
        val viewModel = ExportViewModel(repository)

        viewModel.createReport(ExportFormat.Markdown)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isWorking).isFalse()
        assertThat(state.lastArtifact).isNull()
        assertThat(state.status).isEqualTo(ExportStatus.Error)
    }

    @Test
    fun `createEncryptedBackup success updates state to BackupSuccess`() = runTest(dispatcher) {
        val repository = FakeExportRepository()
        val viewModel = ExportViewModel(repository)

        viewModel.createEncryptedBackup("correct-horse-battery-staple".toCharArray())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isWorking).isFalse()
        assertThat(state.lastArtifact?.format).isEqualTo(ExportFormat.EncryptedBackup)
        assertThat(state.status).isInstanceOf(ExportStatus.BackupSuccess::class.java)
    }

    @Test
    fun `createEncryptedBackup failure updates state to BackupFailure`() = runTest(dispatcher) {
        val repository = FakeExportRepository(onBackup = { throw IllegalArgumentException("short") })
        val viewModel = ExportViewModel(repository)

        viewModel.createEncryptedBackup("short".toCharArray())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isWorking).isFalse()
        assertThat(state.status).isInstanceOf(ExportStatus.BackupFailure::class.java)
    }

    @Test
    fun `importEncryptedBackup success updates state to ImportSuccess`() = runTest(dispatcher) {
        val repository = FakeExportRepository(
            onImport = { _, _ -> ImportResult.Success(cyclesImported = 4, symptomsImported = 10, profileImported = true) },
        )
        val viewModel = ExportViewModel(repository)

        viewModel.importEncryptedBackup(ByteArrayInputStream(byteArrayOf(1, 2, 3)), "passphrase123".toCharArray())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isWorking).isFalse()
        assertThat(state.status).isEqualTo(ExportStatus.ImportSuccess(cycles = 4, symptoms = 10))
    }

    @Test
    fun `importEncryptedBackup with invalid passphrase updates state to ImportFailure with invalid passphrase res`() = runTest(dispatcher) {
        val repository = FakeExportRepository(
            onImport = { _, _ -> ImportResult.Failure.InvalidPassphrase },
        )
        val viewModel = ExportViewModel(repository)

        viewModel.importEncryptedBackup(ByteArrayInputStream(byteArrayOf(1, 2, 3)), "wrongpass".toCharArray())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isWorking).isFalse()
        assertThat(state.status).isEqualTo(ExportStatus.ImportFailure(R.string.import_invalid_passphrase))
    }

    @Test
    fun `idle status maps to idle hint resource`() {
        assertThat(ExportStatus.Idle.toUiText())
            .isEqualTo(UiText.Resource(R.string.export_idle_hint))
    }

    @Test
    fun `working status maps to per-format generating resources`() {
        assertThat(ExportStatus.Working(ExportFormat.Markdown).toUiText())
            .isEqualTo(UiText.Resource(R.string.generating_markdown_report))
        assertThat(ExportStatus.Working(ExportFormat.Pdf).toUiText())
            .isEqualTo(UiText.Resource(R.string.generating_pdf_report))
        assertThat(ExportStatus.Working(ExportFormat.EncryptedBackup).toUiText())
            .isEqualTo(UiText.Resource(R.string.generating_encrypted_backup))
    }

    @Test
    fun `success status maps to per-format saved resources with filename as arg`() {
        val markdown = defaultArtifact(ExportFormat.Markdown)
        val pdf = defaultArtifact(ExportFormat.Pdf)
        val backup = defaultArtifact(ExportFormat.EncryptedBackup)

        assertThat(ExportStatus.Success(markdown).toUiText())
            .isEqualTo(UiText.Resource(R.string.saved_markdown_report, listOf("cyclecare-report.md")))
        assertThat(ExportStatus.Success(pdf).toUiText())
            .isEqualTo(UiText.Resource(R.string.saved_pdf_report, listOf("cyclecare-report.pdf")))
        assertThat(ExportStatus.Success(backup).toUiText())
            .isEqualTo(UiText.Resource(R.string.saved_encrypted_backup, listOf("cyclecare-backup.ccvault")))
    }

    @Test
    fun `error status maps to failure resource`() {
        assertThat(ExportStatus.Error.toUiText())
            .isEqualTo(UiText.Resource(R.string.export_failed_locally))
    }

    @Test
    fun `format labels map to localized resources rather than enum names`() {
        assertThat(ExportFormat.Markdown.labelRes()).isEqualTo(R.string.format_markdown)
        assertThat(ExportFormat.Pdf.labelRes()).isEqualTo(R.string.format_pdf)
        assertThat(ExportFormat.EncryptedBackup.labelRes()).isEqualTo(R.string.format_encrypted_backup)
    }

    private companion object {
        fun defaultArtifact(format: ExportFormat): ExportArtifact {
            val extension = when (format) {
                ExportFormat.Pdf -> "pdf"
                ExportFormat.Markdown -> "md"
                ExportFormat.EncryptedBackup -> "ccvault"
            }
            val mime = when (format) {
                ExportFormat.Pdf -> "application/pdf"
                ExportFormat.Markdown -> "text/markdown"
                ExportFormat.EncryptedBackup -> "application/octet-stream"
            }
            val name = when (format) {
                ExportFormat.EncryptedBackup -> "cyclecare-backup.ccvault"
                else -> "cyclecare-report.$extension"
            }
            return ExportArtifact(
                format = format,
                displayName = name,
                absolutePath = "/files/reports/$name",
                mimeType = mime,
                generatedAt = LocalDate.of(2026, 10, 18),
            )
        }
    }
}
