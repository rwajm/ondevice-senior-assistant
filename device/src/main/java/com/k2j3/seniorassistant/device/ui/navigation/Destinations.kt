package com.k2j3.seniorassistant.device.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object Home : NavKey

@Serializable
data class ScheduleList(val showSavedNotice: Boolean = false) : NavKey

@Serializable
data object Calendar : NavKey

@Serializable
data class ScheduleCreate(val date: String = "", val fromDocument: Boolean = false) : NavKey // fromDocument: 문서 읽은 내용으로 채우기

@Serializable
data class ScheduleEdit(val scheduleId: Long = 0) : NavKey

@Serializable
data object Voice : NavKey // 말로 부탁하기: 카드 고르기

@Serializable
data class VoiceTalk(val mode: String, val editingId: Long = 0) : NavKey // 대화 (mode = VoiceMode 이름)

@Serializable
data object DocumentSource : NavKey // 문서 읽기 (고르기·읽는 중·결과 한 화면에서)

@Serializable
data object Settings : NavKey
