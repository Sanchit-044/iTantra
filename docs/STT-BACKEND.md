# STT backend: the finding, and the decision taken

**Status: DECIDED. Vosk dropped; IndicWav2Vec CTC on ONNX Runtime for all three
languages. Implemented -- `OnnxCtcSttEngine` is the only STT backend in the tree.**

## The finding

The brief specifies wrapping Vosk for Hindi, Tamil and Bengali, and treats Module B2 as
the gate that decides whether to move to AI4Bharat's IndicWav2Vec.

That measurement cannot be taken as specified, because **Vosk publishes no Tamil model
and no Bengali model.**

Checked against the official model list at <https://alphacephei.com/vosk/models>:

| Language | Vosk model | Notes |
|---|---|---|
| Hindi | `vosk-model-small-hi-0.22` (~42 MB), `vosk-model-hi-0.22` (~1.5 GB) | Available |
| Tamil | **none** | Absent from the model list entirely |
| Bengali | **none** | Absent from the model list entirely |

Vosk's Indic coverage is Hindi, Gujarati and Telugu. Tamil and Bengali are not "worse";
they are absent. Two of the three required languages have no Vosk path at any quality
level.

This is not something B2 can measure its way out of. There is nothing to run.

## Why this matters more than a WER number

The B2 gate was designed to answer "is Tamil/Bengali quality acceptable on Vosk?" The
real answer is "Vosk cannot serve them at all", which is a stronger result and points
at a different decision. Had this been discovered after B4, B5 and B6 were built on a
Vosk-shaped `SttEngine`, the rework would have been considerably more expensive.

The code in this repository is written against a **backend-neutral `SttEngine`
interface**. Nothing above that interface knows or cares which engine is in use, which is
what made acting on this finding a small change rather than a rewrite.

## The options

### A. Vosk for Hindi, IndicWav2Vec (ONNX) for Tamil and Bengali

Two inference runtimes in one app, two model formats, two sets of failure modes, and
behaviour that differs by language. Partial results come from Kaldi's online decoder in
one language and from chunked CTC in the others, so latency and partial-text stability
would visibly differ between languages. Not recommended.

### B. IndicWav2Vec CTC on ONNX Runtime for all three languages — **CHOSEN**

One runtime, one model format, uniform behaviour, and ONNX Runtime is **already a
required dependency** because the VITS TTS decoder (Module B3) runs on it. Choosing this
removes the Vosk dependency and its native libraries entirely rather than adding to
them.

Trade-offs, stated honestly:

- **Larger models.** A wav2vec2-base encoder is ~95 M parameters; at INT8 that is
  roughly 95 MB per language versus Vosk Hindi's 42 MB. Only one is resident at a time,
  so this is an APK-size cost rather than a RAM cost. `MemoryBudgetTest` asserts that one
  resident CTC model plus one VITS voice fits the estimated budget, and that three
  resident models do not.
- **No built-in endpointer.** Vosk's decoder provides one; wav2vec2 does not. This is
  already handled: `SilenceEndpointer` in `:core` implements the 800 ms rule
  independently of any backend, which is also why it is unit-tested off-device.
- **Partial results cost CPU.** wav2vec2 is not a streaming model, so partials come
  from re-decoding the utterance so far. `OnnxCtcSttEngine` throttles this to one decode
  per 600 ms and skips rather than queues when one is still in flight. On the 2 GB target
  this needs measuring before it is assumed workable.
- **No language model by default.** Greedy CTC decoding will score worse than Vosk's
  LM-rescored output. An n-gram rescorer is the obvious next step, but the greedy
  baseline should be measured first, otherwise a poor Tamil score cannot be attributed
  between the acoustic model and the LM.

### C. Whisper (small/tiny) via whisper.cpp

Covers all three, MIT-licensed, single model for every language. Rejected as the primary
option: Tamil and Bengali quality is materially behind an Indic-specialised model,
latency is worse, and it cannot produce useful partial results — which the B1 interface
requires.

## What was done

Option B was chosen. Vosk is gone from the tree entirely: `VoskSttEngine`,
`VoskFileDecoder`, the `com.alphacephei:vosk-android` dependency and the Vosk model
catalogue have all been removed. `OnnxCtcSttEngine` is the production Module B1 engine.

`ModelRegistryTest` now asserts a *property* rather than a specific backend — that
whatever backend ships covers all three languages in scope. That is the test Vosk
failed, and it will fail again for any future backend with the same gap.

### Remaining steps before B2 can produce numbers

1. Obtain IndicWav2Vec checkpoints for Hindi, Tamil and Bengali; export to ONNX;
   quantise to INT8.
2. Assemble the evaluation corpus (see `CORPUS.md`).
3. Run `./gradlew :harness:connectedAndroidTest` on the real target handset.
4. Read the findings from `SttEvaluationHarness.Report.findings()`, which flags any
   language materially worse than the Hindi baseline.
5. **Then** decide whether to add an n-gram rescorer.

## What this did not change

Modules B3, B5, B6 and B7 were unaffected. B4 is written against the `SttEngine`
interface and needed only a one-line change of concrete class. That is the payoff from
keeping the interface backend-neutral rather than shaping it around Vosk.

## Note on the deviation from the brief

Vosk was named explicitly in the brief. Dropping it is a deliberate, approved deviation:
the alternative was a prototype supporting only Hindi, which does not meet the stated
three-language scope.

## Consequence to watch

wav2vec2 is not a streaming model, so partial results are produced by re-decoding the
utterance so far — at most every 600 ms, and skipping rather than queueing when a
previous decode is still in flight. See the class comment on `OnnxCtcSttEngine`.
Partial-result CPU cost is the main thing to watch when B2 and B4 first run on the
target handset. The final result is always a fresh full decode and is unaffected.
