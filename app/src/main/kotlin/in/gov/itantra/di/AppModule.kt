package in.gov.itantra.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import `in`.gov.itantra.android.crypto.KeystoreKeyAgreement
import `in`.gov.itantra.android.stt.OnnxCtcSttEngine
import `in`.gov.itantra.android.transport.WifiDirectTransport
import `in`.gov.itantra.android.tts.VitsOnnxTtsEngine
import `in`.gov.itantra.core.crypto.KeyAgreementProvider
import `in`.gov.itantra.core.stt.SttEngine
import `in`.gov.itantra.core.transport.Transport
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
    fun provideTransport(
        @ApplicationContext context: Context,
        keyAgreementProvider: KeyAgreementProvider
    ): Transport {
        return WifiDirectTransport(context, keyAgreementProvider)
    }

    @Provides
    fun provideStartPttTransmissionUseCase(
        sttEngine: SttEngine,
        transport: Transport
    ): StartPttTransmissionUseCase {
        return StartPttTransmissionUseCase(sttEngine, transport)
    }

    @Provides
    fun provideStopPttTransmissionUseCase(
        sttEngine: SttEngine
    ): StopPttTransmissionUseCase {
        return StopPttTransmissionUseCase(sttEngine)
    }
}
