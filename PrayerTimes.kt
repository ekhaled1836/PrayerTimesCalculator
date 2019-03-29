package me.ekhaled1836.islam.util

import androidx.annotation.VisibleForTesting
import java.text.NumberFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.*

//----------------------------------------------------------------------------------------------------//

// Extension Functions have to be top-level.
public fun PrayerTimes.HourMinuteSecond.to24H(): String {
    return "${hour.toTwoDigitsString()}:${minute.toTwoDigitsString()}"
}

public fun PrayerTimes.HourMinuteSecond.to12H(): String {
    return if (hour <= 12) "$hour:$minute ص"
    else "${hour - 12}:$minute م"
}

public fun PrayerTimes.HourMinuteSecond.to24H(numberFormatter: NumberFormat): String {
    return "${hour.toTwoDigitsString()}:${minute.toTwoDigitsString()}"
}

public fun PrayerTimes.HourMinuteSecond.to12H(numberFormatter: NumberFormat): String {
    return if (hour <= 12) "${hour.toTwoDigitsString(numberFormatter)}:${minute.toTwoDigitsString(numberFormatter)} ص"
    else "${(hour - 12).toTwoDigitsString(numberFormatter)}:${minute.toTwoDigitsString(numberFormatter)} م"
}

public fun Double.toHMS(): PrayerTimes.HourMinuteSecond {
    val hourFloat = this * 24
    val hour = hourFloat.toInt()
    val minuteFloat = (hourFloat - hour) * 60
    val minute = minuteFloat.toInt()
    val second = (minuteFloat - minute) * 60
    return PrayerTimes.HourMinuteSecond(hour, minute, second.toInt())
}

private fun Int.toTwoDigitsString(): String =
    if (this < 10) "0$this" else "$this"

private fun Int.toTwoDigitsString(numberFormatter: NumberFormat): String =
    if (this < 10) "${numberFormatter.format(0)}${numberFormatter.format(this)}" else numberFormatter.format(this)

/*private fun String.toTwoDigitsString(): String =
    if (this < 10) "0$this" else "$this"*/

//----------------------------------------------------------------------------------------------------//

// TODO: Improve accuracy by calculating twice. First to get an approximate time. Second using the approximate time as timeAfterMidnight
// TODO: The difference is only about 10s in the worst case, rounding to the nearest second can be sufficient.
// TODO: High Latitudes Adjustments.
// TODO: Elevation Adjustment.
// TODO: Asr Correctness.
class PrayerTimes(
    year: Int,
    month: Int,
    day: Int,
    timeZone: Double,
    latitude: Double,
    longitude: Double,
    elevation: Double,
    calculationMethod: CalculationMethod,
    asrCalculationCalculationMethod: AsrCalculationMethod,
    highLatitudesAdjustment: HighLatitudesAdjustment
) {
    private val sunDeclin: Double
    private val eqOfTime: Double
    private val haSunrise: Double

    init {
        /*val astronomicalMeasurements = getEquationOfTimeAndSolarDeclination(year, month, day, 12)
        sunDeclin = astronomicalMeasurements.second
        eqOfTime = astronomicalMeasurements.first
        val sunriseAngle = calculateSunriseAngle(elevation)
        haSunrise =
            calculateHASunrise(sunriseAngle, latitude, sunDeclin)*/

        val date = calculateDate(year, month, day)
        val time = calculateTime(12, 0, 0)
        val julianDay =
            calculateJulianDay(date, time, timeZone)
        val julianCentury = calculateJulianCentury(julianDay)
        val geomMeanLongSun = calculateGeomMeanLongSun(julianCentury)
        val geomMeanAnomSun = calculateGeomMeanAnomSun(julianCentury)
        val eccentEarthOrbit = calculateEccentEarthOrbit(julianCentury)
        val sunEqOfCtr =
            calculateSunEqOfCtr(geomMeanAnomSun, julianCentury)
        val sunTrueLong =
            calculateSunTrueLong(geomMeanLongSun, sunEqOfCtr)
        val sunAppLong =
            calculateSunAppLong(sunTrueLong, julianCentury)
        val meanObliqEcliptic =
            calculateMeanObliqEcliptic(julianCentury)
        val obliqCorr =
            calculateObliqCorr(meanObliqEcliptic, julianCentury)
        sunDeclin = calculateSunDeclin(obliqCorr, sunAppLong)
        val varY = calculateVarY(obliqCorr)
        eqOfTime = calculateEqOfTime(
            varY,
            geomMeanLongSun,
            eccentEarthOrbit,
            geomMeanAnomSun
        )
        val sunriseAngle = calculateSunriseAngle(elevation)
        haSunrise =
            calculateHASunrise(sunriseAngle, latitude, sunDeclin)
    }

    public val solarNoon by lazy {
        calculateSolarNoon(longitude, eqOfTime, timeZone)
    }
    public val sunriseTime by lazy {
        calculateSunriseTime(solarNoon, haSunrise)
    }
    public val sunsetTime by lazy {
        calculateSunsetTime(solarNoon, haSunrise)
    }
    public val fajrTime by lazy {
        val haFajr = calculateAngleHA(
            calculationMethod.fajrAngle,
            latitude,
            sunDeclin
        )
        calculateHATime(solarNoon, -haFajr)
    }
    public val asrTime by lazy {
        val asrAngle = calculateShadowMultipleAngle(
            if (asrCalculationCalculationMethod == AsrCalculationMethod.Standard) 1.0 else 2.0,
            latitude,
            sunDeclin
        )
        val haAsr = calculateAngleHA(asrAngle, latitude, sunDeclin)
        calculateHATime(solarNoon, haAsr)
    }
    public val maghribTime by lazy {
        if (calculationMethod.maghribAngle == null) {
            sunsetTime
        } else {
            val haMaghrib = calculateAngleHA(
                calculationMethod.maghribAngle,
                latitude,
                sunDeclin
            )
            calculateHATime(solarNoon, haMaghrib)
        }
    }
    public val ishaTime by lazy {
        if (calculationMethod.isIshaMinutes) {
            maghribTime + calculationMethod.ishaValue / (24.0 * 60.0)
        } else {
            val haIsha = calculateAngleHA(
                calculationMethod.ishaValue,
                latitude,
                sunDeclin
            )
            calculateHATime(solarNoon, haIsha)
        }
    }


    companion object {
        /*fun getAngleFloatTime(
            year: Int,
            month: Int,
            supplications_day: Int,
            approximateHour: Int,
            approximateMinute: Int,
            approximateSecond: Int,
            timeZone: Double,
            longitude: Double,
            latitude: Double,
            angle: Double
        ): Double {
            val date = calculateDate(year, month, supplications_day)
            val timePastLocalMidnight = calculateTime(approximateHour, approximateMinute, approximateSecond)
            val julianDay = calculateJulianDay(date, timePastLocalMidnight, timeZone)
            val julianCentury = calculateJulianCentury(julianDay)
            val geomMeanLongSun = calculateGeomMeanLongSun(julianCentury)
            val geomMeanAnomSun = calculateGeomMeanAnomSun(julianCentury)
            val eccentEarthOrbit = calculateEccentEarthOrbit(julianCentury)
            val sunEqOfCtr = calculateSunEqOfCtr(julianCentury, geomMeanAnomSun)
            val sunTrueLong = calculateSunTrueLong(geomMeanLongSun, sunEqOfCtr)
            val sunAppLong = calculateSunAppLong(sunTrueLong, julianCentury)
            val meanObliqEcliptic = calculateMeanObliqEcliptic(julianCentury)
            val obliqCorr = calculateObliqCorr(meanObliqEcliptic, julianCentury)
            val sunDeclin = calculateSunDeclin(obliqCorr, sunAppLong)
            val varY = calculateVarY(obliqCorr)
            val eqOfTime = calculateEqOfTime(varY, geomMeanLongSun, eccentEarthOrbit, geomMeanAnomSun)
            val solarNoon = calculateSolarNoon(longitude, eqOfTime, timeZone)
            if(abs(angle) < 0.05) return solarNoon
            val haSunrise = calculateHASunrise(latitude, sunDeclin)
            val sunriseTime = calculateSunriseTime(solarNoon, haSunrise)
        }*/

        //----------------------------------------------------------------------------------------------------//

        @VisibleForTesting
        fun calculateDate(
            year: Int,
            month: Int,
            day: Int
        ): Long =
            TimeUnit.DAYS.convert(
                GregorianCalendar(year, month, day).timeInMillis - GregorianCalendar(
                    1899,
                    12,
                    30
                ).timeInMillis, TimeUnit.MILLISECONDS
            )
        /* ChronoUnit.DAYS.between(LocalDate.of(1899, 12, 30), LocalDate.of(year, month, day)) */

        @VisibleForTesting
        fun calculateTime(
            hour: Int,
            minute: Int,
            second: Int
        ): Double =
            hour / 24.0 + minute / (24.0 * 60) + second / (24.0 * 60 * 60)

        @VisibleForTesting
        fun calculateJulianDay(
            date: Long,
            time: Double,
            timeZone: Double
        ): Double =
            date + 2415018.5 + time - timeZone / 24.0

        @VisibleForTesting
        fun calculateJulianCentury(
            julianDay: Double
        ): Double =
            (julianDay - 2451545) / 36525

        @VisibleForTesting
        fun calculateGeomMeanLongSun(
            julianCentury: Double
        ): Double =
            (280.46646 + julianCentury * (36000.76983 + julianCentury * 0.0003032)) % 360

        @VisibleForTesting
        fun calculateGeomMeanAnomSun(
            julianCentury: Double
        ): Double =
            357.52911 + julianCentury * (35999.05029 - 0.0001537 * julianCentury)

        @VisibleForTesting
        fun calculateEccentEarthOrbit(
            julianCentury: Double
        ): Double =
            0.016708634 - julianCentury * (0.000042037 + 0.0000001267 * julianCentury)

        @VisibleForTesting
        fun calculateSunEqOfCtr(
            geomMeanAnomSun: Double,
            julianCentury: Double
        ): Double =
            dsin(geomMeanAnomSun) * (1.914602 - julianCentury * (0.004817 + 0.000014 * julianCentury)) + dsin(
                2 * geomMeanAnomSun
            ) * (0.019993 - 0.000101 * julianCentury) + dsin(
                3 * geomMeanAnomSun
            ) * 0.000289

        @VisibleForTesting
        fun calculateSunTrueLong(
            geomMeanLongSun: Double,
            sunEqOfCtr: Double
        ): Double =
            geomMeanLongSun + sunEqOfCtr

        @VisibleForTesting
        fun calculateSunTrueAnom(
            geomMeanAnomSun: Double,
            sunEqOfCtr: Double
        ): Double =
            geomMeanAnomSun + sunEqOfCtr

        @VisibleForTesting
        fun calculateSunRadVector(
            eccentEarthOrbit: Double,
            sunTrueAnom: Double
        ): Double =
            (1.000001018 * (1 - eccentEarthOrbit * eccentEarthOrbit)) / (1 + eccentEarthOrbit * dcos(
                sunTrueAnom
            ))

        @VisibleForTesting
        fun calculateSunAppLong(
            sunTrueLong: Double,
            julianCentury: Double
        ): Double =
            sunTrueLong - 0.00569 - 0.00478 * dsin(125.04 - 1934.136 * julianCentury)

        @VisibleForTesting
        fun calculateMeanObliqEcliptic(
            julianCentury: Double
        ): Double =
            23 + (26 + ((21.448 - julianCentury * (46.815 + julianCentury * (0.00059 - julianCentury * 0.001813)))) / 60) / 60

        @VisibleForTesting
        fun calculateObliqCorr(
            meanObliqEcliptic: Double,
            julianCentury: Double
        ): Double =
            meanObliqEcliptic + 0.00256 * dcos(125.04 - 1934.136 * julianCentury)

        @VisibleForTesting
        fun calculateSunRtAscen(
            sunAppLong: Double,
            obliqCorr: Double
        ): Double =
            datan2(
                dcos(obliqCorr) * dsin(
                    sunAppLong
                ),
                dcos(
                    sunAppLong
                )
            )

        @VisibleForTesting
        fun calculateSunDeclin(
            obliqCorr: Double,
            sunAppLong: Double
        ): Double =
            dasin(
                dsin(
                    obliqCorr
                ) * dsin(sunAppLong)
            )

        @VisibleForTesting
        fun calculateVarY(
            obliqCorr: Double
        ): Double =
            dtan(obliqCorr / 2) * dtan(
                obliqCorr / 2
            )

        @VisibleForTesting
        fun calculateEqOfTime(
            varY: Double,
            geomMeanLongSun: Double,
            eccentEarthOrbit: Double,
            geomMeanAnomSun: Double
        ): Double =
            4 * radToDeg(
                varY * sin(2 * degToRad(geomMeanLongSun)) - 2 * eccentEarthOrbit * dsin(
                    geomMeanAnomSun
                ) + 4 * eccentEarthOrbit * varY * dsin(
                    geomMeanAnomSun
                ) * cos(2 * degToRad(geomMeanLongSun)) - 0.5 * varY * varY * sin(
                    4 * degToRad(geomMeanLongSun)
                ) - 1.25 * eccentEarthOrbit * eccentEarthOrbit * sin(
                    2 * degToRad(geomMeanAnomSun)
                )
            )

        @VisibleForTesting
        fun calculateAngleHA(
            angle: Double,
            latitude: Double,
            sunDeclin: Double
        ): Double =
            dacos(
                dcos(
                    90 + angle
                ) / (dcos(latitude) * dcos(
                    sunDeclin
                )) - dtan(latitude) * dtan(
                    sunDeclin
                )
            )

        @VisibleForTesting
        fun calculateSunriseAngle(elevation: Double) =
            0.833 /*+ 2.076 / 60 * sqrt(elevation)*/

        @VisibleForTesting
        fun calculateHASunrise(
            sunriseAngle: Double,
            latitude: Double,
            sunDeclin: Double
        ): Double =
            calculateAngleHA(sunriseAngle, latitude, sunDeclin)

        @VisibleForTesting
        fun calculateSolarNoon(
            longitude: Double,
            eqOfTime: Double,
            timeZone: Double
        ): Double =
            (720 - 4 * longitude - eqOfTime + timeZone * 60) / 1440

        @VisibleForTesting
        fun calculateHATime(
            solarNoon: Double,
            ha: Double
        ): Double =
            (solarNoon * 1440 + ha * 4) / 1440

        @VisibleForTesting
        fun calculateSunriseTime(
            solarNoon: Double,
            haSunrise: Double
        ): Double =
            calculateHATime(solarNoon, -haSunrise)

        @VisibleForTesting
        fun calculateSunsetTime(
            solarNoon: Double,
            haSunrise: Double
        ): Double =
            calculateHATime(solarNoon, +haSunrise)

        @VisibleForTesting
        fun calculateSunlightDuration(
            haSunrise: Double
        ): Double =
            8 * haSunrise

        @VisibleForTesting
        fun calculateTrueSolarTime(
            time: Double,
            eqOfTime: Double,
            longitude: Double,
            timeZone: Double
        ): Double =
            (time * 1440 + eqOfTime + 4 * longitude - 60 * timeZone) % 1440

        @VisibleForTesting
        fun calculateHourAngle(
            trueSolarTime: Double
        ): Double =
            if (trueSolarTime / 4 < 0) trueSolarTime / 4 + 180 else trueSolarTime / 4 - 180

        @VisibleForTesting
        fun calculateSolarZenithAngle(
            latitude: Double,
            sunDeclin: Double,
            hourAngle: Double
        ): Double =
            dacos(
                dsin(latitude) * dsin(
                    sunDeclin
                ) + dcos(latitude) * dcos(
                    sunDeclin
                ) * dcos(hourAngle)
            )

        @VisibleForTesting
        fun calculateSolarElevationAngle(
            solarZenithAngle: Double
        ): Double =
            90 - solarZenithAngle

        @VisibleForTesting
        fun calculateApproximateAtmosphericRefraction(
            solarElevationAngle: Double
        ): Double =
            when {
                solarElevationAngle > 85 -> 0.0
                solarElevationAngle > 5 -> 58.1 / dtan(
                    solarElevationAngle
                ) - 0.07 /
                        dtan(solarElevationAngle).pow(3) + 0.000086 /
                        dtan(solarElevationAngle).pow(5)
                solarElevationAngle > -0.575 -> 1735 + solarElevationAngle * (-518.2 + solarElevationAngle * (103.4 + solarElevationAngle * (-12.79 + solarElevationAngle * 0.711)))
                else -> -20.772 / dtan(
                    solarElevationAngle
                )
            } / 3600

        @VisibleForTesting
        fun calculateSolarElevationCorrectedForATMRefraction(
            solarElevationAngle: Double,
            approximateAtmosphericRefraction: Double
        ): Double =
            solarElevationAngle + approximateAtmosphericRefraction

        @VisibleForTesting
        fun calculateSolarAzimuthAngle(
            hourAngle: Double,
            latitude: Double,
            solarZenithAngle: Double,
            sunDeclin: Double
        ): Double =
            if (hourAngle > 0) (dacos(
                ((dsin(latitude) * dcos(
                    solarZenithAngle
                )) - dsin(sunDeclin)) / (dcos(
                    latitude
                ) * dsin(
                    solarZenithAngle
                ))
            ) + 180) % 360 else (540 - dacos(
                ((dsin(latitude) * dcos(
                    solarZenithAngle
                )) - dsin(sunDeclin)) / (dcos(
                    latitude
                ) * dsin(solarZenithAngle))
            )) % 360

        @VisibleForTesting
        fun calculateShadowMultipleAngle(multiple: Double, latitude: Double, sunDeclin: Double): Double =
            -dacot(
                multiple + dtan(
                    abs(latitude - sunDeclin) // TODO: abs?
                )
            )

        //----------------------------------------------------------------------------------------------------//

        @VisibleForTesting
        fun getEquationOfTimeAndSolarDeclination(
            year: Int,
            month: Int,
            day: Int,
            hour: Int
        ): Pair<Double, Double> {
            val w = 360 / 365.24
            val d = getOrdinalDate(year, month, day) - 1 + hour / 24
            val a = w * (d + 10)
            val b = a + 360 / PI * 0.0167 * PrayerTimes.dsin(w * (d - 2))
            val c = (a - datan(
                PrayerTimes.dtan(
                    b
                ) / PrayerTimes.dcos(23.44)
            )) / 180
            val equationOfTime = 720 * (c - c.roundToInt()) // min.
            val solarDeclination = -PrayerTimes.dasin(
                PrayerTimes.dsin(
                    23.44
                ) * PrayerTimes.dcos(b)
            ) // deg.
            return equationOfTime to solarDeclination
        }

        private fun getOrdinalDate(year: Int, month: Int, day: Int): Double =
            when (month) {
                1 -> day.toDouble() + 12.0 / 24.0
                2 -> 31 + day.toDouble() + 12.0 / 24.0
                else -> 30 * (month - 1) + (0.6 * (month + 1)).toInt() - (if (isLeapYear(
                        year
                    )
                ) 2 else 3) + day + 12.0 / 24.0
            }

        private fun isLeapYear(year: Int): Boolean = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)

        //----------------------------------------------------------------------------------------------------//

        private fun calculateNightTime(sunrise: Double, sunset: Double): Double =
            calculateTimeDiff(sunrise, sunset, ccw = true)

        private fun calculateTimeDiff(formerTime: Double, latterTime: Double, ccw: Boolean): Double =
            if (!ccw) latterTime - formerTime else 1 - (latterTime - formerTime)

        private fun nightPortion(
            highLatitudesAdjustment: HighLatitudesAdjustment,
            angle: Double,
            nightTime: Double
        ): Double =
            when (highLatitudesAdjustment) {
                HighLatitudesAdjustment.AngleBased -> angle / 60.0 * nightTime
                HighLatitudesAdjustment.NightMiddle -> 0.5 * nightTime
                HighLatitudesAdjustment.OneSeventh -> 1.0 / 7.0 * nightTime
                HighLatitudesAdjustment.None -> 0.0
            }

        private fun adjustHLTime(portion: Double, time: Double, baseTime: Double, ccw: Boolean): Double {
            val timeDiff = calculateTimeDiff(baseTime, time, ccw)
            return if (timeDiff > portion)
                baseTime + if (ccw) -portion else portion
            else
                time
        }

        //----------------------------------------------------------------------------------------------------//

        private fun degToRad(angle: Double): Double =
            angle / 180 * PI

        private fun radToDeg(rad: Double): Double =
            rad * 180.0 / PI

        private fun dsin(angle: Double): Double =
            sin(degToRad(angle))

        private fun dcos(angle: Double): Double =
            cos(degToRad(angle))

        private fun dtan(angle: Double): Double =
            tan(degToRad(angle))

        private fun dasin(ratio: Double): Double =
            radToDeg(asin(ratio))

        private fun dacos(ratio: Double): Double =
            radToDeg(acos(ratio))

        private fun datan(ratio: Double): Double =
            radToDeg(atan(ratio))

        private fun datan2(y: Double, x: Double): Double =
            radToDeg(atan2(y, x))

        private fun dacot(ratio: Double): Double =
            datan2(1.0, ratio)
    }

    public enum class CalculationMethod(
        val fajrAngle: Double,
        val maghribAngle: Double?,
        val isIshaMinutes: Boolean,
        val ishaValue: Double
    ) {
        MWL(18.0, null, false, 17.0), // Muslim World League
        ISNA(15.0, null, false, 15.0), // Islamic Society of North America (ISNA)
        Egypt(19.5, null, false, 17.5), // Egyptian General Authority of Survey
        Makkah(
            18.5,
            null,
            true,
            90.0
        ), // Umm Al-Qura University, Makkah // 120min during Ramadan! // Fajr was 19 degrees before 1430 Hijri!
        Karachi(18.0, null, false, 18.0), // University of Islamic Sciences, Karachi
        Tehran(
            17.7,
            4.5,
            false,
            14.0
        ), // Institute of Geophysics, University of Tehran // Isha is not explicitly specified in this method! // Midnight is between Sunset and Fajr!
        Jafari(16.0, 4.0, false, 14.0) // Shia Ithna-Ashari, Leva Institute, Qum // Midnight is between Sunset and Fajr!
    }

    public enum class AsrCalculationMethod {
        Standard, // Shafi`i, Maliki, Ja`fari, Hanbali
        Hanafi
    }

    public enum class HighLatitudesAdjustment {
        NightMiddle,
        AngleBased,
        OneSeventh,
        None
    }

    public data class HourMinuteSecond(
        public val hour: Int,
        public val minute: Int,
        public val second: Int
    )

    /*public class HourMinuteSecond(hour: Int, minute: Int, second: Int) {
        val mSecond: Int = second % 60
        val mMinute: Int = if (second < 60) {
            minute % 60
        } else {
            minute % 60 + second / 60
        }
        val mHour: Int = if (minute < 60) {
            hour % 60
        } else {
            hour % 60 + minute / 60
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as HourMinuteSecond

            if (mSecond != other.mSecond) return false
            if (mMinute != other.mMinute) return false
            if (mHour != other.mHour) return false

            return true
        }

        override fun hashCode(): Int {
            var result = mSecond
            result = 31 * result + mMinute
            result = 31 * result + mHour
            return result
        }

        override fun toString(): String {
            return "HourMinuteSecond(mSecond=$mSecond, mMinute=$mMinute, mHour=$mHour)"
        }
    }*/
}