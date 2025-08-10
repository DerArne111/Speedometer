package com.arne.bikestats.location

import android.content.Context
import android.location.Location

interface LocationProcessor {
    fun addLocationResult(location: Location)
    fun getAvgSpeed(meters: Int): Float
    fun getCurSpeed(): Float
    fun getMaxSpeed(): Float
    fun getTotalDistance(): Double
    fun clear()
    fun save(context: Context)
    fun load(context: Context)
    fun getName(): String
}