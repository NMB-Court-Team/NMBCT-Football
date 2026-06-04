package net.astrorbits.football.match

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

data class MatchAccessibilitySettings(
    val enableFootballPositionIndicator: Boolean = false,
) {
    companion object {
        val CODEC: Codec<MatchAccessibilitySettings> = RecordCodecBuilder.create { i ->
            i.group(
                Codec.BOOL.optionalFieldOf("enable_football_position_indicator", false)
                    .forGetter(MatchAccessibilitySettings::enableFootballPositionIndicator),
            ).apply(i, ::MatchAccessibilitySettings)
        }

        val DEFAULT = MatchAccessibilitySettings()
    }
}
