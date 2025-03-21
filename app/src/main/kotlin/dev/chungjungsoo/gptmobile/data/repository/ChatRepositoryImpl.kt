package dev.chungjungsoo.gptmobile.data.repository

import android.content.Context
import android.util.Log
import com.aallam.openai.api.chat.*
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIHost
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.*
import com.google.ai.client.generativeai.type.Content
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
import dev.chungjungsoo.gptmobile.data.dto.openailike.ChatSupperCompletionChunk
import dev.chungjungsoo.gptmobile.data.dto.openailike.websearch.ApproximateLocation
import dev.chungjungsoo.gptmobile.data.dto.openailike.websearch.UserLocation
import dev.chungjungsoo.gptmobile.data.dto.openailike.websearch.WebSearchOptions
import dev.chungjungsoo.gptmobile.data.model.ApiType
import dev.chungjungsoo.gptmobile.data.network.AnthropicAPI
import dev.chungjungsoo.gptmobile.data.network.OpenAiLikeAPI
import javax.inject.Inject
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*

class ChatRepositoryImpl @Inject constructor(
    private val appContext: Context,
    private val chatRoomDao: ChatRoomDao,
    private val messageDao: MessageDao,
    private val settingRepository: SettingRepository,
    private val anthropic: AnthropicAPI,
    private val openAiLikeApi: OpenAiLikeAPI
) : ChatRepository {

    private lateinit var openAI: OpenAI
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
        openAiLikeApi.setToken(platform.token)
        openAiLikeApi.setAPIUrl(platform.apiUrl)
        val completeWebsearch = completeWebsearch(question, ApiType.DEEPSEEK)
        var searchQuestion = question
        if (completeWebsearch?.isNotEmpty() == true) {
            searchQuestion = question.copy(
                content = question.content + "\n\n###以下是搜索结果资料，酌情引用:" + completeWebsearch
            )
        }
        val generatedMessages = messageToOpenAICompatibleMessage(ApiType.DEEPSEEK, history + listOf(searchQuestion))
        val generatedMessageWithPrompt = listOf(
            ChatMessage(role = ChatRole.System, content = platform.systemPrompt ?: ModelConstants.OPENAI_PROMPT)
        ) + generatedMessages
        val chatCompletionRequest = ChatCompletionRequest(
            model = ModelId(platform.model ?: ""),
            messages = generatedMessageWithPrompt,
            temperature = platform.temperature?.toDouble(),
            topP = platform.topP?.toDouble()
        )
        var state = false

        return openAiLikeApi.streamChatMessage(chatCompletionRequest)
            .map<ChatSupperCompletionChunk, ApiState> {
                chunk ->
                val message = chunk.choices.getOrNull(0)?.message
                if (message == null) {
                    ApiState.Success("")
                } else if (message.reasoningContent != null) {
                    if (!state) {
                        state = true
                        ApiState.Success(message.reasoningContent)
                    }else {
                        ApiState.Success(message.reasoningContent)
                    }
                } else {
                    if (state) {
                        state = false
                        ApiState.Success("\n\n---\n\n" + message.content)
                    } else {
                        ApiState.Success(message.content ?:"")
                    }
                }
            }
            .catch { throwable -> emit(ApiState.Error(throwable.message ?: "Unknown error")) }
            .onStart { emit(ApiState.Loading) }
            .onCompletion { emit(ApiState.Done) }
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

    override suspend fun completeWebsearch(question: Message,apiType: ApiType): String? {

        val platform = checkNotNull(settingRepository.fetchPlatforms().firstOrNull { it.name == ApiType.OPENAI })
        val currentPlatform = checkNotNull(settingRepository.fetchPlatforms().firstOrNull { it.name == apiType })
        if (currentPlatform.webSearchModel == null) {
            return ""
        }
        openAiLikeApi.setToken(platform.token)
        openAiLikeApi.setAPIUrl(platform.apiUrl)
        val generatedMessages = messageToOpenAICompatibleMessage(apiType, listOf(question))
        val generatedMessageWithPrompt = listOf(
            ChatMessage(role = ChatRole.System, content = ModelConstants.DEFAULT_WEBSEARCH_PROMPT)
        ) + generatedMessages
        val chatCompletionRequest = ChatCompletionRequest(
            model = ModelId(currentPlatform.webSearchModel),
            messages = generatedMessageWithPrompt,
            temperature = platform.temperature?.toDouble(),
            topP = platform.topP?.toDouble()
        )
        val json = Json { encodeDefaults = true }
        val jsonElement = json.encodeToJsonElement(chatCompletionRequest)
        val jsonObject = jsonElement.jsonObject.toMutableMap()
        // 构建对象并序列化
        val webSearchOptionJson = json.encodeToJsonElement(
            WebSearchOptions(
                searchContextSize = "high",
                userLocation = UserLocation(
                    type = "approximate",
                    approximate = ApproximateLocation(
                        country = "GB",
                        city = "London",
                        region = "London"
                    )
                )
            )
        )
        jsonObject["web_search_options"] = webSearchOptionJson
        Log.d("web_search", "chatCompletionRequest: $jsonObject")
        val chatMessage = openAiLikeApi.chatMessage(JsonObject(jsonObject))
        Log.d("web_search", "completeWebsearch: $chatMessage")
        return chatMessage.choices.getOrNull(0)?.message?.content?:""
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
