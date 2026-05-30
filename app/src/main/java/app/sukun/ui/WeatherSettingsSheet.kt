package app.sukun.ui

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import app.sukun.MainViewModel
import app.sukun.R
import app.sukun.data.Constants
import app.sukun.data.Prefs
import app.sukun.databinding.BottomSheetWeatherSettingsBinding
import app.sukun.helper.getColorFromAttr
import app.sukun.helper.hasWeatherLocationPermission
import app.sukun.helper.hideKeyboard
import app.sukun.helper.isLocationServicesEnabled
import app.sukun.helper.showKeyboard
import app.sukun.helper.showToast

class WeatherSettingsSheet : DialogFragment() {

    interface Listener {
        fun onWeatherSettingsChanged()
    }

    private var _binding: BottomSheetWeatherSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: Prefs
    private lateinit var viewModel: MainViewModel
    private var listener: Listener? = null

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
                    || result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (granted) {
                prefs.weatherSourceMode = Constants.WeatherSource.DEVICE
                prefs.clearWeatherCache()
                refreshWeather()
                updateUI()
                listener?.onWeatherSettingsChanged()
            } else {
                requireContext().showToast(R.string.weather_permission_needed)
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
            window.setDimAmount(0.4f)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                window.setBackgroundBlurRadius(40)
                val params = window.attributes
                params.blurBehindRadius = 40
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                window.attributes = params
            }
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
        binding.chipManual.setOnClickListener { selectSource(Constants.WeatherSource.MANUAL) }
        binding.chipDevice.setOnClickListener { selectDeviceSource() }
        binding.chipGoogle.setOnClickListener { selectSource(Constants.WeatherSource.GOOGLE) }
        binding.locationValueLabel.setOnClickListener { openLocationEditor() }
        binding.btnSaveLocation.setOnClickListener { saveLocation() }
        binding.btnCloseLocation.setOnClickListener {
            binding.locationEditLayout.isVisible = false
            binding.etLocation.hideKeyboard()
        }
        binding.chipCelsius.setOnClickListener { selectUnits(Constants.WeatherUnit.CELSIUS) }
        binding.chipFahrenheit.setOnClickListener { selectUnits(Constants.WeatherUnit.FAHRENHEIT) }
    }

    private fun updateUI() {
        val isOn = prefs.showWeatherOnHome
        binding.weatherToggle.text = getString(if (isOn) R.string.on else R.string.off)
        binding.weatherSubSettings.isVisible = isOn
        if (isOn) {
            updateSourceChips()
            updateLocationSection()
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

    private fun updateSourceChips() {
        val source = prefs.weatherSourceMode
        setChipState(binding.chipManual, source == Constants.WeatherSource.MANUAL)
        setChipState(binding.chipDevice, source == Constants.WeatherSource.DEVICE)
        setChipState(binding.chipGoogle, source == Constants.WeatherSource.GOOGLE)
    }

    private fun updateLocationSection() {
        val source = prefs.weatherSourceMode
        binding.locationSection.isVisible = source != Constants.WeatherSource.GOOGLE
        if (source == Constants.WeatherSource.DEVICE) {
            binding.locationEditLayout.isVisible = false
            binding.locationValueLabel.text =
                prefs.weatherLocationLabel.ifBlank { getString(R.string.current_location) }
        } else if (source == Constants.WeatherSource.MANUAL) {
            binding.locationValueLabel.text = when {
                prefs.weatherLocationQuery.isBlank() -> getString(R.string.not_set)
                prefs.weatherLocationLabel.isNotBlank() -> prefs.weatherLocationLabel
                else -> prefs.weatherLocationQuery
            }
        }
    }

    private fun updateUnitChips() {
        val isFahrenheit = prefs.weatherUnits == Constants.WeatherUnit.FAHRENHEIT
        setChipState(binding.chipCelsius, !isFahrenheit)
        setChipState(binding.chipFahrenheit, isFahrenheit)
    }

    private fun setChipState(chip: TextView, selected: Boolean) {
        chip.setBackgroundResource(
            if (selected) R.drawable.bg_chip_selected else R.drawable.bg_chip_unselected
        )
        chip.setTextColor(
            requireContext().getColorFromAttr(
                if (selected) R.attr.primaryInverseColor else R.attr.primaryColor
            )
        )
    }

    private fun selectSource(source: String) {
        if (prefs.weatherSourceMode == source) return
        prefs.weatherSourceMode = source
        prefs.clearWeatherCache()
        refreshWeather()
        updateUI()
        listener?.onWeatherSettingsChanged()
    }

    private fun selectDeviceSource() {
        if (requireContext().hasWeatherLocationPermission()) {
            selectSource(Constants.WeatherSource.DEVICE)
            return
        }
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun openLocationEditor() {
        when (prefs.weatherSourceMode) {
            Constants.WeatherSource.DEVICE -> {
                if (!requireContext().hasWeatherLocationPermission()) {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                } else if (!requireContext().isLocationServicesEnabled()) {
                    requireContext().showToast(R.string.location_services_disabled)
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
            }
            Constants.WeatherSource.MANUAL -> {
                binding.locationEditLayout.isVisible = true
                binding.etLocation.setText(prefs.weatherLocationQuery)
                binding.etLocation.showKeyboard()
            }
        }
    }

    private fun saveLocation() {
        val query = binding.etLocation.text?.toString()?.trim().orEmpty()
        if (query.isBlank()) {
            requireContext().showToast(R.string.weather_location_required)
            binding.etLocation.showKeyboard()
            return
        }
        prefs.weatherLocationQuery = query
        prefs.weatherLocationLabel = query
        prefs.weatherLatitude = ""
        prefs.weatherLongitude = ""
        prefs.clearWeatherCache()
        binding.locationEditLayout.isVisible = false
        binding.etLocation.hideKeyboard()
        refreshWeather()
        updateLocationSection()
        listener?.onWeatherSettingsChanged()
        requireContext().showToast(R.string.weather_saved)

        if (prefs.showPrayerOnHome && prefs.prayerSourceMode == Constants.PrayerSource.MANUAL
            && prefs.prayerLocationQuery.isBlank()
        ) {
            AlertDialog.Builder(requireContext())
                .setMessage(R.string.use_same_location_for_prayer)
                .setPositiveButton(R.string.yes) { _, _ ->
                    prefs.prayerLocationQuery = query
                    prefs.prayerLocationLabel = query
                    prefs.clearPrayerCache()
                    listener?.onWeatherSettingsChanged()
                }
                .setNegativeButton(R.string.no, null)
                .show()
        }
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
        if (prefs.weatherSourceMode == Constants.WeatherSource.MANUAL && prefs.weatherLocationQuery.isBlank()) {
            prefs.weatherSourceMode = Constants.WeatherSource.DEVICE
            updateUI()
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
