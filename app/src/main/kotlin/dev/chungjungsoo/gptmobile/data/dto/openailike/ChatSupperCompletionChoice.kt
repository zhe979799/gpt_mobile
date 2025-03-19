package dev.chungjungsoo.gptmobile.data.dto.openailike

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A chat completion generated by OpenAI
 */
@Serializable
data class ChatSupperCompletionChoice(
        @SerialName("index") val index: Int?,
        @SerialName("delta")
        val message: ChatSupperMessage?,
        @SerialName("finish_reason")
        val finishReason: String?
)


