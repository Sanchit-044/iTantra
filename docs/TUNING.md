# Field tuning

Every constant below has a defensible default but none has been validated on the target
handset. They are gathered here because they are the parameters most likely to need
adjusting after the first real field test, and all are constructor arguments rather than
hardcoded values.

## Endpointer — `SilenceEndpointer`

| Parameter | Default | Notes |
|---|---|---|
| `silenceTimeoutMs` | 800 ms | Specified. Do not change without a reason. |
| `minSpeechLevelDb` | −40 dBFS | **Most likely to need tuning.** The absolute gate below which audio is always silence. |
| `speechMarginDb` | 9 dB | How far above the learned noise floor a frame must sit. |
| `minSpeechMs` | 120 ms | Guards against a cough or a door slam opening an utterance. |
| `frameMs` | 20 ms | Matches the capture frame size. |

**Why `minSpeechLevelDb` is the one to watch.** It is the primary threshold; the adaptive
noise floor can only raise it, never lower it. That ordering exists so the detector works
from the very first frame — with push-to-talk the user often starts speaking the instant
the mic opens, and there is no history to learn a floor from. The cost is that the
default assumes handset-distance speech lands above −40 dBFS.

Symptoms and responses:

- **Speech is not detected at all / no final result.** The threshold is too high for this
  microphone. Lower `minSpeechLevelDb` toward −45 or −50.
- **The utterance never ends in a noisy place.** Background noise is being classified as
  speech. Raise `minSpeechLevelDb`, or raise `speechMarginDb`.
- **Sentences are cut in the middle.** Speakers are pausing longer than 800 ms mid
  sentence. This is a specification question, not a tuning one — raising the timeout
  trades responsiveness for tolerance.

To measure rather than guess, log `SilenceEndpointer.rmsDb()` per frame during a real
recording and look at the distribution of speech versus silence levels.

## TTS chunking — `ClauseChunker`

| Parameter | Default | Notes |
|---|---|---|
| `minChunkChars` | 10 | Calibrated for Indic scripts, which are far denser per character than Latin. |
| `maxChunkChars` | 160 | Bounds time-to-first-audio and peak synthesis memory. |

`minChunkChars` is script-sensitive and was a real bug during development: a
Latin-derived value in the high teens merged every Hindi clause back into a single block
and silently defeated the chunking entirely. A complete Hindi clause such as
"पानी बढ़ रहा है।" is only 16 characters.

Lowering it starts audio sooner but flattens prosody across the boundary, because VITS
no longer sees the whole sentence when predicting duration and pitch.

## Synthesis — `VitsOnnxTtsEngine`

| Parameter | Default | Notes |
|---|---|---|
| `lengthScale` | 1.0 | Above 1.0 is slower. Alerts may warrant ~1.1 for intelligibility under stress. |
| `noiseScale` | 0.667 | Standard VITS default. |
| `noiseScaleW` | 0.8 | Standard VITS default. |
| intra-op threads | 2 | Oversubscribing a low-core handset makes synthesis slower and competes with playback. |

If `ChunkedSpeaker.SpeechListener.onUnderrun` fires, synthesis is slower than real time
(RTF > 1) and speech will stutter between clauses. Raising `queueDepth` hides brief
jitter but cannot fix a sustained RTF above 1 — that needs a smaller model or a faster
device, and should be reported rather than masked.

## Transport — `ChannelArbiter`

| Parameter | Default | Notes |
|---|---|---|
| `retryDelayMs` | 1000 ms | Specified. |
| `maxAttempts` | 3 | After this a packet is abandoned and reported. |
| `maxQueueDepth` | 32 | Bounds memory on a wedged channel. Alerts are never evicted. |

## Microphone — `MicrophoneSource`

Uses `MediaRecorder.AudioSource.VOICE_RECOGNITION`, not `MIC`. `MIC` applies the
handset's recording tuning, which on many devices includes AGC and processing that hurts
recognition accuracy. If accuracy is poor on a specific handset, try toggling the
`NoiseSuppressor` and `AcousticEchoCanceler` effects — on some OEM implementations they
help, and on others they distort speech enough to raise WER.
