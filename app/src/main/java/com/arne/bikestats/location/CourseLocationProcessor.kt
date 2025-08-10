package com.arne.bikestats.location

import android.content.Context
import android.location.Location
import android.util.Log
import io.ticofab.androidgpxparser.parser.GPXParser
import io.ticofab.androidgpxparser.parser.domain.Gpx
import io.ticofab.androidgpxparser.parser.domain.TrackPoint
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.io.InputStream
import kotlin.math.sqrt


class CourseLocationProcessor: LocationProcessor {
    companion object {
        val TAG = "CourseLocationProcessor"
        val ACCURACY_THRESHOLD = 10.0
        val MATCHING_THRESHOLD = 10.0
        val MIN_SECONDS_EVAL = 3
    }

    var mTrackPoints:List<Location> = ArrayList<Location>()
    var mDistances:ArrayList<Double> = ArrayList<Double>()
    var mTimes: ArrayList<Long> = ArrayList<Long>()
    var mPosition = 0
    var mCurSpeed = 0.0f
    var mMaxSpeed = 0.0f


    fun loadGpx(gpxFile: InputStream){
        val parser = GPXParser() // consider injection
        try {
            val parsedGpx = parser.parse(gpxFile) // consider using a background thread
            mTrackPoints = parsedGpx.tracks.first().trackSegments.first().trackPoints.map {
                val newLoc = Location("course")
                newLoc.longitude = it.longitude
                newLoc.latitude = it.latitude
                newLoc.altitude = it.elevation
                newLoc.accuracy = 0.0f
                newLoc
            }
            var prevLocation = mTrackPoints.first()
            var totalDistance = 0.0
            for(loc in mTrackPoints){
                totalDistance += loc.distanceTo(prevLocation).toDouble()
                mDistances.add(totalDistance)
                mTimes.add(0L)
                prevLocation = loc
            }
        } catch (e: IOException) {
            // do something with this exception
            e.printStackTrace()
        } catch (e: XmlPullParserException) {
            e.printStackTrace()
        }
    }

    override fun addLocationResult(location: Location) {
        if(location.accuracy>ACCURACY_THRESHOLD)
            return
        mCurSpeed = location.speed
        if(mMaxSpeed < mCurSpeed){
            mMaxSpeed = mCurSpeed
        }
        val oldDistance = mTrackPoints[mPosition].distanceTo(location)
        val oldPosition = mPosition
        for(i in mPosition..<mTrackPoints.size){
            if(i > mPosition){
                mTimes[i] = location.time
            }
            val newDistance = mTrackPoints[i].distanceTo(location)
            if(newDistance < oldDistance && newDistance < MATCHING_THRESHOLD){
                mPosition = i
                break
            }
        }
        val jumpedPositions = mPosition - oldPosition
        if(jumpedPositions>1 && mTimes[oldPosition] > 0){
            val avgTimePassed = (mTimes[mPosition]-mTimes[oldPosition]).toDouble()/jumpedPositions
            for(i in 1..jumpedPositions){
                mTimes[oldPosition+i] = (mTimes[oldPosition]+avgTimePassed*i).toLong()
            }
        }
    }

    override fun getAvgSpeed(meters: Int): Float {
        var startIndex = 0
        for(i in mPosition downTo 0){
            if(mDistances[mPosition]-mDistances[i] > meters){
                startIndex = i
                break
            }
        }
        for(i in startIndex..mPosition){ // Only start at a waypoint we've been
            if(mTimes[i] > 0){
                startIndex = i
                break
            }
        }
        val diffSeconds = 0.001 * (mTimes[mPosition]-mTimes[startIndex])
        val distance = mDistances[mPosition]-mDistances[startIndex]
        if (diffSeconds < MIN_SECONDS_EVAL) {
            Log.i(GpsLocationProcessor.Companion.TAG, "Only processed $diffSeconds return 0.0")
            return 0.0f
        }
        return (distance / diffSeconds).toFloat()
    }

    override fun getCurSpeed(): Float {
        return mCurSpeed
    }

    override fun getMaxSpeed(): Float {
        return mMaxSpeed
    }

    override fun getTotalDistance(): Double {
        return mDistances[mPosition]
    }

    override fun clear() {
        mPosition = 0
        mTimes = ArrayList<Long>()
        for(loc in mTrackPoints){
            mTimes.add(0L)
        }
    }

    override fun save(context: Context) {

    }

    override fun load(context: Context) {

    }

    override fun getName(): String {
        return "Co"
    }
}