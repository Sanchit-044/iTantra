package `in`.gov.itantra.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.gov.itantra.data.history.HistoryDao
import `in`.gov.itantra.data.history.HistoryMessage
import `in`.gov.itantra.data.history.MessageDirection
import `in`.gov.itantra.data.history.MessageStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryUiState(
    val messages: List<HistoryMessage> = emptyList(),
    val directionFilter: MessageDirection? = null,
    val statusFilter: MessageStatus? = null,
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val historyDao: HistoryDao
) : ViewModel() {

    private val _directionFilter = MutableStateFlow<MessageDirection?>(null)
    private val _statusFilter = MutableStateFlow<MessageStatus?>(null)

    val uiState: StateFlow<HistoryUiState> = combine(
        historyDao.getAllMessages(),
        _directionFilter,
        _statusFilter
    ) { allMessages, direction, status ->
        val filtered = allMessages.filter { msg ->
            val matchDirection = direction == null || msg.direction == direction
            val matchStatus = status == null || msg.status == status
            matchDirection && matchStatus
        }
        HistoryUiState(
            messages = filtered,
            directionFilter = direction,
            statusFilter = status
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HistoryUiState()
    )

    fun setDirectionFilter(direction: MessageDirection?) {
        _directionFilter.value = direction
    }

    fun setStatusFilter(status: MessageStatus?) {
        _statusFilter.value = status
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            historyDao.clearHistory()
        }
    }
}
