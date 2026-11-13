package com.k2j3.seniorassistant.device.ui.document

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.k2j3.seniorassistant.device.ai.document.DocumentFields
import com.k2j3.seniorassistant.device.ui.theme.DialogButton
import com.k2j3.seniorassistant.device.ui.theme.AppCard
import com.k2j3.seniorassistant.device.ui.theme.AppColor
import com.k2j3.seniorassistant.device.ui.theme.AppDialog
import com.k2j3.seniorassistant.device.ui.theme.AppIcons
import com.k2j3.seniorassistant.device.ui.theme.AppScreen
import com.k2j3.seniorassistant.device.ui.theme.SeniorAssistantTheme
import com.k2j3.seniorassistant.device.ui.theme.AppType
import com.k2j3.seniorassistant.device.ui.theme.PillButton
import com.k2j3.seniorassistant.device.ui.theme.SummaryTable
import com.k2j3.seniorassistant.device.ui.theme.keepAll
import com.k2j3.seniorassistant.device.ui.common.koreanDate
import com.k2j3.seniorassistant.device.ui.common.koreanTime
import java.time.LocalDate
import java.time.LocalTime

data class DocumentActions(
    val onBack: () -> Unit = {},
    val onPickFile: () -> Unit = {},
    val onPhoneGuide: () -> Unit = {},
    val onConsent: (Boolean) -> Unit = {},
    val onRetry: () -> Unit = {},
    val onEdit: (DocumentFields) -> Unit = {},
    val onAdd: () -> Unit = {},
    val onWriteManually: () -> Unit = {},
    val onCloseDialog: () -> Unit = {},
    val onDone: () -> Unit = {},
)

@Composable
fun DocumentRoute(viewModel: DocumentViewModel, onExit: () -> Unit, onEdit: (DocumentFields) -> Unit, onWriteManually: () -> Unit, onDone: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // USB·기기 안의 사진 고르기 (안드로이드 사진 선택 창, 한 장만: 누르면 바로 닫힘)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) viewModel.onImage(bytes)
        }
    }
    LaunchedEffect(state.openPicker) {
        if (state.openPicker) { viewModel.pickerOpened(); picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    }
    BackHandler { viewModel.back(onExit) }
    DocumentScreen(
        state,
        DocumentActions(
            onBack = { viewModel.back(onExit) },
            onPickFile = viewModel::pickFile,
            onPhoneGuide = viewModel::showPhoneGuide,
            onConsent = { agree -> viewModel.consent(agree, onWriteManually) },
            onRetry = viewModel::retry,
            onEdit = onEdit,
            onAdd = viewModel::addToSchedule,
            onWriteManually = onWriteManually,
            onCloseDialog = viewModel::closeDialog,
            onDone = onDone,
        ),
    )
}

@Composable
fun DocumentScreen(state: DocumentUiState, actions: DocumentActions) {
    Box(Modifier.fillMaxSize()) {
        when (val step = state.step) {
            DocumentStep.Choose -> ChooseStep(actions)
            is DocumentStep.Reading -> ReadingStep(step.image, actions)
            is DocumentStep.Result -> ResultStep(step, actions)
            is DocumentStep.Error -> ErrorStep(step, actions)
        }
        when (state.dialog) {
            DocumentDialog.CONSENT -> ConsentDialog(actions)
            DocumentDialog.PHONE_GUIDE -> PhoneGuideDialog(actions)
            DocumentDialog.SAVED -> (state.step as? DocumentStep.Result)?.let { SavedDialog(it.fields, actions) }
            DocumentDialog.NONE -> Unit
        }
    }
}

// ---------- 고르기 ----------

@Composable
private fun ChooseStep(actions: DocumentActions) {
    AppScreen(title = "문서 읽기", onBack = actions.onBack) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            ChooseCard(AppIcons.Folder, "파일에서 고르기", "USB에 담긴 사진·파일", Modifier.weight(1f), actions.onPickFile)
            ChooseCard(AppIcons.Phone, "휴대폰으로 보내기", "휴대폰으로 찍은 사진·캡처", Modifier.weight(1f), actions.onPhoneGuide)
        }
    }
}

@Composable
private fun ChooseCard(icon: ImageVector, title: String, sub: String, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(32.dp)
    Column(
        modifier.fillMaxHeight().clip(shape).background(AppColor.Card).border(3.dp, AppColor.Border, shape).clickable(onClick = onClick).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterVertically),
    ) {
        Box(Modifier.size(200.dp).clip(CircleShape).background(AppColor.TileGreen), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(96.dp), tint = AppColor.IconGreen)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = AppType.TileTitle.copy(fontSize = 48.sp, letterSpacing = (-1).sp))
            Text(sub, style = AppType.Caption, color = AppColor.TextSub)
        }
    }
}

// ---------- 읽는 중 ----------

@Composable
private fun ReadingStep(image: ByteArray, actions: DocumentActions) {
    val bitmap = remember(image) { BitmapFactory.decodeByteArray(image, 0, image.size)?.asImageBitmap() }
    Row(
        Modifier.fillMaxSize().background(AppColor.Background).padding(start = 40.dp, end = 40.dp, top = 28.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(28.dp)).background(Color(0xFF1F2937)), contentAlignment = Alignment.Center) {
            if (bitmap != null) Image(bitmap, "고른 문서", Modifier.fillMaxSize().padding(24.dp), contentScale = ContentScale.Fit)
            else Icon(AppIcons.Document, null, Modifier.size(160.dp), tint = Color(0xFF9CA3AF))
        }
        Column(Modifier.width(390.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            PillButton("다시 선택하기", actions.onBack, Modifier.fillMaxWidth(), leading = AppIcons.Back)
            AppCard(Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterVertically),
                ) {
                    CircularProgressIndicator(Modifier.size(120.dp), color = AppColor.Green, trackColor = Color(0xFFE3F4E8), strokeWidth = 12.dp)
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("문서를 읽고 있어요", style = AppType.TileTitle)
                        Text("잠시만 기다려 주세요", style = AppType.Caption, color = AppColor.TextQuiet)
                    }
                }
            }
        }
    }
}

// ---------- 결과 ----------

@Composable
private fun ResultStep(r: DocumentStep.Result, actions: DocumentActions) {
    val f = r.fields
    AppScreen(title = "문서 읽기", onBack = actions.onBack) {
        AppCard(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 24.dp)) {
                Text("이런 내용이 있어요", style = AppType.ScreenTitle.copy(fontSize = 44.sp), modifier = Modifier.padding(top = 8.dp, bottom = 22.dp))
                Box(Modifier.fillMaxWidth().height(3.dp).background(AppColor.Text))
                val whenText = listOfNotNull(f.date?.koreanDate(), f.time?.koreanTime()).joinToString(" ")
                ResultRow("언제", whenText, AppColor.Green)
                ResultRow("무엇", f.title, AppColor.Text)
                ResultRow("어디서", f.place, AppColor.Text)
                ResultRow("챙길것", f.items.joinToString(", "), AppColor.Text, last = true)
                Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (f.date == null) "날짜를 읽지 못했어요. '고치기'로 채워 주세요." else "",
                        style = AppType.Caption, color = AppColor.TextSub, modifier = Modifier.weight(1f),
                    )
                    BigPill("고치기", AppIcons.PencilSmall, AppColor.Card, AppColor.Border, AppColor.Text) { actions.onEdit(f) }
                    if (f.date != null) {
                        Box(Modifier.width(20.dp))
                        BigPill("할 일에 넣기", AppIcons.Plus, AppColor.GreenSoft, AppColor.Green, AppColor.Green, actions.onAdd)
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String, valueColor: Color, last: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(label, style = AppType.TileTitle.copy(fontSize = 34.sp), modifier = Modifier.width(150.dp))
        val shown = value.ifBlank { "읽지 못했어요" }
        Text(
            shown.keepAll(),
            style = AppType.TileTitle.copy(fontSize = 34.sp),
            color = if (value.isBlank()) AppColor.TextQuietBorder else valueColor,
        )
    }
    if (!last) Box(Modifier.fillMaxWidth().height(1.dp).background(AppColor.Border))
}

@Composable
private fun BigPill(text: String, icon: ImageVector, container: Color, border: Color, content: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(42.dp)
    Row(
        Modifier.height(84.dp).clip(shape).background(container).border(3.dp, border, shape).clickable(onClick = onClick).padding(start = 32.dp, end = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, null, Modifier.size(34.dp), tint = content)
        Text(text, style = AppType.TileTitle.copy(fontSize = 32.sp), color = content)
    }
}

// ---------- 오류 ----------

@Composable
private fun ErrorStep(error: DocumentStep.Error, actions: DocumentActions) {
    val readable = error.retakePhoto
    AppScreen(title = "문서 읽기", onBack = actions.onBack) {
        AppCard(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().padding(48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
            ) {
                Icon(AppIcons.Warning, null, Modifier.size(96.dp), tint = AppColor.IconYellow)
                Text(error.message.keepAll(), style = AppType.ScreenTitle.copy(fontSize = 44.sp), textAlign = TextAlign.Center)
                Text(
                    if (readable) "밝은 곳에서 글자가 잘 보이는 사진으로 다시 골라 주세요." else "할 일은 직접 적을 수 있어요.",
                    style = AppType.Body.copy(fontWeight = FontWeight.Medium), color = AppColor.TextSub, textAlign = TextAlign.Center,
                )
                Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    if (readable) {
                        BigPill("다른 파일 고르기", AppIcons.Folder, AppColor.GreenSoft, AppColor.Green, AppColor.Green, actions.onPickFile)
                    } else {
                        BigPill("다시 해 보기", AppIcons.Back, AppColor.Card, AppColor.Border, AppColor.Text, actions.onRetry)
                        BigPill("할 일 직접 적기", AppIcons.Pencil, AppColor.GreenSoft, AppColor.Green, AppColor.Green, actions.onWriteManually)
                    }
                }
            }
        }
    }
}

// ---------- 창 ----------

@Composable
private fun ConsentDialog(actions: DocumentActions) {
    AppDialog(buildAnnotatedString { append("문서 사진을 보내도 될까요?") }) {
        Text(
            "글자를 읽으려고 사진을 인터넷으로 보내요.\n다 읽으면 사진은 바로 지워요.".keepAll(),
            style = AppType.Body.copy(fontWeight = FontWeight.Medium, lineHeight = 44.sp), color = AppColor.TextSub,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            DialogButton("안 보낼게요", { actions.onConsent(false) }, Modifier.weight(1f))
            DialogButton("보내도 돼요", { actions.onConsent(true) }, Modifier.weight(1f), container = AppColor.Green, content = Color.White, bordered = false)
        }
    }
}

@Composable
private fun PhoneGuideDialog(actions: DocumentActions) {
    AppDialog(buildAnnotatedString { append("휴대폰에서 보내 주세요") }, watermark = AppIcons.Phone, watermarkColor = AppColor.Green) {
        Text(
            "휴대폰의 생활도우미 앱에서\n'문서 보내기'를 누르고 사진을 고르면\n이 화면에 바로 보여요.".keepAll(),
            style = AppType.Body.copy(fontWeight = FontWeight.Medium, lineHeight = 44.sp), color = AppColor.TextSub,
        )
        DialogButton("알겠어요", actions.onCloseDialog, Modifier.fillMaxWidth(), container = AppColor.Green, content = Color.White, bordered = false)
    }
}

@Composable
private fun SavedDialog(f: DocumentFields, actions: DocumentActions) {
    AppDialog(buildAnnotatedString { append("할 일에 넣었어요") }, watermark = AppIcons.CheckCircle, watermarkColor = AppColor.Green) {
        SummaryTable(
            listOf(
                "언제" to listOfNotNull(f.date?.koreanDate(), f.time?.koreanTime()).joinToString(" "),
                "무엇" to f.title, "어디서" to f.place, "챙길것" to f.items.joinToString(", "),
            ),
            AppColor.Green,
        )
        DialogButton("확인했어요", actions.onDone, Modifier.fillMaxWidth(), container = AppColor.Green, content = Color.White, bordered = false)
    }
}

// ---------- 미리보기 ----------

private val sample = DocumentFields("건강검진", LocalDate.of(2026, 11, 28), LocalTime.of(9, 0), "○○내과 2층", listOf("신분증", "검진표"))

@Preview(widthDp = 1280, heightDp = 720) @Composable
private fun DocChoosePreview() = SeniorAssistantTheme { DocumentScreen(DocumentUiState(), DocumentActions()) }

@Preview(widthDp = 1280, heightDp = 720) @Composable
private fun DocConsentPreview() = SeniorAssistantTheme { DocumentScreen(DocumentUiState(dialog = DocumentDialog.CONSENT), DocumentActions()) }

@Preview(widthDp = 1280, heightDp = 720) @Composable
private fun DocReadingPreview() = SeniorAssistantTheme { DocumentScreen(DocumentUiState(DocumentStep.Reading(ByteArray(0))), DocumentActions()) }

@Preview(widthDp = 1280, heightDp = 720) @Composable
private fun DocResultPreview() = SeniorAssistantTheme { DocumentScreen(DocumentUiState(DocumentStep.Result(sample)), DocumentActions()) }

@Preview(widthDp = 1280, heightDp = 720) @Composable
private fun DocErrorPreview() = SeniorAssistantTheme { DocumentScreen(DocumentUiState(DocumentStep.Error("지금은 문서를 읽을 수 없어요. 직접 적어 주세요.", retakePhoto = false)), DocumentActions()) }

@Preview(widthDp = 1280, heightDp = 720) @Composable
private fun DocPhonePreview() = SeniorAssistantTheme { DocumentScreen(DocumentUiState(dialog = DocumentDialog.PHONE_GUIDE), DocumentActions()) }
