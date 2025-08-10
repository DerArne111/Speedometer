package com.arne.bikestats.location

import android.location.Location
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.ticofab.androidgpxparser.parser.GPXParser
import junit.framework.TestCase
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CourseLocationProcessorTest: TestCase() {

    @Test
    fun loadGpxTest(){
        val clp = CourseLocationProcessor()
        val inStream = javaClass.classLoader?.getResourceAsStream("zeller_see_clean.gpx")
        clp.loadGpx(inStream!!)
    }

    @Test
    fun testPlayback(){
        val clp = CourseLocationProcessor()
        val inStream = javaClass.classLoader?.getResourceAsStream("zeller_see_clean.gpx")
        clp.loadGpx(inStream!!)

        val parser = GPXParser()
        val replayStream = javaClass.classLoader?.getResourceAsStream("zeller_see_replay.gpx")
        val parsedGpx = parser.parse(replayStream)

        var totalDistance = 0.0
        var lastLoc:Location? = null
        for(trackPoint in parsedGpx.tracks.first().trackSegments.first().trackPoints){
            val loc = Location("course")
            loc.longitude = trackPoint.longitude
            loc.latitude = trackPoint.latitude
            loc.altitude = trackPoint.elevation
            loc.accuracy = 5.0f
            loc.time = trackPoint.time.toInstant().millis
            if(lastLoc != null){
                totalDistance += lastLoc.distanceTo(loc)
            }
            lastLoc = loc
            clp.addLocationResult(loc)
            if(totalDistance > 1000){
                val averageSpeed = clp.getAvgSpeed(300)
                System.out.println("$averageSpeed $totalDistance ${clp.getTotalDistance()} $loc")
                assert(averageSpeed<4 && averageSpeed>1.5, { "AvgSpeed is $averageSpeed at $totalDistance" })
            }
        }
        assert(totalDistance >= 11000 && totalDistance <= 12000)
        val averageSpeed = clp.getAvgSpeed(20000)
        assert(averageSpeed<3.33 && averageSpeed > 2.9, { "AvgSpeed is $averageSpeed" })
    }
}