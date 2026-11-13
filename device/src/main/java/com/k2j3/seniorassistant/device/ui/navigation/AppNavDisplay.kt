package com.k2j3.seniorassistant.device.ui.navigation

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.k2j3.seniorassistant.device.SeniorAssistantApp
import com.k2j3.seniorassistant.device.ui.calendar.CalendarRoute
import com.k2j3.seniorassistant.device.ui.calendar.CalendarViewModel
import com.k2j3.seniorassistant.device.ui.document.DocumentRoute
import com.k2j3.seniorassistant.device.ui.document.DocumentViewModel
import com.k2j3.seniorassistant.device.ui.home.HomeActions
import com.k2j3.seniorassistant.device.ui.home.HomeRoute
import com.k2j3.seniorassistant.device.ui.home.HomeViewModel
import com.k2j3.seniorassistant.device.ui.reminder.NoticeViewModel
import com.k2j3.seniorassistant.device.ui.reminder.NoticeDialog
import com.k2j3.seniorassistant.device.ui.schedule.FormMode
import com.k2j3.seniorassistant.device.ui.schedule.ScheduleFormRoute
import com.k2j3.seniorassistant.device.ui.schedule.ScheduleFormViewModel
import com.k2j3.seniorassistant.device.ui.schedule.TodoRoute
import com.k2j3.seniorassistant.device.ui.schedule.TodoViewModel
import com.k2j3.seniorassistant.device.ui.settings.SettingsRoute
import com.k2j3.seniorassistant.device.ui.settings.SettingsViewModel
import com.k2j3.seniorassistant.device.ui.voice.VoiceChooseScreen
import com.k2j3.seniorassistant.device.ui.voice.VoiceMode
import com.k2j3.seniorassistant.device.ui.voice.VoiceRoute
import com.k2j3.seniorassistant.device.ui.voice.VoiceViewModel
import java.time.LocalDate

@Composable
fun AppNavDisplay(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(Home)
    // 빠르게 두 번 눌러도 같은 화면이 두 번 열리거나, 첫 화면에서 더 뒤로 가 빈 화면이 되지 않게
    val goBack: () -> Unit = { if (backStack.size > 1) backStack.removeLastOrNull() }
    val go: (NavKey) -> Unit = { key -> if (backStack.lastOrNull() != key) backStack.add(key) }
    val activity = LocalActivity.current

    val server = (LocalContext.current.applicationContext as SeniorAssistantApp).server
    val home = viewModel { HomeViewModel(server.settings) }
    val todo = viewModel { TodoViewModel(server.schedules) }
    val calendar = viewModel { CalendarViewModel(server.schedules) }
    val form = viewModel { ScheduleFormViewModel(server.schedules) }
    val voice = viewModel { VoiceViewModel(server) }
    val homeState by home.uiState.collectAsState()
    val documents = viewModel { DocumentViewModel(server) }
    val settings = viewModel { SettingsViewModel(server) }
    val notices = viewModel { NoticeViewModel(server) }

    val openNotices by notices.notices.collectAsState()

    // 선제 알림은 화면 이동 없이 지금 화면 위에 띄운다 (확인할 때까지)
    Box(modifier.fillMaxSize()) {
    NavDisplay(
        backStack = backStack,
        onBack = goBack,
        entryProvider = entryProvider {
            entry<Home> {
                HomeRoute(
                    viewModel = home,
                    actions = HomeActions(
                        openTodo = { go(ScheduleList()) },
                        openWrite = { go(Calendar) },
                        openDocument = { documents.reset(); go(DocumentSource) },
                        openVoice = { go(Voice) },
                        openSettings = { go(Settings) },
                    ),
                    onExit = { activity?.finish() },
                )
            }
            entry<ScheduleList> {
                TodoRoute(
                    viewModel = todo,
                    onBack = goBack,
                    onOtherDate = { go(Calendar) },
                    onEdit = { id -> go(ScheduleEdit(id)) },
                )
            }
            entry<Calendar> {
                CalendarRoute(
                    viewModel = calendar,
                    onBack = goBack,
                    onEdit = { id -> go(ScheduleEdit(id)) },
                    onWrite = { date -> go(ScheduleCreate(date.toString())) },
                )
            }
            entry<ScheduleCreate> { key ->
                LaunchedEffect(key) {
                    val date = key.date.takeIf { it.isNotEmpty() }?.let(LocalDate::parse) ?: LocalDate.now()
                    val prefill = if (key.fromDocument) documents.resultSchedule() else null
                    form.start(FormMode.WRITE, prefill?.date ?: date, prefill = prefill)
                }
                ScheduleFormRoute(viewModel = form, onBack = goBack, onVoice = { go(VoiceTalk(VoiceMode.WRITE.name)) }, onDone = goBack)
            }
            entry<ScheduleEdit> { key ->
                LaunchedEffect(key) { form.start(FormMode.EDIT, editId = key.scheduleId) }
                ScheduleFormRoute(
                    viewModel = form, onBack = goBack, onDone = goBack,
                    onVoice = { go(VoiceTalk(VoiceMode.EDIT.name, key.scheduleId)) },
                )
            }
            entry<Voice> {
                VoiceChooseScreen(onBack = goBack, onChoose = { mode -> go(VoiceTalk(mode.name)) })
            }
            entry<VoiceTalk> { key ->
                LaunchedEffect(key) { voice.start(VoiceMode.valueOf(key.mode), key.editingId.takeIf { it != 0L }) }
                DisposableEffect(key) { onDispose { voice.stop() } }
                VoiceRoute(
                    viewModel = voice, userName = homeState.userName.ifBlank { "어르신" }, onStop = goBack,
                    onSaved = { date ->
                        calendar.show(date)
                        while (backStack.size > 1) backStack.removeLastOrNull()
                        backStack.add(Calendar)
                    },
                )
            }
            entry<DocumentSource> {
                DocumentRoute(
                    viewModel = documents,
                    onExit = goBack,
                    onEdit = { go(ScheduleCreate(fromDocument = true)) },
                    onWriteManually = { go(ScheduleCreate()) },
                    onDone = { while (backStack.size > 1) backStack.removeLastOrNull() },
                )
            }
            entry<Settings> {
                SettingsRoute(viewModel = settings, onBack = goBack)
            }
        },
    )
    openNotices.firstOrNull()?.let { notice ->
        NoticeDialog(notice, onListen = notices::play, onConfirm = notices::dismissFirst)
    }
    }
}
