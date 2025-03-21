package dev.chungjungsoo.gptmobile.data.dto.openailike.websearch

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WebSearchOptions(

    @SerialName("search_context_size")
    val searchContextSize: String = "high",

    @SerialName("user_location")
    val userLocation: UserLocation
)
