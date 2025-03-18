package dev.chungjungsoo.gptmobile.data.repository

import android.content.Context
import com.aallam.openai.api.chat.ChatCompletionChunk
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIHost
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.*
import com.theokanning.openai.client.OpenAiApi
import com.theokanning.openai.common.StreamOption
import com.theokanning.openai.completion.chat.ChatMessageRole
import com.theokanning.openai.service.OpenAiService
import dev.chungjungsoo.gptmobile.data.ModelConstants
import dev.chungjungsoo.gptmobile.data.database.dao.ChatRoomDao
import dev.chungjungsoo.gptmobile.data.database.dao.MessageDao
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoom
import dev.chungjungsoo.gptmobile.data.database.entity.Message
import dev.chungjungsoo.gptmobile.data.dto.ApiState
import dev.chungjungsoo.gptmobile.data.dto.anthropic.common.MessageRole
import dev.chungjungsoo.gptmobile.data.dto.anthropic.common.TextContent
import dev.chungjungsoo.gptmobile.data.dto.anthropic.request.InputMessage
import dev.chungjungsoo.gptmobile.data.dto.anthropic.request.MessageRequest
import dev.chungjungsoo.gptmobile.data.dto.anthropic.response.ContentDeltaResponseChunk
import dev.chungjungsoo.gptmobile.data.dto.anthropic.response.ErrorResponseChunk
import dev.chungjungsoo.gptmobile.data.dto.anthropic.response.MessageResponseChunk
import dev.chungjungsoo.gptmobile.data.model.ApiType
import dev.chungjungsoo.gptmobile.data.network.AnthropicAPI
import io.reactivex.Flowable
import io.reactivex.functions.Action
import io.reactivex.functions.Consumer
import java.time.Duration
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*

class ChatRepositoryImpl @Inject constructor(
    private val appContext: Context,
    private val chatRoomDao: ChatRoomDao,
    private val messageDao: MessageDao,
    private val settingRepository: SettingRepository,
    private val anthropic: AnthropicAPI
) : ChatRepository {

    private lateinit var openAI: OpenAI
    private lateinit var deepseek: OpenAiApi
    private lateinit var google: GenerativeModel
    private lateinit var ollama: OpenAI
    private lateinit var groq: OpenAI

    override suspend fun completeOpenAIChat(question: Message, history: List<Message>): Flow<ApiState> {
        val platform = checkNotNull(settingRepository.fetchPlatforms().firstOrNull { it.name == ApiType.OPENAI })
        openAI = OpenAI(platform.token ?: "", host = OpenAIHost(baseUrl = platform.apiUrl))

        val generatedMessages = messageToOpenAICompatibleMessage(ApiType.OPENAI, history + listOf(question))
        val generatedMessageWithPrompt = listOf(
            ChatMessage(role = ChatRole.System, content = platform.systemPrompt ?: ModelConstants.OPENAI_PROMPT)
        ) + generatedMessages
        val chatCompletionRequest = ChatCompletionRequest(
            model = ModelId(platform.model ?: ""),
            messages = generatedMessageWithPrompt,
            temperature = platform.temperature?.toDouble(),
            topP = platform.topP?.toDouble()
        )

        return openAI.chatCompletions(chatCompletionRequest)
            .map<ChatCompletionChunk, ApiState> { chunk -> ApiState.Success(chunk.choices.getOrNull(0)?.delta?.content ?: "") }
            .catch { throwable -> emit(ApiState.Error(throwable.message ?: "Unknown error")) }
            .onStart { emit(ApiState.Loading) }
            .onCompletion { emit(ApiState.Done) }
    }

    override suspend fun completeAnthropicChat(question: Message, history: List<Message>): Flow<ApiState> {
        val platform = checkNotNull(settingRepository.fetchPlatforms().firstOrNull { it.name == ApiType.ANTHROPIC })
        anthropic.setToken(platform.token)
        anthropic.setAPIUrl(platform.apiUrl)

        val generatedMessages = messageToAnthropicMessage(history + listOf(question))
        val messageRequest = MessageRequest(
            model = platform.model ?: "",
            messages = generatedMessages,
            maxTokens = ModelConstants.ANTHROPIC_MAXIMUM_TOKEN,
            systemPrompt = platform.systemPrompt ?: ModelConstants.DEFAULT_PROMPT,
            stream = true,
            temperature = platform.temperature,
            topP = platform.topP
        )

        return anthropic.streamChatMessage(messageRequest)
            .map<MessageResponseChunk, ApiState> { chunk ->
                when (chunk) {
                    is ContentDeltaResponseChunk -> ApiState.Success(chunk.delta.text)
                    is ErrorResponseChunk -> throw Error(chunk.error.message)
                    else -> ApiState.Success("")
                }
            }
            .catch { throwable -> emit(ApiState.Error(throwable.message ?: "Unknown error")) }
            .onStart { emit(ApiState.Loading) }
            .onCompletion { emit(ApiState.Done) }
    }

    override suspend fun completeGoogleChat(question: Message, history: List<Message>): Flow<ApiState> {
        val platform = checkNotNull(settingRepository.fetchPlatforms().firstOrNull { it.name == ApiType.GOOGLE })
        val config = generationConfig {
            temperature = platform.temperature
            topP = platform.topP
        }
        google = GenerativeModel(
            modelName = platform.model ?: "",
            apiKey = platform.token ?: "",
            systemInstruction = content { text(platform.systemPrompt ?: ModelConstants.DEFAULT_PROMPT) },
            generationConfig = config,
            safetySettings = listOf(
                SafetySetting(HarmCategory.DANGEROUS_CONTENT, BlockThreshold.ONLY_HIGH),
                SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, BlockThreshold.NONE)
            )
        )

        val inputContent = messageToGoogleMessage(history)
        val chat = google.startChat(history = inputContent)

        return chat.sendMessageStream(question.content)
            .map<GenerateContentResponse, ApiState> { response -> ApiState.Success(response.text ?: "") }
            .catch { throwable -> emit(ApiState.Error(throwable.message ?: "Unknown error")) }
            .onStart { emit(ApiState.Loading) }
            .onCompletion { emit(ApiState.Done) }
    }

    override suspend fun completeGroqChat(question: Message, history: List<Message>): Flow<ApiState> {
        val platform = checkNotNull(settingRepository.fetchPlatforms().firstOrNull { it.name == ApiType.GROQ })
        groq = OpenAI(platform.token ?: "", host = OpenAIHost(baseUrl = platform.apiUrl))

        val generatedMessages = messageToOpenAICompatibleMessage(ApiType.GROQ, history + listOf(question))
        val generatedMessageWithPrompt = listOf(
            ChatMessage(role = ChatRole.System, content = platform.systemPrompt ?: ModelConstants.DEFAULT_PROMPT)
        ) + generatedMessages
        val chatCompletionRequest = ChatCompletionRequest(
            model = ModelId(platform.model ?: ""),
            messages = generatedMessageWithPrompt,
            temperature = platform.temperature?.toDouble(),
            topP = platform.topP?.toDouble()
        )

        return groq.chatCompletions(chatCompletionRequest)
            .map<ChatCompletionChunk, ApiState> { chunk -> ApiState.Success(chunk.choices.getOrNull(0)?.delta?.content ?: "") }
            .catch { throwable -> emit(ApiState.Error(throwable.message ?: "Unknown error")) }
            .onStart { emit(ApiState.Loading) }
            .onCompletion { emit(ApiState.Done) }
    }

    override suspend fun completeDeepSeekChat(question: Message, history: List<Message>): Flow<ApiState> {
        val platform = checkNotNull(settingRepository.fetchPlatforms().firstOrNull { it.name == ApiType.DEEPSEEK })

        val customRetrofit = OpenAiService.customRetrofit(
            OpenAiService.defaultClient(platform.token, Duration.ofSeconds(ModelConstants.DEFAULT_TIMEOUT)),
            OpenAiService.defaultObjectMapper(),
            platform.apiUrl
        )
        deepseek = customRetrofit.create(OpenAiApi::class.java)

        val generatedMessages = convertMessagesToChatMessages(ApiType.OPENAI, history + listOf(question))
        val generatedMessageWithPrompt = listOf(
            com.theokanning.openai.completion.chat.ChatMessage(ChatMessageRole.SYSTEM.value(), platform.systemPrompt ?: ModelConstants.DEEPSEEK_PROMPT)
        ) + generatedMessages

        val chatCompletionRequest = com.theokanning.openai.completion.chat.ChatCompletionRequest.builder()
            .model(platform.model ?: "")
            .messages(generatedMessageWithPrompt)
            .topP((platform.topP ?: 1.0) as Double?)
            .temperature((platform.temperature ?: 1.0) as Double?)
            .maxTokens(platform.maxToken)
            .stream(true)
            .streamOption(StreamOption.builder().includeUsage(true).build())
            .build()

        val flowable = OpenAiService.stream<com.theokanning.openai.completion.chat.ChatCompletionChunk>(deepseek.createChatCompletionStream(chatCompletionRequest), com.theokanning.openai.completion.chat.ChatCompletionChunk::class.java)

        return flowableToFlow(flowable)
            .map<com.theokanning.openai.completion.chat.ChatCompletionChunk, ApiState> { chunk ->
                val message = chunk.choices.firstOrNull()?.message
                val content = message?.content ?: ""
                if (content.isEmpty()) {
                    ApiState.Success(message?.reasoningContent ?: "")
                } else {
                    ApiState.Success(content)
                }
            }
            .catch { throwable -> emit(ApiState.Error(throwable.message ?: "Unknown error")) }
            .onStart { emit(ApiState.Loading) }
            .onCompletion { emit(ApiState.Done) }
    }

    fun flowableToFlow(flowable: Flowable<com.theokanning.openai.completion.chat.ChatCompletionChunk>): Flow<com.theokanning.openai.completion.chat.ChatCompletionChunk> = callbackFlow {
        val disposable = flowable.subscribe(
            { data -> trySend(data) },  // 发送数据
            { error -> close(error) },                             // 发送异常
            { close() }                                            // 完成信号
        )
        awaitClose { disposable.dispose() }  // 确保取消订阅
    }

    override suspend fun completeOllamaChat(question: Message, history: List<Message>): Flow<ApiState> {
        val platform = checkNotNull(settingRepository.fetchPlatforms().firstOrNull { it.name == ApiType.OLLAMA })
        ollama = OpenAI(platform.token ?: "", host = OpenAIHost(baseUrl = "${platform.apiUrl}v1/"))

        val generatedMessages = messageToOpenAICompatibleMessage(ApiType.OLLAMA, history + listOf(question))
        val generatedMessageWithPrompt = listOf(
            ChatMessage(role = ChatRole.System, content = platform.systemPrompt ?: ModelConstants.DEFAULT_PROMPT)
        ) + generatedMessages
        val chatCompletionRequest = ChatCompletionRequest(
            model = ModelId(platform.model ?: ""),
            messages = generatedMessageWithPrompt,
            temperature = platform.temperature?.toDouble(),
            topP = platform.topP?.toDouble()
        )

        return ollama.chatCompletions(chatCompletionRequest)
            .map<ChatCompletionChunk, ApiState> { chunk -> ApiState.Success(chunk.choices.getOrNull(0)?.delta?.content ?: "") }
            .catch { throwable -> emit(ApiState.Error(throwable.message ?: "Unknown error")) }
            .onStart { emit(ApiState.Loading) }
            .onCompletion { emit(ApiState.Done) }
    }

    override suspend fun fetchChatList(): List<ChatRoom> = chatRoomDao.getChatRooms()

    override suspend fun fetchMessages(chatId: Int): List<Message> = messageDao.loadMessages(chatId)

    override fun generateDefaultChatTitle(messages: List<Message>): String? = messages.sortedBy { it.createdAt }.firstOrNull { it.platformType == null }?.content?.replace('\n', ' ')?.take(50)

    override suspend fun updateChatTitle(chatRoom: ChatRoom, title: String) {
        chatRoomDao.editChatRoom(chatRoom.copy(title = title.replace('\n', ' ').take(50)))
    }

    override suspend fun saveChat(chatRoom: ChatRoom, messages: List<Message>): ChatRoom {
        if (chatRoom.id == 0) {
            // New Chat
            val chatId = chatRoomDao.addChatRoom(chatRoom)
            val updatedMessages = messages.map { it.copy(chatId = chatId.toInt()) }
            messageDao.addMessages(*updatedMessages.toTypedArray())

            val savedChatRoom = chatRoom.copy(id = chatId.toInt())
            updateChatTitle(savedChatRoom, updatedMessages[0].content)

            return savedChatRoom.copy(title = updatedMessages[0].content.replace('\n', ' ').take(50))
        }

        val savedMessages = fetchMessages(chatRoom.id)
        val updatedMessages = messages.map { it.copy(chatId = chatRoom.id) }

        val shouldBeDeleted = savedMessages.filter { m ->
            updatedMessages.firstOrNull { it.id == m.id } == null
        }
        val shouldBeUpdated = updatedMessages.filter { m ->
            savedMessages.firstOrNull { it.id == m.id && it != m } != null
        }
        val shouldBeAdded = updatedMessages.filter { m ->
            savedMessages.firstOrNull { it.id == m.id } == null
        }

        chatRoomDao.editChatRoom(chatRoom)
        messageDao.deleteMessages(*shouldBeDeleted.toTypedArray())
        messageDao.editMessages(*shouldBeUpdated.toTypedArray())
        messageDao.addMessages(*shouldBeAdded.toTypedArray())

        return chatRoom
    }

    override suspend fun deleteChats(chatRooms: List<ChatRoom>) {
        chatRoomDao.deleteChatRooms(*chatRooms.toTypedArray())
    }

    // 核心转换函数
    fun convertMessagesToChatMessages(apiType: ApiType, messages: List<Message>): List<com.theokanning.openai.completion.chat.ChatMessage> {
        return messages.map { message ->
            val chatMessage = com.theokanning.openai.completion.chat.ChatMessage(
                mapPlatformTypeToRole(apiType, message.platformType),
                message.content
            )
            chatMessage
        }
    }

    // 平台类型到角色映射
    private fun mapPlatformTypeToRole(apiType: ApiType, platformType: ApiType?): String {
        return when (platformType) {
            null -> {
                ChatMessageRole.USER.value()
            }

            apiType -> {
                ChatMessageRole.ASSISTANT.value()
            }

            else -> {
                ""
            }
        }
    }

    private fun messageToOpenAICompatibleMessage(apiType: ApiType, messages: List<Message>): List<ChatMessage> {
        val result = mutableListOf<ChatMessage>()

        messages.forEach { message ->
            when (message.platformType) {
                null -> {
                    result.add(
                        ChatMessage(
                            role = ChatRole.User,
                            content = message.content
                        )
                    )
                }

                apiType -> {
                    result.add(
                        ChatMessage(
                            role = ChatRole.Assistant,
                            content = message.content
                        )
                    )
                }

                else -> {}
            }
        }

        return result
    }

    private fun messageToAnthropicMessage(messages: List<Message>): List<InputMessage> {
        val result = mutableListOf<InputMessage>()

        messages.forEach { message ->
            when (message.platformType) {
                null -> result.add(
                    InputMessage(role = MessageRole.USER, content = listOf(TextContent(text = message.content)))
                )

                ApiType.ANTHROPIC -> result.add(
                    InputMessage(role = MessageRole.ASSISTANT, content = listOf(TextContent(text = message.content)))
                )

                else -> {}
            }
        }

        return result
    }

    private fun messageToGoogleMessage(messages: List<Message>): List<Content> {
        val result = mutableListOf<Content>()

        messages.forEach { message ->
            when (message.platformType) {
                null -> result.add(content(role = "user") { text(message.content) })

                ApiType.GOOGLE -> result.add(content(role = "model") { text(message.content) })

                else -> {}
            }
        }

        return result
    }
}
