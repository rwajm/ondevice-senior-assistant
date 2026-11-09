package com.k2j3.seniorassistant.device.data.settings

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Duration

@Entity(tableName = "settings") // FR-07
data class Settings(
    @PrimaryKey val id: Int = SINGLE_ROW_ID, // single row
    @ColumnInfo(name = "user_name") val userName: String = "",
    @ColumnInfo(name = "font_size") val fontSize: FontSize = FontSize.NORMAL,
    @ColumnInfo(name = "speech_rate") val speechRate: SpeechRate = SpeechRate.NORMAL,
    @ColumnInfo(name = "remind_before") // at least one
    val remindBefore: List<Duration> = listOf(Duration.ofDays(1), Duration.ofHours(1)),
    @ColumnInfo(name = "voice_notification_enabled") val voiceNotificationEnabled: Boolean = true,
    @ColumnInfo(name = "external_transfer_consent") val externalTransferConsent: Boolean = false,
) {
    companion object {
        const val SINGLE_ROW_ID = 0
    }
}

enum class FontSize {
    NORMAL,
    LARGE,
    EXTRA_LARGE,
}

enum class SpeechRate {
    SLOW,
    NORMAL,
    FAST,
}
