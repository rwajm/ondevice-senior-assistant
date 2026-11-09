package com.k2j3.seniorassistant.device.data.settings

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE id = ${Settings.SINGLE_ROW_ID}")
    suspend fun get(): Settings?

    @Upsert
    suspend fun upsert(settings: Settings)
}
