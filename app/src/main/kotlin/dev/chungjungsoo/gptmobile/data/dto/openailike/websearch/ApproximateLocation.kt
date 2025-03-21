package dev.chungjungsoo.gptmobile.data.dto.openailike.websearch

import kotlinx.serialization.Serializable

@Serializable
data class ApproximateLocation(
    val country: String,
    val city: String,
    val region: String
)
