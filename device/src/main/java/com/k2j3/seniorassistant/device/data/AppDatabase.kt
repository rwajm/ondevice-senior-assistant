package com.k2j3.seniorassistant.device.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.k2j3.seniorassistant.device.data.schedule.Reminder
import com.k2j3.seniorassistant.device.data.schedule.Schedule
import com.k2j3.seniorassistant.device.data.schedule.ScheduleDao
import com.k2j3.seniorassistant.device.data.settings.Settings
import com.k2j3.seniorassistant.device.data.settings.SettingsDao

@Database(
    entities = [Schedule::class, Reminder::class, Settings::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scheduleDao(): ScheduleDao

    abstract fun settingsDao(): SettingsDao

    companion object {
        private const val NAME = "senior_assistant.db"

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME).build()
    }
}
