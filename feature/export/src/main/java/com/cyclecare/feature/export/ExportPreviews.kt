@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.cyclecare.feature.export

import androidx.compose.runtime.Composable
import com.cyclecare.core.designsystem.CycleCareTheme
import com.cyclecare.core.designsystem.ScreenWidthPreviews
import com.cyclecare.core.model.ExportArtifact
import com.cyclecare.core.model.ExportFormat
import java.time.LocalDate

@ScreenWidthPreviews
@Composable
internal fun ExportScreenPreview() {
    CycleCareTheme {
        ExportScreen(
            uiState = previewExportState(),
            onCreateMarkdownReport = {},
            onCreatePdfReport = {},
            onRequestCreateBackup = {},
            onRequestImportBackup = {},
            onOpenArtifact = {},
            onShareArtifact = {},
        )
    }
}

@ScreenWidthPreviews
@Composable
internal fun ExportWorkingScreenPreview() {
    CycleCareTheme {
        ExportScreen(
            uiState = previewExportState().copy(
                isWorking = true,
                status = ExportStatus.Working(ExportFormat.Markdown),
            ),
            onCreateMarkdownReport = {},
            onCreatePdfReport = {},
            onRequestCreateBackup = {},
            onRequestImportBackup = {},
            onOpenArtifact = {},
            onShareArtifact = {},
        )
    }
}

@ScreenWidthPreviews
@Composable
internal fun ExportFailedScreenPreview() {
    CycleCareTheme {
        ExportScreen(
            uiState = previewExportState().copy(status = ExportStatus.Error),
            onCreateMarkdownReport = {},
            onCreatePdfReport = {},
            onRequestCreateBackup = {},
            onRequestImportBackup = {},
            onOpenArtifact = {},
            onShareArtifact = {},
        )
    }
}

/** The export screen with a report already written. */
internal fun previewExportState() = ExportUiState(
    lastArtifact = ExportArtifact(
        format = ExportFormat.Markdown,
        displayName = "cyclecare-report.md",
        absolutePath = "/tmp/cyclecare-report.md",
        mimeType = "text/markdown",
        generatedAt = LocalDate.of(2026, 3, 16),
    ),
    status = ExportStatus.Success(
        ExportArtifact(
            format = ExportFormat.Markdown,
            displayName = "cyclecare-report.md",
            absolutePath = "/tmp/cyclecare-report.md",
            mimeType = "text/markdown",
            generatedAt = LocalDate.of(2026, 3, 16),
        ),
    ),
    isWorking = false,
)
