package net.astrorbits.football

import net.astrorbits.football.NMBCTFootball.id
import net.astrorbits.football.input.FootballInputConfig
import net.astrorbits.football.input.FootballInputConfig.SHOOT_FORCE_MAX
import net.astrorbits.football.input.GoalkeeperInputConfig
import net.minecraft.core.BlockPos
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.minecraft.world.level.Level
import net.astrorbits.football.physics.CollisionBounceResult
import net.astrorbits.football.physics.FootballPhysicsConfig
import net.minecraft.sounds.SoundEvents

/**
 * 本 mod 用到的全部游戏音效。
 *
 * ## 替换音效
 * 1. 找到下方对应场景的 [SoundSpec]，将 [SoundSpec.event] 改为目标 [net.minecraft.sounds.SoundEvent]。
 * 2. 按需调整 [SoundSpec.volume]、[SoundSpec.basePitch]、[SoundSpec.pitchSpread]。
 * 3. 若使用自定义音效包，在 `assets/<mod_id>/sounds.json` 注册后，
 *    将 [net.minecraft.sounds.SoundEvent] 换为 `SoundEvent.createVariableRangeEvent(...)` 即可。
 *
 * ## 音效一览
 * | 常量 | 触发场景 | 默认原版音效 |
 * |------|----------|--------------|
 * | [KICK] | 传球 / 射门 / 挑球 | 强力攻击 |
 * | [DRIBBLE] | 带球轻推 | 黏液块脚步 |
 * | [TRAP] | 停球 | 羊毛脚步 |
 * | [FOOTBALL_PLACE] | 放置足球物品 | 黏液块放置 |
 * | [BOUNCE_GROUND] | 球落地反弹 | 黏液块击中 |
 * | [BOUNCE_WALL] | 球撞墙反弹 | 石头击中 |
 * | [GK_CATCH] | 守门员接球 / 鱼跃接住 / 脚下放球 | 羊毛脚步 |
 * | [GK_DIVE] | 守门员鱼跃扑救 | 横扫攻击 |
 * | [GK_PUNCH] | 守门员拳击解围 / 鱼跃挡出 | 击退攻击 |
 * | [GK_THROW] | 守门员手抛球（短抛 / 长抛） | 强力攻击 |
 */
object FootballSounds {
    private const val MAX_PITCH = 2.0f
    private const val KICK_PITCH_MIN_SCALE = 0.95f
    private const val KICK_PITCH_MAX_SCALE = 1.08f

    private val FOOTBALL_KICK_EVENT = register("entity.football.kick")
    private val FOOTBALL_SMASH_KICK_EVENT = register("entity.football.smash_kick")
    private val FOOTBALL_PALM_EVENT = register("entity.football.palm")
    private val FOOTBALL_PERFECT_KICK_EVENT = register("entity.football.perfect_kick")

    /**
     * 单次播放的音量与音高配置。
     *
     * @param event 原版或自定义 [net.minecraft.sounds.SoundEvent]
     * @param source 播放声道（玩家动作通常用 [net.minecraft.sounds.SoundSource.PLAYERS]）
     * @param volume 音量（0~1+，原版脚步/攻击多在 0.3~1.0）
     * @param basePitch 基础音高（1.0 为原速）
     * @param pitchSpread 在 `[basePitch, basePitch + pitchSpread)` 内随机，0 表示固定音高
     */
    data class SoundSpec(
        val event: SoundEvent,
        val source: SoundSource,
        val volume: Float,
        val basePitch: Float,
        val pitchSpread: Float = 0f,
    ) {
        fun resolvePitch(random: RandomSource): Float =
            if (pitchSpread > 0f) {
                basePitch + random.nextFloat() * pitchSpread
            } else {
                basePitch
            }
    }

    /**
     * **踢球**：传球（R 短按）、射门（R 蓄力松开）、挑球（V）。
     *
     * 期望听感：清晰、有力的触球/击球，与力度较大的踢球动作匹配。
     * 替换建议：短促的踢球、击球或皮革球被踢中的音效。
     */
    val KICK: SoundSpec = SoundSpec(
        event = FOOTBALL_SMASH_KICK_EVENT,
        source = SoundSource.NEUTRAL,
        volume = 1.2f,
        basePitch = 1.35f,
        pitchSpread = 0.1f,
    )

    val PERFECT_KICK: SoundSpec = SoundSpec(
        event = FOOTBALL_PERFECT_KICK_EVENT,
        source = SoundSource.NEUTRAL,
        volume = 1.2f,
        basePitch = 1.35f,
        pitchSpread = 0.1f,
    )

    /**
     * **带球**：按住 Space 且移动时，按 [net.astrorbits.football.input.FootballInputConfig.DRIBBLE_SOUND_INTERVAL_TICKS] 周期性播放轻推触球声。
     *
     * 期望听感：短、轻、贴地，像脚与球频繁小触，不盖过踢球声。
     * 替换建议：软质球滚动、轻微触球或短脚步声。
     */
    val DRIBBLE: SoundSpec = SoundSpec(
        event = FOOTBALL_KICK_EVENT,
        source = SoundSource.NEUTRAL,
        volume = 1.0f,
        basePitch = 1.15f,
    )

    /**
     * **停球**：按 X 将球速归零。
     *
     * 期望听感：柔和、略闷，像球被脚「接住」而非弹开。
     * 替换建议：布料/软垫吸收、轻触停球。
     */
    val TRAP: SoundSpec = SoundSpec(
        event = FOOTBALL_PALM_EVENT,
        source = SoundSource.NEUTRAL,
        volume = 1.0f,
        basePitch = 0.85f,
    )

    /**
     * **放置足球**：使用足球物品在方块上/瞄准处生成 [Football] 实体。
     *
     * 期望听感：实体落位、轻微放置，与球体材质一致。
     * 替换建议：球落地、软质物品放置。
     */
    val FOOTBALL_PLACE: SoundSpec = SoundSpec(
        event = FOOTBALL_PALM_EVENT,
        source = SoundSource.NEUTRAL,
        volume = 0.8f,
        basePitch = 1.0f,
    )

    /**
     * **落地反弹**：球以足够速度砸向地面并弹起（[playGroundBounce] / [playCollisionBounces]）。
     *
     * 期望听感：短促、略闷的触地声，音量与音高随下落速度在 [playScaledImpact] 中缩放。
     * 替换建议：皮革球触地、短促弹跳或软质方块击中声。
     */
    val BOUNCE_GROUND: SoundSpec = SoundSpec(
        event = FOOTBALL_KICK_EVENT,
        source = SoundSource.BLOCKS,
        volume = 0.45f,
        basePitch = 0.85f,
        pitchSpread = 0.08f,
    )

    /**
     * **撞墙反弹**：球以足够速度击中竖直墙面并弹开（[playWallBounce] / [playCollisionBounces]）。
     *
     * 期望听感：更硬、更短，与 [BOUNCE_GROUND] 区分；音量与音高随撞墙速度缩放。
     * 替换建议：墙体撞击、短促回弹或略尖锐的击中声。
     */
    val BOUNCE_WALL: SoundSpec = SoundSpec(
        event = FOOTBALL_PALM_EVENT,
        source = SoundSource.BLOCKS,
        volume = 1.0f,
        basePitch = 1.05f,
        pitchSpread = 0.12f,
    )

    fun init() {
        // static init
    }

    fun play(level: Level, pos: BlockPos, spec: SoundSpec, random: RandomSource, volumeScale: Float = 1f) {
        val resolvedVolume = (spec.volume * volumeScale).coerceAtLeast(0f)
        val resolvedPitch = spec.resolvePitch(random).coerceAtMost(MAX_PITCH)
        level.playSound(
            null,
            pos,
            spec.event,
            spec.source,
            resolvedVolume,
            resolvedPitch,
        )
    }

    fun playKick(player: ServerPlayer, force: Double) {
        val normalizedForce = normalizeKickForce(force)
        val pitchScale = KICK_PITCH_MIN_SCALE +
            (KICK_PITCH_MAX_SCALE - KICK_PITCH_MIN_SCALE) * normalizedForce
        val pitch = (KICK.resolvePitch(player.random) * pitchScale).coerceAtMost(MAX_PITCH)
        if (force < SHOOT_FORCE_MAX) {
            player.level().playSound(
                null,
                player.blockPosition(),
                KICK.event,
                KICK.source,
                KICK.volume,
                pitch,
            )
        } else {
            player.level().playSound(
                null,
                player.blockPosition(),
                PERFECT_KICK.event,
                PERFECT_KICK.source,
                PERFECT_KICK.volume,
                pitch,
            )
        }

    }

    fun playDribble(player: ServerPlayer) {
        play(player.level(), player.blockPosition(), DRIBBLE, player.random)
    }

    fun playTrap(player: ServerPlayer) {
        play(player.level(), player.blockPosition(), TRAP, player.random)
    }

    /**
     * **守门员接球**：站立按 X 接住来球、鱼跃过程中成功摘球、按 X 将球放到脚下（[playGkCatch]）。
     *
     * 期望听感：柔和、稳定，像双手/身体把球「兜住」，与场员 [TRAP] 相近但略实。
     * 替换建议：手套触球、软垫接球或短促布料吸收声。
     */
    val GK_CATCH: SoundSpec = SoundSpec(
        event = FOOTBALL_PALM_EVENT,
        source = SoundSource.PLAYERS,
        volume = 0.70f,
        basePitch = 0.90f,
        pitchSpread = 0.06f,
    )

    /**
     * **守门员鱼跃**：按 R 短按触发扑救位移（[playGkDive]）。
     *
     * 期望听感：带风声的扑出动作，短而有动势，不盖过后续触球声。
     * 替换建议：扑地、滑行或短促挥臂/扫击类动作音效。
     */
    val GK_DIVE: SoundSpec = SoundSpec(
        event = SoundEvents.BAT_TAKEOFF,
        source = SoundSource.PLAYERS,
        volume = 0.62f,
        basePitch = 0.94f,
        pitchSpread = 0.05f,
    )

    /**
     * **守门员拳击解围**：按 V 将球击飞；鱼跃时对过快来球挡出（[playGkPunch]）。
     *
     * 期望听感：短、脆、有力，像单拳/单掌把球打离危险区域。
     * 替换建议：拳击手套击球、快速拍击或硬物短击声。
     */
    val GK_PUNCH: SoundSpec = SoundSpec(
        event = SoundEvents.PLAYER_ATTACK_KNOCKBACK,
        source = SoundSource.PLAYERS,
        volume = 0.6f,
        basePitch = 0.85f,
    )

    /**
     * **守门员手抛球**：持球时 R 短按短抛、R 蓄力长抛（[playGkThrow]）。
     *
     * 期望听感：比 [KICK] 略轻、略低，像手抛而非脚射，与脚法踢球区分。
     * 替换建议：上手抛球、短距离传球或略闷的出手声。
     */
    val GK_THROW: SoundSpec = SoundSpec(
        event = SoundEvents.PLAYER_ATTACK_STRONG,
        source = SoundSource.PLAYERS,
        volume = 0.5f,
        basePitch = 0.95f,
    )

    val SLIDE_TACKLE: SoundSpec = SoundSpec(
        event = SoundEvents.PLAYER_ATTACK_SWEEP,
        source = SoundSource.PLAYERS,
        volume = 0.62f,
        basePitch = 0.9f,
        pitchSpread = 0.08f,
    )

    fun playGkCatch(player: ServerPlayer, incomingSpeed: Double = 0.0) {
        val reference = kotlin.math.max(GoalkeeperInputConfig.GK_CATCH_MAX_SPEED, GoalkeeperInputConfig.GK_DIVE_CATCH_MAX_SPEED)
            .coerceAtLeast(0.1)
        val t = (incomingSpeed / reference).toFloat().coerceIn(0f, 1f)
        val volumeScale = 0.80f + 0.65f * t
        val pitch = (GK_CATCH.resolvePitch(player.random) * (0.90f + 0.24f * t)).coerceAtMost(MAX_PITCH)
        player.level().playSound(
            null,
            player.blockPosition(),
            GK_CATCH.event,
            GK_CATCH.source,
            (GK_CATCH.volume * volumeScale).coerceAtLeast(0f),
            pitch,
        )
    }

    fun playGkDive(player: ServerPlayer) {
        play(player.level(), player.blockPosition(), GK_DIVE, player.random)
    }

    fun playGkPunch(player: ServerPlayer) {
        play(player.level(), player.blockPosition(), GK_PUNCH, player.random)
    }

    fun playGkThrow(player: ServerPlayer) {
        play(player.level(), player.blockPosition(), GK_THROW, player.random)
    }

    fun playSlideTackle(player: ServerPlayer) {
        val belowPos = player.blockPosition().below()
        val blockState = player.level().getBlockState(belowPos)
        val soundType = blockState.soundType
        val breakEvent = soundType.breakSound
        if (breakEvent == null) {
            return
        }
        // 滑铲脚步声使用脚下方块破坏声，音量减半。
        player.level().playSound(
            null,
            player.blockPosition(),
            breakEvent,
            SoundSource.BLOCKS,
            soundType.volume * 0.5f,
            soundType.pitch,
        )
    }

    fun playSlideTackleContact(player: ServerPlayer, force: Double) {
        val normalizedForce = normalizeKickForce(force)
        val volumeScale = 0.9f + normalizedForce * 0.2f
        play(player.level(), player.blockPosition(), SLIDE_TACKLE, player.random, volumeScale)
    }

    fun playFootballPlace(level: Level, pos: BlockPos, random: RandomSource) {
        play(level, pos, FOOTBALL_PLACE, random)
    }

    fun playCollisionBounces(level: Level, pos: BlockPos, bounce: CollisionBounceResult, random: RandomSource) {
        if (bounce.hasGroundBounce) {
            playGroundBounce(level, pos, bounce.groundImpactSpeed, random)
        }
        if (bounce.hasWallBounce) {
            playWallBounce(level, pos, bounce.wallImpactSpeed, random)
        }
    }

    fun playGroundBounce(level: Level, pos: BlockPos, impactSpeed: Double, random: RandomSource) {
        if (impactSpeed < FootballPhysicsConfig.BOUNCE_SOUND_MIN_GROUND_VY) {
            return
        }
        playScaledImpact(level, pos, BOUNCE_GROUND, impactSpeed, FootballPhysicsConfig.BOUNCE_SOUND_MIN_GROUND_VY, 0.45, random)
    }

    fun playWallBounce(level: Level, pos: BlockPos, impactSpeed: Double, random: RandomSource) {
        if (impactSpeed < FootballPhysicsConfig.BOUNCE_SOUND_MIN_WALL_SPEED) {
            return
        }
        playScaledImpact(level, pos, BOUNCE_WALL, impactSpeed, FootballPhysicsConfig.BOUNCE_SOUND_MIN_WALL_SPEED, 0.35, random)
    }

    /** 按冲击速度缩放音量与音高，避免轻触也大声播放。 */
    private fun playScaledImpact(
        level: Level,
        pos: BlockPos,
        spec: SoundSpec,
        impactSpeed: Double,
        minSpeed: Double,
        referenceSpeed: Double,
        random: RandomSource,
    ) {
        val t = ((impactSpeed - minSpeed) / referenceSpeed).coerceIn(0.0, 1.0)
        val volume = spec.volume * (0.45f + 0.55f * t.toFloat())
        val pitch = (spec.resolvePitch(random) * (0.92f + 0.18f * t.toFloat())).coerceAtMost(MAX_PITCH)
        level.playSound(null, pos, spec.event, spec.source, volume, pitch)
    }

    private fun register(path: String): SoundEvent {
        val soundId = id(path)
        return Registry.register(
            BuiltInRegistries.SOUND_EVENT,
            soundId,
            SoundEvent.createVariableRangeEvent(soundId),
        )
    }

    private fun normalizeKickForce(force: Double): Float {
        val min = FootballInputConfig.CHIP_FORCE
        val max = SHOOT_FORCE_MAX * FootballInputConfig.SHOOT_SPRINT_BONUS
        if (max <= min) return 1f
        return ((force - min) / (max - min)).toFloat().coerceIn(0f, 1f)
    }

}