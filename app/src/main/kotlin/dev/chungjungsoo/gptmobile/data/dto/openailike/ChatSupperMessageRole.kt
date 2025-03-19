package dev.chungjungsoo.gptmobile.data.dto.openailike

/**
 * Enum representing the role of a message in a chat conversation.
 * See [OpenAI Documentation](https://platform.openai.com/docs/guides/chat/introduction)
 */
enum class ChatSupperMessageRole(val value: String) {
    SYSTEM("system"),       // System message (e.g., instructions to assistant)
    USER("user"),          // User-generated message
    ASSISTANT("assistant"), // Assistant's response
    TOOL("tool");          // Message triggering a tool call

    /**
     * Find the role by its string value.
     * @param value The role string to search for
     * @return The corresponding Role enum constant, or null if not found
     */
    companion object {
        fun fromValue(value: String): ChatSupperMessageRole? =
            values().find { it.value == value }
    }
}
