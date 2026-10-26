package dev.devinprater.healthreader

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.BodyWaterMassRecord
import androidx.health.connect.client.records.BoneMassRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.MenstruationFlowRecord
import androidx.health.connect.client.records.MenstruationPeriodRecord
import androidx.health.connect.client.records.MindfulnessSessionRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.PowerRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SkinTemperatureRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.WheelchairPushesRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.reflect.KClass

/** One finished report for a section: a sentence of summary and one line per record. */
data class Report(val summary: String, val rows: List<String>)

/**
 * A browsable group of Health Connect data. [recordType] decides the read permission, so a
 * section can never ask for something it does not read.
 */
class Section(
    val key: String,
    val title: String,
    val blurb: String,
    val recordType: KClass<out Record>,
    val load: suspend (HealthConnectClient, Instant, Instant) -> Report
) {
    val permission: String get() = HealthPermission.getReadPermission(recordType)
}

private val zone: ZoneId get() = ZoneId.systemDefault()
private val dateFormat = DateTimeFormatter.ofPattern("EEE d MMM", Locale.US)
private val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

fun dayText(instant: Instant): String = dateFormat.format(instant.atZone(zone))
fun clockText(instant: Instant): String = timeFormat.format(instant.atZone(zone))
fun whenText(instant: Instant): String = dayText(instant) + ", " + clockText(instant)

fun spokenDuration(duration: Duration): String {
    val minutes = duration.toMinutes()
    val hours = minutes / 60
    val rest = minutes % 60
    val hourPart = when {
        hours == 0L -> ""
        hours == 1L -> "1 hour"
        else -> "$hours hours"
    }
    val minutePart = when {
        rest == 0L -> ""
        rest == 1L -> "1 minute"
        else -> "$rest minutes"
    }
    val text = listOf(hourPart, minutePart).filter { it.isNotEmpty() }.joinToString(" ")
    return text.ifEmpty { "less than a minute" }
}

fun number(value: Double, digits: Int = 1): String =
    String.format(Locale.US, "%.${digits}f", value).removeSuffix(".0")

private suspend fun <T : Record> readRecords(
    client: HealthConnectClient,
    type: KClass<T>,
    start: Instant,
    end: Instant,
    pageSize: Int = 500
): List<T> = client.readRecords(
    ReadRecordsRequest(
        recordType = type,
        timeRangeFilter = TimeRangeFilter.between(start, end),
        ascendingOrder = false,
        pageSize = pageSize
    )
).records

private fun emptyReport(about: String): Report =
    Report("No $about in the last 30 days.", emptyList())

/** Latest reading plus the range across the window, for single-value records. */
private suspend fun <T : Record> latestValueReport(
    client: HealthConnectClient,
    type: KClass<T>,
    start: Instant,
    end: Instant,
    what: String,
    unit: String,
    digits: Int = 1,
    value: (T) -> Double,
    stamp: (T) -> Instant
): Report {
    val records = readRecords(client, type, start, end)
    if (records.isEmpty()) return emptyReport(what)
    val newest = records.maxByOrNull { stamp(it) }!!
    val values = records.map(value)
    val summary = StringBuilder()
    summary.append("Latest $what ").append(number(value(newest), digits)).append(' ').append(unit)
    summary.append(", ").append(whenText(stamp(newest))).append('.')
    if (values.size > 1) {
        summary.append(" Over ").append(values.size).append(" readings: lowest ")
            .append(number(values.min(), digits)).append(", highest ")
            .append(number(values.max(), digits)).append(", average ")
            .append(number(values.average(), digits)).append(' ').append(unit).append('.')
    }
    val rows = records.take(20).map {
        "${whenText(stamp(it))}: ${number(value(it), digits)} $unit"
    }
    return Report(summary.toString(), rows)
}

private fun exerciseName(type: Int): String = when (type) {
    ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> "Biking"
    ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> "Stationary biking"
    ExerciseSessionRecord.EXERCISE_TYPE_DANCING -> "Dancing"
    ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL -> "Elliptical"
    ExerciseSessionRecord.EXERCISE_TYPE_EXERCISE_CLASS -> "Exercise class"
    ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "Hiking"
    ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "Running"
    ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> "Treadmill running"
    ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING -> "Strength training"
    ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL -> "Pool swimming"
    ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "Open water swimming"
    ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "Walking"
    ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "Yoga"
    ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING -> "Stretching"
    ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT -> "Workout"
    else -> "Workout"
}

object Sections {

    private suspend fun sleep(client: HealthConnectClient, start: Instant, end: Instant): Report {
        val sessions = readRecords(client, SleepSessionRecord::class, start, end, 60)
            .sortedByDescending { it.endTime }
        if (sessions.isEmpty()) return emptyReport("sleep sessions")
        val rows = sessions.take(14).map { session ->
            val inBed = Duration.between(session.startTime, session.endTime)
            val nap = inBed.toMinutes() < 180
            val stages = session.stages.groupBy { it.stage }.mapValues { entry ->
                entry.value.fold(Duration.ZERO) { total, stage ->
                    total.plus(Duration.between(stage.startTime, stage.endTime))
                }
            }
            val parts = listOf(
                "awake" to stages[SleepSessionRecord.STAGE_TYPE_AWAKE],
                "light" to stages[SleepSessionRecord.STAGE_TYPE_LIGHT],
                "deep" to stages[SleepSessionRecord.STAGE_TYPE_DEEP],
                "REM" to stages[SleepSessionRecord.STAGE_TYPE_REM],
                "out of bed" to stages[SleepSessionRecord.STAGE_TYPE_OUT_OF_BED]
            ).mapNotNull { pair ->
                val length = pair.second ?: return@mapNotNull null
                if (length.toMinutes() < 1) null else "${pair.first} ${spokenDuration(length)}"
            }.joinToString(", ")
            val stageLine = if (parts.isEmpty()) " No stage breakdown was recorded." else " Stages: $parts."
            val prefix = if (nap) "Nap" else "Night"
            "$prefix starting ${whenText(session.startTime)}: in bed ${spokenDuration(inBed)}, from " +
                "${clockText(session.startTime)} to ${clockText(session.endTime)}.$stageLine"
        }
        val nights = sessions.filter { Duration.between(it.startTime, it.endTime).toMinutes() >= 180 }
        val naps = sessions.size - nights.size
        val napLine = if (naps > 0) " $naps shorter naps are listed but not averaged." else ""
        val summary = if (nights.isEmpty()) {
            "No full nights in the last 30 days, only $naps shorter naps.$napLine"
        } else {
            val average = nights.map { Duration.between(it.startTime, it.endTime).toMinutes() }.average()
            "${nights.size} nights, average " +
                "${spokenDuration(Duration.ofMinutes(average.toLong()))} in bed.$napLine"
        }
        return Report(summary, rows)
    }

    private suspend fun exercise(client: HealthConnectClient, start: Instant, end: Instant): Report {
        val sessions = readRecords(client, ExerciseSessionRecord::class, start, end, 100)
            .sortedByDescending { it.startTime }
        if (sessions.isEmpty()) return emptyReport("exercise sessions")
        val rows = sessions.take(20).map { session ->
            val length = Duration.between(session.startTime, session.endTime)
            val name = session.title?.takeIf { it.isNotBlank() } ?: exerciseName(session.exerciseType)
            "${whenText(session.startTime)}: $name, ${spokenDuration(length)}."
        }
        val total = sessions.fold(Duration.ZERO) { sum, s -> sum.plus(Duration.between(s.startTime, s.endTime)) }
        return Report("${sessions.size} sessions, ${spokenDuration(total)} in total.", rows)
    }

    private suspend fun steps(client: HealthConnectClient, start: Instant, end: Instant): Report {
        val records = readRecords(client, StepsRecord::class, start, end, 2000)
        if (records.isEmpty()) return emptyReport("step records")
        val byDay = records.groupBy { dayText(it.startTime) }
            .mapValues { entry -> entry.value.sumOf { it.count } }
            .toList()
            .sortedByDescending { it.second }
        val today = byDay.first()
        val average = byDay.map { it.second }.average().toLong()
        val rows = byDay.map { "${it.first}: ${"%,d".format(it.second)} steps" }
        return Report("Most recent day ${today.first} had ${"%,d".format(today.second)} steps. " +
            "Daily average ${"%,d".format(average)}.", rows)
    }

    private suspend fun distance(client: HealthConnectClient, start: Instant, end: Instant): Report {
        val records = readRecords(client, DistanceRecord::class, start, end, 2000)
        if (records.isEmpty()) return emptyReport("distance records")
        val byDay = records.groupBy { dayText(it.startTime) }
            .mapValues { entry -> entry.value.sumOf { it.distance.inKilometers } }
            .toList()
            .sortedByDescending { it.second }
        val rows = byDay.map { "${it.first}: ${number(it.second, 2)} kilometres" }
        return Report("Most recent day ${byDay.first().first} covered " +
            "${number(byDay.first().second, 2)} kilometres.", rows)
    }

    private suspend fun calories(client: HealthConnectClient, start: Instant, end: Instant): Report {
        val active = readRecords(client, ActiveCaloriesBurnedRecord::class, start, end, 2000)
        val total = readRecords(client, TotalCaloriesBurnedRecord::class, start, end, 2000)
        if (active.isEmpty() && total.isEmpty()) return emptyReport("calorie records")
        val activeByDay = active.groupBy { dayText(it.startTime) }
            .mapValues { entry -> entry.value.sumOf { it.energy.inKilocalories } }
        val totalByDay = total.groupBy { dayText(it.startTime) }
            .mapValues { entry -> entry.value.sumOf { it.energy.inKilocalories } }
        val days = (activeByDay.keys + totalByDay.keys).sortedDescending()
        val rows = days.map { day ->
            val a = activeByDay[day] ?: 0.0
            val t = totalByDay[day] ?: 0.0
            "$day: ${number(a, 0)} active calories, ${number(t, 0)} total calories burned"
        }
        val newest = days.firstOrNull()
        val summary = if (newest == null) "No calories recorded." else
            "$newest: ${number(activeByDay[newest] ?: 0.0, 0)} active calories of " +
                "${number(totalByDay[newest] ?: 0.0, 0)} total."
        return Report(summary, rows)
    }

    private suspend fun floors(client: HealthConnectClient, start: Instant, end: Instant): Report {
        val records = readRecords(client, FloorsClimbedRecord::class, start, end, 2000)
        if (records.isEmpty()) return emptyReport("floors climbed")
        val byDay = records.groupBy { dayText(it.startTime) }
            .mapValues { entry -> entry.value.sumOf { it.floors } }
            .toList().sortedByDescending { it.second }
        val rows = byDay.map { "${it.first}: ${number(it.second, 0)} floors" }
        return Report("Most recent day ${byDay.first().first} climbed " +
            "${number(byDay.first().second, 0)} floors.", rows)
    }

    private suspend fun heartRate(client: HealthConnectClient, start: Instant, end: Instant): Report {
        val records = readRecords(client, HeartRateRecord::class, start, end, 200)
        val samples = records.flatMap { it.samples }.sortedByDescending { it.time }
        if (samples.isEmpty()) return emptyReport("heart rate samples")
        val beats = samples.map { it.beatsPerMinute.toDouble() }
        val summary = "Latest ${number(beats.first(), 0)} beats per minute at " +
            "${whenText(samples.first().time)}. Over ${samples.size} samples: lowest " +
            "${number(beats.min(), 0)}, highest ${number(beats.max(), 0)}, average " +
            "${number(beats.average(), 0)} beats per minute."
        val rows = samples.take(30).map { "${whenText(it.time)}: ${it.beatsPerMinute} beats per minute" }
        return Report(summary, rows)
    }

    private suspend fun restingHeartRate(client: HealthConnectClient, start: Instant, end: Instant): Report =
        latestValueReport(client, RestingHeartRateRecord::class, start, end,
            "resting heart rate", "beats per minute", 0,
            { it.beatsPerMinute.toDouble() }, { it.time })

    private suspend fun hrv(client: HealthConnectClient, start: Instant, end: Instant): Report =
        latestValueReport(client, HeartRateVariabilityRmssdRecord::class, start, end,
            "heart rate variability", "milliseconds", 1,
            { it.heartRateVariabilityMillis }, { it.time })

    private suspend fun oxygen(client: HealthConnectClient, start: Instant, end: Instant): Report =
        latestValueReport(client, OxygenSaturationRecord::class, start, end,
            "oxygen saturation", "percent", 0,
            { it.percentage.value }, { it.time })

    private suspend fun respiratory(client: HealthConnectClient, start: Instant, end: Instant): Report =
        latestValueReport(client, RespiratoryRateRecord::class, start, end,
            "respiratory rate", "breaths per minute", 1,
            { it.rate }, { it.time })

    private suspend fun bodyTemperature(client: HealthConnectClient, start: Instant, end: Instant): Report =
        latestValueReport(client, BodyTemperatureRecord::class, start, end,
            "body temperature", "degrees Celsius", 1,
            { it.temperature.inCelsius }, { it.time })

    private suspend fun skinTemperature(client: HealthConnectClient, start: Instant, end: Instant): Report =
        latestValueReport(client, SkinTemperatureRecord::class, start, end,
            "skin temperature baseline", "degrees Celsius", 1,
            { it.baseline?.inCelsius ?: 0.0 }, { it.startTime })

    private suspend fun weight(client: HealthConnectClient, start: Instant, end: Instant): Report =
        latestValueReport(client, WeightRecord::class, start, end,
            "weight", "kilograms", 1, { it.weight.inKilograms }, { it.time })

    private suspend fun bodyFat(client: HealthConnectClient, start: Instant, end: Instant): Report =
        latestValueReport(client, BodyFatRecord::class, start, end,
            "body fat", "percent", 1, { it.percentage.value }, { it.time })

    private suspend fun height(client: HealthConnectClient, start: Instant, end: Instant): Report =
        latestValueReport(client, HeightRecord::class, start, end,
            "height", "centimetres", 1, { it.height.inMeters * 100 }, { it.time })

    private suspend fun basalRate(client: HealthConnectClient, start: Instant, end: Instant): Report =
        latestValueReport(client, BasalMetabolicRateRecord::class, start, end,
            "basal metabolic rate", "kilocalories per day", 0,
            { it.basalMetabolicRate.inKilocaloriesPerDay }, { it.time })

    private suspend fun boneMass(client: HealthConnectClient, start: Instant, end: Instant): Report =
        latestValueReport(client, BoneMassRecord::class, start, end,
            "bone mass", "kilograms", 2, { it.mass.inKilograms }, { it.time })

    private suspend fun bodyWater(client: HealthConnectClient, start: Instant, end: Instant): Report =
        latestValueReport(client, BodyWaterMassRecord::class, start, end,
            "body water mass", "kilograms", 1, { it.mass.inKilograms }, { it.time })

    private suspend fun leanMass(client: HealthConnectClient, start: Instant, end: Instant): Report =
        latestValueReport(client, LeanBodyMassRecord::class, start, end,
            "lean body mass", "kilograms", 1, { it.mass.inKilograms }, { it.time })

    private suspend fun glucose(client: HealthConnectClient, start: Instant, end: Instant): Report =
        latestValueReport(client, BloodGlucoseRecord::class, start, end,
            "blood glucose", "millimoles per litre", 1,
            { it.level.inMillimolesPerLiter }, { it.time })

    private suspend fun vo2(client: HealthConnectClient, start: Instant, end: Instant): Report =
        latestValueReport(client, Vo2MaxRecord::class, start, end,
            "VO2 max", "millilitres per kilogram per minute", 1,
            { it.vo2MillilitersPerMinuteKilogram }, { it.time })

    private suspend fun speed(client: HealthConnectClient, start: Instant, end: Instant): Report {
        val records = readRecords(client, SpeedRecord::class, start, end, 200)
        val samples = records.flatMap { it.samples }.sortedByDescending { it.time }
        if (samples.isEmpty()) return emptyReport("speed samples")
        val values = samples.map { it.speed.inMetersPerSecond }
        val summary = "Latest ${number(values.first(), 2)} metres per second at " +
            "${whenText(samples.first().time)}. Lowest ${number(values.min(), 2)}, highest " +
            "${number(values.max(), 2)}, average ${number(values.average(), 2)}."
        return Report(summary, samples.take(30).map {
            "${whenText(it.time)}: ${number(it.speed.inMetersPerSecond, 2)} metres per second"
        })
    }

    private suspend fun power(client: HealthConnectClient, start: Instant, end: Instant): Report {
        val records = readRecords(client, PowerRecord::class, start, end, 200)
        val samples = records.flatMap { it.samples }.sortedByDescending { it.time }
        if (samples.isEmpty()) return emptyReport("power samples")
        val values = samples.map { it.power.inWatts }
        val summary = "Latest ${number(values.first(), 0)} watts at " +
            "${whenText(samples.first().time)}. Lowest ${number(values.min(), 0)}, highest " +
            "${number(values.max(), 0)}, average ${number(values.average(), 0)}."
        return Report(summary, samples.take(30).map {
            "${whenText(it.time)}: ${number(it.power.inWatts, 0)} watts"
        })
    }

    private suspend fun wheelchair(client: HealthConnectClient, start: Instant, end: Instant): Report {
        val records = readRecords(client, WheelchairPushesRecord::class, start, end, 2000)
        if (records.isEmpty()) return emptyReport("wheelchair push records")
        val byDay = records.groupBy { dayText(it.startTime) }
            .mapValues { entry -> entry.value.sumOf { it.count } }
            .toList().sortedByDescending { it.second }
        val rows = byDay.map { "${it.first}: ${"%,d".format(it.second)} pushes" }
        return Report("Most recent day ${byDay.first().first} had " +
            "${"%,d".format(byDay.first().second)} pushes.", rows)
    }

    private suspend fun bloodPressure(client: HealthConnectClient, start: Instant, end: Instant): Report {
        val records = readRecords(client, BloodPressureRecord::class, start, end, 100)
        if (records.isEmpty()) return emptyReport("blood pressure readings")
        val newest = records.maxByOrNull { it.time }!!
        val rows = records.sortedByDescending { it.time }.take(20).map {
            "${whenText(it.time)}: ${it.systolic.inMillimetersOfMercury} over " +
                "${it.diastolic.inMillimetersOfMercury} millimetres of mercury"
        }
        return Report("Latest ${newest.systolic.inMillimetersOfMercury} over " +
            "${newest.diastolic.inMillimetersOfMercury}, ${whenText(newest.time)}.", rows)
    }

    private suspend fun nutrition(client: HealthConnectClient, start: Instant, end: Instant): Report {
        val records = readRecords(client, NutritionRecord::class, start, end, 200)
        if (records.isEmpty()) return emptyReport("nutrition entries")
        val rows = records.sortedByDescending { it.startTime }.take(20).map { entry ->
            val energy = entry.energy?.inKilocalories
            val protein = entry.protein?.inGrams
            val carbs = entry.totalCarbohydrate?.inGrams
            val fat = entry.totalFat?.inGrams
            buildString {
                append(whenText(entry.startTime)).append(": ")
                append(if (energy != null) "${number(energy, 0)} kilocalories" else "no energy recorded")
                if (protein != null) append(", ${number(protein, 0)} grams protein")
                if (carbs != null) append(", ${number(carbs, 0)} grams carbohydrate")
                if (fat != null) append(", ${number(fat, 0)} grams fat")
                append('.')
            }
        }
        val today = dayText(Instant.now())
        val todayEnergy = records.filter { dayText(it.startTime) == today }
            .sumOf { it.energy?.inKilocalories ?: 0.0 }
        return Report("${records.size} nutrition entries. Today: ${number(todayEnergy, 0)} kilocalories.", rows)
    }

    private suspend fun hydration(client: HealthConnectClient, start: Instant, end: Instant): Report {
        val records = readRecords(client, HydrationRecord::class, start, end, 500)
        if (records.isEmpty()) return emptyReport("hydration entries")
        val byDay = records.groupBy { dayText(it.startTime) }
            .mapValues { entry -> entry.value.sumOf { it.volume.inLiters } }
            .toList().sortedByDescending { it.second }
        val rows = byDay.map { "${it.first}: ${number(it.second, 2)} litres" }
        return Report("Most recent day ${byDay.first().first} drank " +
            "${number(byDay.first().second, 2)} litres.", rows)
    }

    private suspend fun mindfulness(client: HealthConnectClient, start: Instant, end: Instant): Report {
        val records = readRecords(client, MindfulnessSessionRecord::class, start, end, 100)
        if (records.isEmpty()) return emptyReport("mindfulness sessions")
        val rows = records.sortedByDescending { it.startTime }.take(20).map {
            "${whenText(it.startTime)}: ${spokenDuration(Duration.between(it.startTime, it.endTime))}"
        }
        val total = records.fold(Duration.ZERO) { sum, r ->
            sum.plus(Duration.between(r.startTime, r.endTime))
        }
        return Report("${records.size} sessions, ${spokenDuration(total)} in total.", rows)
    }

    private suspend fun cycle(client: HealthConnectClient, start: Instant, end: Instant): Report {
        val flow = readRecords(client, MenstruationFlowRecord::class, start, end, 200)
        val periods = readRecords(client, MenstruationPeriodRecord::class, start, end, 50)
        if (flow.isEmpty() && periods.isEmpty()) return emptyReport("menstrual records")
        val rows = buildList {
            periods.sortedByDescending { it.startTime }.take(10).forEach {
                add("Period from ${whenText(it.startTime)} to ${clockText(it.endTime)}, " +
                    spokenDuration(Duration.between(it.startTime, it.endTime)) + ".")
            }
            flow.sortedByDescending { it.time }.take(10).forEach {
                add("Flow on ${whenText(it.time)}, level ${it.flow} of 3.")
            }
        }
        return Report("${periods.size} periods and ${flow.size} flow entries in 30 days.", rows)
    }

    val all: List<Section> = listOf(
        Section("sleep", "Sleep", "Each night with its stages in words.", SleepSessionRecord::class, ::sleep),
        Section("exercise", "Exercise", "Sessions with length and type.", ExerciseSessionRecord::class, ::exercise),
        Section("heart", "Heart rate", "Latest, lowest, highest and average.", HeartRateRecord::class, ::heartRate),
        Section("resting", "Resting heart rate", "One number a day.", RestingHeartRateRecord::class, ::restingHeartRate),
        Section("hrv", "Heart rate variability", "Beat to beat variation.", HeartRateVariabilityRmssdRecord::class, ::hrv),
        Section("spo2", "Oxygen saturation", "Blood oxygen readings.", OxygenSaturationRecord::class, ::oxygen),
        Section("respiratory", "Respiratory rate", "Breaths per minute while asleep.", RespiratoryRateRecord::class, ::respiratory),
        Section("steps", "Steps", "Day by day totals.", StepsRecord::class, ::steps),
        Section("distance", "Distance", "Day by day distance.", DistanceRecord::class, ::distance),
        Section("calories", "Calories", "Active and total burn, by day.", ActiveCaloriesBurnedRecord::class, ::calories),
        Section("floors", "Floors climbed", "Day by day floors.", FloorsClimbedRecord::class, ::floors),
        Section("weight", "Weight", "Readings over the window.", WeightRecord::class, ::weight),
        Section("bodyfat", "Body fat", "Percentage readings.", BodyFatRecord::class, ::bodyFat),
        Section("height", "Height", "Recorded height.", HeightRecord::class, ::height),
        Section("bmr", "Basal metabolic rate", "Calories your body uses at rest.", BasalMetabolicRateRecord::class, ::basalRate),
        Section("bone", "Bone mass", "Recorded bone mass.", BoneMassRecord::class, ::boneMass),
        Section("water", "Body water", "Recorded water mass.", BodyWaterMassRecord::class, ::bodyWater),
        Section("lean", "Lean body mass", "Recorded lean mass.", LeanBodyMassRecord::class, ::leanMass),
        Section("bp", "Blood pressure", "Systolic and diastolic together.", BloodPressureRecord::class, ::bloodPressure),
        Section("glucose", "Blood glucose", "Readings with units.", BloodGlucoseRecord::class, ::glucose),
        Section("nutrition", "Nutrition", "Meals with energy and macros.", NutritionRecord::class, ::nutrition),
        Section("hydration", "Hydration", "Day by day water.", HydrationRecord::class, ::hydration),
        Section("mindfulness", "Mindfulness", "Sessions with length.", MindfulnessSessionRecord::class, ::mindfulness),
        Section("vo2", "VO2 max", "Cardio fitness estimate.", Vo2MaxRecord::class, ::vo2),
        Section("speed", "Speed", "Recorded speed values.", SpeedRecord::class, ::speed),
        Section("power", "Power", "Recorded power values.", PowerRecord::class, ::power),
        Section("wheelchair", "Wheelchair pushes", "Day by day pushes.", WheelchairPushesRecord::class, ::wheelchair),
        Section("temperature", "Body temperature", "Readings in Celsius.", BodyTemperatureRecord::class, ::bodyTemperature),
        Section("skintemp", "Skin temperature", "Baseline readings.", SkinTemperatureRecord::class, ::skinTemperature),
        Section("cycle", "Menstrual cycle", "Periods and flow.", MenstruationPeriodRecord::class, ::cycle)
    )

    fun byKey(key: String): Section? = all.firstOrNull { it.key == key }

    /** Sections sometimes read a second type, such as total calories next to active calories. */
    private val extraPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class)
    )

    val permissions: Set<String> = all.map { it.permission }.toSet() + extraPermissions
}
