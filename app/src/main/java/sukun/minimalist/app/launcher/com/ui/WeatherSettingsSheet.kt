package sukun.minimalist.app.launcher.com.ui

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import sukun.minimalist.app.launcher.com.MainViewModel
import sukun.minimalist.app.launcher.com.R
import sukun.minimalist.app.launcher.com.data.Constants
import sukun.minimalist.app.launcher.com.data.Prefs
import sukun.minimalist.app.launcher.com.databinding.BottomSheetWeatherSettingsBinding
import sukun.minimalist.app.launcher.com.helper.hasWeatherLocationPermission
import sukun.minimalist.app.launcher.com.helper.showToast

class WeatherSettingsSheet : DialogFragment() {

    interface Listener {
        fun onWeatherSettingsChanged()
    }

    private var _binding: BottomSheetWeatherSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: Prefs
    private lateinit var viewModel: MainViewModel
    private var listener: Listener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_FRAME, 0)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetWeatherSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]
        setupClickListeners()
        updateUI()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            window.setGravity(Gravity.BOTTOM)
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.setDimAmount(0.55f)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun setListener(listener: Listener) {
        this.listener = listener
    }

    private fun setupClickListeners() {
        binding.weatherToggleRow.setOnClickListener { toggleWeather() }
        binding.weatherUnitsRow.setOnClickListener { toggleUnits() }
    }

    private fun updateUI() {
        val isOn = prefs.showWeatherOnHome
        binding.weatherToggle.text = getString(if (isOn) R.string.on else R.string.off)
        binding.weatherSubSettings.isVisible = isOn
        if (isOn) {
            updateUnitChips()
        }
    }

    private fun toggleWeather() {
        if (!prefs.showWeatherOnHome && !prefs.isProUser) {
            requireContext().showToast(R.string.premium_feature_requires_upgrade)
            return
        }
        prefs.showWeatherOnHome = !prefs.showWeatherOnHome
        updateUI()
        refreshWeather()
        listener?.onWeatherSettingsChanged()
    }

    private fun updateUnitChips() {
        binding.weatherUnitsValue.text = getString(
            if (prefs.weatherUnits == Constants.WeatherUnit.FAHRENHEIT)
                R.string.fahrenheit_short
            else
                R.string.celsius_short
        )
    }

    private fun toggleUnits() {
        val next = if (prefs.weatherUnits == Constants.WeatherUnit.FAHRENHEIT) {
            Constants.WeatherUnit.CELSIUS
        } else {
            Constants.WeatherUnit.FAHRENHEIT
        }
        selectUnits(next)
    }

    private fun selectUnits(units: String) {
        if (prefs.weatherUnits == units) return
        prefs.weatherUnits = units
        prefs.clearWeatherCache()
        refreshWeather()
        updateUnitChips()
        listener?.onWeatherSettingsChanged()
    }

    private fun refreshWeather() {
        if (!prefs.showWeatherOnHome) {
            viewModel.cancelWeatherWorker()
            viewModel.loadWeather()
            return
        }
        if (prefs.weatherSourceMode == Constants.WeatherSource.GOOGLE) {
            viewModel.cancelWeatherWorker(clearCachedWeather = true)
            viewModel.loadWeather()
            return
        }
        if (prefs.weatherSourceMode == Constants.WeatherSource.MANUAL) {
            if (prefs.weatherLocationQuery.isBlank()) {
                viewModel.cancelWeatherWorker(clearCachedWeather = true)
                viewModel.loadWeather()
                return
            }
            viewModel.setWeatherWorker()
            viewModel.loadWeather(true)
            return
        }
        val canRefresh = when (prefs.weatherSourceMode) {
            Constants.WeatherSource.DEVICE -> requireContext().hasWeatherLocationPermission()
            else -> prefs.weatherLocationQuery.isNotBlank()
        }
        if (canRefresh) {
            viewModel.setWeatherWorker()
            viewModel.loadWeather(true)
        } else {
            viewModel.cancelWeatherWorker()
            viewModel.loadWeather()
        }
    }

    companion object {
        const val TAG = "WeatherSettingsSheet"
        fun newInstance() = WeatherSettingsSheet()
    }
}
