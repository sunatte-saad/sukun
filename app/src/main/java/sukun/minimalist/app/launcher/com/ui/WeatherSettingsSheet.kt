package sukun.minimalist.app.launcher.com.ui

import android.Manifest
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import sukun.minimalist.app.launcher.com.helper.hasWeatherLocationPermission
import sukun.minimalist.app.launcher.com.helper.isLocationServicesEnabled
import sukun.minimalist.app.launcher.com.helper.showLocationPermissionRationaleDialog
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
                requireContext().showLocationPermissionRationaleDialog()
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
            window.setDimAmount(0.55f)
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
        binding.weatherToggleRow.setOnClickListener { toggleWeather() }
        binding.weatherSourceRow.setOnClickListener { toggleSourceChips() }
        binding.chipWeatherDevice.setOnClickListener { selectWeatherSource(Constants.WeatherSource.DEVICE) }
        binding.chipWeatherManual.setOnClickListener { selectWeatherSource(Constants.WeatherSource.MANUAL) }
        binding.chipWeatherGoogle.setOnClickListener { selectWeatherSource(Constants.WeatherSource.GOOGLE) }
        binding.weatherUnitsRow.setOnClickListener { toggleUnits() }
    }

    private fun updateUI() {
        val isOn = prefs.showWeatherOnHome
        binding.weatherToggle.text = getString(if (isOn) R.string.on else R.string.off)
        binding.weatherSubSettings.isVisible = isOn
        if (isOn) {
            updateSourceLabel()
            updateSourceChips()
            updateUnitChips()
        }
    }

    private fun toggleWeather() {
        prefs.showWeatherOnHome = !prefs.showWeatherOnHome
        updateUI()
        refreshWeather()
        listener?.onWeatherSettingsChanged()
    }

    private fun toggleSourceChips() {
        binding.weatherSourceChips.isVisible = !binding.weatherSourceChips.isVisible
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

    private fun updateSourceChips() {
        setSourceChipState(binding.chipWeatherDevice, prefs.weatherSourceMode == Constants.WeatherSource.DEVICE)
        setSourceChipState(binding.chipWeatherManual, prefs.weatherSourceMode == Constants.WeatherSource.MANUAL)
        setSourceChipState(binding.chipWeatherGoogle, prefs.weatherSourceMode == Constants.WeatherSource.GOOGLE)
    }

    private fun setSourceChipState(chip: TextView, selected: Boolean) {
        chip.setTextColor(
            requireContext().getColorFromAttr(
                if (selected) R.attr.primaryColor else R.attr.primaryColorTrans50
            )
        )
        chip.paint.isFakeBoldText = selected
    }

    private fun selectWeatherSource(source: String) {
        if (prefs.weatherSourceMode == source) {
            binding.weatherSourceChips.isVisible = false
            return
        }
        when (source) {
            Constants.WeatherSource.DEVICE -> requestDeviceWeatherSource()
            Constants.WeatherSource.MANUAL -> {
                if (prefs.weatherLocationQuery.isBlank()) {
                    binding.weatherSourceChips.isVisible = false
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
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun applyWeatherSource(source: String) {
        prefs.weatherSourceMode = source
        if (source == Constants.WeatherSource.GOOGLE || source == Constants.WeatherSource.DEVICE) {
            prefs.clearWeatherCache()
        }
        binding.weatherSourceChips.isVisible = false
        updateSourceLabel()
        updateSourceChips()
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
