/* While this template provides a good starting point for using Wear Compose, you can always
 * take a look at https://github.com/android/wear-os-samples/tree/main/ComposeStarter and
 * https://github.com/android/wear-os-samples/tree/main/ComposeAdvanced to find the most up to date
 * changes to the libraries and their usages.
 */

package com.arne.bikestats.presentation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.MotionEvent
import android.view.MotionEvent.ACTION_UP
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.InputDeviceCompat
import androidx.core.view.MotionEventCompat
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.MeasureClient
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DataTypeAvailability
import androidx.health.services.client.data.DeltaDataType
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.TimeTextDefaults
import com.arne.bikestats.presentation.theme.BikeStatsTheme
import com.arne.bikestats.utils.LocationHelper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.Timer
import java.util.TimerTask
import kotlin.math.max
import kotlin.math.min
import androidx.core.content.edit
import androidx.health.services.client.unregisterMeasureCallback
import kotlinx.coroutines.runBlocking


interface ViewModel {
    var mCurSpeed: Double // current speed
    var mAvgSpeed: Double // avg speed over the last mAvgDistance meters
    var mAvgDistance: Int // see mAvgSpeed
    var mCurBpm: Double // current heart rate
    var mCurSpeedValid: Boolean
    var mCurBpmValid: Boolean
    var mKeepScreenOn: Boolean // keep screen on flag
    var mMaxSpeed: Double // max speed since last reset
    var mMaxBpm: Double // max heart rate since last reset
    var mTotalDistance: Double // total distance since last reset
    var mPaceMode: Boolean // show pace (as opposed to speed)
    var mCurrentTime: String // formated time
}

class MainActivity : ComponentActivity(), ViewModel {

    val AVERAGE_DISTANCES = listOf(50, 200, 1000, 2000, 5000, 10000, 20000, 50000)
    var mAverageDistanceIndex = 3

    override var mCurSpeed by mutableStateOf(-1.0)
    override var mCurBpm by mutableStateOf(-1.0)
    override var mMaxSpeed by mutableStateOf(-1.0)
    override var mMaxBpm by mutableStateOf(-1.0)
    override var mCurSpeedValid by mutableStateOf(false)
    override var mCurBpmValid by mutableStateOf(false)
    override var mKeepScreenOn by mutableStateOf(false)
    override var mAvgSpeed by mutableStateOf(-1.0)
    override var mAvgDistance by mutableStateOf(AVERAGE_DISTANCES[mAverageDistanceIndex])
    override var mTotalDistance by mutableStateOf(-1.0)
    override var mPaceMode by mutableStateOf(false)
    override var mCurrentTime by mutableStateOf("-")

    val mLocations = LocationHelper()
    var mListenersRegistered = false
    var mTimeUpdater: Timer? = null
    var mLocationProviderClient: FusedLocationProviderClient? = null
    var mHealthClient: MeasureClient? = null
    var mLocationCallback = object : LocationCallback() {
        override fun onLocationAvailability(p0: LocationAvailability) {
            Log.i(TAG, "onLocationAvailability $p0")
            if (!p0.isLocationAvailable) {
                mCurSpeedValid = false
                mCurSpeed = 0.0
            }
        }

        override fun onLocationResult(p0: LocationResult) {
            if (p0.lastLocation == null)
                return
            mLocations.addLocationResult(p0.lastLocation!!)
            //appendLocationLog(p0.lastLocation!!)
            mCurSpeed = p0.lastLocation!!.speed.toDouble()
            mAvgSpeed = mLocations.getAvgSpeed(mAvgDistance).toDouble()
            mTotalDistance = mLocations.getTotalDistance()
            mCurSpeedValid = true
            if (mCurSpeed > mMaxSpeed) {
                mMaxSpeed = mCurSpeed
            }
        }
    }
    var mHeartRateCallback = object : MeasureCallback {
        override fun onAvailabilityChanged(
            dataType: DeltaDataType<*, *>,
            availability: Availability
        ) {
            if (availability != DataTypeAvailability.AVAILABLE) {
                mCurBpmValid = false
            }
        }

        override fun onDataReceived(data: DataPointContainer) {
            mCurBpm = data.getData(DataType.HEART_RATE_BPM).last().value
            mCurBpmValid = true
            if (mCurBpm > mMaxBpm) {
                mMaxBpm = mCurBpm
            }
        }
    }

    companion object {
        val TAG = "BikeStats"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        load()

        setTheme(android.R.style.Theme_DeviceDefault)

        setContent {
            WearApp(this)
        }
    }

    fun appendLocationLog(location: Location) {
        val logFile = File(applicationContext.filesDir, "locations.log")
        Log.i(TAG, "Logfile: $logFile")
        if (!logFile.exists()) {
            try {
                logFile.createNewFile()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        try {
            //BufferedWriter for performance, true to set append to file flag
            val buf = BufferedWriter(FileWriter(logFile, true))
            buf.append("${location.time},${location.longitude},${location.latitude},${location.altitude},${location.accuracy}")
            buf.newLine()
            buf.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun onResume() {
        super.onResume()
        mCurBpmValid = false
        mCurSpeedValid = false
        registerListeners()
    }

    fun registerListeners() {
        if (mListenersRegistered) {
            return
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 0)
            return
        }
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BODY_SENSORS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.BODY_SENSORS), 0)
            return
        }
        mHealthClient = HealthServices.getClient(applicationContext).measureClient
        mHealthClient!!.registerMeasureCallback(DataType.HEART_RATE_BPM, mHeartRateCallback)

        mLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(applicationContext)
        val locationRequest = LocationRequest.Builder(1000)
            .setPriority(android.location.LocationRequest.QUALITY_HIGH_ACCURACY)
            .setMinUpdateIntervalMillis(1000).build()
        mLocationProviderClient!!.requestLocationUpdates(locationRequest, mLocationCallback, null)

        mTimeUpdater = Timer()
        mTimeUpdater!!.schedule(object : TimerTask() {
            override fun run() {
                runOnUiThread {
                    mCurrentTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                }
            }
        }, 0, 1000)
        mListenersRegistered = true
    }

    fun unregisterListeners(){
        if(!mListenersRegistered)
            return
        mLocationProviderClient?.removeLocationUpdates(mLocationCallback)
        mTimeUpdater?.cancel()
        runBlocking {
            mHealthClient?.unregisterMeasureCallback(DataType.Companion.HEART_RATE_BPM, mHeartRateCallback)
        }
        mListenersRegistered = false
    }

    override fun onPause() {
        super.onPause()
        unregisterListeners()
        if (!mKeepScreenOn)
            finish()
    }

    override fun onStop() {
        super.onStop()
        save()
    }

    fun save() {
        mLocations.save(applicationContext)
        val sharedPref = this.getPreferences(Context.MODE_PRIVATE)
        sharedPref.edit {
            putFloat("max_bpm", mMaxBpm.toFloat())
            putBoolean("pace_mode", mPaceMode)
            putInt("average_distance_index", mAverageDistanceIndex)
        }
    }

    fun load() {
        mLocations.load(applicationContext)
        mMaxSpeed = mLocations.getMaxSpeed().toDouble()
        mTotalDistance = mLocations.getTotalDistance()

        val sharedPref = this.getPreferences(Context.MODE_PRIVATE)
        mMaxBpm = sharedPref.getFloat("max_bpm", -1.0f).toDouble()
        mPaceMode = sharedPref.getBoolean("pace_mode", false)
        mAverageDistanceIndex =
            min(sharedPref.getInt("average_distance_index", 0), AVERAGE_DISTANCES.count() - 1)
        mAvgDistance = AVERAGE_DISTANCES[mAverageDistanceIndex]
    }

    var mTapCount = 0
    var mTapEvaluator = Timer()
    val TAP_TIMEOUT = 400L
    val LONG_PRESS_TIME = 1000L
    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev == null)
            return super.dispatchTouchEvent(ev)

        if (ev.action == ACTION_UP) {
            if (ev.eventTime - ev.downTime > LONG_PRESS_TIME) {
                mPaceMode = !mPaceMode
                Log.i(TAG, "Switching to pacemode: $mPaceMode")
                return super.dispatchTouchEvent(ev)
            }
            mTapCount += 1
            mTapEvaluator.cancel()
            mTapEvaluator = Timer()
            mTapEvaluator.schedule(object : TimerTask() {
                override fun run() {
                    runOnUiThread {
                        if (mTapCount == 2) {
                            mKeepScreenOn = !mKeepScreenOn
                            if (mKeepScreenOn) {
                                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                            } else {
                                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                            }
                        }
                        if (mTapCount == 3) {
                            mMaxSpeed = 0.0
                            mMaxBpm = 0.0
                            mTotalDistance = 0.0
                            mLocations.clear()
                        }
                        mTapCount = 0
                    }
                }
            }, TAP_TIMEOUT)

        }
        return super.dispatchTouchEvent(ev)
    }

    var mScrollAmount = 0.0
    override fun dispatchGenericMotionEvent(ev: MotionEvent?): Boolean {
        if (ev != null && ev.action == MotionEvent.ACTION_SCROLL
            && ev.isFromSource(InputDeviceCompat.SOURCE_ROTARY_ENCODER)
            && mPaceMode
        ) {
            val delta = ev.getAxisValue(MotionEventCompat.AXIS_SCROLL)
            mScrollAmount += delta
            if (mScrollAmount > 0.4) {
                mAverageDistanceIndex = max(mAverageDistanceIndex - 1, 0)
                mAvgDistance = AVERAGE_DISTANCES[mAverageDistanceIndex]
                mAvgSpeed = mLocations.getAvgSpeed(mAvgDistance).toDouble()

                mScrollAmount = 0.0
                val vibrator = baseContext?.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                return true
            }
            if (mScrollAmount < -0.4) {
                mAverageDistanceIndex =
                    min(mAverageDistanceIndex + 1, AVERAGE_DISTANCES.count() - 1)
                mAvgDistance = AVERAGE_DISTANCES[mAverageDistanceIndex]
                mAvgSpeed = mLocations.getAvgSpeed(mAvgDistance).toDouble()
                mScrollAmount = 0.0
                val vibrator = baseContext?.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))

                return true
            }
        }
        return super.dispatchGenericMotionEvent(ev)
    }
}


@Composable
fun WearApp(viewModel: ViewModel) {
    BikeStatsTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background),
            contentAlignment = Alignment.Center
        ) {
            key(viewModel.mCurrentTime) {
                TimeText(timeSource = TimeTextDefaults.timeSource("HH:mm:ss"))
            }
            Column {
                if (viewModel.mPaceMode) {
                    Pace(viewModel = viewModel)
                } else {
                    Speed(viewModel = viewModel)
                }
                Bpm(viewModel = viewModel)
                Distance(viewModel = viewModel)
                Text("")
                DataQuality(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun Speed(viewModel: ViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            color = MaterialTheme.colors.primary,
            text = if (viewModel.mCurSpeed < 0) "-" else String.format(
                Locale.ROOT,
                "%.1f",
                viewModel.mCurSpeed * 3.6
            ),
            style = TextStyle(
                fontSize = 48.sp,
            )
        )
        Column {
            Text(
                color = MaterialTheme.colors.primary,
                text = "km/h",
                style = TextStyle(
                    fontSize = 12.sp,
                ),
            )
            Text(
                color = MaterialTheme.colors.primary,
                text = if (viewModel.mMaxSpeed < 0) "- max" else String.format(
                    Locale.ROOT,
                    "%.1f",
                    viewModel.mMaxSpeed * 3.6
                ) + " max",
                style = TextStyle(
                    fontSize = 12.sp,
                ),
            )
        }
    }
}

@Composable
fun Pace(viewModel: ViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val avgPace = com.arne.bikestats.utils.Pace.fromSpeed(viewModel.mAvgSpeed)

        Text(
            color = MaterialTheme.colors.primary,
            text = if (viewModel.mAvgSpeed < 0.2) "-" else "" + avgPace.paceMin + ":" + "%02d".format(
                avgPace.paceSec
            ),
            style = TextStyle(
                fontSize = 48.sp,
            )
        )
        Column {
            Text(
                color = MaterialTheme.colors.primary,
                text = "min/km",
                style = TextStyle(
                    fontSize = 12.sp,
                ),
            )
            Text(
                color = MaterialTheme.colors.primary,
                text = "" + viewModel.mAvgDistance + " m",
                style = TextStyle(
                    fontSize = 12.sp,
                ),
            )
        }
    }
}

@Composable
fun Distance(viewModel: ViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            color = MaterialTheme.colors.secondary,
            text = if (viewModel.mTotalDistance < 0) "- km" else String.format(
                Locale.ROOT,
                "%.2f",
                viewModel.mTotalDistance / 1000
            ) + " km",
            style = TextStyle(
                fontSize = 24.sp,
            )
        )
    }
}

@Composable
fun Bpm(viewModel: ViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            color = MaterialTheme.colors.secondary,
            text = if (viewModel.mCurBpm < 0) "-" else "${viewModel.mCurBpm.toInt()}",
            style = TextStyle(
                fontSize = 24.sp,
            )
        )
        Column {
            Text(
                color = MaterialTheme.colors.secondary,
                text = "bpm",
                style = TextStyle(
                    fontSize = 12.sp,
                ),
            )
            Text(
                color = MaterialTheme.colors.secondary,
                text = if (viewModel.mMaxBpm < 0) "- max" else "${viewModel.mMaxBpm.toInt()} max",
                style = TextStyle(
                    fontSize = 12.sp,
                ),
            )
        }
    }
}

@Composable
fun DataQuality(viewModel: ViewModel) {
    var text: String = ""
    //if (viewModel.mCurSpeedValid)
    //    text += "⌖"
    //if (viewModel.mCurBpmValid)
    //    text += "♥"
    if (viewModel.mKeepScreenOn)
        text += "\uD83D\uDD12"
    Text(
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        color = Color.Gray,
        text = text,
        style = TextStyle(
            fontSize = 8.sp,
        )
    )
}


@Preview(device = Devices.WEAR_OS_SMALL_ROUND, showSystemUi = true)
@Composable
fun DefaultPreview() {
    WearApp(object : ViewModel {
        override var mCurBpm: Double = 63.0
        override var mCurSpeed: Double = 6.0
        override var mAvgSpeed: Double = 0.2
        override var mAvgDistance: Int = 200
        override var mMaxBpm: Double = 163.0
        override var mTotalDistance: Double = 13024.2
        override var mPaceMode: Boolean = true
        override var mCurrentTime: String = "12:41:34"
        override var mMaxSpeed: Double = 14.0
        override var mCurBpmValid: Boolean = true
        override var mCurSpeedValid: Boolean = true
        override var mKeepScreenOn: Boolean = true

    })
}