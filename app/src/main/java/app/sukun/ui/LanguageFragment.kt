package app.sukun.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import app.sukun.databinding.FragmentLanguageBinding
import app.sukun.databinding.ItemLanguageBinding
import app.sukun.helper.LocaleHelper

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
        val currentLanguage = LocaleHelper.getSelectedLanguage(requireContext())
        if (currentLanguage == language) {
            findNavController().navigateUp()
            return
        }
        LocaleHelper.applyAppLocale(requireContext(), language.code)
        findNavController().popBackStack()
        requireActivity().recreate()
    }
}
