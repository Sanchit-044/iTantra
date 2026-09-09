package `in`.gov.itantra.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.gov.itantra.android.discover.NearbyDiscovery
import `in`.gov.itantra.core.discover.NearbyPeer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class RadarFilter { BOTH, WIFI, BLUETOOTH }

data class RadarUiState(
    val scanning: Boolean = false,
    val filter: RadarFilter = RadarFilter.BOTH,
    val peers: List<NearbyPeer> = emptyList(),
    val scanError: String? = null,
)

@HiltViewModel
class RadarViewModel @Inject constructor(
    private val discovery: NearbyDiscovery,
) : ViewModel() {

    private val filter = MutableStateFlow(RadarFilter.BOTH)
    private val scanning = MutableStateFlow(false)

    val uiState: StateFlow<RadarUiState> = combine(
        scanning,
        filter,
        discovery.peers,
        discovery.error,
    ) { scanningNow, selected, peers, error ->
        RadarUiState(
            scanning = scanningNow,
            filter = selected,
            peers = peers.filter { matches(it, selected) },
            scanError = error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RadarUiState())

    fun start() {
        scanning.value = true
        discovery.start()
    }

    fun stop() {
        scanning.value = false
        discovery.stop()
    }

    fun setFilter(next: RadarFilter) {
        filter.value = next
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }

    private fun matches(peer: NearbyPeer, selected: RadarFilter): Boolean = when (selected) {
        RadarFilter.BOTH -> true
        RadarFilter.WIFI -> peer.hasWifi
        RadarFilter.BLUETOOTH -> peer.hasBluetooth
    }
}
