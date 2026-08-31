package sukun.minimalist.app.launcher.com.helper.sync

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import sukun.minimalist.app.launcher.com.R
import sukun.minimalist.app.launcher.com.data.Constants
import sukun.minimalist.app.launcher.com.data.Prefs

/** OAuth access token for the signed-in user's Google Drive app-data folder. */
object GoogleDriveAuthHelper {

    const val TAG = "SukunDriveAuth"
    const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    const val REQUEST_CODE_DRIVE_AUTH = 9102

    private var cachedToken: String? = null
    private var tokenExpiryMs: Long = 0L
    private var pendingContinuation: ((String?) -> Unit)? = null

    fun clearToken() {
        cachedToken = null
        tokenExpiryMs = 0L
    }

    suspend fun getAccessToken(context: Context, activity: Activity? = null): String? {
        val now = System.currentTimeMillis()
        cachedToken?.takeIf { it.isNotBlank() && now < tokenExpiryMs - 60_000L }?.let { return it }
        val host = activity ?: (context as? Activity)
        if (host == null) {
            Log.w(TAG, "getAccessToken: no Activity available for Drive authorization")
            return null
        }
        return authorize(host)
    }

    suspend fun authorize(activity: Activity): String? = suspendCancellableCoroutine { cont ->
        val client = Identity.getAuthorizationClient(activity)
        val request = AuthorizationRequest.Builder()
            .setRequestedScopes(listOf(Scope(DRIVE_APPDATA_SCOPE)))
            .requestOfflineAccess(activity.getString(R.string.default_web_client_id).takeIf {
                it.endsWith(".apps.googleusercontent.com") && !it.startsWith("YOUR_WEB_CLIENT_ID")
            } ?: Constants.GOOGLE_WEB_CLIENT_ID, false)
            .build()
        client.authorize(request)
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    val pendingIntent = result.pendingIntent
                    if (pendingIntent == null) {
                        cont.resume(null)
                        return@addOnSuccessListener
                    }
                    pendingContinuation = { token -> cont.resume(token) }
                    try {
                        activity.startIntentSenderForResult(
                            pendingIntent.intentSender,
                            REQUEST_CODE_DRIVE_AUTH,
                            null,
                            0,
                            0,
                            0,
                            null,
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Drive authorization UI failed", e)
                        pendingContinuation = null
                        cont.resume(null)
                    }
                } else {
                    cacheToken(result.accessToken)
                    cont.resume(result.accessToken)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Drive authorization failed", e)
                cont.resume(null)
            }
    }

    fun handleActivityResult(activity: Activity, requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != REQUEST_CODE_DRIVE_AUTH) return
        val continuation = pendingContinuation
        pendingContinuation = null
        if (continuation == null) return
        try {
            val result = Identity.getAuthorizationClient(activity)
                .getAuthorizationResultFromIntent(data)
            cacheToken(result.accessToken)
            continuation(result.accessToken)
        } catch (e: Exception) {
            Log.e(TAG, "Drive authorization result failed", e)
            continuation(null)
        }
    }

    private fun cacheToken(token: String?) {
        if (token.isNullOrBlank()) return
        cachedToken = token
        tokenExpiryMs = System.currentTimeMillis() + 3_500_000L
    }
}
