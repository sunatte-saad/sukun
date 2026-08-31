package sukun.minimalist.app.launcher.com.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import sukun.minimalist.app.launcher.com.MainActivity
import sukun.minimalist.app.launcher.com.MainViewModel
import sukun.minimalist.app.launcher.com.data.OnboardingAction
import sukun.minimalist.app.launcher.com.databinding.FragmentLanguageBinding
import sukun.minimalist.app.launcher.com.databinding.ItemLanguageBinding
import sukun.minimalist.app.launcher.com.helper.LocaleHelper

class LanguageFragment : Fragment() {

    private var _binding: FragmentLanguageBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentLanguageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        populateLanguageList()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun populateLanguageList() {
        binding.languageList.removeAllViews()
        val currentLanguage = LocaleHelper.getSelectedLanguage(requireContext())
        LocaleHelper.getAvailableLanguages().forEach { language ->
            val itemBinding = ItemLanguageBinding.inflate(
                layoutInflater,
                binding.languageList,
                false,
            )
            itemBinding.tvLanguageName.text = language.listLabel()
            val subtitle = language.listSubtitle()
            itemBinding.tvLanguageSubtitle.isVisible = subtitle != null
            if (subtitle != null) {
                itemBinding.tvLanguageSubtitle.text = subtitle
            }
            val isSelected = language == currentLanguage
            itemBinding.tvLanguageSelected.isVisible = isSelected
            itemBinding.tvLanguageName.setTypeface(
                itemBinding.tvLanguageName.typeface,
                if (isSelected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL,
            )
            itemBinding.root.setOnClickListener {
                selectLanguage(language)
            }
            binding.languageList.addView(itemBinding.root)
        }
    }

    private fun selectLanguage(language: LocaleHelper.Language) {
        val viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]
        val onLanguageStep = viewModel.isOnboardingActive()
            && viewModel.currentOnboardingStep().requiredAction == OnboardingAction.TAP_LANGUAGE
        val currentLanguage = LocaleHelper.getSelectedLanguage(requireContext())
        if (currentLanguage == language) {
            if (onLanguageStep) {
                viewModel.reportOnboardingAction(OnboardingAction.TAP_LANGUAGE)
            } else {
                findNavController().navigateUp()
            }
            return
        }
        LocaleHelper.applyAppLocale(requireContext(), language.code)
        if (onLanguageStep) {
            viewModel.reportOnboardingAction(OnboardingAction.TAP_LANGUAGE)
            // Avoid activity recreate during the tour — it would kick the user out of the flow.
            try { findNavController().navigateUp() } catch (_: Exception) {}
            return
        }
        (requireActivity() as? MainActivity)?.safeRecreate()
    }
}
