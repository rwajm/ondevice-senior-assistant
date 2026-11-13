package com.k2j3.seniorassistant.device.ui.schedule

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import com.k2j3.seniorassistant.device.ui.theme.DialogButton
import com.k2j3.seniorassistant.device.ui.theme.AppCard
import com.k2j3.seniorassistant.device.ui.theme.AppColor
import com.k2j3.seniorassistant.device.ui.theme.AppDialog
import com.k2j3.seniorassistant.device.ui.theme.AppIcons
import com.k2j3.seniorassistant.device.ui.theme.AppScreen
import com.k2j3.seniorassistant.device.ui.theme.AppSegment
import com.k2j3.seniorassistant.device.ui.theme.AppSelect
import com.k2j3.seniorassistant.device.ui.theme.AppTextField
import com.k2j3.seniorassistant.device.ui.theme.SeniorAssistantTheme
import com.k2j3.seniorassistant.device.ui.theme.AppType
import com.k2j3.seniorassistant.device.ui.theme.PillButton
import com.k2j3.seniorassistant.device.ui.theme.SummaryTable
import com.k2j3.seniorassistant.device.ui.theme.TouchHeight
import com.k2j3.seniorassistant.device.ui.common.koreanDate
import com.k2j3.seniorassistant.device.ui.common.koreanTime
import java.time.LocalDate

data class FormActions(
    val onBack: () -> Unit = {},
    val onChange: ((ScheduleForm) -> ScheduleForm) -> Unit = {},
    val onSave: () -> Unit = {},
    val onVoice: () -> Unit = {},
    val onAskDelete: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onCloseDialog: () -> Unit = {},
    val onDone: () -> Unit = {},
)

@Composable
fun ScheduleFormRoute(viewModel: ScheduleFormViewModel, onBack: () -> Unit, onVoice: () -> Unit, onDone: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    ScheduleFormScreen(
        state,
        FormActions(
            onBack = onBack,
            onChange = viewModel::change,
            onSave = viewModel::save,
            onVoice = onVoice,
            onAskDelete = viewModel::askDelete,
            onDelete = { viewModel.delete(onDone) },
            onCloseDialog = viewModel::closeDialog,
            onDone = onDone,
        ),
    )
}

/** 할 일 적기 / 할 일 고치기 (같은 칸, 제목과 버튼만 다름) */
@Composable
fun ScheduleFormScreen(state: ScheduleFormUiState, actions: FormActions) {
    val edit = state.mode == FormMode.EDIT
    val focus = LocalFocusManager.current
    // 창이 떠 있을 때 기기의 뒤로 가기는 창만 닫는다
    BackHandler(enabled = state.dialog != FormDialog.NONE) { actions.onCloseDialog() }
    val save = { focus.clearFocus(); actions.onSave() }
    Box(Modifier.fillMaxSize()) {
        AppScreen(
            title = if (edit) "할 일 고치기" else "할 일 적기",
            onBack = actions.onBack,
            trailing = {
                if (edit) PillButton(
                    "할 일 지우기", actions.onAskDelete, leading = AppIcons.Trash,
                    container = AppColor.RedSoft, border = AppColor.Red, content = AppColor.Red,
                )
            },
        ) {
            AppCard(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxSize().padding(start = 48.dp, end = 48.dp, top = 28.dp, bottom = 32.dp), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                    Fields(state, actions, Modifier.weight(1f).fillMaxHeight())
                    SideButtons(edit, actions.copy(onSave = save), Modifier.width(320.dp).fillMaxHeight())
                }
            }
        }
        when (state.dialog) {
            FormDialog.SAVED -> SavedDialog(state, actions)
            FormDialog.DELETE -> DeleteDialog(state, actions)
            FormDialog.NONE -> Unit
        }
    }
}

private val Label = AppType.TileTitle.copy(fontSize = 34.sp)
private val LabelWidth = 150.dp

@Composable
private fun Fields(state: ScheduleFormUiState, actions: FormActions, modifier: Modifier) {
    val f = state.form
    val change = actions.onChange
    Column(modifier) {
        FieldRow("언제", Modifier.weight(2f)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    AppSelect(f.month, (1..12).toList(), { "${it}월" }, { m -> change { it.copy(month = m) } }, Modifier.width(200.dp))
                    AppSelect(f.date.dayOfMonth, (1..f.date.lengthOfMonth()).toList(), { "${it}일" }, { d -> change { it.copy(day = d) } }, Modifier.weight(1f))
                    Box(
                        Modifier.weight(1f).height(TouchHeight).clip(RoundedCornerShape(16.dp)).background(AppColor.Divider),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(f.date.koreanDate().substringAfter("(").removeSuffix(")") + "요일", style = AppType.Body, color = AppColor.TextSub)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    AppSegment("오전", "오후", f.pm, { pm -> change { it.copy(pm = pm) } }, Modifier.width(200.dp))
                    AppSelect(f.hour12, (1..12).toList(), { "${it}시" }, { h -> change { it.copy(hour12 = h) } }, Modifier.weight(1f))
                    AppSelect(f.minute, (0..55 step 5).toList(), { "%02d분".format(it) }, { m -> change { it.copy(minute = m) } }, Modifier.weight(1f))
                }
            }
        }
        Divider()
        FieldRow("무엇", Modifier.weight(1f)) {
            AppTextField(
                f.title, { v -> change { it.copy(title = v) } },
                if (state.titleMissing) "무엇을 하는지 적어 주세요" else "예) 치과 가기",
                Modifier.fillMaxWidth(), isError = state.titleMissing,
            )
        }
        Divider()
        FieldRow("어디서", Modifier.weight(1f)) {
            AppTextField(f.place, { v -> change { it.copy(place = v) } }, "예) ○○치과", Modifier.fillMaxWidth())
        }
        Divider()
        FieldRow("챙길것", Modifier.weight(1f)) {
            AppTextField(f.items, { v -> change { it.copy(items = v) } }, "예) 신분증", Modifier.fillMaxWidth(), last = true)
        }
    }
}

@Composable
private fun FieldRow(label: String, modifier: Modifier, content: @Composable () -> Unit) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(label, style = Label, modifier = Modifier.width(LabelWidth))
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun Divider() = Box(Modifier.fillMaxWidth().height(1.dp).background(AppColor.Border))

@Composable
private fun SideButtons(edit: Boolean, actions: FormActions, modifier: Modifier) {
    Column(modifier.padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        BigSideButton(if (edit) "말로 고치기" else "말로 적기", AppColor.BlueSoft, AppColor.Blue, actions.onVoice)
        Canvas(Modifier.fillMaxWidth().height(2.dp)) {
            drawLine(AppColor.Border, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = size.height, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)))
        }
        BigSideButton("저장하기", AppColor.GreenSoft, AppColor.Green, actions.onSave)
    }
}

@Composable
private fun ColumnScope.BigSideButton(text: String, container: Color, content: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        Modifier.weight(1f).fillMaxWidth().clip(shape).background(container).border(3.dp, content, shape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = Label, color = content) }
}

private fun ScheduleForm.summary() = listOf(
    "언제" to "${date.koreanDate()} ${time.koreanTime()}",
    "무엇" to title,
    "어디서" to place,
    "챙길것" to items,
)

@Composable
private fun SavedDialog(state: ScheduleFormUiState, actions: FormActions) {
    val title = if (state.mode == FormMode.EDIT) "할 일을 고쳤어요" else "할 일에 넣었어요"
    AppDialog(buildAnnotatedString { append(title) }, watermark = AppIcons.CheckCircle, watermarkColor = AppColor.Green) {
        SummaryTable(state.form.summary(), AppColor.Green)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            DialogButton("고치기", actions.onCloseDialog, Modifier.weight(1f), icon = AppIcons.PencilSmall)
            DialogButton("확인했어요", actions.onDone, Modifier.weight(1f), container = AppColor.Green, content = Color.White, bordered = false)
        }
    }
}

@Composable
private fun DeleteDialog(state: ScheduleFormUiState, actions: FormActions) {
    AppDialog(
        buildAnnotatedString {
            append("이 할 일을 ")
            withStyle(SpanStyle(color = AppColor.Red)) { append("지울까요?") }
        },
        watermark = AppIcons.TrashOpen, watermarkColor = AppColor.Red,
    ) {
        SummaryTable(state.form.summary(), AppColor.Red)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            DialogButton("아니요", actions.onCloseDialog, Modifier.weight(1f))
            DialogButton("지울게요", actions.onDelete, Modifier.weight(1f), container = AppColor.Red, content = Color.White, bordered = false)
        }
    }
}

private val sampleForm = ScheduleForm(2026, 11, 13, pm = true, hour12 = 2, title = "병원 진료", place = "○○내과", items = "신분증, 먹는 약")

@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun WritePreview() = SeniorAssistantTheme {
    ScheduleFormScreen(ScheduleFormUiState(FormMode.WRITE, ScheduleForm.from(LocalDate.of(2026, 11, 13))), FormActions())
}

@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun EditPreview() = SeniorAssistantTheme { ScheduleFormScreen(ScheduleFormUiState(FormMode.EDIT, sampleForm), FormActions()) }

@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun SavedPreview() = SeniorAssistantTheme {
    ScheduleFormScreen(ScheduleFormUiState(FormMode.WRITE, sampleForm, dialog = FormDialog.SAVED), FormActions())
}

@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun DeletePreview() = SeniorAssistantTheme {
    ScheduleFormScreen(ScheduleFormUiState(FormMode.EDIT, sampleForm, dialog = FormDialog.DELETE), FormActions())
}
