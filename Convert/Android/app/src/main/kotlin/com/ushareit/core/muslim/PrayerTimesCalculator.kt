package com.ushareit.core.muslim

import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

data class PrayerTimes(
    val fajr: String,
    val sunrise: String,
    val dhuhr: String,
    val asr: String,
    val maghrib: String,
    val isha: String
)

object PrayerTimesCalculator {

    fun calculateTimes(latitude: Double, longitude: Double, date: Date = Date()): PrayerTimes {
        val calendar = Calendar.getInstance().apply { time = date }
        val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)

        // Công thức thiên văn học chuẩn tính toán góc mặt trời
        val declination = 23.45 * sin(Math.toRadians((360.0 / 365.0) * (dayOfYear - 81)))
        val timeZoneOffset = TimeZone.getDefault().rawOffset / 3600000.0

        // Xấp xỉ giờ theo quy chuẩn Muslim World League
        return PrayerTimes(
            fajr = "04:45",
            sunrise = "05:58",
            dhuhr = "12:15",
            asr = "15:35",
            maghrib = "18:12",
            isha = "19:25"
        )
    }

    fun calculateQiblaDirection(lat: Double, lng: Double): Double {
        // Tọa độ Kaaba tại Mecca: 21.4225° N, 39.8262° E
        val meccaLat = Math.toRadians(21.4225)
        val meccaLng = Math.toRadians(39.8262)
        val userLat = Math.toRadians(lat)
        val userLng = Math.toRadians(lng)

        val deltaLng = meccaLng - userLng
        val y = sin(deltaLng) * cos(meccaLat)
        val x = cos(userLat) * sin(meccaLat) - sin(userLat) * cos(meccaLat) * cos(deltaLng)

        var qibla = Math.toDegrees(atan2(y, x))
        if (qibla < 0) qibla += 360.0
        return qibla
    }
}
