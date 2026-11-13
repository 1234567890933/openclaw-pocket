package com.openclaude.mobile.data.chat

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter fun roleToString(role: MessageEntity.Role): String = role.name
    @TypeConverter fun stringToRole(value: String): MessageEntity.Role =
        runCatching { MessageEntity.Role.valueOf(value) }.getOrDefault(MessageEntity.Role.SYSTEM)
}

@Database(
    entities = [SessionEntity::class, MessageEntity::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun sessionDao(): SessionDao
}
