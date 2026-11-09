package com.k2j3.seniorassistant.device.data

import androidx.room.TypeConverter
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.serialization.json.Json

class Converters {
    @TypeConverter
    fun fromLocalDate(value: LocalDate?): String? = value?.toString() // ISO text sorts by date

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter
    fun fromLocalTime(value: LocalTime?): String? = value?.toString()

    @TypeConverter
    fun toLocalTime(value: String?): LocalTime? = value?.let(LocalTime::parse)

    @TypeConverter
    fun fromDuration(value: Duration?): String? = value?.toString()

    @TypeConverter
    fun toDuration(value: String?): Duration? = value?.let(Duration::parse)

    @TypeConverter
    fun fromStrings(value: List<String>): String = Json.encodeToString(value)

    @TypeConverter
    fun toStrings(value: String): List<String> = Json.decodeFromString(value)

    @TypeConverter
    fun fromDurations(value: List<Duration>): String = value.joinToString(",") // no comma in ISO

    @TypeConverter
    fun toDurations(value: String): List<Duration> =
        if (value.isEmpty()) emptyList() else value.split(",").map(Duration::parse)
}
