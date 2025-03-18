package dev.chungjungsoo.gptmobile.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import dev.chungjungsoo.gptmobile.data.model.ApiType

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatRoom::class,
            parentColumns = ["chat_id"],
            childColumns = ["chat_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Message(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo("message_id")
    val id: Int = 0,

    @ColumnInfo(name = "chat_id")
    val chatId: Int = 0,

    @ColumnInfo(name = "content")
    val content: String,

    @ColumnInfo(name = "reasoning_content")
    val reasoningContent: String,

    @ColumnInfo(name = "image_data")
    val imageData: String? = null,

    @ColumnInfo(name = "linked_message_id")
    val linkedMessageId: Int = 0,

    @ColumnInfo(name = "platform_type")
    val platformType: ApiType?,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis() / 1000
)
