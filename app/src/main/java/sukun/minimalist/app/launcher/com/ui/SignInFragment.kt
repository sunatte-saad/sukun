package sukun.minimalist.app.launcher.com.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.launch
import sukun.minimalist.app.launcher.com.R
import sukun.minimalist.app.launcher.com.data.Prefs
import sukun.minimalist.app.launcher.com.databinding.FragmentSignInBinding
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
        viewLifecycleOwner.lifecycleScope.launch {
            when (val result = googleAuthHelper.signIn(requireActivity())) {
                is GoogleAuthHelper.SignInResult.Success -> {
                    val account = result.account
                    prefs.saveAccount(account.id, account.name, account.email, account.photoUrl)
                    requireContext().showToast(getString(R.string.signed_in_as, account.email))
                    finishOnboarding()
                }
                is GoogleAuthHelper.SignInResult.Cancelled -> Unit
                is GoogleAuthHelper.SignInResult.Error ->
                    requireContext().showToast(result.message)
            }
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
