package dev.chungjungsoo.gptmobile.data.dto.openailike

import com.aallam.openai.api.chat.Tool
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import kotlin.jvm.JvmOverloads
import kotlinx.serialization.Serializable

/**
 * <p>Each object has a role (either "system", "user", or "assistant") and content (the content of the message). Conversations can be as short as 1 message or fill many pages.</p>
 * <p>Typically, a conversation is formatted with a system message first, followed by alternating user and assistant messages.</p>
 * <p>The system message helps set the behavior of the assistant...（完整文档注释保留）</p>
 */
@Serializable
data class ChatSupperMessage(

    // 显式声明所有字段以保持 Jackson 兼容性
    val role: String?,
    val content: String?,
    val name: String?,
    @JsonProperty("reasoning_content")
    val reasoningContent: String?,
    @JsonProperty("tool_calls")
    val toolCalls: List<Tool>?,
    @JsonProperty("tool_call_id")
    val toolCallId: String?
    ) {
    // 自动处理空值序列化（通过 JsonInclude）
}
