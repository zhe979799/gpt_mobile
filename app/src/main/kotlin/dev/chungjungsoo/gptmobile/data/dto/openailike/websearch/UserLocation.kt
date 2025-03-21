package dev.chungjungsoo.gptmobile.data.dto.openailike.websearch

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserLocation(
    val type: String,
    val approximate: ApproximateLocation
)
