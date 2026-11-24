package app.flipscan.ui

import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

enum class LockerAuthStatus {
    /** Fingerprint or face enrolled; the prompt also offers the screen lock. */
    BIOMETRIC,

    /** No biometric enrolled, but the phone has a PIN/pattern/password to fall back to. */
    CREDENTIAL,

    /** Neither: the user must set a screen lock before the locker can open. */
    NO_LOCK
}

/**
 * Thin wrapper around Android's own BiometricPrompt.
 *
 * FlipScan never sees or stores the PIN/pattern/password and does not implement any biometric
 * matching itself. The prompt belongs to the system; this only asks it for a yes/no answer.
 */
object LockerAuth {
    fun status(ctx: Context): LockerAuthStatus {
        val keyguard = ctx.getSystemService(KeyguardManager::class.java)
        val secure = keyguard?.isDeviceSecure == true
        val can = try {
            BiometricManager.from(ctx).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        } catch (e: Exception) {
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
        }
        return when {
            can == BiometricManager.BIOMETRIC_SUCCESS -> LockerAuthStatus.BIOMETRIC
            secure -> LockerAuthStatus.CREDENTIAL
            else -> LockerAuthStatus.NO_LOCK
        }
    }

    /**
     * Shows the system prompt. [onCancelled] covers the back button and the "Use PIN" path,
     * [onError] only receives messages that are safe to display.
     * Returns false when no prompt could be started at all.
     */
    fun unlock(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onCancelled: () -> Unit,
        onError: (String) -> Unit
    ): Boolean {
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                when (errorCode) {
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_CANCELED -> onCancelled()
                    else -> onError(messageFor(errorCode))
                }
            }
        }
        val prompt = try {
            BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
        } catch (e: Exception) {
            onError("Authentication is unavailable on this device.")
            return false
        }

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Digital Locker")
            .setSubtitle("Confirm it's you to open your encrypted documents")
            .setConfirmationRequired(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            info.setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
        } else {
            @Suppress("DEPRECATION")
            info.setDeviceCredentialAllowed(true)
        }
        return try {
            prompt.authenticate(info.build())
            true
        } catch (e: Exception) {
            onError("Authentication could not start. Try again.")
            false
        }
    }

    private fun messageFor(code: Int): String = when (code) {
        BiometricPrompt.ERROR_LOCKOUT ->
            "Too many attempts. Wait about 30 seconds, then try again."
        BiometricPrompt.ERROR_LOCKOUT_PERMANENT ->
            "Biometric unlock is locked. Unlock the phone with its PIN, then try again."
        BiometricPrompt.ERROR_NO_BIOMETRICS ->
            "No fingerprint or face is set up. Add one in the phone's security settings."
        BiometricPrompt.ERROR_HW_NOT_PRESENT, BiometricPrompt.ERROR_HW_UNAVAILABLE ->
            "This phone's biometric sensor is not available. Use your screen lock instead."
        BiometricPrompt.ERROR_SECURITY_UPDATE_REQUIRED ->
            "The phone needs a security update before biometrics can be used."
        BiometricPrompt.ERROR_TIMEOUT ->
            "The prompt timed out. Try again."
        else -> "Could not verify it's you. Try again."
    }
}

/** The prompt needs a FragmentActivity; the app's MainActivity is one. */
internal fun Context.findFragmentActivity(): FragmentActivity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is FragmentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
