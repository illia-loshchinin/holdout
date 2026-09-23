package holdout.app.vision

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import holdout.repcounter.Placement
import holdout.repcounter.Tilt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Which way the phone is pointing, from gravity (T046).
 *
 * This is how the app knows where the floor is **before the model has seen
 * anybody** — which is exactly when the member needs telling, because a phone
 * lying on its back is looking at the ceiling and no amount of getting into a
 * plank will fix that.
 *
 * `TYPE_GRAVITY` is a fused, already-smoothed reading. Where a device does not
 * provide it, the raw accelerometer stands in behind a low-pass filter, which
 * is what the fused sensor is doing anyway: at rest on a propped phone, gravity
 * is the whole of the acceleration.
 *
 * No permission, and nothing here is recorded.
 */
class PhoneTilt(context: Context) : SensorEventListener {

    private val sensors = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val sensor: Sensor? = sensors?.getDefaultSensor(Sensor.TYPE_GRAVITY)
        ?: sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val usingRawAccelerometer = sensor?.type == Sensor.TYPE_ACCELEROMETER

    private val _tilt = MutableStateFlow(Tilt(0f))
    val tilt: StateFlow<Tilt> = _tilt.asStateFlow()

    private var smoothed: FloatArray? = null

    fun start() {
        val sensor = sensor ?: return
        sensors?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop() {
        sensors?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val reading = if (usingRawAccelerometer) lowPass(event.values) else event.values
        _tilt.value = Placement.pitchFromGravity(reading[0], reading[1], reading[2])
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun lowPass(values: FloatArray): FloatArray {
        val previous = smoothed
        val next = if (previous == null) {
            values.copyOf()
        } else {
            FloatArray(3) { i -> previous[i] + ALPHA * (values[i] - previous[i]) }
        }
        smoothed = next
        return next
    }

    private companion object {
        /** Slow enough to ignore a hand steadying the phone, fast enough to follow a move. */
        const val ALPHA = 0.15f
    }
}
