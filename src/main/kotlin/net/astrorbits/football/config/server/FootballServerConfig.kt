package net.astrorbits.football.config.server

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

/**
 * 服务端权威配置根对象，按物理 / 球员输入 / 守门员 / 粒子 / 体力机制分类。
 * [CODEC] 用于网络同步与持久化。
 */
data class FootballServerConfig(
    val physics: PhysicsSettings = PhysicsSettings.DEFAULT,
    val playerInput: PlayerInputSettings = PlayerInputSettings.DEFAULT,
    val goalkeeper: GoalkeeperSettings = GoalkeeperSettings.DEFAULT,
    val particles: ParticleSettings = ParticleSettings.DEFAULT,
    val staminaMechanism: StaminaMechanismSettings = StaminaMechanismSettings(),
) {
    companion object {
        val CODEC: Codec<FootballServerConfig> = RecordCodecBuilder.create { instance ->
            instance.group(
                PhysicsSettings.CODEC.fieldOf("physics").forGetter(FootballServerConfig::physics),
                PlayerInputSettings.CODEC.fieldOf("player_input").forGetter(FootballServerConfig::playerInput),
                GoalkeeperSettings.CODEC.fieldOf("goalkeeper").forGetter(FootballServerConfig::goalkeeper),
                ParticleSettings.CODEC.fieldOf("particles").forGetter(FootballServerConfig::particles),
                StaminaMechanismSettings.CODEC.optionalFieldOf("stamina_mechanism", StaminaMechanismSettings())
                    .forGetter(FootballServerConfig::staminaMechanism),
            ).apply(instance, ::FootballServerConfig)
        }

        val DEFAULT = FootballServerConfig()
    }
}
