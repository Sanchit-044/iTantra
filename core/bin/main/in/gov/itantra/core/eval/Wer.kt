package `in`.gov.itantra.core.eval

import `in`.gov.itantra.core.Language

/**
 * Word-error-rate scoring for Module B2.
 *
 * WER = (substitutions + deletions + insertions) / reference-word-count, computed by
 * Levenshtein alignment over word tokens. The individual edit counts are reported
 * separately because they diagnose different failures: a deletion-heavy result usually
 * means the endpointer cut the utterance short, whereas substitution-heavy results
 * point at the acoustic model or the lexicon.
 *
 * A note on comparing across these three languages. Raw WER is not directly comparable
 * between Hindi, Tamil and Bengali, because Tamil is strongly agglutinative: one Tamil
 * orthographic word can carry what Hindi expresses in three or four. A single wrong
 * suffix damages one Tamil token, so Tamil WER is depressed relative to the amount of
 * meaning lost, while the opposite bias applies to Hindi. [characterErrorRate] is
 * reported alongside for this reason and is the fairer cross-language comparison.
 */
object Wer {

    data class Result(
        val referenceWords: Int,
        val substitutions: Int,
        val deletions: Int,
        val insertions: Int,
        val hits: Int,
    ) {
        val errors: Int get() = substitutions + deletions + insertions

        /** Error rate in 0..N. Can exceed 1.0 when the hypothesis inserts heavily. */
        val wer: Double get() = if (referenceWords == 0) 0.0 else errors.toDouble() / referenceWords

        fun format(): String =
            "WER=%.2f%% (S=%d D=%d I=%d H=%d / N=%d)"
                .format(wer * 100, substitutions, deletions, insertions, hits, referenceWords)
    }

    /**
     * Normalises for scoring: folds native digits to ASCII, strips punctuation, and
     * collapses whitespace. Case folding is a no-op for these three scripts but is
     * applied anyway for any Latin tokens that leak in.
     *
     * Devanagari and Bengali combining marks are deliberately NOT stripped -- they are
     * part of the word, not decoration, and removing them would flatter the score.
     */
    fun normalize(text: String): String {
        val folded = StringBuilder(text.length)
        for (ch in text) {
            folded.append(
                when (ch.code) {
                    in 0x0966..0x096F -> ('0' + (ch.code - 0x0966))
                    in 0x09E6..0x09EF -> ('0' + (ch.code - 0x09E6))
                    in 0x0BE6..0x0BEF -> ('0' + (ch.code - 0x0BE6))
                    else -> ch
                }
            )
        }
        return folded.toString()
            .replace(PUNCTUATION, " ")
            .replace(WHITESPACE, " ")
            .trim()
            .lowercase()
    }

    fun tokenize(text: String): List<String> =
        normalize(text).split(' ').filter { it.isNotEmpty() }

    fun score(reference: String, hypothesis: String): Result =
        scoreTokens(tokenize(reference), tokenize(hypothesis))

    /** Levenshtein alignment with backtrace, so edit types can be counted separately. */
    fun scoreTokens(ref: List<String>, hyp: List<String>): Result {
        val n = ref.size
        val m = hyp.size
        if (n == 0) {
            return Result(0, 0, 0, m, 0)
        }

        // dp[i][j] = edit distance between ref[0..i) and hyp[0..j)
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in 0..n) dp[i][0] = i
        for (j in 0..m) dp[0][j] = j
        for (i in 1..n) {
            for (j in 1..m) {
                val cost = if (ref[i - 1] == hyp[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // deletion from reference
                    dp[i][j - 1] + 1,      // insertion into hypothesis
                    dp[i - 1][j - 1] + cost,
                )
            }
        }

        var i = n
        var j = m
        var subs = 0
        var dels = 0
        var ins = 0
        var hits = 0
        while (i > 0 || j > 0) {
            when {
                i > 0 && j > 0 && dp[i][j] == dp[i - 1][j - 1] + (if (ref[i - 1] == hyp[j - 1]) 0 else 1) -> {
                    if (ref[i - 1] == hyp[j - 1]) hits++ else subs++
                    i--; j--
                }
                i > 0 && dp[i][j] == dp[i - 1][j] + 1 -> {
                    dels++; i--
                }
                else -> {
                    ins++; j--
                }
            }
        }
        return Result(n, subs, dels, ins, hits)
    }

    /**
     * Character error rate, ignoring whitespace. Preferred when comparing an
     * agglutinative language against an analytic one, for the reason given above.
     */
    fun characterErrorRate(reference: String, hypothesis: String): Double {
        val r = normalize(reference).replace(" ", "")
        val h = normalize(hypothesis).replace(" ", "")
        if (r.isEmpty()) return if (h.isEmpty()) 0.0 else 1.0
        return levenshtein(r, h).toDouble() / r.length
    }

    private fun levenshtein(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }

    private val PUNCTUATION = Regex("""[.,!?;:'"()\[\]{}\-–—।॥]""")
    private val WHITESPACE = Regex("""\s+""")
}

/** One scored utterance in a Module B2 evaluation run. */
data class UtteranceScore(
    val id: String,
    val language: Language,
    val reference: String,
    val hypothesis: String,
    val result: Wer.Result,
    val characterErrorRate: Double,
    val audioDurationMs: Long,
    val processingMs: Long,
) {
    /** Processing time over audio duration. Below 1.0 means faster than real time. */
    val realTimeFactor: Double
        get() = if (audioDurationMs == 0L) 0.0 else processingMs.toDouble() / audioDurationMs
}

/** Aggregate result for one language in a Module B2 evaluation run. */
data class LanguageReport(
    val language: Language,
    val utterances: List<UtteranceScore>,
) {
    /**
     * Corpus WER: total errors over total reference words. This is the correct
     * aggregate -- averaging per-utterance WER would weight a three-word utterance the
     * same as a thirty-word one and is a common way to accidentally report a prettier
     * number than the system deserves.
     */
    val corpusWer: Double
        get() {
            val totalRef = utterances.sumOf { it.result.referenceWords }
            val totalErr = utterances.sumOf { it.result.errors }
            return if (totalRef == 0) 0.0 else totalErr.toDouble() / totalRef
        }

    val corpusCer: Double
        get() = if (utterances.isEmpty()) 0.0 else utterances.sumOf { it.characterErrorRate } / utterances.size

    val meanRealTimeFactor: Double
        get() = if (utterances.isEmpty()) 0.0 else utterances.sumOf { it.realTimeFactor } / utterances.size

    val totalAudioMs: Long get() = utterances.sumOf { it.audioDurationMs }
}
