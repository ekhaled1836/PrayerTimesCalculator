# PrayerTimesCalculator
A utility class to calculate prayers times.

## Usage
Each instace of this class is immutable, so to change any property you have to create another instance.

you can create an instance like so:

```kotlin
  val prayerTimes = PrayerTimes(
      <year>,
      <month>,
      <day>,
      <timezone in hours> can include fractions,
      <latitude>,
      <longitude>,
      <altitude>,
      <calculation method> one of:
          PrayerTimes.CalculationMethod.Egypt
          PrayerTimes.CalculationMethod.MWL
          PrayerTimes.CalculationMethod.ISNA
          PrayerTimes.CalculationMethod.Makkah
          PrayerTimes.CalculationMethod.Karachi
          PrayerTimes.CalculationMethod.Egypt
      ,
      <asr calculation method> one of:
          PrayerTimes.AsrCalculationMethod.Standard
          PrayerTimes.AsrCalculationMethod.Hanafi
      ,
      PrayerTimes.HighLatitudesAdjustment.None
  )
```

To get a prayer time you can access its final field:
```fajrTime```
```sunriseTime```
```solarNoonTime```
```asrTime```
```maghribTime```
```ishaTime```
theses fields are the fraction of the day, i.e. 0.5 is hald a day after midnight.

To convert these to Hour/Minute/Second you can use the Kotlin Extension Function ```.toHMS()```
or if you are in Java the static function ```toHMS(float)```.

To get the time in 12h or 24h format you can again use the intiutively named extension function ot the Java equivalent static function.
