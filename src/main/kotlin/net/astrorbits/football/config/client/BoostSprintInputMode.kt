package net.astrorbits.football.config.client

import com.mojang.serialization.Codec

enum class BoostSprintInputMode(
    val serializedName: String,
    val translationKey: String,
) {
    TOGGLE("toggle", "yacl3.config.nmbct-football.client.boost_sprint_mode.toggle"),
    HOLD("hold", "yacl3.config.nmbct-football.client.boost_sprint_mode.hold");

    companion object {
        fun fromSerializedName(name: String): BoostSprintInputMode =
            entries.firstOrNull { it.serializedName == name } ?: HOLD

        val CODEC: Codec<BoostSprintInputMode> =
            Codec.STRING.xmap(::fromSerializedName, BoostSprintInputMode::serializedName)
    }
}
