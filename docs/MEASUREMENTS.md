# Measurements: what is measured, what is estimated, what is missing

The brief asks for measured memory footprint, measured WER, and measured diagnostics,
and asks that anything threatening the 2 GB budget be flagged rather than assumed. This
document states plainly which numbers exist.

## Nothing has been measured yet

**No performance figure in this repository is a measurement.** The reasons are
mechanical, not evasive:

| Blocker | Consequence |
|---|---|
| No STT models present | B2 WER cannot be computed |
| No TTS models present | Synthesis time and RTF cannot be computed |
| No evaluation corpus | B2 has nothing to score against |
| No Android SDK on the authoring machine | `:android` and `:harness` have not been compiled |
| No target device available | RAM, CPU, APK size and radio behaviour cannot be observed |

Reporting a plausible number under these conditions would be fabrication. Everything the
diagnostics module cannot observe is `null`, not `0`, precisely so that an unmeasured
quantity can never be mistaken for a measured one.

## What *is* verified

133 unit tests pass in `:core`, covering the logic that does not need a device:

- 800 ms silence endpointing, including leading silence, mid-word pauses, and adaptation
  to a noisy environment
- Packet encode/decode, framing across partial and coalesced reads, and tamper detection
  on the header (including the alert-flag flip)
- ECDH → HKDF → AES-256-GCM, nonce uniqueness, pairing-code derivation and its
  order-independence
- Indic number formatting (lakh/crore grouping), text normalisation for all three
  languages, clause chunking on the Devanagari danda
- WER and CER scoring, corpus-level aggregation, and the cross-language regression verdict
- Half-duplex busy detection, the 1-second retry, alert priority, queue eviction
- Alert playback forcing audio focus on **both** paths, and non-interruptibility
- WAV parsing including the non-canonical chunk layouts that break naive parsers

```bash
./gradlew :core:test
```

## Estimates, clearly labelled

`MemoryBudget` computes budget arithmetic from *declared* model sizes. Its output string
begins with `ESTIMATE (not a measurement)`, and a unit test asserts that it does, so the
figure cannot be quoted as observed.

Current estimates, one resident STT model plus one resident TTS voice:

| Configuration | STT | TTS | Runtime | Total | vs 350 MB budget |
|---|---|---|---|---|---|
| IndicWav2Vec INT8 + VITS INT8 (shipping) | ~133 MB | ~40 MB | ~90 MB | **~263 MB** | fits |
| *All three CTC models resident* | ~399 MB | ~40 MB | ~90 MB | **~529 MB** | **exceeds** |

The third row is why one-model-at-a-time is enforced rather than merely encouraged, and
is asserted by `MemoryBudgetTest`.

The 350 MB app budget is itself a judgement, not a platform constant: on a 2 GB handset
Android, the system UI and platform services consume most of the first gigabyte, and the
low-memory killer starts culling well before the nominal limit.

### Install size — resolved

Bundling three STT models and three TTS voices at install time — required by the offline
constraint — puts the package in the region of **400–500 MB**, above the 150 MB Play
limit for a plain APK.

**Decision: Android App Bundle with an install-time asset pack** (`:models-pack`).
Install-time packs are present before first launch and are served through the ordinary
`AssetManager`, so the zero-network guarantee is untouched and no code changes were
needed. The pack must never be switched to fast-follow or on-demand delivery — both
fetch over the network after install. `MemoryBudgetTest` asserts the bundled total still
exceeds 150 MB, so if models ever shrink enough to make the pack unnecessary, the test
says so rather than leaving stale complexity in place.

This is an install-size concern only; it does not affect RAM.

## How to produce the real numbers

Once models and a corpus exist:

```bash
# B2: WER, CER and RTF per language
./gradlew :harness:connectedAndroidTest
adb pull /sdcard/Android/data/in.gov.itantra.harness/files/b2-stt-evaluation.txt

# B4: round trip, time-to-first-audio, underruns
adb pull /sdcard/Android/data/in.gov.itantra.harness/files/b4-round-trip.txt

# B7: live memory, CPU, APK size, transport counters
#     AndroidDiagnosticsService.snapshot().toJson()
```

Report the numbers as they come out. If Tamil or Bengali is materially worse than Hindi,
`SttEvaluationHarness.Report.findings()` says so explicitly rather than leaving it to be
noticed.
