package sukun.minimalist.app.launcher.com.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import sukun.minimalist.app.launcher.com.MainActivity
import sukun.minimalist.app.launcher.com.MainViewModel
import sukun.minimalist.app.launcher.com.R
import sukun.minimalist.app.launcher.com.data.Prefs
import sukun.minimalist.app.launcher.com.databinding.FragmentSignInBinding
import sukun.minimalist.app.launcher.com.helper.sync.AccountSyncManager
import sukun.minimalist.app.launcher.com.helper.GoogleAuthHelper
import sukun.minimalist.app.launcher.com.helper.showToast

/**
 * One-time onboarding screen offering "Sign in with Google". Shown on first open;
 * the user can skip. Either way [Prefs.signInPromptShown] is set so it never reappears.
 */
class SignInFragment : Fragment() {

    private var _binding: FragmentSignInBinding? = null
    private val binding get() = _binding!!

    private lateinit var prefs: Prefs
    private val viewModel by lazy {
        ViewModelProvider(requireActivity())[MainViewModel::class.java]
    }
    private val googleAuthHelper by lazy { GoogleAuthHelper(requireContext().applicationContext) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSignInBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())
        binding.btnSignIn.setOnClickListener { signIn() }
        binding.btnSkip.setOnClickListener { finishOnboarding() }
    }

    private fun signIn() {
        android.util.Log.i(GoogleAuthHelper.TAG, "Sign-in tapped from SignInFragment")
        if (!googleAuthHelper.isConfigured()) {
            showSignInFeedback(getString(R.string.sign_in_not_configured), isError = true)
            return
        }
        binding.btnSignIn.isEnabled = false
        requireActivity().showToast(getString(R.string.sign_in_opening), Toast.LENGTH_SHORT)
        viewModel.isAuthFlowActive = true
        requireActivity().lifecycleScope.launch {
            try {
                val result = withTimeout(90_000L) {
                    googleAuthHelper.signIn(requireActivity())
                }
                handleSignInResult(result)
            } catch (e: TimeoutCancellationException) {
                android.util.Log.e(GoogleAuthHelper.TAG, "SignInFragment sign-in timed out", e)
                showSignInFeedback(getString(R.string.sign_in_timed_out), isError = true)
            } catch (e: CancellationException) {
                android.util.Log.e(
                    GoogleAuthHelper.TAG,
                    "SignInFragment coroutine cancelled: ${e.javaClass.name} ${e.message}",
                    e
                )
                showSignInFeedback(getString(R.string.sign_in_cancelled), isError = false)
            } catch (e: Exception) {
                android.util.Log.e(
                    GoogleAuthHelper.TAG,
                    "SignInFragment unexpected error: ${e.javaClass.name} ${e.message}",
                    e
                )
                showSignInFeedback(getString(R.string.sign_in_failed), isError = true)
            } finally {
                viewModel.isAuthFlowActive = false
                if (isAdded) binding.btnSignIn.isEnabled = true
            }
        }
    }

    private suspend fun handleSignInResult(result: GoogleAuthHelper.SignInResult) {
        when (result) {
            is GoogleAuthHelper.SignInResult.Success -> {
                android.util.Log.i(GoogleAuthHelper.TAG, "SignInFragment result=Success email=${result.account.email}")
                if (!isAdded) return
                val account = result.account
                val ctx = requireContext()
                prefs.saveAccount(account.id, account.name, account.email, account.photoUrl)
                val syncResult = withContext(Dispatchers.IO) {
                    AccountSyncManager.onSignInSuccess(ctx, requireActivity())
                }
                when (syncResult) {
                    is AccountSyncManager.SyncResult.Success -> {
                        val msg = if (syncResult.restored) {
                            getString(R.string.signed_in_settings_restored, account.email)
                        } else {
                            getString(R.string.signed_in_backup_saved, account.email)
                        }
                        requireActivity().showToast(msg, Toast.LENGTH_LONG)
                        finishOnboarding()
                    }
                    is AccountSyncManager.SyncResult.NeedsRestoreConfirm -> {
                        val activity = requireActivity() as? MainActivity
                        if (activity != null) {
                            activity.promptDriveRestoreIfNeeded(
                                syncResult.remote,
                                onKeepLocal = {
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        AccountSyncManager.keepLocalAndPushToDrive(
                                            ctx,
                                            syncResult.remote.updatedAt,
                                            activity,
                                        )
                                    }
                                    finishOnboarding()
                                },
                                onRestored = { finishOnboarding() },
                            )
                        } else {
                            finishOnboarding()
                        }
                    }
                    is AccountSyncManager.SyncResult.Error -> {
                        requireActivity().showToast(
                            getString(R.string.signed_in_sync_failed, account.email),
                            Toast.LENGTH_LONG,
                        )
                        finishOnboarding()
                    }
                }
            }
            is GoogleAuthHelper.SignInResult.Cancelled -> {
                android.util.Log.w(GoogleAuthHelper.TAG, "SignInFragment result=Cancelled")
                showSignInFeedback(getString(R.string.sign_in_cancelled), isError = false)
            }
            is GoogleAuthHelper.SignInResult.Error -> {
                android.util.Log.e(GoogleAuthHelper.TAG, "SignInFragment result=Error ${result.message}")
                showSignInFeedback(result.message, isError = true)
            }
        }
    }

    private fun showSignInFeedback(message: String, isError: Boolean) {
        if (!isAdded) return
        requireActivity().showToast(message, Toast.LENGTH_LONG)
        if (isError) {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.sign_in_with_google)
                .setMessage(message)
                .setPositiveButton(R.string.okay, null)
                .show()
        }
    }

    private fun finishOnboarding() {
        prefs.signInPromptShown = true
        if (!findNavController().popBackStack(R.id.mainFragment, false)) {
            findNavController().popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
