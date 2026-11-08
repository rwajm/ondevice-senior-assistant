package com.k2j3.seniorassistant.device.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.ui.NavDisplay
import com.k2j3.seniorassistant.device.ui.calendar.CalendarScreen
import com.k2j3.seniorassistant.device.ui.document.DocumentAnalysisScreen
import com.k2j3.seniorassistant.device.ui.document.DocumentResultScreen
import com.k2j3.seniorassistant.device.ui.document.DocumentSourceScreen
import com.k2j3.seniorassistant.device.ui.home.HomeScreen
import com.k2j3.seniorassistant.device.ui.reminder.ReminderDialog
import com.k2j3.seniorassistant.device.ui.schedule.ScheduleCreateScreen
import com.k2j3.seniorassistant.device.ui.schedule.ScheduleEditScreen
import com.k2j3.seniorassistant.device.ui.schedule.ScheduleListScreen
import com.k2j3.seniorassistant.device.ui.settings.SettingsScreen
import com.k2j3.seniorassistant.device.ui.voice.VoiceScreen

// Placeholder arguments until screens receive real data.
private const val SAMPLE_SCHEDULE_ID = "sch_sample"
private const val SAMPLE_DATE = "2026-10-15"

@Composable
fun AppNavDisplay(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(Home)
    val goBack: () -> Unit = { backStack.removeLastOrNull() }

    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = goBack,
        sceneStrategies = listOf(DialogSceneStrategy()),
        entryProvider = entryProvider {
            entry<Home> {
                HomeScreen(
                    onScheduleListClick = { backStack.add(ScheduleList()) },
                    onScheduleWriteClick = { backStack.add(Calendar) },
                    onDocumentClick = { backStack.add(DocumentSource) },
                    onVoiceClick = { backStack.add(Voice) },
                    onSettingsClick = { backStack.add(Settings) },
                    onReminderPreviewClick = { backStack.add(Reminder) },
                )
            }
            entry<ScheduleList> { key ->
                ScheduleListScreen(
                    showSavedNotice = key.showSavedNotice,
                    onBackClick = goBack,
                    onOtherDateClick = { backStack.add(Calendar) },
                    onEditClick = { backStack.add(ScheduleEdit(SAMPLE_SCHEDULE_ID)) },
                )
            }
            entry<Calendar> {
                CalendarScreen(
                    onBackClick = goBack,
                    onDateSelect = {},
                    onEditClick = { backStack.add(ScheduleEdit(SAMPLE_SCHEDULE_ID)) },
                    onWriteForDateClick = { backStack.add(ScheduleCreate(SAMPLE_DATE)) },
                )
            }
            entry<ScheduleCreate> { key ->
                ScheduleCreateScreen(
                    date = key.date,
                    onBackClick = goBack,
                    onVoiceWriteClick = {},
                    onSaveClick = { backStack.showSavedScheduleList() },
                )
            }
            entry<ScheduleEdit> { key ->
                ScheduleEditScreen(
                    scheduleId = key.scheduleId,
                    onBackClick = goBack,
                    onVoiceEditClick = {},
                    onSaveClick = { backStack.showSavedScheduleList() },
                    onDeleteClick = {},
                )
            }
            entry<Voice> {
                VoiceScreen(
                    onBackClick = goBack,
                    onStartClick = {},
                    onAnswerPlay = {},
                    onCreateDraftClick = { backStack.add(ScheduleCreate()) },
                    onEditDraftClick = { backStack.add(ScheduleEdit(SAMPLE_SCHEDULE_ID)) },
                )
            }
            entry<DocumentSource> {
                DocumentSourceScreen(
                    onBackClick = goBack,
                    onPickFileClick = { backStack.add(DocumentAnalysis) },
                )
            }
            entry<DocumentAnalysis> {
                DocumentAnalysisScreen(
                    onBackClick = goBack,
                    onAnalysisComplete = {
                        backStack.removeLastOrNull()
                        backStack.add(DocumentResult)
                    },
                )
            }
            entry<DocumentResult> {
                DocumentResultScreen(
                    onBackClick = goBack,
                    onEditClick = { backStack.add(ScheduleEdit()) },
                    onAddToScheduleClick = { backStack.showSavedScheduleList() },
                )
            }
            entry<Settings> {
                SettingsScreen(
                    onBackClick = goBack,
                    onFontSizeClick = {},
                    onSpeechRateClick = {},
                    onSpeechPreviewClick = {},
                    onRemindBeforeClick = {},
                    onUserNameClick = {},
                    onNotificationMethodClick = {},
                    onPhonePairingClick = {},
                    onExternalConsentClick = {},
                )
            }
            entry<Reminder>(metadata = DialogSceneStrategy.dialog()) {
                ReminderDialog(
                    onListenClick = {},
                    onConfirmClick = goBack,
                )
            }
        },
    )
}

// After saving, return to the schedule list with only Home beneath it.
private fun NavBackStack<NavKey>.showSavedScheduleList() {
    while (size > 1) {
        removeAt(lastIndex)
    }
    add(ScheduleList(showSavedNotice = true))
}
