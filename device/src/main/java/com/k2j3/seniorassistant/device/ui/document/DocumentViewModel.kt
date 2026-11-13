package com.k2j3.seniorassistant.device.ui.document

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.k2j3.seniorassistant.device.ai.document.DocumentFields
import com.k2j3.seniorassistant.device.data.schedule.Schedule
import com.k2j3.seniorassistant.device.server.BoardApiServer
import com.k2j3.seniorassistant.device.server.DocumentReading
import com.k2j3.seniorassistant.device.server.Sentences
import java.time.LocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 문서 읽기 단계 */
sealed interface DocumentStep {
    data object Choose : DocumentStep
    data class Reading(val image: ByteArray) : DocumentStep
    data class Result(val fields: DocumentFields) : DocumentStep
    /** retakePhoto: 글자를 못 찾음(다른 파일 고르기) / 아니면 지금은 읽을 수 없음(다시 해 보기·직접 적기) */
    data class Error(val message: String, val retakePhoto: Boolean) : DocumentStep
}

enum class DocumentDialog { NONE, CONSENT, PHONE_GUIDE, SAVED }

data class DocumentUiState(
    val step: DocumentStep = DocumentStep.Choose,
    val dialog: DocumentDialog = DocumentDialog.NONE,
    /** 동의를 받은 뒤 사진 선택 창을 열어야 할 때 true (화면이 열고 나서 pickerOpened()를 부른다) */
    val openPicker: Boolean = false,
)

/**
 * 문서 읽기 (FR-04): 고르기 → (처음이면 외부 전송 동의) → 읽는 중 → 결과 / 실패 → 할 일에 넣기.
 */
class DocumentViewModel(private val server: BoardApiServer) : ViewModel() {

    private val state = MutableStateFlow(DocumentUiState())
    val uiState: StateFlow<DocumentUiState> = state.asStateFlow()

    private var pending: ByteArray? = null
    private var pickAfterConsent = false
    private var job: Job? = null

    /** 고르기 화면으로 돌아왔을 때 처음 상태로 */
    fun reset() {
        job?.cancel(); pending = null; pickAfterConsent = false
        state.value = DocumentUiState()
    }

    /** '파일에서 고르기': 처음이면 외부 전송 동의부터 받고, 그다음 사진 선택 창을 연다 */
    fun pickFile() {
        viewModelScope.launch {
            if (server.settings.getSettings().externalTransferConsent) state.update { it.copy(openPicker = true) }
            else { pickAfterConsent = true; state.update { it.copy(dialog = DocumentDialog.CONSENT) } }
        }
    }

    fun pickerOpened() = state.update { it.copy(openPicker = false) }

    /** 사진을 골랐을 때 */
    fun onImage(image: ByteArray) {
        if (image.size > MAX_IMAGE_BYTES) {
            state.update { it.copy(step = DocumentStep.Error("사진 파일이 너무 커요. 다른 파일을 골라 주세요.", retakePhoto = true)) }
            return
        }
        pending = image
        readPending()
    }

    fun consent(agree: Boolean, onRefuse: () -> Unit) {
        state.update { it.copy(dialog = DocumentDialog.NONE) }
        if (!agree) { pending = null; pickAfterConsent = false; onRefuse(); return }
        viewModelScope.launch {
            val s = server.settings.getSettings()
            server.settings.updateSettings(s.copy(externalTransferConsent = true))
            if (pickAfterConsent) { pickAfterConsent = false; state.update { it.copy(openPicker = true) } }
            else readPending()
        }
    }

    fun retry() = readPending()

    private fun readPending() {
        val image = pending ?: return
        job?.cancel()
        state.update { it.copy(step = DocumentStep.Reading(image)) }
        job = viewModelScope.launch {
            when (val r = server.documents.read(image)) {
                is DocumentReading.Extracted -> state.update { it.copy(step = DocumentStep.Result(r.fields)) }
                DocumentReading.ConsentRequired -> state.update { it.copy(step = DocumentStep.Choose, dialog = DocumentDialog.CONSENT) }
                is DocumentReading.Failed -> state.update {
                    it.copy(step = DocumentStep.Error(r.text, retakePhoto = r.text == Sentences.DOCUMENT_NOT_DETECTED))
                }
            }
        }
    }

    /** 결과의 '할 일에 넣기' (SCH-02) */
    fun addToSchedule() {
        val r = state.value.step as? DocumentStep.Result ?: return
        val date = r.fields.date ?: return
        viewModelScope.launch {
            server.schedules.addSchedule(r.fields.toSchedule(date))
            state.update { it.copy(dialog = DocumentDialog.SAVED) }
        }
    }

    /** 뒤로: 고르기 화면이 아니면 고르기로, 고르기 화면이면 나가기 */
    fun back(onExit: () -> Unit) {
        if (state.value.step == DocumentStep.Choose) onExit() else reset()
    }

    /** 결과 '고치기': 읽은 내용을 할 일 적기 화면에 채울 때 (날짜를 못 읽었으면 오늘) */
    fun resultSchedule(): Schedule? =
        (state.value.step as? DocumentStep.Result)?.fields?.let { it.toSchedule(it.date ?: LocalDate.now()) }

    fun showPhoneGuide() = state.update { it.copy(dialog = DocumentDialog.PHONE_GUIDE) }
    fun closeDialog() = state.update { it.copy(dialog = DocumentDialog.NONE) }

    private companion object {
        const val MAX_IMAGE_BYTES = 10 * 1024 * 1024 // API 정의서: 최대 10 MB
    }
}

fun DocumentFields.toSchedule(date: LocalDate) =
    Schedule(title = title, date = date, time = time, place = place, items = items)
