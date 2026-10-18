@file:OptIn(ExperimentalLayoutApi::class)

package com.cyclecare.feature.export

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
import com.cyclecare.core.designsystem.text.UiText
import com.cyclecare.core.designsystem.text.resolve
import com.cyclecare.core.designsystem.text.uiText
import com.cyclecare.core.designsystem.token.CycleCareSpace
import com.cyclecare.core.domain.ExportRepository
import com.cyclecare.core.domain.ImportResult
import com.cyclecare.core.model.ExportArtifact
import com.cyclecare.core.model.ExportFormat
import com.cyclecare.core.security.PassphrasePolicy
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ExportStatus {
    data object Idle : ExportStatus
    data class Working(val format: ExportFormat) : ExportStatus
    data class Success(val artifact: ExportArtifact) : ExportStatus
    data object Error : ExportStatus
    data class BackupSuccess(val artifact: ExportArtifact) : ExportStatus
    data class ImportSuccess(val cycles: Int, val symptoms: Int) : ExportStatus
    data class ImportFailure(val failureRes: Int) : ExportStatus
    data class BackupFailure(val message: String) : ExportStatus
}

data class ExportUiState(
    val lastArtifact: ExportArtifact? = null,
    val status: ExportStatus = ExportStatus.Idle,
    val isWorking: Boolean = false,
)

class ExportViewModel(
    private val exportRepository: ExportRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = mutableState.asStateFlow()

    fun createReport(format: ExportFormat) {
        viewModelScope.launch {
            mutableState.update { it.copy(isWorking = true, status = ExportStatus.Working(format)) }
            runCatching {
                exportRepository.createReport(format)
            }.onSuccess { artifact ->
                mutableState.update {
                    it.copy(
                        lastArtifact = artifact,
                        isWorking = false,
                        status = ExportStatus.Success(artifact),
                    )
                }
            }.onFailure {
                mutableState.update {
                    it.copy(
                        isWorking = false,
                        status = ExportStatus.Error,
                    )
                }
            }
        }
    }

    fun createEncryptedBackup(passphrase: CharArray) {
        viewModelScope.launch {
            mutableState.update { it.copy(isWorking = true, status = ExportStatus.Working(ExportFormat.EncryptedBackup)) }
            runCatching {
                exportRepository.createEncryptedBackup(passphrase)
            }.onSuccess { artifact ->
                mutableState.update {
                    it.copy(
                        lastArtifact = artifact,
                        isWorking = false,
                        status = ExportStatus.BackupSuccess(artifact),
                    )
                }
            }.onFailure { error ->
                mutableState.update {
                    it.copy(
                        isWorking = false,
                        status = ExportStatus.BackupFailure(error.message ?: "Backup failed"),
                    )
                }
            }
        }
    }

    fun importEncryptedBackup(inputStream: InputStream, passphrase: CharArray) {
        viewModelScope.launch {
            mutableState.update { it.copy(isWorking = true) }
            runCatching {
                exportRepository.importEncryptedBackup(inputStream, passphrase)
            }.onSuccess { result ->
                when (result) {
                    is ImportResult.Success -> {
                        mutableState.update {
                            it.copy(
                                isWorking = false,
                                status = ExportStatus.ImportSuccess(result.cyclesImported, result.symptomsImported),
                            )
                        }
                    }
                    is ImportResult.Failure.InvalidPassphrase -> {
                        mutableState.update {
                            it.copy(
                                isWorking = false,
                                status = ExportStatus.ImportFailure(R.string.import_invalid_passphrase),
                            )
                        }
                    }
                    is ImportResult.Failure.CorruptedFile -> {
                        mutableState.update {
                            it.copy(
                                isWorking = false,
                                status = ExportStatus.ImportFailure(R.string.import_corrupted_file),
                            )
                        }
                    }
                    is ImportResult.Failure.UnsupportedVersion -> {
                        mutableState.update {
                            it.copy(
                                isWorking = false,
                                status = ExportStatus.ImportFailure(R.string.import_unsupported_version),
                            )
                        }
                    }
                    is ImportResult.Failure.Error -> {
                        mutableState.update {
                            it.copy(
                                isWorking = false,
                                status = ExportStatus.ImportFailure(R.string.import_failed),
                            )
                        }
                    }
                }
            }.onFailure {
                mutableState.update {
                    it.copy(
                        isWorking = false,
                        status = ExportStatus.ImportFailure(R.string.import_failed),
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

internal fun ExportStatus.toUiText(): UiText = when (this) {
    ExportStatus.Idle -> uiText(R.string.export_idle_hint)
    is ExportStatus.Working -> when (format) {
        ExportFormat.Markdown -> uiText(R.string.generating_markdown_report)
        ExportFormat.Pdf -> uiText(R.string.generating_pdf_report)
        ExportFormat.EncryptedBackup -> uiText(R.string.generating_encrypted_backup)
    }
    is ExportStatus.Success -> when (artifact.format) {
        ExportFormat.Markdown -> uiText(R.string.saved_markdown_report, artifact.displayName)
        ExportFormat.Pdf -> uiText(R.string.saved_pdf_report, artifact.displayName)
        ExportFormat.EncryptedBackup -> uiText(R.string.saved_encrypted_backup, artifact.displayName)
    }
    ExportStatus.Error -> uiText(R.string.export_failed_locally)
    is ExportStatus.BackupSuccess -> uiText(R.string.saved_encrypted_backup, artifact.displayName)
    is ExportStatus.ImportSuccess -> uiText(R.string.import_success_message, cycles, symptoms)
    is ExportStatus.ImportFailure -> uiText(failureRes)
    is ExportStatus.BackupFailure -> uiText(R.string.export_failed_locally)
}

@StringRes
internal fun ExportFormat.labelRes(): Int = when (this) {
    ExportFormat.Markdown -> R.string.format_markdown
    ExportFormat.Pdf -> R.string.format_pdf
    ExportFormat.EncryptedBackup -> R.string.format_encrypted_backup
}

@Composable
fun ExportRoute(factory: ExportViewModelFactory) {
    val context = LocalContext.current
    val viewModel: ExportViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var showExportPasswordDialog by remember { mutableStateOf(false) }
    var showImportPasswordDialog by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            pendingImportUri = uri
            showImportPasswordDialog = true
        }
    }

    ExportScreen(
        uiState = uiState,
        onCreateMarkdownReport = { viewModel.createReport(ExportFormat.Markdown) },
        onCreatePdfReport = { viewModel.createReport(ExportFormat.Pdf) },
        onRequestCreateBackup = { showExportPasswordDialog = true },
        onRequestImportBackup = { filePickerLauncher.launch(arrayOf("*/*")) },
        onOpenArtifact = { artifact -> openArtifact(context, artifact) },
        onShareArtifact = { artifact -> shareArtifact(context, artifact) },
    )

    if (showExportPasswordDialog) {
        PassphraseDialog(
            title = stringResource(R.string.create_encrypted_backup),
            message = stringResource(R.string.create_passphrase_message),
            confirmButtonLabel = stringResource(R.string.confirm),
            onDismiss = { showExportPasswordDialog = false },
            onConfirm = { passphrase ->
                showExportPasswordDialog = false
                viewModel.createEncryptedBackup(passphrase.toCharArray())
            },
        )
    }

    if (showImportPasswordDialog && pendingImportUri != null) {
        PassphraseDialog(
            title = stringResource(R.string.restore_encrypted_backup),
            message = stringResource(R.string.restore_passphrase_message),
            confirmButtonLabel = stringResource(R.string.restore_encrypted_backup),
            onDismiss = {
                showImportPasswordDialog = false
                pendingImportUri = null
            },
            onConfirm = { passphrase ->
                val uri = pendingImportUri
                showImportPasswordDialog = false
                pendingImportUri = null
                if (uri != null) {
                    val stream = context.contentResolver.openInputStream(uri)
                    if (stream != null) {
                        viewModel.importEncryptedBackup(stream, passphrase.toCharArray())
                    }
                }
            },
        )
    }
}

@Composable
private fun ExportScreen(
    uiState: ExportUiState,
    onCreateMarkdownReport: () -> Unit,
    onCreatePdfReport: () -> Unit,
    onRequestCreateBackup: () -> Unit,
    onRequestImportBackup: () -> Unit,
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
            subtitle = uiState.status.toUiText().resolve(),
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
        DashboardSectionCard(
            title = stringResource(R.string.encrypted_backup_and_restore),
            subtitle = stringResource(R.string.backup_restore_description),
            eyebrow = stringResource(R.string.storage),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space12)) {
                Button(
                    onClick = onRequestCreateBackup,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isWorking,
                ) {
                    Text(stringResource(R.string.create_encrypted_backup))
                }
                FilledTonalButton(
                    onClick = onRequestImportBackup,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isWorking,
                ) {
                    Text(stringResource(R.string.restore_encrypted_backup))
                }
            }
        }
        uiState.lastArtifact?.let { artifact ->
            DashboardSectionCard(
                title = stringResource(R.string.latest_export),
                subtitle = artifact.displayName,
                eyebrow = stringResource(artifact.format.labelRes()),
                accent = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space12)) {
                    if (artifact.format != ExportFormat.EncryptedBackup) {
                        OutlinedButton(onClick = { onOpenArtifact(artifact) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.open_latest_export))
                        }
                    }
                    OutlinedButton(onClick = { onShareArtifact(artifact) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.share_latest_export))
                    }
                }
            }
        }
    }
}

@Composable
private fun PassphraseDialog(
    title: String,
    message: String,
    confirmButtonLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var passphrase by remember { mutableStateOf("") }
    val isAcceptable = PassphrasePolicy.isAcceptable(passphrase.toCharArray())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space8)) {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(CycleCareSpace.space4))
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text(stringResource(R.string.passphrase_hint)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                    isError = passphrase.isNotEmpty() && !isAcceptable,
                    supportingText = {
                        if (passphrase.isNotEmpty() && !isAcceptable) {
                            Text(
                                stringResource(R.string.passphrase_too_short),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(passphrase) },
                enabled = isAcceptable,
            ) {
                Text(confirmButtonLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
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
        Intent.createChooser(shareIntent, uiText(R.string.share_report_chooser_title).resolve(context))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

private fun ExportArtifact.toShareUri(context: Context) = FileProvider.getUriForFile(
    context,
    "${context.packageName}.fileprovider",
    File(absolutePath),
)
