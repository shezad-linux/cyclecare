package com.cyclecare.feature.privacy

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cyclecare.core.designsystem.text.UiText
import com.cyclecare.core.designsystem.text.uiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SecureAuthCapability {
    None,
    Biometric,
    DeviceCredential,
    Both,
    ;

    val supportsBiometric: Boolean
        get() = this == Biometric || this == Both

    val supportsDeviceCredential: Boolean
        get() = this == DeviceCredential || this == Both

    fun summaryLabel(): UiText = when (this) {
        None -> uiText(R.string.no_secure_recovery)
        Biometric -> uiText(R.string.biometric_recovery)
        DeviceCredential -> uiText(R.string.screen_lock_recovery)
        Both -> uiText(R.string.biometric_and_screen_lock_recovery)
    }
}

enum class SecureAuthPreference {
    PreferBiometric,
    DeviceCredentialOnly,
}

@Stable
interface SecureAuthController {
    val capability: StateFlow<SecureAuthCapability>

    fun authenticate(
        preference: SecureAuthPreference,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit,
        onCancel: () -> Unit = {},
    )
}

fun resolveSecureAuthCapability(context: Context): SecureAuthCapability {
    val biometricAvailable =
        BiometricManager.from(context).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK,
        ) == BiometricManager.BIOMETRIC_SUCCESS

    val keyguardManager = context.getSystemService(KeyguardManager::class.java)
    val deviceCredentialAvailable = keyguardManager?.isDeviceSecure == true

    return when {
        biometricAvailable && deviceCredentialAvailable -> SecureAuthCapability.Both
        biometricAvailable -> SecureAuthCapability.Biometric
        deviceCredentialAvailable -> SecureAuthCapability.DeviceCredential
        else -> SecureAuthCapability.None
    }
}

@Composable
fun rememberPlatformSecureAuthController(): SecureAuthController {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = context.findFragmentActivity()
    val capabilityState = remember {
        MutableStateFlow(resolveSecureAuthCapability(context))
    }
    var pendingCredentialRequest by remember {
        mutableStateOf<PendingCredentialRequest?>(null)
    }

    val credentialLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val request = pendingCredentialRequest ?: return@rememberLauncherForActivityResult
        pendingCredentialRequest = null
        capabilityState.value = resolveSecureAuthCapability(context)
        if (result.resultCode == Activity.RESULT_OK) {
            request.onSuccess()
        } else {
            request.onCancel()
        }
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_RESUME) {
                capabilityState.value = resolveSecureAuthCapability(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return remember(context, activity, credentialLauncher) {
        object : SecureAuthController {
            override val capability: StateFlow<SecureAuthCapability> = capabilityState.asStateFlow()

            override fun authenticate(
                preference: SecureAuthPreference,
                title: String,
                subtitle: String,
                onSuccess: () -> Unit,
                onFailure: (String) -> Unit,
                onCancel: () -> Unit,
            ) {
                val currentCapability = capabilityState.value
                when {
                    preference == SecureAuthPreference.DeviceCredentialOnly -> {
                        launchDeviceCredentialConfirmation(
                            context = context,
                            title = title,
                            subtitle = subtitle,
                            launchIntent = credentialLauncher::launch,
                            pendingCredentialRequest = { request -> pendingCredentialRequest = request },
                            onFailure = onFailure,
                            onSuccess = onSuccess,
                            onCancel = onCancel,
                        )
                    }

                    currentCapability.supportsBiometric -> {
                        val fragmentActivity = activity
                        if (fragmentActivity == null) {
                            onFailure(context.getString(R.string.secure_authentication_unavailable_in_this_screen))
                            return
                        }
                        val executor = ContextCompat.getMainExecutor(context)
                        val biometricPrompt = BiometricPrompt(
                            fragmentActivity,
                            executor,
                            object : BiometricPrompt.AuthenticationCallback() {
                                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                    capabilityState.value = resolveSecureAuthCapability(context)
                                    onSuccess()
                                }

                                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                    capabilityState.value = resolveSecureAuthCapability(context)
                                    when {
                                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                                            currentCapability.supportsDeviceCredential -> {
                                            launchDeviceCredentialConfirmation(
                                                context = context,
                                                title = title,
                                                subtitle = subtitle,
                                                launchIntent = credentialLauncher::launch,
                                                pendingCredentialRequest = { request -> pendingCredentialRequest = request },
                                                onFailure = onFailure,
                                                onSuccess = onSuccess,
                                                onCancel = onCancel,
                                            )
                                        }

                                        errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                                            errorCode == BiometricPrompt.ERROR_CANCELED -> onCancel()

                                        else -> onFailure(errString.toString())
                                    }
                                }

                                override fun onAuthenticationFailed() {
                                    onFailure(context.getString(R.string.biometric_not_recognized_try_again_or_use_your_screen_lock))
                                }
                            },
                        )

                        val promptInfo = BiometricPrompt.PromptInfo.Builder()
                            .setTitle(title)
                            .setSubtitle(subtitle)
                            .apply {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    setAllowedAuthenticators(
                                        BiometricManager.Authenticators.BIOMETRIC_STRONG or
                                            BiometricManager.Authenticators.BIOMETRIC_WEAK,
                                    )
                                }
                                setNegativeButtonText(
                                    if (currentCapability.supportsDeviceCredential) {
                                        context.getString(R.string.use_screen_lock)
                                    } else {
                                        context.getString(R.string.cancel)
                                    },
                                )
                            }
                            .build()

                        biometricPrompt.authenticate(promptInfo)
                    }

                    currentCapability.supportsDeviceCredential -> {
                        launchDeviceCredentialConfirmation(
                            context = context,
                            title = title,
                            subtitle = subtitle,
                            launchIntent = credentialLauncher::launch,
                            pendingCredentialRequest = { request -> pendingCredentialRequest = request },
                            onFailure = onFailure,
                            onSuccess = onSuccess,
                            onCancel = onCancel,
                        )
                    }

                    else -> onFailure(context.getString(R.string.secure_recovery_unavailable_on_this_device))
                }
            }
        }
    }
}

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}

private data class PendingCredentialRequest(
    val onSuccess: () -> Unit,
    val onCancel: () -> Unit,
)

private fun launchDeviceCredentialConfirmation(
    context: Context,
    title: String,
    subtitle: String,
    launchIntent: (Intent) -> Unit,
    pendingCredentialRequest: (PendingCredentialRequest) -> Unit,
    onFailure: (String) -> Unit,
    onSuccess: () -> Unit,
    onCancel: () -> Unit,
) {
    val keyguardManager = context.getSystemService(KeyguardManager::class.java)
    if (keyguardManager?.isDeviceSecure != true) {
        onFailure(context.getString(R.string.set_up_a_screen_lock_for_secure_recovery))
        return
    }

    @Suppress("DEPRECATION")
    val intent = keyguardManager.createConfirmDeviceCredentialIntent(title, subtitle)
    if (intent == null) {
        onFailure(context.getString(R.string.screen_lock_confirmation_unavailable_on_this_device))
        return
    }

    pendingCredentialRequest(PendingCredentialRequest(onSuccess = onSuccess, onCancel = onCancel))
    launchIntent(intent)
}
