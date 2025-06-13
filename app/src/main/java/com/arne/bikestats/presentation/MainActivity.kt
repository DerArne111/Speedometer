/* While this template provides a good starting point for using Wear Compose, you can always
 * take a look at https://github.com/android/wear-os-samples/tree/main/ComposeStarter and
 * https://github.com/android/wear-os-samples/tree/main/ComposeAdvanced to find the most up to date
 * changes to the libraries and their usages.
 */

package com.arne.bikestats.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.MotionEvent.ACTION_UP
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Snackbar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DataTypeAvailability
import androidx.health.services.client.data.DeltaDataType
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.arne.bikestats.R
import com.arne.bikestats.presentation.theme.BikeStatsTheme
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationListener
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import java.util.Locale
import java.util.Timer
import java.util.TimerTask

interface ViewModel {
    var mCurSpeed: Double
    var mCurBpm: Double
    var mCurSpeedValid: Boolean
    var mCurBpmValid: Boolean
    var mKeepScreenOn: Boolean
    var mMaxSpeed: Double
    var mMaxBpm: Double
}

class MainActivity : ComponentActivity(), ViewModel {
    override var mCurSpeed by mutableStateOf(-1.0)
    override var mCurBpm by mutableStateOf(-1.0)
    override var mMaxSpeed by mutableStateOf(-1.0)
    override var mMaxBpm by mutableStateOf(-1.0)
    override var mCurSpeedValid by mutableStateOf(false)
    override var mCurBpmValid by mutableStateOf(false)
    override var mKeepScreenOn by mutableStateOf(false)

    var mLastLocation: Location? = null

    lateinit var fusedLocationProviderClient: FusedLocationProviderClient


    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        super.onCreate(savedInstanceState)

        setTheme(android.R.style.Theme_DeviceDefault)

        setContent {
            WearApp(this)
        }
    }

    override fun onResume() {
        super.onResume()
        mCurBpmValid = false
        mCurSpeedValid = false
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED) {
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

        val measureClient = HealthServices.getClient(applicationContext).measureClient
        val heartRateCallback = object : MeasureCallback {
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
        measureClient.registerMeasureCallback(DataType.HEART_RATE_BPM, heartRateCallback)


        fusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(applicationContext)
        val locationCallback = object : LocationCallback() {
            override fun onLocationAvailability(p0: LocationAvailability) {
                Log.i("BikeStats", "onLocationAvailability $p0")
                if (!p0.isLocationAvailable) {
                    mCurSpeedValid = false
                    mCurSpeed = 0.0
                }
            }

            override fun onLocationResult(p0: LocationResult) {
                if (p0.lastLocation == null)
                    return
                if (mLastLocation == null) {
                    mLastLocation = p0.lastLocation
                    return
                }
                if(p0.lastLocation!!.hasSpeed()){
                    mCurSpeed = p0.lastLocation!!.speed.toDouble()
                    Log.i("BikeStats", "Received new speed: $mCurSpeed; ${p0.lastLocation!!.speedAccuracyMetersPerSecond}")
                }else {
                    val distance = p0.lastLocation!!.distanceTo(mLastLocation!!)
                    val timeDiff = p0.lastLocation!!.time - mLastLocation!!.time
                    if (timeDiff < 50) {
                        return
                    }
                    mCurSpeed = (1000 * distance / timeDiff).toDouble()
                }
                mCurSpeedValid = true
                mLastLocation = p0.lastLocation
                if (mCurSpeed > mMaxSpeed) {
                    mMaxSpeed = mCurSpeed
                }
            }
        }
        val locationRequest = LocationRequest.Builder(1000)
            .setPriority(android.location.LocationRequest.QUALITY_HIGH_ACCURACY)
            .setMinUpdateIntervalMillis(100).build()
        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, null)
    }

    override fun onPause(){
        super.onPause()
        if(!mKeepScreenOn)
            finish()
    }

    var mTapCount = 0
    var mTapEvaluator = Timer()
    val TAP_TIMEOUT = 400L
    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev == null)
            return super.dispatchTouchEvent(ev)

        if (ev.action == ACTION_UP) {
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
                        }
                        mTapCount = 0
                    }
                }
            }, TAP_TIMEOUT)

        }
        return super.dispatchTouchEvent(ev)
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
            TimeText()
            Column {
                Speed(viewModel = viewModel)
                Bpm(viewModel = viewModel)
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
            text = if (viewModel.mCurSpeed < 0) "-" else String.format(Locale.ROOT, "%.1f", viewModel.mCurSpeed * 3.6),
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
                text = if (viewModel.mMaxSpeed < 0) "- max" else String.format(Locale.ROOT, "%.1f", viewModel.mMaxSpeed * 3.6)+" max",
                style = TextStyle(
                    fontSize = 12.sp,
                ),
            )
        }
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
        override var mMaxBpm: Double = 163.0
        override var mMaxSpeed: Double = 14.0
        override var mCurBpmValid: Boolean = true
        override var mCurSpeedValid: Boolean = true
        override var mKeepScreenOn: Boolean = true
    })
}