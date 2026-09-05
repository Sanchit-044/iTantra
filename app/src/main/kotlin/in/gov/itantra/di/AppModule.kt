package `in`.gov.itantra.di

import android.content.Context
import android.media.AudioAttributes
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import `in`.gov.itantra.android.alert.AndroidForcedAudioFocus
import `in`.gov.itantra.android.alert.WavTemplateSource
import `in`.gov.itantra.android.audio.AndroidAudioSinkFactory
import `in`.gov.itantra.android.audio.AudioTrackSink
import `in`.gov.itantra.android.crypto.KeystoreKeyAgreement
import `in`.gov.itantra.android.pack.LocalLanguagePackManager
import `in`.gov.itantra.android.stt.OnnxCtcSttEngine
import `in`.gov.itantra.android.tts.VitsOnnxTtsEngine
import `in`.gov.itantra.core.alert.AlertPlayer
import `in`.gov.itantra.core.alert.ForcedAudioFocus
import `in`.gov.itantra.core.alert.TemplateAudioSource
import `in`.gov.itantra.core.audio.AudioSinkFactory
import `in`.gov.itantra.core.crypto.KeyAgreementProvider
import `in`.gov.itantra.core.lang.LanguageSettingsStore
import `in`.gov.itantra.core.pack.LanguagePackManager
import `in`.gov.itantra.core.stt.LanguageIdEngine
import `in`.gov.itantra.core.stt.ScriptLanguageId
import `in`.gov.itantra.core.stt.SttEngine
import `in`.gov.itantra.core.translate.DictionaryTranslationEngine
import `in`.gov.itantra.core.translate.TranslationEngine
import `in`.gov.itantra.core.tts.ChunkedSpeaker
import `in`.gov.itantra.core.tts.TtsEngine
import `in`.gov.itantra.core.usecase.ReceivePttTransmissionUseCase
import `in`.gov.itantra.core.usecase.SendAlertUseCase
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
    fun provideAudioSinkFactory(): AudioSinkFactory {
        return AndroidAudioSinkFactory()
    }

    @Provides
    fun provideReceivePttTransmissionUseCase(
        ttsEngine: TtsEngine,
        audioSinkFactory: `in`.gov.itantra.core.audio.AudioSinkFactory,
        translationEngine: TranslationEngine,
    ): ReceivePttTransmissionUseCase {
        return ReceivePttTransmissionUseCase(ttsEngine, audioSinkFactory, translationEngine)
    }

    @Provides
    @Singleton
    fun provideForcedAudioFocus(@ApplicationContext context: Context): ForcedAudioFocus {
        return AndroidForcedAudioFocus(context)
    }

    @Provides
    @Singleton
    fun provideTemplateAudioSource(@ApplicationContext context: Context): TemplateAudioSource {
        return WavTemplateSource(context)
    }

    @Provides
    @Singleton
    fun provideChunkedSpeaker(ttsEngine: TtsEngine): ChunkedSpeaker {
        return ChunkedSpeaker(ttsEngine)
    }

    @Provides
    @Singleton
    fun provideAlertPlayer(
        focus: ForcedAudioFocus,
        templates: TemplateAudioSource,
        speaker: ChunkedSpeaker,
    ): AlertPlayer {
        return AlertPlayer(
            focus = focus,
            templates = templates,
            speaker = speaker,
            sinkProvider = { format ->
                AudioTrackSink(
                    format,
                    usage = android.media.AudioAttributes.USAGE_ALARM,
                    contentType = android.media.AudioAttributes.CONTENT_TYPE_SPEECH,
                )
            },
        )
    }

    @Provides
    fun provideSendAlertUseCase(): SendAlertUseCase {
        return SendAlertUseCase()
    }
}
