package `in`.gov.itantra.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import `in`.gov.itantra.android.crypto.KeystoreKeyAgreement
import `in`.gov.itantra.android.diag.AndroidDiagnosticsService
import `in`.gov.itantra.android.stt.OnnxCtcSttEngine
import `in`.gov.itantra.android.tts.VitsOnnxTtsEngine
import `in`.gov.itantra.core.crypto.KeyAgreementProvider
import `in`.gov.itantra.core.diag.DiagnosticsSink
import `in`.gov.itantra.core.stt.SttEngine
import `in`.gov.itantra.core.tts.TtsEngine
import `in`.gov.itantra.core.usecase.StartPttTransmissionUseCase
import `in`.gov.itantra.core.usecase.StopPttTransmissionUseCase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSttEngine(@ApplicationContext context: Context): SttEngine {
        return OnnxCtcSttEngine(context)
    }

    @Provides
    @Singleton
    fun provideTtsEngine(@ApplicationContext context: Context): TtsEngine {
        return VitsOnnxTtsEngine(context)
    }

    @Provides
    @Singleton
    fun provideKeyAgreementProvider(): KeyAgreementProvider {
        return KeystoreKeyAgreement()
    }

    @Provides
    @Singleton
    fun provideDiagnosticsService(
        @ApplicationContext context: Context,
        sttEngine: SttEngine,
        ttsEngine: TtsEngine,
    ): AndroidDiagnosticsService {
        return AndroidDiagnosticsService(
            context = context,
            sttEngine = { sttEngine },
            ttsEngine = { ttsEngine },
        )
    }

    @Provides
    @Singleton
    fun provideDiagnosticsSink(diagnostics: AndroidDiagnosticsService): DiagnosticsSink = diagnostics

    @Provides
    fun provideStartPttTransmissionUseCase(
        sttEngine: SttEngine,
        diagnostics: DiagnosticsSink,
    ): StartPttTransmissionUseCase {
        return StartPttTransmissionUseCase(sttEngine, diagnostics)
    }

    @Provides
    fun provideStopPttTransmissionUseCase(
        sttEngine: SttEngine
    ): StopPttTransmissionUseCase {
        return StopPttTransmissionUseCase(sttEngine)
    }

    @Provides
    @Singleton
    fun provideAudioSinkFactory(): `in`.gov.itantra.core.audio.AudioSinkFactory {
        return `in`.gov.itantra.android.audio.AndroidAudioSinkFactory()
    }

    @Provides
    fun provideReceivePttTransmissionUseCase(
        ttsEngine: TtsEngine,
        audioSinkFactory: `in`.gov.itantra.core.audio.AudioSinkFactory
    ): `in`.gov.itantra.core.usecase.ReceivePttTransmissionUseCase {
        return `in`.gov.itantra.core.usecase.ReceivePttTransmissionUseCase(ttsEngine, audioSinkFactory)
    }
}
