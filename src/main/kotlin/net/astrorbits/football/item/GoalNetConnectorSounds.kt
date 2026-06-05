package net.astrorbits.football.item

import net.astrorbits.football.NMBCTFootball
import net.astrorbits.football.block.GoalNetAnchorBlock
import net.astrorbits.football.GoalNetEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

/**
 * 球网连接器相关音效。
 *
 * ## 如何替换音效
 * 1. 在下方找到对应场景的 [SoundSpec]（或 `playXxx` 调用的常量），修改 [SoundSpec.event]。
 * 2. 按需调整 [SoundSpec.volume]、[SoundSpec.basePitch]、[SoundSpec.pitchSpread]。
 * 3. 若使用自定义 `.ogg` 文件：
 *    - 放入 `assets/nmbct-football/sounds/goal_net_connector/`（文件名见下方表格「建议文件」列）
 *    - 在 `assets/nmbct-football/sounds.json` 增加对应条目（键名见「SoundEvent ID」列）
 *    - 将 [SoundSpec.event] 改为 `register("item.goal_net_connector.xxx")` 注册的 [net.minecraft.sounds.SoundEvent]
 *
 * ## 音效一览
 * | 常量 | play 方法 | 触发场景 | 默认原版音效 | SoundEvent ID（自定义时用） | 建议文件 |
 * |------|-----------|----------|--------------|----------------------------|----------|
 * | [ANCHOR_SELECT] | [playAnchorSelect] | 成功选中锚点（未满 4 个） | 音符盒 Ping | `item.goal_net_connector.select` | `select.ogg` |
 * | [ANCHOR_DUPLICATE] | [playAnchorDuplicate] | 重复点击已选锚点 | 音符盒 Bass | `item.goal_net_connector.duplicate` | `duplicate.ogg` |
 * | [NET_CREATED] | [playNetCreated] | 四点合法，球网创建成功 | 经验球拾取 | `item.goal_net_connector.create` | `create.ogg` |
 * | [NET_FAIL] | [playNetFail] | 四点非法 / 生成失败 / 无效锚点 | 村民拒绝 | `item.goal_net_connector.fail` | `fail.ogg` |
 * | [SELECTION_CLEAR] | [playSelectionClear] | Shift+右键空气，清空选点 | 书页翻动 | `item.goal_net_connector.clear` | `clear.ogg` |
 * | [SLACK_INCREASE] | [playSlackIncrease] | 右键球网，提高松弛度 | 拉杆扳动 | `item.goal_net_connector.slack_up` | `slack_up.ogg` |
 * | [SLACK_DECREASE] | [playSlackDecrease] | Shift+右键球网，降低松弛度 | 拉杆扳动（低音） | `item.goal_net_connector.slack_down` | `slack_down.ogg` |
 * | [NET_DESTROY] | [playNetDestroy] | 左键球网，销毁球网 | 剪刀修剪 | `item.goal_net_connector.destroy` | `destroy.ogg` |
 *
 * ## 调用位置
 * - [GoalNetConnectorItem]：选点 / 清空 / 建网 / 失败 / 调松弛
 * - [net.astrorbits.football.GoalNetInteractions]：左键销毁球网
 */
object GoalNetConnectorSounds {
    private const val MAX_PITCH = 2.0f

    /**
     * 单次播放的音量与音高配置。
     *
     * @param event 原版或自定义 [net.minecraft.sounds.SoundEvent]
     * @param source 播放声道（玩家手持工具动作用 [net.minecraft.sounds.SoundSource.PLAYERS]）
     * @param volume 音量（0~1+）
     * @param basePitch 基础音高（1.0 为原速）
     * @param pitchSpread 在 `[basePitch, basePitch + pitchSpread)` 内随机；0 表示固定音高
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

    /** 选中锚点：短促、清晰，表示「记录了一个角点」。 */
    val ANCHOR_SELECT: SoundSpec = SoundSpec(
        event = SoundEvents.NOTE_BLOCK_PLING.value(),
        source = SoundSource.PLAYERS,
        volume = 0.55f,
        basePitch = 1.25f,
        pitchSpread = 0.08f,
    )

    /** 重复选点：低沉提示，表示「这个点已经选过了」。 */
    val ANCHOR_DUPLICATE: SoundSpec = SoundSpec(
        event = SoundEvents.NOTE_BLOCK_BASS.value(),
        source = SoundSource.PLAYERS,
        volume = 0.45f,
        basePitch = 0.85f,
    )

    /** 建网成功：明亮、短促的完成感。 */
    val NET_CREATED: SoundSpec = SoundSpec(
        event = SoundEvents.EXPERIENCE_ORB_PICKUP,
        source = SoundSource.PLAYERS,
        volume = 0.65f,
        basePitch = 1.05f,
        pitchSpread = 0.06f,
    )

    /** 建网失败：明确否定，与成功音区分。 */
    val NET_FAIL: SoundSpec = SoundSpec(
        event = SoundEvents.VILLAGER_NO,
        source = SoundSource.PLAYERS,
        volume = 0.55f,
        basePitch = 1.0f,
    )

    /** 清空选点：轻量撤销感。 */
    val SELECTION_CLEAR: SoundSpec = SoundSpec(
        event = SoundEvents.BOOK_PAGE_TURN,
        source = SoundSource.PLAYERS,
        volume = 0.5f,
        basePitch = 0.95f,
    )

    /** 提高松弛度：机械/拉紧感（拉杆向上）。 */
    val SLACK_INCREASE: SoundSpec = SoundSpec(
        event = SoundEvents.LEVER_CLICK,
        source = SoundSource.PLAYERS,
        volume = 0.45f,
        basePitch = 1.15f,
    )

    /** 降低松弛度：同拉杆，音高更低表示「放松」。 */
    val SLACK_DECREASE: SoundSpec = SoundSpec(
        event = SoundEvents.LEVER_CLICK,
        source = SoundSource.PLAYERS,
        volume = 0.45f,
        basePitch = 0.78f,
    )

    /** 销毁球网：剪切/断开感。 */
    val NET_DESTROY: SoundSpec = SoundSpec(
        event = SoundEvents.SHEARS_SNIP,
        source = SoundSource.PLAYERS,
        volume = 0.6f,
        basePitch = 1.0f,
        pitchSpread = 0.05f,
    )

    fun init() {
        // 若改用 register("item.goal_net_connector.xxx") 注册自定义 SoundEvent，在此 static init 中触发注册。
    }

    fun playAnchorSelect(player: ServerPlayer, anchorBlock: BlockPos) {
        playLocalAt(
            player.level(),
            player,
            resolveAnchorSoundPos(player.level(), anchorBlock),
            ANCHOR_SELECT,
            player.random,
        )
    }

    fun playAnchorDuplicate(player: ServerPlayer, anchorBlock: BlockPos) {
        playLocalAt(
            player.level(),
            player,
            resolveAnchorSoundPos(player.level(), anchorBlock),
            ANCHOR_DUPLICATE,
            player.random,
        )
    }

    fun playNetCreated(player: ServerPlayer) {
        playLocal(player.level(), player, player.blockPosition(), NET_CREATED, player.random)
    }

    fun playNetFail(player: ServerPlayer) {
        playLocal(player.level(), player, player.blockPosition(), NET_FAIL, player.random)
    }

    fun playSelectionClear(player: ServerPlayer) {
        playLocal(player.level(), player, player.blockPosition(), SELECTION_CLEAR, player.random)
    }

    fun playSlackIncrease(player: ServerPlayer, net: GoalNetEntity) {
        playLocalAt(player.level(), player, net.position(), SLACK_INCREASE, player.random)
    }

    fun playSlackDecrease(player: ServerPlayer, net: GoalNetEntity) {
        playLocalAt(player.level(), player, net.position(), SLACK_DECREASE, player.random)
    }

    fun playNetDestroy(player: ServerPlayer, net: GoalNetEntity) {
        playAt(player.level(), net.position(), NET_DESTROY, player.random)
    }

    fun play(level: Level, pos: BlockPos, spec: SoundSpec, random: RandomSource) {
        val pitch = spec.resolvePitch(random).coerceAtMost(MAX_PITCH)
        level.playSound(null, pos, spec.event, spec.source, spec.volume, pitch)
    }

    private fun playAt(level: Level, pos: Vec3, spec: SoundSpec, random: RandomSource) {
        val pitch = spec.resolvePitch(random).coerceAtMost(MAX_PITCH)
        level.playSound(null, pos.x, pos.y, pos.z, spec.event, spec.source, spec.volume, pitch)
    }

    private fun playLocal(
        level: Level,
        player: ServerPlayer,
        pos: BlockPos,
        spec: SoundSpec,
        random: RandomSource,
    ) {
        val pitch = spec.resolvePitch(random).coerceAtMost(MAX_PITCH)
        level.playSound(player, pos, spec.event, spec.source, spec.volume, pitch)
    }

    private fun playLocalAt(
        level: Level,
        player: ServerPlayer,
        pos: Vec3,
        spec: SoundSpec,
        random: RandomSource,
    ) {
        val pitch = spec.resolvePitch(random).coerceAtMost(MAX_PITCH)
        level.playSound(player, pos.x, pos.y, pos.z, spec.event, spec.source, spec.volume, pitch)
    }

    private fun resolveAnchorSoundPos(level: Level, anchorBlock: BlockPos): Vec3 {
        val state = level.getBlockState(anchorBlock)
        val block = state.block
        if (block is GoalNetAnchorBlock) {
            return block.getAnchorPos(anchorBlock, state)
        }
        return Vec3(
            anchorBlock.x + 0.5,
            anchorBlock.y + 0.5,
            anchorBlock.z + 0.5,
        )
    }

    /** 注册自定义 SoundEvent；路径相对 mod id，例如 `item.goal_net_connector.select`。 */
    @Suppress("unused")
    private fun register(path: String): SoundEvent {
        val soundId = NMBCTFootball.id(path)
        return Registry.register(
            BuiltInRegistries.SOUND_EVENT,
            soundId,
            SoundEvent.createVariableRangeEvent(soundId),
        )
    }
}