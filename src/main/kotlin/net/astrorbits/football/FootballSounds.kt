package net.astrorbits.football

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.minecraft.world.level.Level

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
 */
object FootballSounds {
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
        event = SoundEvents.PLAYER_ATTACK_STRONG,
        source = SoundSource.PLAYERS,
        volume = 0.55f,
        basePitch = 1.05f,
        pitchSpread = 0.1f,
    )

    /**
     * **带球**：按住 Space 且移动时，周期性轻推足球。
     *
     * 期望听感：短、轻、贴地，像脚与球频繁小触，不盖过踢球声。
     * 替换建议：软质球滚动、轻微触球或短脚步声。
     */
    val DRIBBLE: SoundSpec = SoundSpec(
        event = SoundEvents.SLIME_BLOCK_STEP,
        source = SoundSource.PLAYERS,
        volume = 0.35f,
        basePitch = 1.15f,
    )

    /**
     * **停球**：按 X 将球速归零。
     *
     * 期望听感：柔和、略闷，像球被脚「接住」而非弹开。
     * 替换建议：布料/软垫吸收、轻触停球。
     */
    val TRAP: SoundSpec = SoundSpec(
        event = SoundEvents.WOOL_STEP,
        source = SoundSource.PLAYERS,
        volume = 0.45f,
        basePitch = 0.85f,
    )

    /**
     * **放置足球**：使用足球物品在方块上/瞄准处生成 [Football] 实体。
     *
     * 期望听感：实体落位、轻微放置，与球体材质一致。
     * 替换建议：球落地、软质物品放置。
     */
    val FOOTBALL_PLACE: SoundSpec = SoundSpec(
        event = SoundEvents.SLIME_BLOCK_PLACE,
        source = SoundSource.BLOCKS,
        volume = 0.8f,
        basePitch = 1.0f,
    )

    fun play(level: Level, pos: BlockPos, spec: SoundSpec, random: RandomSource) {
        level.playSound(
            null,
            pos,
            spec.event,
            spec.source,
            spec.volume,
            spec.resolvePitch(random),
        )
    }

    fun playKick(player: ServerPlayer) {
        play(player.level(), player.blockPosition(), KICK, player.random)
    }

    fun playDribble(player: ServerPlayer) {
        play(player.level(), player.blockPosition(), DRIBBLE, player.random)
    }

    fun playTrap(player: ServerPlayer) {
        play(player.level(), player.blockPosition(), TRAP, player.random)
    }

    fun playFootballPlace(level: Level, pos: BlockPos, random: RandomSource) {
        play(level, pos, FOOTBALL_PLACE, random)
    }
}