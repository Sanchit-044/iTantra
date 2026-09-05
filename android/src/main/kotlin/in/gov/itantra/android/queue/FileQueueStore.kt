package `in`.gov.itantra.android.queue

import android.content.Context
import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.queue.InboxMessage
import `in`.gov.itantra.core.queue.OutboundMessage
import `in`.gov.itantra.core.queue.OutboundState
import `in`.gov.itantra.core.queue.QueueStore
import android.util.Base64
import java.io.File

/**
 * Tab-separated persistence. Text is Base64 so a newline in speech cannot split a row.
 * A corrupt file is treated as empty rather than crashing start-up.
 */
class FileQueueStore(context: Context) : QueueStore {

    private val outboundFile = File(context.filesDir, "queue-outbound.tsv")
    private val inboxFile = File(context.filesDir, "queue-inbox.tsv")
    private val lock = Any()

    override fun loadOutbound(): List<OutboundMessage> = synchronized(lock) {
        readLines(outboundFile).mapNotNull { line ->
            val p = line.split('\t')
            if (p.size < 5) return@mapNotNull null
            val language = Language.fromCode(p[1]) ?: return@mapNotNull null
            val state = runCatching { OutboundState.valueOf(p[2]) }.getOrNull()
                ?: return@mapNotNull null
            val at = p[3].toLongOrNull() ?: return@mapNotNull null
            val text = decode(p[4]) ?: return@mapNotNull null
            if (text.isBlank()) return@mapNotNull null
            OutboundMessage(p[0], language, text, at, state)
        }
    }

    override fun saveOutbound(items: List<OutboundMessage>) = synchronized(lock) {
        writeLines(
            outboundFile,
            items.map { "${it.id}\t${it.language.code}\t${it.state.name}\t${it.createdAtMs}\t${encode(it.text)}" },
        )
    }

    override fun loadInbox(): List<InboxMessage> = synchronized(lock) {
        readLines(inboxFile).mapNotNull { line ->
            val p = line.split('\t')
            if (p.size < 5) return@mapNotNull null
            val language = Language.fromCode(p[1]) ?: return@mapNotNull null
            val at = p[2].toLongOrNull() ?: return@mapNotNull null
            val unread = p[3] == "1"
            val text = decode(p[4]) ?: return@mapNotNull null
            if (text.isBlank()) return@mapNotNull null
            InboxMessage(p[0], language, text, at, unread)
        }
    }

    override fun saveInbox(items: List<InboxMessage>) = synchronized(lock) {
        writeLines(
            inboxFile,
            items.map {
                val flag = if (it.unread) "1" else "0"
                "${it.id}\t${it.language.code}\t${it.receivedAtMs}\t$flag\t${encode(it.text)}"
            },
        )
    }

    private fun readLines(file: File): List<String> = try {
        if (!file.exists()) emptyList() else file.readLines(Charsets.UTF_8).filter { it.isNotBlank() }
    } catch (_: Exception) {
        emptyList()
    }

    private fun writeLines(file: File, lines: List<String>) {
        try {
            if (lines.isEmpty()) {
                if (file.exists()) file.delete()
                File(file.parentFile, file.name + ".tmp").delete()
                return
            }
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(lines.joinToString("\n"), Charsets.UTF_8)
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
        } catch (_: Exception) {
            // Persistence is a convenience; a write failure must not break PTT.
        }
    }

    private fun encode(text: String): String =
        Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

    private fun decode(value: String): String? = try {
        String(Base64.decode(value, Base64.DEFAULT), Charsets.UTF_8)
    } catch (_: Exception) {
        null
    }
}
