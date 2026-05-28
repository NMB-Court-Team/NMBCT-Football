package net.astrorbits.football

import net.astrorbits.football.input.FootballInputConfig
import net.astrorbits.football.physics.CollisionBounceResult
import net.astrorbits.football.physics.FootballPhysicsConfig
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.BlockParticleOption
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.RandomSource
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import net.minecraft.core.particles.DustParticleOptions

/**
 * 本 mod 足球相关的全部粒子效果播放入口。
 *
 * ## 如何调整
 * 1. 在下方找到对应场景的 [ParticleBurst] 或 [playXxx] 方法，修改粒子类型、数量、散布与速度。
 * 2. 触发阈值与力度缩放见 [FootballParticleConfig]。
 * 3. 将 [ParticleBurst.options] 换为任意 [net.minecraft.core.particles.ParticleOptions]（含 [BlockParticleOption]、[net.minecraft.core.particles.DustParticleOptions] 等）。
 *
 * ## 粒子一览
 * | 方法 | 触发场景 | 默认粒子 |
 * |------|----------|----------|
 * | [playKick] | 传球 / 射门 / 挑球 | 横扫 + 暴击 + 烟尘 |
 * | [playDribble] | 带球周期性触球 | 轻微烟尘 |
 * | [playTrap] | 停球 | 云雾吸收感 |
 * | [playFootballPlace] | 放置足球物品 | 放置烟尘 |
 * | [playGroundBounce] | 球落地反弹 | 脚下方块碎屑 + 尘土 |
 * | [playWallBounce] | 球撞墙反弹 | 暴击 + 白烟 |
 * | [playGkCatch] | 守门员接球 / 放球 | 云雾 |
 * | [playGkDive] | 守门员鱼跃 | 云 + 白烟拖尾 |
 * | [playGkPunch] | 守门员拳击 / 挡出 | 暴击 |
 * | [playGkThrow] | 守门员手抛 | 横扫 + 轻尘 |
 *
 * 所有播放均在服务端 [ServerLevel.sendParticles] 执行，会自动同步给附近客户端。
 */
object FootballParticles {
    fun init() {
        // static init
        // 若将来注册自定义 ParticleType，可在此初始化
    }

    /**
     * 单次粒子喷发配置。
     *
     * @param options 粒子类型（原版或 mod 注册）
     * @param count 粒子数量
     * @param spreadX/Y/Z 以中心点为原点的随机偏移盒半宽（格）
     * @param speed 粒子初速度随机幅度
     */
    data class ParticleBurst(
        val options: ParticleOptions,
        val count: Int,
        val spreadX: Double = FootballParticleConfig.SPREAD_HORIZONTAL,
        val spreadY: Double = FootballParticleConfig.SPREAD_VERTICAL,
        val spreadZ: Double = FootballParticleConfig.SPREAD_HORIZONTAL,
        val speed: Double = 0.0,
    )

    fun centerOfFootball(football: Football): Vec3 =
        football.position().add(0.0, FootballPhysicsConfig.RADIUS, 0.0)

    fun playKick(level: Level, center: Vec3, force: Double) {
        val scale = kickForceScale(force)
        val countBoost = (FootballParticleConfig.KICK_COUNT_FORCE_EXTRA * scale).toInt()
        emitBurst(level, center, sweepKickBurst(FootballParticleConfig.KICK_COUNT_BASE + countBoost))
        emitBurst(level, center, critBurst((FootballParticleConfig.KICK_COUNT_BASE / 2) + countBoost / 2))
        emitBurst(level, center, poofBurst(2 + countBoost / 3, FootballParticleConfig.KICK_PARTICLE_SPEED))
    }

    fun playKick(player: ServerPlayer, football: Football, force: Double) {
        playKick(player.level(), centerOfFootball(football), force)
    }

    fun playDribble(level: Level, center: Vec3) {
        emitBurst(level, center, poofBurst(FootballParticleConfig.DRIBBLE_COUNT, 0.02))
    }

    fun playDribble(player: ServerPlayer, football: Football) {
        playDribble(player.level(), centerOfFootball(football))
    }

    fun playTrap(level: Level, center: Vec3) {
        emitBurst(level, center, cloudBurst(FootballParticleConfig.TRAP_COUNT, 0.02))
        emitBurst(level, center, poofBurst(3, 0.01))
    }

    fun playTrap(player: ServerPlayer, football: Football) {
        playTrap(player.level(), centerOfFootball(football))
    }

    fun playFootballPlace(level: Level, pos: BlockPos) {
        val center = Vec3(pos.x + 0.5, pos.y + FootballPhysicsConfig.RADIUS, pos.z + 0.5)
        emitBurst(level, center, poofBurst(FootballParticleConfig.PLACE_COUNT, 0.03))
        emitBurst(level, center, cloudBurst(4, 0.02))
    }

    fun playCollisionBounces(level: Level, pos: BlockPos, bounce: CollisionBounceResult) {
        val center = Vec3(pos.x + 0.5, pos.y + FootballPhysicsConfig.RADIUS, pos.z + 0.5)
        if (bounce.hasGroundBounce) {
            playGroundBounce(level, center, pos, bounce.groundImpactSpeed)
        }
        if (bounce.hasWallBounce) {
            playWallBounce(level, center, bounce.wallImpactSpeed)
        }
    }

    fun playGroundBounce(level: Level, center: Vec3, blockPos: BlockPos, impactSpeed: Double) {
        if (impactSpeed < FootballParticleConfig.BOUNCE_PARTICLE_MIN_GROUND_VY) {
            return
        }
        val t = impactScale(
            impactSpeed,
            FootballParticleConfig.BOUNCE_PARTICLE_MIN_GROUND_VY,
            FootballParticleConfig.BOUNCE_PARTICLE_REFERENCE_SPEED,
        )
        val blockState = level.getBlockState(blockPos.below())
        if (!blockState.isAir) {
            emitBurst(
                level,
                center,
                ParticleBurst(
                    options = BlockParticleOption(ParticleTypes.BLOCK, blockState),
                    count = (4 + 10 * t).toInt(),
                    spreadX = 0.28,
                    spreadY = 0.08,
                    spreadZ = 0.28,
                    speed = FootballParticleConfig.BOUNCE_PARTICLE_SPEED * (0.5 + t),
                ),
            )
        }
        emitBurst(level, center, poofBurst((3 + 6 * t).toInt(), FootballParticleConfig.BOUNCE_PARTICLE_SPEED))
        emitBurst(level, center, dustPuffBurst((2 + 4 * t).toInt()))
    }

    fun playWallBounce(level: Level, center: Vec3, impactSpeed: Double) {
        if (impactSpeed < FootballParticleConfig.BOUNCE_PARTICLE_MIN_WALL_SPEED) {
            return
        }
        val t = impactScale(
            impactSpeed,
            FootballParticleConfig.BOUNCE_PARTICLE_MIN_WALL_SPEED,
            FootballParticleConfig.BOUNCE_PARTICLE_REFERENCE_SPEED,
        )
        emitBurst(level, center, critBurst((4 + 8 * t).toInt(), FootballParticleConfig.BOUNCE_PARTICLE_SPEED))
        emitBurst(level, center, whiteSmokeBurst((3 + 5 * t).toInt(), FootballParticleConfig.BOUNCE_PARTICLE_SPEED))
    }

    fun playGkCatch(level: Level, center: Vec3) {
        emitBurst(level, center, cloudBurst(FootballParticleConfig.GK_CATCH_COUNT, 0.02))
        emitBurst(level, center, poofBurst(3, 0.015))
    }

    fun playGkCatch(player: ServerPlayer, football: Football? = null) {
        val center = football?.let { centerOfFootball(it) }
            ?: player.position().add(0.0, 1.0, 0.0)
        playGkCatch(player.level(), center)
    }

    fun playGkDive(player: ServerPlayer) {
        val center = player.position().add(0.0, 0.35, 0.0)
        emitBurst(player.level(), center, cloudBurst(FootballParticleConfig.GK_DIVE_COUNT, FootballParticleConfig.GK_DIVE_PARTICLE_SPEED))
        emitBurst(player.level(), center, whiteSmokeBurst(6, FootballParticleConfig.GK_DIVE_PARTICLE_SPEED))
    }

    fun playGkPunch(level: Level, center: Vec3) {
        emitBurst(level, center, critBurst(FootballParticleConfig.GK_PUNCH_COUNT, FootballParticleConfig.KICK_PARTICLE_SPEED))
        emitBurst(level, center, sweepKickBurst(4))
    }

    fun playGkPunch(player: ServerPlayer, football: Football) {
        playGkPunch(player.level(), centerOfFootball(football))
    }

    fun playGkThrow(level: Level, center: Vec3) {
        emitBurst(level, center, sweepKickBurst(FootballParticleConfig.GK_THROW_COUNT))
        emitBurst(level, center, poofBurst(4, 0.025))
    }

    fun playGkThrow(player: ServerPlayer, football: Football) {
        playGkThrow(player.level(), centerOfFootball(football))
    }

    private fun emitBurst(level: Level, center: Vec3, burst: ParticleBurst) {
        val server = level as? ServerLevel ?: return
        if (burst.count <= 0) {
            return
        }
        server.sendParticles(
            burst.options,
            center.x,
            center.y,
            center.z,
            burst.count,
            burst.spreadX,
            burst.spreadY,
            burst.spreadZ,
            burst.speed,
        )
    }

    private fun sweepKickBurst(count: Int): ParticleBurst =
        ParticleBurst(ParticleTypes.SWEEP_ATTACK, count, spreadX = 0.35, spreadY = 0.2, spreadZ = 0.35)

    private fun critBurst(count: Int, speed: Double = FootballParticleConfig.KICK_PARTICLE_SPEED): ParticleBurst =
        ParticleBurst(ParticleTypes.CRIT, count, speed = speed)

    private fun poofBurst(count: Int, speed: Double = 0.0): ParticleBurst =
        ParticleBurst(ParticleTypes.POOF, count, speed = speed)

    private fun cloudBurst(count: Int, speed: Double = 0.0): ParticleBurst =
        ParticleBurst(ParticleTypes.CLOUD, count, speed = speed)

    private fun whiteSmokeBurst(count: Int, speed: Double = 0.0): ParticleBurst =
        ParticleBurst(ParticleTypes.WHITE_SMOKE, count, spreadX = 0.25, spreadY = 0.15, spreadZ = 0.25, speed = speed)

    private fun dustPuffBurst(count: Int): ParticleBurst {
        // 棕褐色尘土（RGB 0x8C6B47）
        val dust = DustParticleOptions(0xFF8C6B47.toInt(), 0.9f)
        return ParticleBurst(
            dust,
            count,
            spreadX = 0.2,
            spreadY = 0.06,
            spreadZ = 0.2,
            speed = 0.02,
        )
    }

    private fun impactScale(impactSpeed: Double, minSpeed: Double, referenceSpeed: Double): Float =
        ((impactSpeed - minSpeed) / referenceSpeed).toFloat().coerceIn(0f, 1f)

    private fun kickForceScale(force: Double): Float {
        val min = FootballInputConfig.CHIP_FORCE
        val max = FootballInputConfig.SHOOT_FORCE_MAX * FootballInputConfig.SHOOT_SPRINT_BONUS
        @Suppress("KotlinConstantConditions")
        if (max <= min) {
            return 1f
        }
        return ((force - min) / (max - min)).toFloat().coerceIn(0f, 1f)
    }
}
