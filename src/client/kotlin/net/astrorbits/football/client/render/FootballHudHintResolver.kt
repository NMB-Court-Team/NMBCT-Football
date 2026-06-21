package net.astrorbits.football.client.render

import net.astrorbits.football.client.FootballOperabilityClient
import net.astrorbits.football.client.GoalKickPlacedKickerClient
import net.astrorbits.football.client.GoalkeeperHoldStealProtectionClient
import net.astrorbits.football.client.GoalkeeperStateClient
import net.astrorbits.football.client.SetPieceAreaViolationClient
import net.astrorbits.football.client.SetPieceClient
import net.astrorbits.football.client.key.FootballInputHandler
import net.astrorbits.football.client.key.FootballKeyBindings
import net.astrorbits.football.client.match.MatchStartClient
import net.astrorbits.football.match.GoalKickPhase
import net.astrorbits.football.match.MatchFieldAreaUtil
import net.astrorbits.football.match.SetPieceKind
import net.astrorbits.football.match.TeamSide
import net.minecraft.client.DeltaTracker
import net.minecraft.client.KeyMapping
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.chat.Component
import net.minecraft.world.level.Level

/**
 * 按屏幕位置汇总 HUD 提示：各 [Position] 对应一类固定布局，由 [resolve] 按当前比赛/定位球状态决定显示内容。
 *
 * | 位置 | 典型内容 |
 * |------|----------|
 * | [Position.TOP_RIGHT] | 键位操作面板 |
 * | [Position.BOTTOM_ABOVE_HOTBAR] | 开球倒计时、定位球角色提示 |
 * | [Position.BOTTOM_HOLD_LOCK] | 持球保护进度条 |
 * | [Position.CROSSHAIR_NEAR] | 抢球保护 |
 * | [Position.CROSSHAIR_FAR] | 区域违规警告 |
 */
object FootballHudHintResolver {

    enum class Position {
        /** 右上角键位面板（距边 8px） */
        TOP_RIGHT,
        /** 屏幕底部距底 128px：开球锁定 / 定位球角色 */
        BOTTOM_ABOVE_HOTBAR,
        /** 屏幕底部距底 92px：持球保护进度条 */
        BOTTOM_HOLD_LOCK,
        /** 准心下方 12px：抢球保护 */
        CROSSHAIR_NEAR,
        /** 准心下方 32px：区域违规（在抢球保护下方） */
        CROSSHAIR_FAR,
    }

    object Layout {
        const val TOP_RIGHT_MARGIN = 8
        const val BOTTOM_ABOVE_HOTBAR_OFFSET = 128
        const val BOTTOM_HOLD_LOCK_OFFSET = 92
        const val BOTTOM_LINE_GAP = 14
        const val CROSSHAIR_NEAR_OFFSET = 12f
        const val CROSSHAIR_FAR_OFFSET = 32f
    }

    data class TextLine(
        val translationKey: String,
        val color: Int,
        val args: List<Any> = emptyList(),
        val bold: Boolean = true,
        /** [args] 中下标在此集合内的字符串元素视为 nested translation key */
        val translatableArgIndices: Set<Int> = emptySet(),
    ) {
        fun format(): String {
            val resolvedArgs = args.mapIndexed { i, arg ->
                if (i in translatableArgIndices && arg is String) {
                    Component.translatable(arg).string
                } else {
                    arg
                }
            }.toTypedArray()
            return Component.translatable(translationKey, *resolvedArgs).string
        }
    }

    data class KeybindRow(
        val key: KeyMapping,
        val labelKey: String,
        val active: Boolean,
    )

    data class KeybindPanel(
        val titleKey: String,
        val titleActive: Boolean,
        val rows: List<KeybindRow>,
    )

    data class ProgressBarHint(
        val labelKey: String,
        val labelColor: Int,
        val ratio: Float,
        val timeText: String?,
        val timeColor: Int,
    )

    sealed interface Content {
        data class TextLines(val lines: List<TextLine>) : Content
        data class Keybinds(val panel: KeybindPanel) : Content
        data class ProgressBar(val bar: ProgressBarHint) : Content
    }

    fun resolve(position: Position, player: LocalPlayer, level: Level, delta: DeltaTracker): Content? =
        when (position) {
            Position.TOP_RIGHT -> resolveTopRight(player, level)?.let(Content::Keybinds)
            Position.BOTTOM_ABOVE_HOTBAR -> resolveBottomAboveHotbar(player)?.let(Content::TextLines)
            Position.BOTTOM_HOLD_LOCK -> resolveBottomHoldLock(player, delta)?.let(Content::ProgressBar)
            Position.CROSSHAIR_NEAR -> resolveCrosshairNear(player)?.let { Content.TextLines(listOf(it)) }
            Position.CROSSHAIR_FAR -> resolveCrosshairFar()?.let { Content.TextLines(listOf(it)) }
        }

    // ── TOP_RIGHT：键位面板 ──

    private fun resolveTopRight(player: LocalPlayer, level: Level): KeybindPanel? {
        val rows = if (FootballOperabilityClient.canShowFootballHints(player)) {
            buildFootballKeybindRows(player, level)
        } else {
            listOf(
                KeybindRow(
                    key = FootballKeyBindings.LOOK_AROUND,
                    labelKey = KEY_LOOK_AROUND,
                    active = true,
                ),
            )
        }
        return KeybindPanel(
            titleKey = KEY_TITLE,
            titleActive = rows.any { it.active },
            rows = rows,
        )
    }

    private fun buildFootballKeybindRows(player: LocalPlayer, level: Level): List<KeybindRow> {
        val holdingBall = GoalkeeperStateClient.isHoldingBall
        val actionRows = if (holdingBall) {
            listOf(
                FootballKeyBindings.GK_DIVE to KEY_GK_THROW,
                FootballKeyBindings.GK_CATCH to KEY_GK_DROP,
                FootballKeyBindings.BOOST_SPRINT to KEY_BOOST_SPRINT,
            )
        } else {
            listOf(
                FootballKeyBindings.KICK to KEY_PASS_SHOOT_STRIKE,
                FootballKeyBindings.DRIBBLE to KEY_DRIBBLE,
                FootballKeyBindings.TRAP to KEY_TRAP,
                FootballKeyBindings.CHIP to KEY_CHIP,
                FootballKeyBindings.GK_DIVE to KEY_GK_DIVE,
                FootballKeyBindings.GK_CATCH to KEY_GK_CATCH,
                FootballKeyBindings.SLIDE_TACKLE to KEY_SLIDE_TACKLE,
                FootballKeyBindings.BOOST_SPRINT to KEY_BOOST_SPRINT,
            )
        }.toMutableList()

        if (FootballInputHandler.isAnyChargeActive()) {
            actionRows += FootballKeyBindings.INTERRUPT_CHARGE to KEY_INTERRUPT_CHARGE
        }
        actionRows += FootballKeyBindings.LOOK_AROUND to KEY_LOOK_AROUND

        return actionRows.map { (key, labelKey) ->
            KeybindRow(
                key = key,
                labelKey = labelKey,
                active = FootballOperabilityClient.canUseFootballHint(player, level, key),
            )
        }
    }

    // ── BOTTOM_ABOVE_HOTBAR：开球锁定 / 定位球角色 ──

    private fun resolveBottomAboveHotbar(player: LocalPlayer): List<TextLine>? {
        if (player.isSpectator) return null

        if (MatchStartClient.isChoosing) {
            return listOf(
                TextLine(KEY_KICKOFF_WAITING, COLOR_COUNTDOWN),
            )
        }

        if (!isAwaitingSetPieceTouch()) return null

        val lines = mutableListOf<TextLine>()
        val inCountdown = MatchStartClient.countdownSeconds > 0 && MatchStartClient.startTimeMs > 0L
        if (inCountdown) {
            lines += TextLine(
                KEY_KICKOFF_COUNTDOWN,
                COLOR_COUNTDOWN,
                args = listOf(MatchStartClient.countdownSeconds.toString()),
            )
        }

        resolveSetPieceRoleHint(player)?.let { lines += it }
        return lines.takeIf { it.isNotEmpty() }
    }

    private fun isAwaitingSetPieceTouch(): Boolean {
        if (MatchStartClient.isChoosing) return false
        if (SetPieceClient.kind == SetPieceKind.GOAL_KICK &&
            SetPieceClient.goalKickPhase == GoalKickPhase.AWAITING_PA_EXIT
        ) {
            return true
        }
        // 开球锁已启动时始终显示倒计时；ballResetPending 仅用于操作层禁触，不应压制 HUD
        return MatchStartClient.startTimeMs > 0L && !MatchStartClient.kickoffTouched
    }

    private fun resolveSetPieceRoleHint(player: LocalPlayer): TextLine? {
        if (activeSetPieceKind() == SetPieceKind.GOAL_KICK &&
            SetPieceClient.goalKickPhase == GoalKickPhase.AWAITING_PA_EXIT
        ) {
            return if (isInGoalKickAwaitingExitPenaltyArea(player)) {
                TextLine(KEY_LEAVE_PENALTY_AREA_GOAL_KICK_AWAIT, COLOR_LEAVE_PA_HINT)
            } else {
                TextLine(KEY_WAIT_GOAL_KICK_KICK_OUT, COLOR_WAIT)
            }
        }
        if (activeSetPieceKind() == SetPieceKind.GOAL_KICK &&
            SetPieceClient.goalKickPhase == GoalKickPhase.PLACED
        ) {
            return if (GoalKickPlacedKickerClient.isPlacedKicker(player)) {
                TextLine(KEY_SERVE_GOAL_KICK_KICK_OUT, COLOR_SERVE)
            } else {
                TextLine(KEY_WAIT_GOAL_KICK_KICK_OUT, COLOR_WAIT)
            }
        }

        val restartTeam = activeRestartTeam() ?: return null
        val localTeam = FootballOperabilityClient.resolveLocalPlayerTeam(player) ?: MatchStartClient.playerTeam
        if (localTeam != restartTeam) {
            return TextLine(KEY_WAIT_OPPONENT, COLOR_WAIT)
        }
        if (isSetPieceServer(player, restartTeam)) {
            return TextLine(resolveServeKey(), COLOR_SERVE)
        }
        if (GoalkeeperStateClient.isGoalkeeper) {
            return null
        }
        return TextLine(KEY_WAIT_TEAMMATE, COLOR_WAIT)
    }

    private fun isInGoalKickAwaitingExitPenaltyArea(player: LocalPlayer): Boolean {
        if (SetPieceClient.kind != SetPieceKind.GOAL_KICK ||
            SetPieceClient.goalKickPhase != GoalKickPhase.AWAITING_PA_EXIT
        ) {
            return false
        }
        val areaSide = SetPieceClient.defendingSide ?: SetPieceClient.restartTeam ?: return false
        return MatchFieldAreaUtil.isPlayerInPenaltyArea(player, areaSide)
    }

    private fun activeRestartTeam() =
        SetPieceClient.restartTeam ?: MatchStartClient.pendingRestartTeam ?: MatchStartClient.kickoffTeam

    private fun activeSetPieceKind(): SetPieceKind = when {
        SetPieceClient.kind != SetPieceKind.NONE -> SetPieceClient.kind
        MatchStartClient.pendingSetPieceKind != null -> MatchStartClient.pendingSetPieceKind!!
        else -> SetPieceKind.NONE
    }

    private fun isSetPieceServer(player: LocalPlayer, restartTeam: TeamSide): Boolean {
        val localTeam = FootballOperabilityClient.resolveLocalPlayerTeam(player) ?: MatchStartClient.playerTeam
        val base = when (SetPieceClient.kind) {
            SetPieceKind.FREE_KICK -> player.uuid == SetPieceClient.freeKickTakerUuid
            SetPieceKind.CORNER_KICK -> player.uuid == SetPieceClient.cornerKickTakerUuid
            SetPieceKind.THROW_IN -> player.uuid == SetPieceClient.throwInTakerUuid
            SetPieceKind.PENALTY_KICK -> player.uuid == SetPieceClient.penaltyKickerUuid
            SetPieceKind.GOAL_KICK -> when (SetPieceClient.goalKickPhase) {
                GoalKickPhase.WAITING_PICKUP -> true
                GoalKickPhase.PLACING -> player.uuid == SetPieceClient.goalKickPickerUuid
                GoalKickPhase.PLACED -> GoalKickPlacedKickerClient.isPlacedKicker(player)
                else -> false
            }
            SetPieceKind.CENTER_KICKOFF -> localTeam == restartTeam
            SetPieceKind.NONE -> MatchStartClient.isKickoffTeam
            else -> false
        }
        if (!base) return false
        if (!GoalkeeperStateClient.isGoalkeeper) return true
        return isGoalkeeperSetPieceServer(player)
    }

    /** 门将仅在明确担任该定位球主罚/捡球/摆球者时显示发球提示（球门球捡球阶段发球方任一球员均可）。 */
    private fun isGoalkeeperSetPieceServer(player: LocalPlayer): Boolean = when (activeSetPieceKind()) {
        SetPieceKind.GOAL_KICK -> when (SetPieceClient.goalKickPhase) {
            GoalKickPhase.WAITING_PICKUP, null -> true
            GoalKickPhase.PLACING -> player.uuid == SetPieceClient.goalKickPickerUuid
            GoalKickPhase.PLACED -> GoalKickPlacedKickerClient.isPlacedKicker(player)
            else -> false
        }
        SetPieceKind.FREE_KICK -> player.uuid == SetPieceClient.freeKickTakerUuid
        SetPieceKind.CORNER_KICK -> player.uuid == SetPieceClient.cornerKickTakerUuid
        SetPieceKind.THROW_IN -> player.uuid == SetPieceClient.throwInTakerUuid
        SetPieceKind.PENALTY_KICK -> player.uuid == SetPieceClient.penaltyKickerUuid
        else -> false
    }

    private fun resolveServeKey(): String = when (activeSetPieceKind()) {
        SetPieceKind.FREE_KICK -> KEY_SERVE_FREE_KICK
        SetPieceKind.CORNER_KICK -> KEY_SERVE_CORNER_KICK
        SetPieceKind.THROW_IN -> KEY_SERVE_THROW_IN
        SetPieceKind.PENALTY_KICK -> KEY_SERVE_PENALTY
        SetPieceKind.GOAL_KICK -> when {
            SetPieceClient.goalKickPhase == GoalKickPhase.WAITING_PICKUP ||
                SetPieceClient.goalKickPhase == null -> KEY_SERVE_GOAL_KICK_PICKUP
            SetPieceClient.goalKickPhase == GoalKickPhase.PLACING -> KEY_SERVE_GOAL_KICK_PLACE
            else -> KEY_SERVE_GOAL_KICK
        }
        SetPieceKind.CENTER_KICKOFF, SetPieceKind.NONE -> KEY_SERVE_KICKOFF
        else -> KEY_SERVE_KICKOFF
    }

    // ── BOTTOM_HOLD_LOCK：持球保护进度条 ──

    private fun resolveBottomHoldLock(player: LocalPlayer, delta: DeltaTracker): ProgressBarHint? {
        if (!GoalkeeperStateClient.isGoalkeeper && !GoalkeeperStateClient.isHoldingBall) {
            return null
        }
        val ratio = GoalkeeperStateClient.liveHoldReleaseLockRatio(delta.getGameTimeDeltaTicks())
        if (ratio <= 0f) return null

        val secondsLeft = ratio * GoalkeeperStateClient.holdReleaseLockTotalTicks() / 20f
        return ProgressBarHint(
            labelKey = KEY_GK_HOLD_LOCK,
            labelColor = COLOR_GK_HOLD_LOCK_LABEL,
            ratio = ratio,
            timeText = String.format("%.1fs", secondsLeft),
            timeColor = COLOR_GK_HOLD_LOCK_TIME,
        )
    }

    // ── CROSSHAIR_NEAR：抢球保护 ──

    private fun resolveCrosshairNear(player: LocalPlayer): TextLine? {
        if (!GoalkeeperHoldStealProtectionClient.shouldShowHud(player)) return null
        return TextLine(KEY_GK_HOLD_STEAL_PROTECTION, COLOR_GK_STEAL_PROTECTION, bold = false)
    }

    // ── CROSSHAIR_FAR：区域违规 ──

    private fun resolveCrosshairFar(): TextLine? {
        if (!SetPieceAreaViolationClient.isActive()) return null
        return TextLine(
            KEY_AREA_VIOLATION,
            COLOR_AREA_VIOLATION,
            args = listOf(
                SetPieceAreaViolationClient.areaNameKey,
                SetPieceAreaViolationClient.secondsRemaining,
            ),
            translatableArgIndices = setOf(0),
            bold = false,
        )
    }

    // ── 颜色 ──

    private const val COLOR_COUNTDOWN = 0xFFFF5555.toInt()
    private const val COLOR_WAIT = 0xFFFFAA00.toInt()
    private const val COLOR_LEAVE_PA_HINT = 0xFFFFCC66.toInt()
    private const val COLOR_SERVE = 0xFF55FF55.toInt()
    private const val COLOR_GK_HOLD_LOCK_LABEL = 0xFF90CAF9.toInt()
    private const val COLOR_GK_HOLD_LOCK_TIME = 0xFFCCCCCC.toInt()
    private const val COLOR_GK_STEAL_PROTECTION = 0xFF42A5F5.toInt()
    private const val COLOR_AREA_VIOLATION = 0xFFFF4444.toInt()

    // ── 语言键：键位面板 ──

    private const val KEY_TITLE = "hud.nmbct-football.hint.title"
    private const val KEY_LOOK_AROUND = "hud.nmbct-football.hint.look_around"
    private const val KEY_PASS_SHOOT_STRIKE = "hud.nmbct-football.hint.pass_shoot_strike"
    private const val KEY_DRIBBLE = "hud.nmbct-football.hint.dribble"
    private const val KEY_SLIDE_TACKLE = "hud.nmbct-football.hint.slide_tackle"
    private const val KEY_BOOST_SPRINT = "hud.nmbct-football.hint.boost_sprint"
    private const val KEY_TRAP = "hud.nmbct-football.hint.trap"
    private const val KEY_CHIP = "hud.nmbct-football.hint.chip"
    private const val KEY_GK_DIVE = "hud.nmbct-football.hint.gk_dive"
    private const val KEY_GK_THROW = "hud.nmbct-football.hint.gk_throw"
    private const val KEY_GK_CATCH = "hud.nmbct-football.hint.gk_catch"
    private const val KEY_GK_DROP = "hud.nmbct-football.hint.gk_drop"
    private const val KEY_INTERRUPT_CHARGE = "hud.nmbct-football.hint.interrupt_charge"

    // ── 语言键：底部提示 ──

    private const val KEY_KICKOFF_WAITING = "hud.nmbct-football.kickoff_lock.waiting"
    private const val KEY_KICKOFF_COUNTDOWN = "hud.nmbct-football.kickoff_lock.countdown"
    private const val KEY_WAIT_OPPONENT = "hud.nmbct-football.set_piece.role.wait_opponent"
    private const val KEY_WAIT_TEAMMATE = "hud.nmbct-football.set_piece.role.wait_teammate"
    private const val KEY_SERVE_KICKOFF = "hud.nmbct-football.set_piece.role.serve.kickoff"
    private const val KEY_SERVE_FREE_KICK = "hud.nmbct-football.set_piece.role.serve.free_kick"
    private const val KEY_SERVE_CORNER_KICK = "hud.nmbct-football.set_piece.role.serve.corner_kick"
    private const val KEY_SERVE_THROW_IN = "hud.nmbct-football.set_piece.role.serve.throw_in"
    private const val KEY_SERVE_PENALTY = "hud.nmbct-football.set_piece.role.serve.penalty"
    private const val KEY_SERVE_GOAL_KICK = "hud.nmbct-football.set_piece.role.serve.goal_kick"
    private const val KEY_SERVE_GOAL_KICK_PICKUP = "hud.nmbct-football.set_piece.role.serve.goal_kick_pickup"
    private const val KEY_SERVE_GOAL_KICK_PLACE = "hud.nmbct-football.set_piece.role.serve.goal_kick_place"
    private const val KEY_SERVE_GOAL_KICK_KICK_OUT = "hud.nmbct-football.set_piece.role.serve.goal_kick_kick_out"
    private const val KEY_WAIT_GOAL_KICK_KICK_OUT = "hud.nmbct-football.set_piece.role.wait.goal_kick_kick_out"
    private const val KEY_LEAVE_PENALTY_AREA_GOAL_KICK_AWAIT =
        "hud.nmbct-football.set_piece.role.leave_penalty_area.goal_kick_await"

    // ── 语言键：准心 / 进度条 ──

    private const val KEY_GK_HOLD_LOCK = "hud.nmbct-football.gk_hold_lock"
    private const val KEY_GK_HOLD_STEAL_PROTECTION = "hud.nmbct-football.gk_hold_steal_protection"
    private const val KEY_AREA_VIOLATION = "hud.nmbct-football.area_violation.warning"
}
