package `in`.gov.itantra.core.usecase

import `in`.gov.itantra.core.diag.AppLog
import `in`.gov.itantra.core.stt.SttEngine
import `in`.gov.itantra.core.transport.Transport

/**
 * Ends a PTT turn: stop STT (safe if it never started) and release the talk floor.
 */
class StopPttTransmissionUseCase(
    private val sttEngine: SttEngine
) {
    fun execute(transport: Transport? = null) {
        AppLog.d("StopPttUseCase", "execute() invoked")
        sttEngine.stop()
        
        // If STT is still listening, it means it's busy finalizing the decode.
        // In that case, we MUST NOT release the floor yet. StartPttTransmissionUseCase 
        // will release the floor when onFinal/onError is triggered.
        if (sttEngine.state != `in`.gov.itantra.core.stt.SttState.LISTENING) {
            AppLog.d("StopPttUseCase", "STT is not listening. Releasing floor immediately.")
            transport?.releaseFloor()
        } else {
            AppLog.d("StopPttUseCase", "STT is still finalizing. Deferring floor release.")
        }
    }
}
