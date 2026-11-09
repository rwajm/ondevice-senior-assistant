package com.k2j3.seniorassistant.device.data.settings

import com.k2j3.seniorassistant.device.data.AppDatabase

class SettingsRepository(db: AppDatabase) {
    private val dao = db.settingsDao()

    suspend fun getSettings(): Settings = dao.get() ?: Settings() // defaults until first save

    suspend fun updateSettings(settings: Settings) {
        dao.upsert(settings.copy(id = Settings.SINGLE_ROW_ID))
    }
}
