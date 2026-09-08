package `in`.gov.itantra.data.history

import androidx.room.TypeConverter
import `in`.gov.itantra.core.Language

class HistoryTypeConverters {
    @TypeConverter
    fun fromLanguage(language: Language): String {
        return language.name
    }

    @TypeConverter
    fun toLanguage(name: String): Language {
        return try {
            Language.valueOf(name)
        } catch (e: Exception) {
            Language.HINDI // Fallback
        }
    }

    @TypeConverter
    fun fromDirection(direction: MessageDirection): String = direction.name

    @TypeConverter
    fun toDirection(name: String): MessageDirection = MessageDirection.valueOf(name)

    @TypeConverter
    fun fromStatus(status: MessageStatus): String = status.name

    @TypeConverter
    fun toStatus(name: String): MessageStatus = MessageStatus.valueOf(name)
}
