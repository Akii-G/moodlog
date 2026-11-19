package io.github.akiig.moodlog

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.time.temporal.ChronoUnit

/**
 * Reads the last days of steps, resting heart rate and sleep from Health Connect, and turns
 * them into the same text lines the iPhone app sends (metric;date;value and
 * sleep;start;end;stage). Read only: Moodlog never writes to Health Connect.
 */
class HealthReader(private val context: Context) {
    val permissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
    )

    fun isAvailable(): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    /** Health Connect is missing or needs an update (Android 13 and older). */
    fun needsInstall(): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED

    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    suspend fun hasPermissions(): Boolean =
        isAvailable() && client.permissionController.getGrantedPermissions().containsAll(permissions)

    suspend fun lines(days: Int): List<String> {
        val out = mutableListOf<String>()
        val startDay = LocalDate.now().minusDays((days - 1).toLong())
        val range = TimeRangeFilter.between(startDay.atStartOfDay(), LocalDateTime.now())

        val steps = client.aggregateGroupByPeriod(AggregateGroupByPeriodRequest(
            setOf(StepsRecord.COUNT_TOTAL), range, Period.ofDays(1)))
        for (group in steps) {
            val n = group.result[StepsRecord.COUNT_TOTAL] ?: continue
            out.add("steps;${group.startTime.toLocalDate()};$n")
        }

        val hr = client.aggregateGroupByPeriod(AggregateGroupByPeriodRequest(
            setOf(RestingHeartRateRecord.BPM_AVG), range, Period.ofDays(1)))
        for (group in hr) {
            val bpm = group.result[RestingHeartRateRecord.BPM_AVG] ?: continue
            out.add("resting_hr;${group.startTime.toLocalDate()};$bpm")
        }

        // sleep from the evening before the first day
        val sleepStart = startDay.minusDays(1).atStartOfDay()
        val sessions = client.readRecords(ReadRecordsRequest(SleepSessionRecord::class,
            TimeRangeFilter.between(sleepStart, LocalDateTime.now()))).records
        for (s in sessions) out.addAll(sleepLines(s.startTime, s.endTime, s.stages.map {
            Triple(it.startTime, it.endTime, it.stage) }))
        return out
    }

    companion object {
        private fun iso(t: Instant) = t.truncatedTo(ChronoUnit.SECONDS).toString()

        /** The same stage names as Apple Health, which the PC understands. */
        fun stageName(stage: Int): String = when (stage) {
            SleepSessionRecord.STAGE_TYPE_AWAKE, SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
            SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> "Awake"
            SleepSessionRecord.STAGE_TYPE_LIGHT -> "Core"
            SleepSessionRecord.STAGE_TYPE_DEEP -> "Deep"
            SleepSessionRecord.STAGE_TYPE_REM -> "REM"
            else -> "Asleep"
        }

        /** One line per stage, or one for the whole session if it has no stages. */
        fun sleepLines(start: Instant, end: Instant, stages: List<Triple<Instant, Instant, Int>>): List<String> =
            if (stages.isEmpty()) listOf("sleep;${iso(start)};${iso(end)};Asleep")
            else stages.sortedBy { it.first }.map { (a, b, st) -> "sleep;${iso(a)};${iso(b)};${stageName(st)}" }
    }
}
