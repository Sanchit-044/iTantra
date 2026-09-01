package `in`.gov.itantra.core.usecase

import `in`.gov.itantra.core.stt.SttEngine

/**
 * UseCase for handling Push-to-Talk deactivation.
 * It stops the STT engine.
 */
class StopPttTransmissionUseCase(
    private val sttEngine: SttEngine
) {
    fun execute() {
        sttEngine.stop()
    }
}
