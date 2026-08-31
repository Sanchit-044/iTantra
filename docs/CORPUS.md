# Evaluation corpus for Module B2

The corpus is **not committed**. Recordings of real speakers are personal data, and
checking them into a repository is a decision for whoever owns the data, not a
convenience.

## Layout

```
harness/src/androidTest/assets/
  corpus/hi/<id>.wav   corpus/hi/<id>.txt
  corpus/ta/<id>.wav   corpus/ta/<id>.txt
  corpus/bn/<id>.wav   corpus/bn/<id>.txt
  roundtrip/hi-sample.wav      # optional, for the unattended B4 run
```

- **Audio**: 16 kHz, mono, 16-bit PCM WAV. The harness rejects anything else rather than
  resampling, because a silent resample is an easy way to measure the wrong thing.
- **Reference**: UTF-8 text in the language's own script, one utterance per file.

## What makes a corpus that tells you something

A WER measured on clean, read speech in a quiet room will look good and predict nothing
about a flood-response deployment. To be worth the effort the corpus should include:

- **At least 30 utterances per language**, or the number is dominated by noise. 100+ is
  better.
- **Multiple speakers**, both genders, a range of ages. A single-speaker corpus measures
  how well the model knows that one voice.
- **Real operational vocabulary** — place names, agency names, numbers, coordinates,
  casualty counts. These are exactly what a general-purpose model gets wrong and exactly
  what this app has to get right.
- **Realistic noise.** Wind, rain, crowds, vehicles, generators. A separate clean subset
  is useful to isolate whether errors come from the model or the environment.
- **Natural utterance length** — 3 to 15 seconds, matching how people actually speak
  into a radio.

Record on the **target handset**, not a studio microphone. Microphone response and the
platform's own audio processing meaningfully affect accuracy, and `MicrophoneSource`
deliberately requests `VOICE_RECOGNITION` to minimise that processing.

## Reference transcript conventions

These matter because they change the number:

- Write numbers as digits or as words **consistently**. `Wer.normalize` folds Devanagari,
  Bengali and Tamil digits to ASCII, so `२५` and `25` score as equal — but `25` versus
  `पच्चीस` counts as an error.
- Punctuation is stripped before scoring; do not agonise over it.
- Combining marks are **not** stripped. `बढ़` and `बढ` are different words and score as
  an error, which is correct.

## Running

```bash
./gradlew :harness:connectedAndroidTest

adb pull /sdcard/Android/data/in.gov.itantra.harness/files/b2-stt-evaluation.txt
```

Without a corpus the test **skips** rather than passing. A green run that measured
nothing would be worse than a skip.

## Reading the result

The report gives per-language WER, CER and RTF, then a findings list. Read the findings
first: they flag any language materially worse than the Hindi baseline, and any language
with no model at all. See [STT-BACKEND.md](STT-BACKEND.md) for why that second check
exists.

Compare **CER** as well as WER across languages. Tamil is strongly agglutinative: one
Tamil word carries what Hindi spreads over three or four, so a single wrong suffix
damages one token and Tamil WER understates the damage relative to Hindi. CER is the
fairer cross-language comparison.
