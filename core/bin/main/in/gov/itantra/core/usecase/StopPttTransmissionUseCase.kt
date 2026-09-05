package `in`.gov.itantra.core.usecase

import `in`.gov.itantra.core.stt.SttEngine
import `in`.gov.itantra.core.transport.Transport

/**
 * Ends a PTT turn: stop STT (safe if it never started) and release the talk floor.
 */
class StopPttTransmissionUseCase(
    private val sttEngine: SttEngine
) {
    fun execute(transport: Transport? = null) {
        sttEngine.stop()
        transport?.releaseFloor()
    }
}
