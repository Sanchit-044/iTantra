package `in`.gov.itantra.core.tts

/**
 * Splits text into clause-sized chunks for Module B3's incremental playback.
 *
 * Why this exists: VITS is non-autoregressive. It generates a whole utterance in one
 * forward pass and cannot stream audio out mid-utterance. The only honest way to get
 * "starts speaking before the full sentence is ready" is at the text level -- cut the
 * text at clause boundaries, synthesise each clause separately, and begin playing
 * clause 1 while clause 2 is still in the model. That is what this class feeds.
 *
 * The trade-off is real and worth stating: chunking at a comma slightly flattens the
 * prosody across that boundary, because the model no longer sees the whole sentence
 * when predicting duration and pitch. [minChunkChars] exists to keep that cost small
 * by refusing to emit chunks so short that they sound clipped.
 */
class ClauseChunker(
    /**
     * Chunks shorter than this are merged forward rather than emitted alone.
     *
     * Calibrated for Indic scripts, which are far denser per character than Latin: a
     * complete Hindi clause such as "पानी बढ़ रहा है।" is only 16 characters. A Latin-
     * derived threshold in the high teens silently merges every real clause back into
     * one block and defeats the entire point of chunking.
     */
    private val minChunkChars: Int = 10,
    /**
     * Hard ceiling. A clause longer than this is split at the last word boundary
     * before the limit, which bounds both latency-to-first-audio and peak memory
     * during synthesis -- both of which matter on the 2 GB target device.
     */
    private val maxChunkChars: Int = 160,
) {
    /**
     * Returns the chunks in speaking order. Terminal punctuation is retained because
     * VITS uses it as a prosodic cue; the trailing separator is what makes a chunk
     * sound like the end of a clause rather than an abrupt cut.
     */
    fun chunk(text: String): List<String> {
        val normalised = text.replace(Regex("\\s+"), " ").trim()
        if (normalised.isEmpty()) return emptyList()

        val raw = splitOnBoundaries(normalised)
        val bounded = raw.flatMap { splitOverlongChunk(it) }
        return mergeShortChunks(bounded)
    }

    /**
     * Splits at clause punctuation, keeping the delimiter attached to the chunk it
     * ends. Handles the Devanagari/Bengali danda (U+0964) and double danda (U+0965)
     * alongside Latin punctuation; Tamil conventionally uses the Latin full stop.
     */
    private fun splitOnBoundaries(text: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            sb.append(ch)
            if (ch in BOUNDARY_CHARS) {
                // Consume any run of punctuation and the following space, so "!?" or
                // "..." does not produce a string of empty chunks.
                while (i + 1 < text.length && text[i + 1] in BOUNDARY_CHARS) {
                    sb.append(text[i + 1]); i++
                }
                // A period between digits is a decimal point, not a clause end. Text
                // reaching here is already normalised, but transport messages can
                // arrive un-normalised, so guard anyway.
                val isDecimalPoint = ch == '.' &&
                    sb.length >= 2 && sb[sb.length - 2].isDigit() &&
                    i + 1 < text.length && text[i + 1].isDigit()
                if (!isDecimalPoint) {
                    out += sb.toString().trim()
                    sb.setLength(0)
                }
            }
            i++
        }
        if (sb.isNotBlank()) out += sb.toString().trim()
        return out.filter { it.isNotBlank() }
    }

    private fun splitOverlongChunk(chunk: String): List<String> {
        if (chunk.length <= maxChunkChars) return listOf(chunk)
        val out = mutableListOf<String>()
        var rest = chunk
        while (rest.length > maxChunkChars) {
            val window = rest.substring(0, maxChunkChars)
            val cut = window.lastIndexOf(' ').takeIf { it > minChunkChars } ?: maxChunkChars
            out += rest.substring(0, cut).trim()
            rest = rest.substring(cut).trim()
        }
        if (rest.isNotBlank()) out += rest
        return out
    }

    /**
     * Merges a chunk that is too short into the following one. Merging forward rather
     * than backward keeps time-to-first-audio low: the first chunk stays as small as
     * the minimum allows instead of absorbing its neighbour.
     */
    private fun mergeShortChunks(chunks: List<String>): List<String> {
        if (chunks.isEmpty()) return chunks
        val out = mutableListOf<String>()
        var pending = StringBuilder()
        for (c in chunks) {
            if (pending.isNotEmpty()) pending.append(' ')
            pending.append(c)
            if (pending.length >= minChunkChars) {
                out += pending.toString()
                pending = StringBuilder()
            }
        }
        if (pending.isNotEmpty()) {
            // A trailing fragment joins the previous chunk rather than being spoken alone.
            if (out.isNotEmpty()) out[out.lastIndex] = out.last() + " " + pending.toString()
            else out += pending.toString()
        }
        return out
    }

    companion object {
        /**
         * U+0964 DEVANAGARI DANDA and U+0965 DOUBLE DANDA are the sentence terminators
         * in Hindi and Bengali orthography and are not covered by Latin punctuation.
         */
        private val BOUNDARY_CHARS = charArrayOf(
            '.', '!', '?', ',', ';', ':', '।', '॥',
        )
    }
}
