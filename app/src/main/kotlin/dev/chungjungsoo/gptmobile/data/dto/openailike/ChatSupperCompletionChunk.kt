package dev.chungjungsoo.gptmobile.data.dto.openailike

import com.aallam.openai.api.core.Usage
import java.util.List
import kotlinx.serialization.Serializable

/**
 * Object containing a response chunk from the chat completions streaming API.
 */
@Serializable
data class ChatSupperCompletionChunk(
        val id: String,
        val `object`: String = "chat.completion.chunk",
        val created: Long,
        val model: String,
        val choices: MutableList<ChatSupperCompletionChoice>,
        val usage: Usage?
)
