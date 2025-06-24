package com.arne.bikestats.utils

import android.location.Location
import android.util.Log
import kotlin.math.sqrt


data class Pace(val paceMin: Int, val paceSec: Int){
    companion object{
        fun fromSpeed(speed: Float): Pace{
            val pace = (1000/60)/speed
            val paceMin = pace.toInt()
            val paceSec = ((pace-paceMin)*60).toInt()
            return Pace(paceMin, paceSec)
        }
        fun fromSpeed(speed: Double): Pace{
            return fromSpeed(speed.toFloat())
        }
    }
}

class LocationHelper{
    companion object{
        val TAG = "LocationHelper"
    }
    val MAX_LOCATIONS = 64000
    val ACCURACY_THRESHOLD = 15.0
    val MIN_SECONDS_EVAL = 3.0

    var mLocations = ArrayDeque<Location>()
    var mTotalDistance = 0.0




    fun addLocationResult(location: Location){
        if(location.accuracy > ACCURACY_THRESHOLD){
            Log.i(TAG, "Location above accuracy threshold: ${location.accuracy}. Discarding")
            return
        }
        if(mLocations.count() == 0){
            mLocations.addFirst(location)
            return
        }
        val lastLoc = mLocations.first()
        if(lastLoc.time > location.time){
            Log.w(TAG, "Time is flowing in the wrong direction!")
            return
        }
        val diff = lastLoc.distanceTo(location)

        if(diff*0.2 < location.accuracy){
            Log.i(TAG, "Location difference $diff too small for accuracy ${location.accuracy}. Discarding")
            return
        }

        val overestimation = sqrt(location.accuracy*location.accuracy+diff*diff)-diff // TODO: Make formula reasonable

        mTotalDistance += diff-overestimation

        mLocations.addFirst(location)
        if(mLocations.count() > MAX_LOCATIONS){
            mLocations.removeLast()
        }
    }

    fun getAvgSpeed(meters:Int): Float {
        var processedMeters = 0.0
        var processedSeconds = 0.0
        var lastLocation: Location? = null
        for(location in mLocations){
            if(lastLocation == null){
                lastLocation = location
                continue
            }
            val diffMeters = location.distanceTo(lastLocation)
            val overestimation = sqrt(location.accuracy*location.accuracy+diffMeters*diffMeters)-diffMeters // TODO: Make formula reasonable
            val diffSeconds = 0.001*(lastLocation.time-location.time)
            lastLocation = location
            processedMeters += diffMeters-overestimation
            processedSeconds += diffSeconds
            if(processedMeters>meters)
                break
        }
        if(processedSeconds < MIN_SECONDS_EVAL){
            Log.i(TAG, "Only processed $processedSeconds return 0.0")
            return 0.0f
        }
        Log.i(TAG, "$processedMeters m in $processedSeconds s")
        return (processedMeters/processedSeconds).toFloat()
    }

    fun getCurSpeed(): Float {
        val lastLoc = mLocations.firstOrNull() ?: return -1.0f
        return lastLoc.speed
    }

    fun getTotalDistance(): Double{
        return mTotalDistance
    }

    fun clear() {
        mLocations.clear()
        mTotalDistance = 0.0
    }
}