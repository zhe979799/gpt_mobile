package dev.chungjungsoo.gptmobile.data.network

import com.aallam.openai.api.chat.ChatCompletionRequest
import dev.chungjungsoo.gptmobile.data.dto.openailike.ChatSupperCompletionChunk
import kotlinx.coroutines.flow.Flow

interface OpenAiLikeAPI {
    fun setToken(token: String?)
    fun setAPIUrl(url: String)
    fun streamChatMessage(messageRequest: ChatCompletionRequest): Flow<ChatSupperCompletionChunk>
}
