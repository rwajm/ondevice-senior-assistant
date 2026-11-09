package com.k2j3.seniorassistant.device.ui.navigation

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.ui.NavDisplay
import com.k2j3.seniorassistant.device.SeniorAssistantApp
import com.k2j3.seniorassistant.device.ui.calendar.CalendarScreen
import com.k2j3.seniorassistant.device.ui.document.DocumentAnalysisScreen
import com.k2j3.seniorassistant.device.ui.document.DocumentResultScreen
import com.k2j3.seniorassistant.device.ui.document.DocumentSourceScreen
import com.k2j3.seniorassistant.device.ui.document.DocumentViewModel
import com.k2j3.seniorassistant.device.ui.home.HomeScreen
import com.k2j3.seniorassistant.device.ui.reminder.NoticeViewModel
import com.k2j3.seniorassistant.device.ui.reminder.ReminderDialog
import com.k2j3.seniorassistant.device.ui.schedule.ScheduleCreateScreen
import com.k2j3.seniorassistant.device.ui.schedule.ScheduleEditScreen
import com.k2j3.seniorassistant.device.ui.schedule.ScheduleListScreen
import com.k2j3.seniorassistant.device.ui.schedule.ScheduleViewModel
import com.k2j3.seniorassistant.device.ui.settings.SettingsScreen
import com.k2j3.seniorassistant.device.ui.settings.SettingsViewModel
import com.k2j3.seniorassistant.device.ui.voice.VoiceScreen
import com.k2j3.seniorassistant.device.ui.voice.VoiceViewModel
import java.time.LocalDate

@Composable
fun AppNavDisplay(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(Home)
    val goBack: () -> Unit = { backStack.removeLastOrNull() }

    val server = (LocalContext.current.applicationContext as SeniorAssistantApp).server
    val schedules = viewModel { ScheduleViewModel(server) }
    val voice = viewModel { VoiceViewModel(server) }
    val documents = viewModel { DocumentViewModel(server) }
    val settings = viewModel { SettingsViewModel(server) }
    val notices = viewModel { NoticeViewModel(server) }

    val openNotices by notices.notices.collectAsState()
    LaunchedEffect(openNotices.firstOrNull()?.id) { // shown until dismissed
        if (openNotices.isNotEmpty() && backStack.lastOrNull() != Reminder) backStack.add(Reminder)
    }
    val reading by documents.reading.collectAsState()

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
                LaunchedEffect(Unit) { schedules.loadTodayAndTomorrow() }
                ScheduleListScreen(
                    showSavedNotice = key.showSavedNotice,
                    onBackClick = goBack,
                    onOtherDateClick = { backStack.add(Calendar) },
                    onEditClick = { backStack.add(ScheduleEdit(schedules.firstScheduleId())) },
                )
            }
            entry<Calendar> {
                CalendarScreen(
                    onBackClick = goBack,
                    onDateSelect = { schedules.selectDate(LocalDate.now().plusDays(1)) },
                    onEditClick = { backStack.add(ScheduleEdit(schedules.firstScheduleId())) },
                    onWriteForDateClick = {
                        backStack.add(ScheduleCreate(schedules.selectedDate.toString()))
                    },
                )
            }
            entry<ScheduleCreate> { key ->
                ScheduleCreateScreen(
                    date = key.date,
                    onBackClick = goBack,
                    onVoiceWriteClick = { voice.ask() },
                    onSaveClick = {
                        val date = key.date.takeIf { it.isNotEmpty() }?.let(LocalDate::parse)
                        schedules.add(date ?: LocalDate.now(), voice.takeDraft())
                        backStack.showSavedScheduleList()
                    },
                )
            }
            entry<ScheduleEdit> { key ->
                ScheduleEditScreen(
                    scheduleId = key.scheduleId.toString(),
                    onBackClick = goBack,
                    onVoiceEditClick = { voice.ask(editingId = key.scheduleId) },
                    onSaveClick = {
                        schedules.update(key.scheduleId, voice.takeDraft())
                        backStack.showSavedScheduleList()
                    },
                    onDeleteClick = { schedules.delete(key.scheduleId) },
                )
            }
            entry<Voice> {
                VoiceScreen(
                    onBackClick = goBack,
                    onStartClick = { voice.ask() },
                    onAnswerPlay = { voice.play() },
                    onCreateDraftClick = { backStack.add(ScheduleCreate()) },
                    onEditDraftClick = { backStack.add(ScheduleEdit(schedules.firstScheduleId())) },
                )
            }
            entry<DocumentSource> {
                DocumentSourceScreen(
                    onBackClick = goBack,
                    onPickFileClick = {
                        documents.read()
                        backStack.add(DocumentAnalysis)
                    },
                )
            }
            entry<DocumentAnalysis> {
                val showResult: () -> Unit = {
                    backStack.removeLastOrNull()
                    backStack.add(DocumentResult)
                }
                LaunchedEffect(reading) { if (reading != null) showResult() }
                DocumentAnalysisScreen(onBackClick = goBack, onAnalysisComplete = showResult)
            }
            entry<DocumentResult> {
                DocumentResultScreen(
                    onBackClick = goBack,
                    onEditClick = { backStack.add(ScheduleEdit()) },
                    onAddToScheduleClick = {
                        documents.addToSchedule()
                        backStack.showSavedScheduleList()
                    },
                )
            }
            entry<Settings> {
                SettingsScreen(
                    onBackClick = goBack,
                    onFontSizeClick = settings::changeFontSize,
                    onSpeechRateClick = settings::changeSpeechRate,
                    onSpeechPreviewClick = settings::previewSpeech,
                    onRemindBeforeClick = settings::changeRemindBefore,
                    onUserNameClick = settings::changeUserName,
                    onNotificationMethodClick = settings::changeVoiceNotification,
                    onPhonePairingClick = { Log.d(TAG, "pairing comes with the companion app") },
                    onExternalConsentClick = settings::changeExternalConsent,
                )
            }
            entry<Reminder>(metadata = DialogSceneStrategy.dialog()) {
                ReminderDialog(
                    onListenClick = notices::play,
                    onConfirmClick = {
                        notices.dismissFirst()
                        goBack()
                    },
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

private const val TAG = "AppNavDisplay"
