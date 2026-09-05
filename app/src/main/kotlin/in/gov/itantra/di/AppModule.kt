package `in`.gov.itantra.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import `in`.gov.itantra.android.crypto.KeystoreKeyAgreement
import `in`.gov.itantra.android.pack.LocalLanguagePackManager
import `in`.gov.itantra.android.stt.OnnxCtcSttEngine
import `in`.gov.itantra.android.tts.VitsOnnxTtsEngine
import `in`.gov.itantra.core.crypto.KeyAgreementProvider
import `in`.gov.itantra.core.lang.LanguageSettingsStore
import `in`.gov.itantra.core.pack.LanguagePackManager
import `in`.gov.itantra.core.stt.LanguageIdEngine
import `in`.gov.itantra.core.stt.ScriptLanguageId
import `in`.gov.itantra.core.stt.SttEngine
import `in`.gov.itantra.core.translate.DictionaryTranslationEngine
import `in`.gov.itantra.core.translate.TranslationEngine
import `in`.gov.itantra.core.tts.TtsEngine
import `in`.gov.itantra.core.usecase.ReceivePttTransmissionUseCase
import `in`.gov.itantra.core.usecase.StartPttTransmissionUseCase
import `in`.gov.itantra.core.usecase.StopPttTransmissionUseCase
import `in`.gov.itantra.lang.PrefsLanguageSettingsStore
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
    fun provideLanguageSettingsStore(@ApplicationContext context: Context): LanguageSettingsStore {
        return PrefsLanguageSettingsStore(context)
    }

    @Provides
    @Singleton
    fun provideLanguagePackManager(@ApplicationContext context: Context): LanguagePackManager {
        return LocalLanguagePackManager(context)
    }

    @Provides
    @Singleton
    fun provideLanguageIdEngine(): LanguageIdEngine {
        return ScriptLanguageId()
    }

    @Provides
    @Singleton
    fun provideTranslationEngine(): TranslationEngine {
        return DictionaryTranslationEngine()
    }

    @Provides
    fun provideStartPttTransmissionUseCase(
        sttEngine: SttEngine
    ): StartPttTransmissionUseCase {
        return StartPttTransmissionUseCase(sttEngine)
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
        audioSinkFactory: `in`.gov.itantra.core.audio.AudioSinkFactory,
        translationEngine: TranslationEngine,
    ): ReceivePttTransmissionUseCase {
        return ReceivePttTransmissionUseCase(ttsEngine, audioSinkFactory, translationEngine)
    }
}
