package sukun.minimalist.app.launcher.com.helper

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.appcompat.app.AppCompatDelegate

/**
 * Switches between light and dark theme based on ambient light (lux), similar to map apps.
 */
class AmbientThemeController(
    context: Context,
    initialDark: Boolean,
    private val onDarkModeChanged: (dark: Boolean) -> Unit,
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val lightSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
    private var lastAppliedDark: Boolean = initialDark
    private var registered = false

    fun start() {
        if (lightSensor == null || registered) return
        sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL)
        registered = true
    }

    fun stop() {
        if (!registered) return
        sensorManager.unregisterListener(this)
        registered = false
    }

    fun hasLightSensor(): Boolean = lightSensor != null

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_LIGHT) return
        val lux = event.values.firstOrNull() ?: return
        val wantDark = when {
            lastAppliedDark -> lux < LUX_LIGHT_THRESHOLD
            else -> lux < LUX_DARK_THRESHOLD
        }
        if (wantDark == lastAppliedDark) return
        lastAppliedDark = wantDark
        onDarkModeChanged(wantDark)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    companion object {
        /** Below this lux → switch to dark mode. */
        const val LUX_DARK_THRESHOLD = 12f

        /** When already dark, switch to light only above this lux (hysteresis). */
        const val LUX_LIGHT_THRESHOLD = 28f

        fun hasLightSensor(context: Context): Boolean {
            val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            return manager.getDefaultSensor(Sensor.TYPE_LIGHT) != null
        }

        fun nightModeForDark(dark: Boolean): Int =
            if (dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
    }
}
