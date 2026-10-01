package sukun.minimalist.app.launcher.com.helper

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import java.security.MessageDigest
import kotlinx.coroutines.suspendCancellableCoroutine
import sukun.minimalist.app.launcher.com.BuildConfig
import sukun.minimalist.app.launcher.com.R
import kotlin.coroutines.resume

/**
 * Google sign-in. Uses the full Google Sign-In activity (not Credential Manager's
 * bottom sheet) so accounts with 2-step verification can complete the Google prompt.
 */
class GoogleAuthHelper(private val context: Context) {

    companion object {
        const val TAG = "SukunAuth"
        const val REQUEST_CODE_GOOGLE_AUTH = 9103

        private var pendingAuthorization: ((AuthorizationResult?) -> Unit)? = null
        private var pendingGoogleSignInIntent: ((Intent?) -> Unit)? = null

        fun deliverGoogleSignInIntent(resultCode: Int, data: Intent?) {
            Log.i(TAG, "deliverGoogleSignInIntent resultCode=$resultCode hasData=${data != null}")
            val continuation = pendingGoogleSignInIntent
            pendingGoogleSignInIntent = null
            continuation?.invoke(data)
        }

        fun handleActivityResult(activity: Activity, requestCode: Int, resultCode: Int, data: Intent?) {
            if (requestCode != REQUEST_CODE_GOOGLE_AUTH) return
            val continuation = pendingAuthorization
            pendingAuthorization = null
            if (continuation == null) return
            if (resultCode != Activity.RESULT_OK) {
                continuation(null)
                return
            }
            try {
                continuation(
                    Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(data)
                )
            } catch (e: Exception) {
                Log.e(TAG, "Identity authorization result failed", e)
                continuation(null)
            }
        }
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
     * Launches Google's full account picker so 2-step verification can be completed.
     * [activity] must be the foreground Activity. Call from a coroutine.
     */
    suspend fun signIn(activity: Activity): SignInResult {
        if (!isConfigured()) {
            Log.e(TAG, "signIn aborted: OAuth web client id is not configured")
            return SignInResult.Error(context.getString(R.string.sign_in_not_configured))
        }

        val serverClientId = webClientId()
        val sha1 = appSigningSha1()
        Log.i(
            TAG,
            "signIn start activity=${activity.javaClass.simpleName} " +
                "finishing=${activity.isFinishing} destroyed=${activity.isDestroyed} " +
                "hasFocus=${activity.hasWindowFocus()} " +
                "webClientId=$serverClientId debug=${BuildConfig.DEBUG} sha1=$sha1"
        )
        return requestGoogleSignInIntent(activity, serverClientId)
    }

    private suspend fun requestGoogleSignInIntent(
        activity: Activity,
        serverClientId: String,
    ): SignInResult {
        Log.i(TAG, "requestGoogleSignInIntent (full Google Sign-In, 2FA-capable)")
        val data = suspendCancellableCoroutine<Intent?> { cont ->
            pendingGoogleSignInIntent = { intent ->
                if (cont.isActive) cont.resume(intent)
            }
            try {
                activity.startActivity(
                    Intent(activity, GoogleSignInHostActivity::class.java)
                        .putExtra(GoogleSignInHostActivity.EXTRA_WEB_CLIENT_ID, serverClientId)
                )
            } catch (e: Exception) {
                dump(e, "GoogleSignInHostActivity failed to start")
                pendingGoogleSignInIntent = null
                if (cont.isActive) cont.resume(null)
            }
            cont.invokeOnCancellation { pendingGoogleSignInIntent = null }
        }
        return parseGoogleSignInIntent(data)
    }

    @Suppress("DEPRECATION")
    private fun parseGoogleSignInIntent(data: Intent?): SignInResult {
        if (data == null) return SignInResult.Cancelled
        return try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(data)
                .getResult(ApiException::class.java)
            val email = account.email?.trim().orEmpty()
            if (email.isEmpty()) {
                Log.e(TAG, "GoogleSignIn intent returned no email")
                return SignInResult.Error(context.getString(R.string.sign_in_failed))
            }
            Log.i(TAG, "GoogleSignIn intent ok email=$email name=${account.displayName}")
            SignInResult.Success(
                GoogleAccount(
                    id = account.id?.takeIf { it.isNotBlank() } ?: email,
                    name = account.displayName.orEmpty(),
                    email = email,
                    photoUrl = account.photoUrl?.toString().orEmpty(),
                    idToken = account.idToken.orEmpty(),
                )
            )
        } catch (e: ApiException) {
            dump(e, "GoogleSignIn intent ApiException status=${e.statusCode}")
            when (e.statusCode) {
                GoogleSignInStatusCodes.SIGN_IN_CANCELLED -> SignInResult.Cancelled
                GoogleSignInStatusCodes.NETWORK_ERROR ->
                    SignInResult.Error(context.getString(R.string.sign_in_failed))
                GoogleSignInStatusCodes.DEVELOPER_ERROR -> {
                    val sha1 = appSigningSha1().ifBlank { "unknown" }
                    Log.e(TAG, "DEVELOPER_ERROR package=${context.packageName} sha1=$sha1 debug=${BuildConfig.DEBUG}")
                    SignInResult.Error(context.getString(R.string.sign_in_developer_error, sha1))
                }
                else -> SignInResult.Error(context.getString(R.string.sign_in_failed))
            }
        } catch (e: Exception) {
            dump(e, "GoogleSignIn intent parse failed")
            SignInResult.Error(context.getString(R.string.sign_in_failed))
        }
    }

    @Suppress("DEPRECATION")
    private fun appSigningSha1(): String {
        return try {
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES,
                ).signingInfo?.apkContentsSigners
            } else {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNATURES,
                ).signatures
            }
            val cert = signatures?.firstOrNull()?.toByteArray() ?: return ""
            MessageDigest.getInstance("SHA-1")
                .digest(cert)
                .joinToString(":") { "%02X".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Could not read signing SHA-1", e)
            ""
        }
    }

    private fun dump(error: Throwable, label: String) {
        Log.e(TAG, "$label class=${error.javaClass.name} message=${error.message}")
        var cause = error.cause
        var depth = 1
        while (cause != null && depth <= 6) {
            Log.e(TAG, "  cause[$depth] class=${cause.javaClass.name} message=${cause.message}")
            cause = cause.cause
            depth++
        }
        Log.e(TAG, label, error)
    }

    /** Clears the stored account and the Credential Manager state. */
    suspend fun signOut() {
        try {
            @Suppress("DEPRECATION")
            val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
            GoogleSignIn.getClient(context, options).signOut()
        } catch (e: Exception) {
            dump(e, "signOut GoogleSignIn.signOut failed (ignored)")
        }
        try {
            clearCredentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            dump(e, "signOut clearCredentialState failed (ignored)")
        }
    }
}
