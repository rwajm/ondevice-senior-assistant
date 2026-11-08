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
data class ScheduleCreate(val date: String = "") : NavKey

@Serializable
data class ScheduleEdit(val scheduleId: String = "") : NavKey

@Serializable
data object Voice : NavKey

@Serializable
data object DocumentSource : NavKey

@Serializable
data object DocumentAnalysis : NavKey

@Serializable
data object DocumentResult : NavKey

@Serializable
data object Settings : NavKey

@Serializable
data object Reminder : NavKey
