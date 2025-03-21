package dev.chungjungsoo.gptmobile.data.network

import android.util.Log
import com.aallam.openai.api.chat.ChatCompletion
import com.aallam.openai.api.chat.ChatCompletionRequest
import dev.chungjungsoo.gptmobile.data.ModelConstants
import dev.chungjungsoo.gptmobile.data.dto.openailike.ChatSupperCompletionChunk
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.HttpStatement
import io.ktor.client.statement.content
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.cancel
import io.ktor.utils.io.readUTF8Line
import javax.inject.Inject
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.*

class OpenAiLikeAPIImpl @Inject constructor(
    private val networkClient: NetworkClient
) : OpenAiLikeAPI {

    private var token: String? = null
    private var apiUrl: String = ModelConstants.DEEPSEEK_API_URL

    override fun setToken(token: String?) {
        this.token = token
    }

    override fun setAPIUrl(url: String) {
        this.apiUrl = url
    }

    override fun streamChatMessage(messageRequest: ChatCompletionRequest): Flow<ChatSupperCompletionChunk> {
        val body = Json.encodeToJsonElement(messageRequest)
        val jsonObject = body.jsonObject.toMutableMap()
        jsonObject["stream"] = JsonPrimitive(value = true)
        val builder = HttpRequestBuilder().apply {
            method = HttpMethod.Post
            if (apiUrl.endsWith("/")) url("${apiUrl}chat/completions") else url("$apiUrl/chat/completions")
            contentType(ContentType.Application.Json)
            setBody(JsonObject(jsonObject))
            accept(ContentType.Text.EventStream)
            headers {
                append(API_KEY_HEADER, API_KEY_PREFIX + (token ?: ""))
            }
        }

        return flow {
            try {
                HttpStatement(builder = builder, client = networkClient()).execute {
                    streamEventsFrom(it)
                }
            } catch (e: Exception) {
                Log.e("OpenAiLikeApi", "e: ${e.message} ")
                throw Error(e.message)
            }
        }
    }

    @OptIn(InternalAPI::class)
    override suspend fun chatMessage(messageRequest: JsonObject): ChatCompletion {
        val builder = HttpRequestBuilder().apply {
            method = HttpMethod.Post
            if (apiUrl.endsWith("/")) url("${apiUrl}chat/completions") else url("$apiUrl/chat/completions")
            contentType(ContentType.Application.Json)
            setBody(messageRequest)
            headers {
                append(API_KEY_HEADER, API_KEY_PREFIX + (token ?: ""))
            }
        }
        val jsonInstance = Json {
            ignoreUnknownKeys = true  // 忽略 JSON 中多余的字段
            explicitNulls = false     // 允许缺失字段（自动设为 null）
        }
        try {
            val response = networkClient().request(builder)

            return jsonInstance.decodeFromString<ChatCompletion>(response.content.readUTF8Line()?: "")
        }  catch (e: Exception) {
            Log.w("Network Error", "Retrying... ${e.message}")
            throw Error(e.message)
        }
    }

    private suspend inline fun responseBody (response: HttpResponse) {

    }

    private suspend inline fun <reified T> FlowCollector<T>.streamEventsFrom(response: HttpResponse) {
        val channel: ByteReadChannel = response.body()
        val jsonInstance = Json {
            ignoreUnknownKeys = true  // 忽略 JSON 中多余的字段
            explicitNulls = false     // 允许缺失字段（自动设为 null）
        }

        try {
            while (currentCoroutineContext().isActive && !channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: continue
                val value: T = when {
                    line.contains(STREAM_END_TOKEN) -> break
                    line.startsWith(STREAM_PREFIX) -> {
                        val str = line.removePrefix(STREAM_PREFIX)
                        when {
                            str == STREAM_END_TOKEN -> break

                            else -> jsonInstance.decodeFromString(str)
                        }
                    }

                    else -> continue
                }
                emit(value)
            }
        } finally {
            channel.cancel()
        }
    }

    companion object {
        private const val STREAM_PREFIX = "data:"
        private const val STREAM_END_TOKEN = "[DONE]"
        private const val API_KEY_HEADER = "Authorization"
        private const val API_KEY_PREFIX = "Bearer "
    }
}
