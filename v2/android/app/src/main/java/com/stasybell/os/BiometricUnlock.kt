package com.stasybell.os

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

fun canUnlockWithFingerprint(activity: FragmentActivity): Boolean =
    BiometricManager.from(activity).canAuthenticate(
        BiometricManager.Authenticators.BIOMETRIC_STRONG,
    ) == BiometricManager.BIOMETRIC_SUCCESS

/**
 * Optional biometric gate before a saved owner session is restored.
 * The user can always return to the password login screen if it is cancelled.
 */
suspend fun confirmFingerprint(activity: FragmentActivity): Boolean =
    suspendCancellableCoroutine { continuation ->
        if (!canUnlockWithFingerprint(activity)) {
            continuation.resume(false)
            return@suspendCancellableCoroutine
        }
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (continuation.isActive) continuation.resume(true)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (continuation.isActive) continuation.resume(false)
                }
            })
        continuation.invokeOnCancellation { prompt.cancelAuthentication() }
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Stasy Bell OS")
                .setSubtitle("Разблокировать личный кабинет")
                .setNegativeButtonText("Войти с паролем")
                .build(),
        )
    }
