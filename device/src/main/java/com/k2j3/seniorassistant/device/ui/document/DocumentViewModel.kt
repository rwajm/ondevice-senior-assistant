package com.k2j3.seniorassistant.device.ui.document

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.k2j3.seniorassistant.device.data.schedule.Schedule
import com.k2j3.seniorassistant.device.server.BoardApiServer
import com.k2j3.seniorassistant.device.server.DocumentReading
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DocumentViewModel(private val server: BoardApiServer) : ViewModel() {
    private val _reading = MutableStateFlow<DocumentReading?>(null)

    val reading: StateFlow<DocumentReading?> = _reading.asStateFlow() // null while reading

    fun read() {
        _reading.value = null
        viewModelScope.launch {
            val reading = server.documents.read(SAMPLE_IMAGE)
            val fields = (reading as? DocumentReading.Extracted)?.fields
            Log.d(TAG, "${reading::class.simpleName}: fields=$fields")
            _reading.value = reading
        }
    }

    fun addToSchedule() {
        val fields = (reading.value as? DocumentReading.Extracted)?.fields ?: return
        viewModelScope.launch {
            val schedule = Schedule(
                title = fields.title,
                date = fields.date ?: LocalDate.now(), // filled by the user once there is input
                time = fields.time,
                place = fields.place,
                items = fields.items,
            )
            Log.d(TAG, "added ${server.schedules.addSchedule(schedule)}: $schedule")
        }
    }

    private companion object {
        const val TAG = "DocumentViewModel"
        val SAMPLE_IMAGE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47) // PNG; no file picker yet
    }
}
