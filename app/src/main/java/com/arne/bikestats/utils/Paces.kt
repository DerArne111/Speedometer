package com.arne.bikestats.utils


data class Pace(val paceMin: Int, val paceSec: Int) {
    companion object {
        fun fromSpeed(speed: Float): Pace {
            val pace = (1000.0f / 60.0f) / speed
            val paceMin = pace.toInt()
            val paceSec = ((pace - paceMin) * 60).toInt()
            return Pace(paceMin, paceSec)
        }

        fun fromSpeed(speed: Double): Pace {
            return fromSpeed(speed.toFloat())
        }
    }
}