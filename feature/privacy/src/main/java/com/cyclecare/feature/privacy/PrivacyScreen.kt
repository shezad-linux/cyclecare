package com.cyclecare.feature.privacy

import com.cyclecare.core.designsystem.token.CycleCareSpace
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyclecare.core.designsystem.DashboardKeyValueRow
import com.cyclecare.core.designsystem.DashboardPill
import com.cyclecare.core.designsystem.DashboardSectionCard
import com.cyclecare.core.designsystem.GradientHeaderCard
import com.cyclecare.core.designsystem.text.UiText
import com.cyclecare.core.designsystem.text.resolve
import com.cyclecare.core.designsystem.text.uiText
import com.cyclecare.core.domain.PrivacyRepository
import com.cyclecare.core.domain.PrivacyRules
import com.cyclecare.core.model.ConsentState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PrivacyUiState(
    val appLockEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val hasPinConfigured: Boolean = false,
    val hideNotificationPreview: Boolean = true,
    val entryMode: PrivacyEntryMode = PrivacyEntryMode.Setup,
    val pinEntry: String = "",
    val secureAuthCapability: SecureAuthCapability = SecureAuthCapability.None,
    val message: UiText? = null,
)

