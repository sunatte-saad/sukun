package sukun.minimalist.app.launcher.com.helper

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions

/**
 * Non-HOME host for Google's full sign-in UI. Credential Manager's sheet cannot
 * complete 2-step verification; this activity can.
 */
class GoogleSignInHostActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_WEB_CLIENT_ID = "web_client_id"
        private const val RC_SIGN_IN = 4401
        private const val STATE_STARTED = "sign_in_started"
    }

    private var started = false
    private var delivered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(null)
        started = savedInstanceState?.getBoolean(STATE_STARTED) == true
        if (started) return
        started = true

        val clientId = intent.getStringExtra(EXTRA_WEB_CLIENT_ID).orEmpty()
        if (clientId.isEmpty()) {
            deliver(RESULT_CANCELED, null)
            finish()
            return
        }

        @Suppress("DEPRECATION")
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(clientId)
            .requestEmail()
            .requestProfile()
            .build()
        @Suppress("DEPRECATION")
        startActivityForResult(GoogleSignIn.getClient(this, options).signInIntent, RC_SIGN_IN)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_STARTED, started)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != RC_SIGN_IN) return
        deliver(resultCode, data)
        finish()
    }

    override fun onDestroy() {
        if (!delivered && isFinishing) {
            deliver(RESULT_CANCELED, null)
        }
        super.onDestroy()
    }

    private fun deliver(resultCode: Int, data: android.content.Intent?) {
        if (delivered) return
        delivered = true
        GoogleAuthHelper.deliverGoogleSignInIntent(resultCode, data)
    }
}
