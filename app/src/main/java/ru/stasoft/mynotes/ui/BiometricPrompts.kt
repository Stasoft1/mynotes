package ru.stasoft.mynotes.ui

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import javax.crypto.Cipher

/** Показывает системное окно отпечатка. [onSuccess] получает тот же шифр, уже разрешённый отпечатком. */
fun showBiometricPrompt(
    activity: FragmentActivity,
    title: String,
    negativeText: String,
    cipher: Cipher,
    onSuccess: (Cipher) -> Unit,
    onFailure: (String) -> Unit,
) {
    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            val c = result.cryptoObject?.cipher
            if (c != null) onSuccess(c) else onFailure("Не удалось подтвердить отпечаток")
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            // Отмена пользователем — не ошибка, просто остаётся ввод пароля.
            if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                errorCode == BiometricPrompt.ERROR_CANCELED
            ) return
            onFailure(errString.toString())
        }
    }
    val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setNegativeButtonText(negativeText)
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        .build()
    prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
}
