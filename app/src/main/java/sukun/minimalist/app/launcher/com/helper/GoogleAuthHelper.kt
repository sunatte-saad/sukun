package sukun.minimalist.app.launcher.com.helper

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import sukun.minimalist.app.launcher.com.R
import sukun.minimalist.app.launcher.com.data.Prefs
import java.security.MessageDigest
import java.util.UUID

/**
 * Wraps Jetpack Credential Manager to sign in with Google. Authenticate-only: the
 * resulting account name/email/photo are stored locally in [Prefs]. There is no backend.
 *
 * Sign-in requires a valid OAuth Web client id in res/values/auth.xml
 * (see the comment in that file). Until then [signIn] returns [SignInResult.Error].
 */
class GoogleAuthHelper(private val context: Context) {

    companion object {
        const val TAG = "SukunAuth"
    }

    private val clearCredentialManager = CredentialManager.create(context)

    data class GoogleAccount(
        val id: String,
        val name: String,
        val email: String,
        val photoUrl: String,
        val idToken: String,
    )

    sealed class SignInResult {
        data class Success(val account: GoogleAccount) : SignInResult()
        /** User dismissed the picker — not an error worth surfacing loudly. */
        data object Cancelled : SignInResult()
        data class Error(val message: String) : SignInResult()
    }

    private fun webClientId(): String {
        val fromResources = context.getString(R.string.default_web_client_id)
        if (fromResources.endsWith(".apps.googleusercontent.com") &&
            !fromResources.startsWith("YOUR_WEB_CLIENT_ID")
        ) {
            return fromResources
        }
        return sukun.minimalist.app.launcher.com.data.Constants.GOOGLE_WEB_CLIENT_ID
    }

    fun isConfigured(): Boolean {
        val clientId = webClientId()
        return clientId.endsWith(".apps.googleusercontent.com") &&
            !clientId.startsWith("YOUR_WEB_CLIENT_ID")
    }

    /**
     * Launches the Google account picker. [activity] must be the foreground Activity so
     * Credential Manager can show its UI. Call from a coroutine.
     */
    suspend fun signIn(activity: Activity): SignInResult {
        if (!isConfigured()) {
            Log.e(TAG, "signIn aborted: OAuth web client id is not configured")
            return SignInResult.Error(context.getString(R.string.sign_in_not_configured))
        }

        val credentialManager = CredentialManager.create(activity)
        val serverClientId = webClientId()
        Log.i(
            TAG,
            "signIn start activity=${activity.javaClass.simpleName} " +
                "finishing=${activity.isFinishing} destroyed=${activity.isDestroyed} " +
                "hasFocus=${activity.hasWindowFocus()} " +
                "webClientId=$serverClientId"
        )

        return try {
            try {
                requestSignInWithGoogle(activity, credentialManager, serverClientId)
            } catch (e: NoCredentialException) {
                dump(e, "Sign in with Google returned no credentials; trying Google ID sheet")
                requestGoogleId(activity, credentialManager, serverClientId)
            }
        } catch (e: TimeoutCancellationException) {
            dump(e, "signIn timed out")
            SignInResult.Error(context.getString(R.string.sign_in_timed_out))
        } catch (e: CancellationException) {
            dump(e, "signIn coroutine CancellationException (job cancelled or GMS aborted)")
            throw e
        } catch (e: GetCredentialCancellationException) {
            classifyCancellation(e)
        } catch (e: NoCredentialException) {
            dump(e, "no Google credentials on device")
            SignInResult.Error(context.getString(R.string.sign_in_no_accounts))
        } catch (e: GoogleIdTokenParsingException) {
            dump(e, "ID token parse failed")
            SignInResult.Error(context.getString(R.string.sign_in_failed))
        } catch (e: GetCredentialProviderConfigurationException) {
            dump(e, "Credential provider not configured / Play services")
            SignInResult.Error(context.getString(R.string.sign_in_play_services))
        } catch (e: GetCredentialException) {
            dump(e, "GetCredentialException type=${e.type}")
            SignInResult.Error(credentialErrorMessage(e))
        } catch (e: Exception) {
            dump(e, "unexpected signIn failure")
            SignInResult.Error(context.getString(R.string.sign_in_failed))
        }
    }

    private suspend fun requestGoogleId(
        activity: Activity,
        credentialManager: CredentialManager,
        serverClientId: String,
    ): SignInResult {
        Log.i(TAG, "requestGoogleId (One Tap sheet)")
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(serverClientId)
            .setAutoSelectEnabled(false)
            .setNonce(hashedNonce())
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        return parseGoogleCredential(getCredential(activity, credentialManager, request, "GoogleId"))
    }

    private suspend fun requestSignInWithGoogle(
        activity: Activity,
        credentialManager: CredentialManager,
        serverClientId: String,
    ): SignInResult {
        Log.i(TAG, "requestSignInWithGoogle (interactive picker)")
        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId)
            .setNonce(hashedNonce())
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()
        return parseGoogleCredential(
            getCredential(activity, credentialManager, request, "SignInWithGoogle")
        )
    }

    private suspend fun getCredential(
        activity: Activity,
        credentialManager: CredentialManager,
        request: GetCredentialRequest,
        source: String,
    ): Credential {
        Log.i(
            TAG,
            "$source getCredential() activity=${activity.javaClass.simpleName} " +
                "finishing=${activity.isFinishing} destroyed=${activity.isDestroyed} " +
                "hasFocus=${activity.hasWindowFocus()}"
        )
        val result = credentialManager.getCredential(activity, request)
        val credential = result.credential
        Log.i(
            TAG,
            "$source getCredential() ok type=${credential.type} class=${credential.javaClass.simpleName}"
        )
        return credential
    }

    private fun parseGoogleCredential(credential: Credential): SignInResult {
        return try {
            if (credential !is CustomCredential ||
                credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                Log.e(TAG, "unexpected credential type=${credential.type} class=${credential.javaClass.name}")
                return SignInResult.Error(context.getString(R.string.sign_in_failed))
            }
            val token = GoogleIdTokenCredential.createFrom(credential.data)
            Log.i(
                TAG,
                "parsed Google account id=${token.id} name=${token.displayName} " +
                    "hasToken=${token.idToken.isNotBlank()}"
            )
            SignInResult.Success(
                GoogleAccount(
                    id = token.id,
                    name = token.displayName.orEmpty(),
                    email = token.id,
                    photoUrl = token.profilePictureUri?.toString().orEmpty(),
                    idToken = token.idToken,
                )
            )
        } catch (e: GoogleIdTokenParsingException) {
            dump(e, "parseGoogleCredential token parse failed")
            SignInResult.Error(context.getString(R.string.sign_in_failed))
        } catch (e: Exception) {
            dump(e, "parseGoogleCredential unexpected failure")
            SignInResult.Error(context.getString(R.string.sign_in_failed))
        }
    }

    private fun hashedNonce(): String {
        val raw = UUID.randomUUID().toString()
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun classifyCancellation(error: GetCredentialCancellationException): SignInResult {
        dump(error, "GetCredentialCancellationException")
        val blob = exceptionBlob(error)
        return when {
            blob.contains("reauth") -> {
                Log.w(TAG, "classified cancel as reauth/2FA failure")
                SignInResult.Error(context.getString(R.string.sign_in_reauth_needed))
            }
            blob.contains("28433") || blob.contains("28444") ||
                blob.contains("developer") || blob.contains("oauth") -> {
                Log.w(TAG, "classified cancel as OAuth/developer misconfig")
                SignInResult.Error(context.getString(R.string.sign_in_developer_error))
            }
            blob.contains("network") || blob.contains("timeout") -> {
                Log.w(TAG, "classified cancel as network/timeout")
                SignInResult.Error(context.getString(R.string.sign_in_failed))
            }
            else -> {
                Log.w(
                    TAG,
                    "classified as cancelled. If the Google screen closed by itself, " +
                        "this is often SHA-1 mismatch, 2FA reauth, or the launcher onStop interrupting GMS."
                )
                SignInResult.Cancelled
            }
        }
    }

    private fun isReauthFailure(error: Exception): Boolean {
        return exceptionBlob(error).contains("reauth")
    }

    private fun credentialErrorMessage(error: GetCredentialException): String {
        val blob = exceptionBlob(error)
        val isDeveloperMisconfig =
            blob.contains("28433") ||
                blob.contains("28444") ||
                blob.contains("developer") ||
                blob.contains("oauth")
        return when {
            isReauthFailure(error) -> context.getString(R.string.sign_in_reauth_needed)
            isDeveloperMisconfig -> context.getString(R.string.sign_in_developer_error)
            error.message.isNullOrBlank() -> context.getString(R.string.sign_in_failed)
            else -> "${context.getString(R.string.sign_in_failed)}\n${error.message}"
        }
    }

    private fun exceptionBlob(error: Throwable): String {
        return buildString {
            var current: Throwable? = error
            var depth = 0
            while (current != null && depth < 8) {
                append(current.javaClass.name).append(' ')
                append(current.message).append(' ')
                if (current is GetCredentialException) {
                    append(current.type).append(' ')
                }
                current = current.cause
                depth++
            }
        }.lowercase()
    }

    private fun dump(error: Throwable, label: String) {
        Log.e(
            TAG,
            "$label class=${error.javaClass.name} message=${error.message} " +
                "type=${(error as? GetCredentialException)?.type}"
        )
        var cause = error.cause
        var depth = 1
        while (cause != null && depth <= 6) {
            Log.e(
                TAG,
                "  cause[$depth] class=${cause.javaClass.name} message=${cause.message} " +
                    "type=${(cause as? GetCredentialException)?.type}"
            )
            cause = cause.cause
            depth++
        }
        Log.e(TAG, label, error)
    }

    /** Clears the stored account and the Credential Manager state. */
    suspend fun signOut() {
        try {
            clearCredentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            dump(e, "signOut clearCredentialState failed (ignored)")
        }
    }
}
