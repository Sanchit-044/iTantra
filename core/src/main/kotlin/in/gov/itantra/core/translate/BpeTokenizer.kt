package `in`.gov.itantra.core.translate

import `in`.gov.itantra.core.Language

/**
 * Pure-Kotlin BPE tokenizer compatible with SentencePiece BPE models.
 *
 * Loaded from a JSON file exported by `export_translation_models.py` containing:
 * ```json
 * {
 *   "vocab": { "▁": 4, "hello": 57, ... },
 *   "merges": ["▁ h", "e l", "l o", ...],
 *   "special_tokens": { "<s>": 0, "</s>": 2, "<pad>": 1, "<unk>": 3 },
 *   "lang_tags": { "hin_Deva": 256001, "ben_Beng": 256002, ... }
 * }
 * ```
 *
 * ## How BPE encoding works
 *
 * 1. Pre-tokenise: split on whitespace, prepend `▁` (U+2581) to mark word boundaries.
 * 2. Split each word into individual characters (the initial "tokens").
 * 3. Iteratively merge the adjacent pair with the **lowest rank** (i.e. the pair that
 *    appears earliest in the merge list) until no more merges apply.
 * 4. Map the resulting subwords to integer IDs via the vocabulary.
 *
 * ## Thread safety
 *
 * Instances are immutable once constructed — safe to share across threads.
 */
class BpeTokenizer private constructor(
    private val tokenToId: Map<String, Int>,
    private val idToToken: Map<Int, String>,
    private val mergeRank: Map<Pair<String, String>, Int>,
    private val specialTokens: Map<String, Int>,
    private val langTags: Map<String, Int>,
    val bosId: Int,
    val eosId: Int,
    val padId: Int,
    val unkId: Int,
) {
    /** Total vocabulary size including special tokens. */
    val vocabSize: Int get() = tokenToId.size + specialTokens.size + langTags.size

    /**
     * Encode [text] into BPE token IDs.
     *
     * Does **not** add BOS/EOS — the caller is responsible for framing
     * (IndicTrans2 wants `[src_tokens] [EOS] [src_lang_tag]` for the encoder
     * and `[EOS] [tgt_lang_tag]` to seed the decoder).
     */
    fun encode(text: String): IntArray {
        if (text.isBlank()) return IntArray(0)

        val words = preTokenize(text)
        val ids = mutableListOf<Int>()

        for (word in words) {
            val tokens = bpeMerge(word)
            for (token in tokens) {
                ids += tokenToId[token]
                    ?: specialTokens[token]
                    ?: langTags[token]
                    ?: unkId
            }
        }
        return ids.toIntArray()
    }

    /** Decode a sequence of token IDs back to text. */
    fun decode(ids: IntArray): String {
        val sb = StringBuilder()
        for (id in ids) {
            if (id == bosId || id == eosId || id == padId) continue
            val token = idToToken[id] ?: continue
            sb.append(token)
        }
        return sb.toString()
            .replace(SPACE_MARKER, ' ')
            .trim()
    }

    /** Returns the integer ID for an IndicTrans2 language tag, or null. */
    fun langTagId(flores200Code: String): Int? = langTags[flores200Code]

    // ---- Pre-tokenization: split on whitespace, add ▁ word-boundary markers ----

    /**
     * Split text into words with SentencePiece `▁` boundary markers.
     *
     * SentencePiece convention: `▁` is prepended to **every** word including the
     * first. This makes the first token different from the same word appearing
     * mid-sentence (e.g. `▁Hello` vs `▁world`), which is how the BPE vocabulary
     * was trained. Skipping the first `▁` would mis-align every input.
     */
    private fun preTokenize(text: String): List<List<String>> {
        val words = text.trim().split(WHITESPACE_RE)
        return words.filter { it.isNotEmpty() }.map { word ->
            "$SPACE_MARKER$word".map { it.toString() }
        }
    }

    // ---- BPE merge loop ----

    /**
     * Given a word split into single characters, iteratively merge the
     * highest-priority (lowest-rank) adjacent pair until no more merges apply.
     */
    private fun bpeMerge(chars: List<String>): List<String> {
        if (chars.size <= 1) return chars

        var tokens = chars.toMutableList()

        while (tokens.size > 1) {
            var bestRank = Int.MAX_VALUE
            var bestIndex = -1

            for (i in 0 until tokens.size - 1) {
                val pair = tokens[i] to tokens[i + 1]
                val rank = mergeRank[pair]
                if (rank != null && rank < bestRank) {
                    bestRank = rank
                    bestIndex = i
                }
            }

            if (bestIndex < 0) break

            val merged = tokens[bestIndex] + tokens[bestIndex + 1]
            tokens[bestIndex] = merged
            tokens.removeAt(bestIndex + 1)
        }

        return tokens
    }

    companion object {
        /** SentencePiece word-boundary marker. */
        const val SPACE_MARKER = '▁'

        private val WHITESPACE_RE = Regex("\\s+")

        /**
         * IndicTrans2 FLORES-200 language codes mapped from iTantra [Language].
         *
         * These codes must match the tags baked into the exported ONNX model.
         */
        private val LANGUAGE_TO_FLORES = mapOf(
            Language.HINDI to "hin_Deva",
            Language.TAMIL to "tam_Taml",
            Language.BENGALI to "ben_Beng",
            Language.GUJARATI to "guj_Gujr",
            Language.MARATHI to "mar_Deva",
            Language.KANNADA to "kan_Knda",
            Language.MALAYALAM to "mal_Mlym",
            Language.TELUGU to "tel_Telu",
            Language.ODIA to "ory_Orya",
            Language.ENGLISH to "eng_Latn",
        )

        /** Convert an iTantra [Language] to the FLORES-200 code used by IndicTrans2. */
        fun floresToCode(language: Language): String =
            LANGUAGE_TO_FLORES[language]
                ?: throw IllegalArgumentException("No FLORES-200 code for ${language.code}")

        /**
         * Build from a parsed JSON map. The export script produces this structure.
         *
         * @param vocab  token→id mapping (regular subword tokens)
         * @param merges ordered list of merge rules as "token1 token2" strings
         * @param specialTokens  `<s>`, `</s>`, `<pad>`, `<unk>` → id
         * @param langTags  FLORES-200 code → id (e.g. "hin_Deva" → 256001)
         */
        fun build(
            vocab: Map<String, Int>,
            merges: List<String>,
            specialTokens: Map<String, Int>,
            langTags: Map<String, Int>,
        ): BpeTokenizer {
            val mergeRank = HashMap<Pair<String, String>, Int>(merges.size)
            for ((index, merge) in merges.withIndex()) {
                val parts = merge.split(' ', limit = 2)
                if (parts.size == 2) {
                    mergeRank[parts[0] to parts[1]] = index
                }
            }

            val fullTokenToId = HashMap<String, Int>(vocab.size + specialTokens.size + langTags.size)
            fullTokenToId.putAll(vocab)

            val fullIdToToken = HashMap<Int, String>(fullTokenToId.size)
            for ((token, id) in vocab) fullIdToToken[id] = token
            for ((token, id) in specialTokens) fullIdToToken[id] = token
            for ((tag, id) in langTags) fullIdToToken[id] = tag

            return BpeTokenizer(
                tokenToId = fullTokenToId,
                idToToken = fullIdToToken,
                mergeRank = mergeRank,
                specialTokens = specialTokens,
                langTags = langTags,
                bosId = specialTokens["<s>"] ?: 0,
                eosId = specialTokens["</s>"] ?: 2,
                padId = specialTokens["<pad>"] ?: 1,
                unkId = specialTokens["<unk>"] ?: 3,
            )
        }
    }
}
