package `in`.gov.itantra.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import `in`.gov.itantra.android.crypto.KeystoreKeyAgreement
import `in`.gov.itantra.android.notify.QueuedMessageNotifier
import `in`.gov.itantra.android.queue.FileQueueStore
import `in`.gov.itantra.android.stt.OnnxCtcSttEngine
import `in`.gov.itantra.android.tts.VitsOnnxTtsEngine
import `in`.gov.itantra.core.crypto.KeyAgreementProvider
import `in`.gov.itantra.core.queue.InboundMessageInbox
import `in`.gov.itantra.core.queue.OutboundMessageQueue
import `in`.gov.itantra.core.queue.QueueStore
import `in`.gov.itantra.core.stt.SttEngine
import `in`.gov.itantra.core.tts.TtsEngine
import `in`.gov.itantra.core.usecase.FlushQueuedMessagesUseCase
import `in`.gov.itantra.core.usecase.StartPttTransmissionUseCase
import `in`.gov.itantra.core.usecase.StopPttTransmissionUseCase
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
    fun provideQueueStore(@ApplicationContext context: Context): QueueStore {
        return FileQueueStore(context)
    }

    @Provides
    @Singleton
    fun provideOutboundQueue(store: QueueStore): OutboundMessageQueue {
        return OutboundMessageQueue(store)
    }

    @Provides
    @Singleton
    fun provideInbox(store: QueueStore): InboundMessageInbox {
        return InboundMessageInbox(store)
    }

    @Provides
    @Singleton
    fun providePacketSequence(): AtomicInteger = AtomicInteger(0)

    @Provides
    @Singleton
    fun provideNotifier(@ApplicationContext context: Context): QueuedMessageNotifier {
        return QueuedMessageNotifier(context)
    }

    @Provides
    fun provideStartPttTransmissionUseCase(
        sttEngine: SttEngine,
        outboundQueue: OutboundMessageQueue,
        sequence: AtomicInteger,
    ): StartPttTransmissionUseCase {
        return StartPttTransmissionUseCase(sttEngine, outboundQueue, sequence)
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
