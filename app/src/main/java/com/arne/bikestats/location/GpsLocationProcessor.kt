package com.arne.bikestats.location

import android.content.Context
import android.location.Location
import android.util.Log
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import kotlin.math.sqrt

class GpsLocationProcessor: LocationProcessor {
    companion object {
        val TAG = "LocationHelper"
    }

    val MAX_LOCATIONS = 64000
    val ACCURACY_THRESHOLD = 150.0
    val MIN_SECONDS_EVAL = 3.0

    var mLocations = ArrayDeque<Location>()
    var mTotalDistance = 0.0

    override fun addLocationResult(location: Location) {
        if (location.accuracy > ACCURACY_THRESHOLD) {
            Log.i(TAG, "Location above accuracy threshold: ${location.accuracy}. Discarding")
            return
        }
        if (mLocations.count() == 0) {
            mLocations.addFirst(location)
            return
        }
        val lastLoc = mLocations.first()
        if (lastLoc.time > location.time) {
            Log.w(TAG, "Time is flowing in the wrong direction!")
            return
        }
        val diff = lastLoc.distanceTo(location)

        if (diff * 0.3 < location.accuracy) {
            Log.i(
                TAG,
                "Location difference $diff too small for accuracy ${location.accuracy}. Discarding"
            )
            return
        }
        val overestimation =
            (sqrt(location.accuracy * location.accuracy + diff * diff) - diff)/2 // TODO: Make formula reasonable

        mTotalDistance += diff - overestimation

        mLocations.addFirst(location)
        if (mLocations.count() > MAX_LOCATIONS) {
            mLocations.removeLast()
        }
    }

    fun recalculateTotalDistance() {
        mTotalDistance = 0.0
        var lastLoc: Location? = null
        for (location in mLocations) {
            if (lastLoc == null) {
                lastLoc = location
                continue
            }
            val diff = lastLoc.distanceTo(location)
            val overestimation =
                (sqrt(location.accuracy * location.accuracy + diff * diff) - diff)/2 // TODO: Make formula reasonable
            mTotalDistance += diff - overestimation
            lastLoc = location
        }
    }

    override fun getAvgSpeed(meters: Int): Float {
        var processedMeters = 0.0
        var processedSeconds = 0.0
        var lastLocation: Location? = null
        for (location in mLocations) {
            if (lastLocation == null) {
                lastLocation = location
                continue
            }
            val diffMeters = location.distanceTo(lastLocation)
            val overestimation =
                sqrt(location.accuracy * location.accuracy + diffMeters * diffMeters) - diffMeters // TODO: Make formula reasonable
            val diffSeconds = 0.001 * (lastLocation.time - location.time)
            lastLocation = location
            processedMeters += diffMeters - overestimation
            processedSeconds += diffSeconds
            if (processedMeters > meters)
                break
        }
        if (processedSeconds < MIN_SECONDS_EVAL) {
            Log.i(TAG, "Only processed $processedSeconds return 0.0")
            return 0.0f
        }
        Log.i(TAG, "$processedMeters m in $processedSeconds s")
        return (processedMeters / processedSeconds).toFloat()
    }

    override fun getCurSpeed(): Float {
        val lastLoc = mLocations.firstOrNull() ?: return -1.0f
        return lastLoc.speed
    }

    override fun getMaxSpeed(): Float {
        if(mLocations.isEmpty())
            return -1.0f
        return mLocations.maxOf { l -> l.speed }
    }

    override fun getTotalDistance(): Double {
        return mTotalDistance
    }

    override fun clear() {
        mLocations.clear()
        mTotalDistance = 0.0
    }

    override fun save(context: Context) {
        val logFile = File(context.filesDir, "locations.txt")
        Log.i(TAG, "Saving to $logFile")
        if (logFile.exists()) {
            logFile.delete()
        }
        try {
            logFile.createNewFile()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        try {
            //BufferedWriter for performance, true to set append to file flag
            val buf = BufferedWriter(FileWriter(logFile))
            for (location in mLocations) {
                buf.append("${location.time},${location.longitude},${location.latitude},${location.altitude},${location.accuracy}")
                buf.newLine()
            }
            buf.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun load(context: Context) {
        val logFile = File(context.filesDir, "locations.txt")
        Log.i(TAG, "Loading from $logFile")
        if (!logFile.exists()) {
            Log.i(TAG, "File does not exist")
            return
        }
        try {
            val buf = BufferedReader(FileReader(logFile))
            for (line in buf.lines()) {
                val entries = line.split(",")
                val newLoc = Location("loaded")
                newLoc.time = entries[0].toLong()
                newLoc.longitude = entries[1].toDouble()
                newLoc.latitude = entries[2].toDouble()
                newLoc.altitude = entries[3].toDouble()
                newLoc.accuracy = entries[4].toFloat()
                mLocations.addLast(newLoc)
            }
            buf.close()
            recalculateTotalDistance()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun getName(): String {
        return "GPS"
    }
}