package com.arne.bikestats.utils

import android.location.Location
import android.util.Log

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
        val lastLoc = mLocations.firstOrNull()
        if(lastLoc != null){
            mTotalDistance += lastLoc.distanceTo(location)
        }
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
            val diffSeconds = (lastLocation.time-location.time)/1000
            lastLocation = location
            processedMeters += diffMeters
            processedSeconds += diffSeconds
            if(processedMeters>meters)
                break
        }
        if(processedSeconds < MIN_SECONDS_EVAL){
            Log.i(TAG, "Only processed $processedSeconds return 0.0")
            return 0.0f
        }
        return (processedMeters/processedSeconds).toFloat()
    }

    fun getCurSpeed(): Float {
        val lastLoc = mLocations.firstOrNull()
        if(lastLoc == null)
            return -1.0f
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