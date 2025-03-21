package dev.chungjungsoo.gptmobile.data.network

import com.aallam.openai.api.chat.ChatCompletion
import com.aallam.openai.api.chat.ChatCompletionRequest
import dev.chungjungsoo.gptmobile.data.dto.openailike.ChatSupperCompletionChunk
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

interface OpenAiLikeAPI {
    fun setToken(token: String?)
    fun setAPIUrl(url: String)
    fun streamChatMessage(messageRequest: ChatCompletionRequest): Flow<ChatSupperCompletionChunk>
    suspend fun chatMessage(messageRequest: JsonObject): ChatCompletion
}
