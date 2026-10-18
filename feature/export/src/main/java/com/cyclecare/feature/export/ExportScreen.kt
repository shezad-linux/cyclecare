@file:OptIn(ExperimentalLayoutApi::class)

package com.cyclecare.feature.export

import com.cyclecare.core.designsystem.token.CycleCareSpace
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyclecare.core.designsystem.DashboardSectionCard
import com.cyclecare.core.designsystem.GradientHeaderCard
import com.cyclecare.core.designsystem.MetricCard
import com.cyclecare.core.domain.ExportRepository
import com.cyclecare.core.model.ExportArtifact
import com.cyclecare.core.model.ExportFormat
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExportUiState(
    val lastArtifact: ExportArtifact? = null,
    val statusMessage: String = "Generate a local markdown or PDF report and keep it on-device.",
    val isWorking: Boolean = false,
)

class ExportViewModel(
    private val exportRepository: ExportRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = mutableState.asStateFlow()

    fun createReport(format: ExportFormat) {
        viewModelScope.launch {
            mutableState.update { it.copy(isWorking = true, statusMessage = "Generating ${format.name.lowercase()} report...") }
            runCatching {
                exportRepository.createReport(format)
            }.onSuccess { artifact ->
                mutableState.update {
                    it.copy(
                        lastArtifact = artifact,
                        isWorking = false,
                        statusMessage = "Saved ${artifact.format.name.lowercase()} report to ${artifact.displayName}.",
                    )
                }
            }.onFailure {
                mutableState.update {
                    it.copy(
                        isWorking = false,
                        statusMessage = "Export failed locally. Please try again.",
                    )
                }
            }
        }
    }
}

class ExportViewModelFactory(
    private val exportRepository: ExportRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = ExportViewModel(exportRepository) as T
}

@Composable
fun ExportRoute(factory: ExportViewModelFactory) {
    val context = LocalContext.current
    val viewModel: ExportViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ExportScreen(
        uiState = uiState,
        onCreateMarkdownReport = { viewModel.createReport(ExportFormat.Markdown) },
        onCreatePdfReport = { viewModel.createReport(ExportFormat.Pdf) },
        onOpenArtifact = { artifact -> openArtifact(context, artifact) },
        onShareArtifact = { artifact -> shareArtifact(context, artifact) },
    )
}

@Composable
private fun ExportScreen(
    uiState: ExportUiState,
    onCreateMarkdownReport: () -> Unit,
    onCreatePdfReport: () -> Unit,
    onOpenArtifact: (ExportArtifact) -> Unit,
    onShareArtifact: (ExportArtifact) -> Unit,
) {
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(CycleCareSpace.space16),
        verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space16),
    ) {
        GradientHeaderCard(
            title = stringResource(R.string.export_report),
            subtitle = uiState.statusMessage,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(CycleCareSpace.space12),
            verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space12),
            modifier = Modifier.fillMaxWidth(),
        ) {
            MetricCard(
                label = stringResource(R.string.formats),
                value = stringResource(R.string.markdown_plus_pdf),
                modifier = Modifier.widthIn(min = 150.dp),
                accent = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.86f),
            )
            MetricCard(
                label = stringResource(R.string.storage),
                value = stringResource(R.string.on_device),
                modifier = Modifier.widthIn(min = 150.dp),
                accent = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.86f),
            )
            MetricCard(
                label = stringResource(R.string.status),
                value = if (uiState.isWorking) {
                    stringResource(R.string.working)
                } else {
                    stringResource(R.string.ready)
                },
                modifier = Modifier.widthIn(min = 150.dp),
                accent = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f),
            )
        }
        DashboardSectionCard(
            title = stringResource(R.string.generate_a_local_report),
            subtitle = stringResource(R.string.create_either_a_markdown_or_pdf_export_without_sending),
            eyebrow = stringResource(R.string.formats),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space12)) {
                Button(
                    onClick = onCreateMarkdownReport,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isWorking,
                ) {
                    Text(stringResource(R.string.create_markdown_report))
                }
                FilledTonalButton(
                    onClick = onCreatePdfReport,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isWorking,
                ) {
                    Text(stringResource(R.string.create_pdf_report))
                }
            }
        }
        uiState.lastArtifact?.let { artifact ->
            DashboardSectionCard(
                title = stringResource(R.string.latest_export),
                subtitle = artifact.displayName,
                eyebrow = artifact.format.name,
                accent = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space12)) {
                    OutlinedButton(onClick = { onOpenArtifact(artifact) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.open_latest_export))
                    }
                    OutlinedButton(onClick = { onShareArtifact(artifact) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.share_latest_export))
                    }
                }
            }
        }
    }
}

private fun openArtifact(
    context: Context,
    artifact: ExportArtifact,
) {
    val uri = artifact.toShareUri(context)
    val openIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, artifact.mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(openIntent)
    } catch (_: ActivityNotFoundException) {
        shareArtifact(context, artifact)
    }
}

private fun shareArtifact(
    context: Context,
    artifact: ExportArtifact,
) {
    val uri = artifact.toShareUri(context)
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = artifact.mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        Intent.createChooser(shareIntent, "Share CycleCare report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

private fun ExportArtifact.toShareUri(context: Context) = FileProvider.getUriForFile(
    context,
    "${context.packageName}.fileprovider",
    File(absolutePath),
)
