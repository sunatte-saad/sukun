package sukun.minimalist.app.launcher.com.ui

import android.Manifest
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import sukun.minimalist.app.launcher.com.MainViewModel
import sukun.minimalist.app.launcher.com.R
import sukun.minimalist.app.launcher.com.data.Constants
import sukun.minimalist.app.launcher.com.data.Prefs
import sukun.minimalist.app.launcher.com.databinding.BottomSheetWeatherSettingsBinding
import sukun.minimalist.app.launcher.com.helper.getColorFromAttr
import sukun.minimalist.app.launcher.com.helper.applyDeviceLocationDeniedFallbacks
import sukun.minimalist.app.launcher.com.helper.hasWeatherLocationPermission
import sukun.minimalist.app.launcher.com.helper.isLocationServicesEnabled
import sukun.minimalist.app.launcher.com.helper.missingSukunSetupPermissions
import sukun.minimalist.app.launcher.com.helper.showLocationServicesDisabledDialog

class WeatherSettingsSheet : DialogFragment() {

    interface Listener {
        fun onWeatherSettingsChanged()
        fun onWeatherLocationNeeded()
    }

    private var _binding: BottomSheetWeatherSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: Prefs
    private lateinit var viewModel: MainViewModel
    private var listener: Listener? = null
    private var pendingWeatherSource: String? = null

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
                    || result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            val source = pendingWeatherSource
            pendingWeatherSource = null
            if (granted && source != null) {
                applyWeatherSource(source)
            } else if (!granted) {
                requireContext().applyDeviceLocationDeniedFallbacks(prefs)
                applyWeatherSource(prefs.weatherSourceMode)
            }
        }

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
            window.setDimAmount(0.45f)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                window.setBackgroundBlurRadius(36)
                val params = window.attributes
                params.blurBehindRadius = 36
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                window.attributes = params
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (requireActivity().isFinishing) {
            dismissAllowingStateLoss()
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
        binding.weatherSourceRow.setOnClickListener {
            val show = !binding.weatherSourceChips.isVisible
            hideSelectors()
            binding.weatherSourceChips.isVisible = show
        }
        binding.chipWeatherDevice.setOnClickListener { selectWeatherSource(Constants.WeatherSource.DEVICE) }
        binding.chipWeatherManual.setOnClickListener { selectWeatherSource(Constants.WeatherSource.MANUAL) }
        binding.chipWeatherGoogle.setOnClickListener { selectWeatherSource(Constants.WeatherSource.GOOGLE) }
        binding.weatherLocationRow.setOnClickListener {
            hideSelectors()
            dismiss()
            listener?.onWeatherLocationNeeded()
        }
        binding.weatherUnitsRow.setOnClickListener {
            val show = !binding.weatherUnitsSelectLayout.isVisible
            hideSelectors()
            binding.weatherUnitsSelectLayout.isVisible = show
        }
        binding.chipWeatherCelsius.setOnClickListener { selectUnits(Constants.WeatherUnit.CELSIUS) }
        binding.chipWeatherFahrenheit.setOnClickListener { selectUnits(Constants.WeatherUnit.FAHRENHEIT) }
    }

    private fun hideSelectors() {
        binding.weatherSourceChips.isVisible = false
        binding.weatherUnitsSelectLayout.isVisible = false
    }

    private fun updateUI() {
        hideSelectors()
        updateSourceLabel()
        updateSourceChips()
        updateLocationLabel()
        updateUnitLabel()
        binding.weatherLocationRow.isVisible =
            prefs.weatherSourceMode != Constants.WeatherSource.GOOGLE
    }

    private fun updateSourceLabel() {
        binding.weatherSourceValue.text = getString(
            when (prefs.weatherSourceMode) {
                Constants.WeatherSource.GOOGLE -> R.string.google_weather
                Constants.WeatherSource.MANUAL -> R.string.manual_location
                else -> R.string.device_location
            }
        )
    }

    private fun updateLocationLabel() {
        binding.weatherLocationValue.text = when {
            prefs.weatherSourceMode == Constants.WeatherSource.GOOGLE ->
                getString(R.string.google_weather_short)
            prefs.weatherSourceMode == Constants.WeatherSource.DEVICE ->
                prefs.weatherLocationLabel.ifBlank { getString(R.string.device_location) }
            prefs.weatherLocationLabel.isNotBlank() -> prefs.weatherLocationLabel
            else -> getString(R.string.not_set)
        }
    }

    private fun updateSourceChips() {
        setOptionState(binding.chipWeatherDevice, prefs.weatherSourceMode == Constants.WeatherSource.DEVICE)
        setOptionState(binding.chipWeatherManual, prefs.weatherSourceMode == Constants.WeatherSource.MANUAL)
        setOptionState(binding.chipWeatherGoogle, prefs.weatherSourceMode == Constants.WeatherSource.GOOGLE)
    }

    private fun setOptionState(option: TextView, selected: Boolean) {
        option.alpha = if (selected) 1f else 0.4f
        option.setTextColor(requireContext().getColorFromAttr(R.attr.primaryColor))
    }

    private fun selectWeatherSource(source: String) {
        if (prefs.weatherSourceMode == source) {
            hideSelectors()
            return
        }
        when (source) {
            Constants.WeatherSource.DEVICE -> requestDeviceWeatherSource()
            Constants.WeatherSource.MANUAL -> {
                if (prefs.weatherLocationQuery.isBlank()) {
                    hideSelectors()
                    dismiss()
                    listener?.onWeatherLocationNeeded()
                    return
                }
                applyWeatherSource(source)
            }
            else -> applyWeatherSource(source)
        }
    }

    private fun requestDeviceWeatherSource() {
        val context = requireContext()
        if (!context.isLocationServicesEnabled()) {
            context.showLocationServicesDisabledDialog()
            return
        }
        if (context.hasWeatherLocationPermission()) {
            applyWeatherSource(Constants.WeatherSource.DEVICE)
            return
        }
        pendingWeatherSource = Constants.WeatherSource.DEVICE
        val needed = context.missingSukunSetupPermissions()
        if (needed.isEmpty()) {
            applyWeatherSource(Constants.WeatherSource.DEVICE)
            return
        }
        locationPermissionLauncher.launch(needed)
    }

    private fun applyWeatherSource(source: String) {
        prefs.weatherSourceMode = source
        if (source == Constants.WeatherSource.GOOGLE || source == Constants.WeatherSource.DEVICE) {
            prefs.clearWeatherCache()
        }
        hideSelectors()
        updateUI()
        refreshWeather()
        listener?.onWeatherSettingsChanged()
    }

    private fun updateUnitLabel() {
        binding.weatherUnitsValue.text = getString(
            if (prefs.weatherUnits == Constants.WeatherUnit.FAHRENHEIT)
                R.string.fahrenheit_short
            else
                R.string.celsius_short
        )
        setOptionState(
            binding.chipWeatherCelsius,
            prefs.weatherUnits != Constants.WeatherUnit.FAHRENHEIT,
        )
        setOptionState(
            binding.chipWeatherFahrenheit,
            prefs.weatherUnits == Constants.WeatherUnit.FAHRENHEIT,
        )
    }

    private fun selectUnits(units: String) {
        if (prefs.weatherUnits == units) {
            hideSelectors()
            return
        }
        prefs.weatherUnits = units
        prefs.clearWeatherCache()
        hideSelectors()
        refreshWeather()
        updateUnitLabel()
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
