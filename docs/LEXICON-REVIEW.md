# Lexicon review — needed before any demo

`NumberLexicon` and `AbbreviationLexicon` in `core/…/tts` contain hand-authored Hindi,
Tamil and Bengali word lists. They are plain data with no logic attached, so corrections
need no code change.

## Confidence, stated honestly

| Language | Numbers 0–99 | Months / units / abbreviations |
|---|---|---|
| Hindi | High confidence | High confidence |
| Bengali | **Needs native-speaker review** | **Needs native-speaker review** |
| Tamil | **Needs native-speaker review** | **Needs native-speaker review** |

These were authored without native-speaker validation. That is a real risk for a
demo — a mispronounced number in an evacuation instruction is worse than a merely
awkward one — so it is flagged rather than left implicit.

## What is structurally sound regardless

- All three languages group by the Indian system (hundred / thousand / lakh / crore),
  which `IndicNumberFormatter` implements once and shares. `spell(100_000)` produces
  "one lakh", never "one hundred thousand".
- Hindi and Bengali are irregular from 21 to 99 and carry full 100-entry tables.
- Tamil composes 21–99 regularly from a combining ten plus a unit (21 = இருபத்தி ஒன்று),
  so that range is generated rather than typed, which removes a whole class of typos.
  Tamil *hundreds* are irregular (300 = முந்நூறு, 900 = தொள்ளாயிரம்) and are tabulated.
- A test asserts each language has exactly 100 distinct, non-blank number words, so a
  copy-paste duplicate cannot slip through unnoticed.

## How to review

1. Open `core/src/main/kotlin/in/gov/itantra/core/tts/NumberLexicon.kt`.
2. Check `BENGALI_UNITS` (100 entries, 0–99) and the Tamil `TAMIL_ONES`, `TAMIL_TEENS`,
   `TAMIL_TENS`, `TAMIL_TENS_COMBINING` and the irregular `hundreds` list.
3. Open `AbbreviationLexicon.kt` and check month names, unit names, and the agency
   expansions (NDRF, SDRF, NDMA, IMD, ISRO).
4. Run `./gradlew :core:test` — the structural tests still have to pass.

## Points a reviewer should specifically decide

- **Bengali "hundred".** Currently composed as `<digit>শো` (একশো, দুইশো). Whether
  দুইশো or দুশো is preferred in spoken register is a judgement call.
- **Bengali hour word.** `টা` is used for clock times ("দুটো"), which is right
  colloquially but may not suit a formal alert.
- **Tamil zero.** `சுழியம்` is used; `பூஜ்யம்` is also common and may be more natural
  in speech.
- **Agency names.** Whether NDRF should be spoken expanded ("தேசிய பேரிடர் மீட்புப்
  படை") or as letters is an operational preference. Expansion is currently assumed;
  operators under stress may recognise the acronym faster.
