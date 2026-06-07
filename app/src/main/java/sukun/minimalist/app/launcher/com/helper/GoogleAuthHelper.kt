package sukun.minimalist.app.launcher.com.helper

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import sukun.minimalist.app.launcher.com.R
import sukun.minimalist.app.launcher.com.data.Prefs

/**
 * Wraps Jetpack Credential Manager to sign in with Google. Authenticate-only: the
 * resulting account name/email/photo are stored locally in [Prefs]. There is no backend.
 *
 * Sign-in requires a valid OAuth Web client id in res/values/auth.xml
 * (see the comment in that file). Until then [signIn] returns [SignInResult.Error].
 */
class GoogleAuthHelper(private val context: Context) {

    private val credentialManager = CredentialManager.create(context)

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

    private fun webClientId(): String = context.getString(R.string.default_web_client_id)

    fun isConfigured(): Boolean =
        webClientId().endsWith(".apps.googleusercontent.com") &&
            !webClientId().startsWith("YOUR_WEB_CLIENT_ID")

    /**
     * Launches the Google account picker. [activityContext] must be an Activity context so
     * Credential Manager can show its UI. Call from a coroutine.
     */
    suspend fun signIn(activityContext: Context): SignInResult {
        if (!isConfigured()) {
            return SignInResult.Error(context.getString(R.string.sign_in_not_configured))
        }

        val googleIdOption = GetGoogleIdOption.Builder()
            // Show all Google accounts, not only previously authorized ones.
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId())
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val response = credentialManager.getCredential(activityContext, request)
            val credential = response.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val token = GoogleIdTokenCredential.createFrom(credential.data)
                SignInResult.Success(
                    GoogleAccount(
                        id = token.id,
                        name = token.displayName.orEmpty(),
                        email = token.id, // GoogleIdTokenCredential.id is the email address
                        photoUrl = token.profilePictureUri?.toString().orEmpty(),
                        idToken = token.idToken,
                    )
                )
            } else {
                SignInResult.Error(context.getString(R.string.sign_in_failed))
            }
        } catch (_: GetCredentialCancellationException) {
            SignInResult.Cancelled
        } catch (_: NoCredentialException) {
            SignInResult.Error(context.getString(R.string.sign_in_no_accounts))
        } catch (_: GoogleIdTokenParsingException) {
            SignInResult.Error(context.getString(R.string.sign_in_failed))
        } catch (_: GetCredentialException) {
            SignInResult.Error(context.getString(R.string.sign_in_failed))
        }
    }

    /** Clears the stored account and the Credential Manager state. */
    suspend fun signOut() {
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (_: Exception) {
            // Clearing the cached state is best-effort; local prefs are cleared by the caller.
        }
    }
}
