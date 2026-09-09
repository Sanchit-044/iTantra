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
import `in`.gov.itantra.android.discover.NearbyDiscovery
import `in`.gov.itantra.android.pack.LocalLanguagePackManager
import `in`.gov.itantra.android.diag.AndroidDiagnosticsService
import `in`.gov.itantra.android.notify.QueuedMessageNotifier
import `in`.gov.itantra.android.alert.BleAlertBroadcaster
import `in`.gov.itantra.android.alert.BleAlertScanner
import `in`.gov.itantra.android.alert.WifiAlertBroadcaster
import `in`.gov.itantra.android.alert.WifiAlertScanner
import `in`.gov.itantra.android.queue.FileQueueStore
import `in`.gov.itantra.android.stt.OnnxCtcSttEngine
import `in`.gov.itantra.android.tts.VitsOnnxTtsEngine
import `in`.gov.itantra.core.alert.AlertPlayer
import `in`.gov.itantra.core.alert.ForcedAudioFocus
import `in`.gov.itantra.core.alert.TemplateAudioSource
import `in`.gov.itantra.core.audio.AudioSinkFactory
import `in`.gov.itantra.core.crypto.KeyAgreementProvider
import `in`.gov.itantra.core.lang.LanguageSettingsStore
import `in`.gov.itantra.core.profile.ProfileStore
import `in`.gov.itantra.profile.FileProfileStore
import `in`.gov.itantra.core.theme.ThemeStore
import `in`.gov.itantra.theme.PrefsThemeStore
import `in`.gov.itantra.core.pack.LanguagePackManager
import `in`.gov.itantra.core.stt.LanguageIdEngine
import `in`.gov.itantra.core.stt.ScriptLanguageId
import `in`.gov.itantra.core.stt.SttEngine
import `in`.gov.itantra.core.translate.DictionaryTranslationEngine
import `in`.gov.itantra.core.translate.TranslationEngine
import `in`.gov.itantra.core.tts.ChunkedSpeaker
import `in`.gov.itantra.core.diag.DiagnosticsSink
import `in`.gov.itantra.core.tts.TtsEngine
import `in`.gov.itantra.core.usecase.ReceivePttTransmissionUseCase
import `in`.gov.itantra.core.usecase.SendAlertUseCase
import `in`.gov.itantra.core.usecase.StartPttTransmissionUseCase
import `in`.gov.itantra.core.usecase.StopPttTransmissionUseCase
import `in`.gov.itantra.data.history.HistoryDao
import `in`.gov.itantra.lang.PrefsLanguageSettingsStore
import `in`.gov.itantra.core.queue.InboundMessageInbox
import `in`.gov.itantra.core.queue.OutboundMessageQueue
import `in`.gov.itantra.core.queue.QueueStore
import `in`.gov.itantra.core.usecase.FlushQueuedMessagesUseCase
import java.util.concurrent.atomic.AtomicInteger
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
    fun provideFileProfileStore(@ApplicationContext context: Context): FileProfileStore {
        return FileProfileStore(context)
    }

    @Provides
    @Singleton
    fun provideThemeStore(@ApplicationContext context: Context): ThemeStore {
        return PrefsThemeStore(context)
    }

    @Provides
    @Singleton
    fun provideProfileStore(store: FileProfileStore): ProfileStore = store

    @Provides
    @Singleton
    fun provideQueueStore(@ApplicationContext context: Context): QueueStore {
        return FileQueueStore(context)
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
    fun provideOutboundQueue(store: QueueStore): OutboundMessageQueue {
        return OutboundMessageQueue(store)
    }

    @Provides
    @Singleton
    fun provideLanguagePackManager(@ApplicationContext context: Context): LanguagePackManager {
        return LocalLanguagePackManager(context, baseUrl = `in`.gov.itantra.BuildConfig.MODEL_PACK_BASE_URL)
    }

    @Provides
    @Singleton
    fun provideInbox(store: QueueStore): InboundMessageInbox {
        return InboundMessageInbox(store)
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
    @Singleton
    fun provideDiagnosticsSink(diagnostics: AndroidDiagnosticsService): DiagnosticsSink = diagnostics

    @Provides
    fun providePacketSequence(): AtomicInteger = AtomicInteger(0)

    @Provides
    @Singleton
    fun provideNotifier(@ApplicationContext context: Context): QueuedMessageNotifier {
        return QueuedMessageNotifier(context)
    }

    @Provides
    fun provideStartPttTransmissionUseCase(
        sttEngine: SttEngine,
        diagnostics: DiagnosticsSink,
        outboundQueue: OutboundMessageQueue,
        sequence: AtomicInteger,
    ): StartPttTransmissionUseCase {
        return StartPttTransmissionUseCase(sttEngine, diagnostics, outboundQueue, sequence)
    }

    @Provides
    fun provideFlushQueuedMessagesUseCase(
        outboundQueue: OutboundMessageQueue,
        sequence: AtomicInteger,
    ): FlushQueuedMessagesUseCase {
        return FlushQueuedMessagesUseCase(outboundQueue, sequence)
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

    @Provides
    @Singleton
    fun provideNearbyDiscovery(@ApplicationContext context: Context): NearbyDiscovery {
        return NearbyDiscovery(context)
    }

    @Provides
    @Singleton
    fun provideBleAlertBroadcaster(@ApplicationContext context: Context): BleAlertBroadcaster {
        return BleAlertBroadcaster(context)
    }

    @Provides
    @Singleton
    fun provideBleAlertScanner(
        @ApplicationContext context: Context,
        alertPlayer: AlertPlayer
    ): BleAlertScanner {
        return BleAlertScanner(context, alertPlayer)
    }

    @Provides
    @Singleton
    fun provideWifiAlertBroadcaster(@ApplicationContext context: Context): WifiAlertBroadcaster {
        return WifiAlertBroadcaster(context)
    }

    @Provides
    @Singleton
    fun provideWifiAlertScanner(
        @ApplicationContext context: Context,
        alertPlayer: AlertPlayer
    ): WifiAlertScanner {
        return WifiAlertScanner(context, alertPlayer)
    }

    @Provides
    @Singleton
    fun provideLanBroadcastAlertManager(@ApplicationContext context: Context): `in`.gov.itantra.android.alert.LanBroadcastAlertManager {
        return `in`.gov.itantra.android.alert.LanBroadcastAlertManager(context)
    }
}
