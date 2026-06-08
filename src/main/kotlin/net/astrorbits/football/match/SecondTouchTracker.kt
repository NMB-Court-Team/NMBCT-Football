package net.astrorbits.football.match

import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import java.util.*

/**
 * 定位球开球后的二次触球监测：主罚在他人触球前再次触球 → 对方间接任意球。
 */
object SecondTouchTracker {
    private enum class Phase {
        /** 等待主罚完成开球触球（不计犯规）。 */
        OPENING,
        /** 开球已完成，等待其他球员触球。 */
        AWAITING_OTHER,
    }

    /** 主罚触球后短暂宽限，避免同一开球动作的连续触球（推球分离、带球等）误判二次触球。 */
    private const val TAKER_GRACE_TICKS = 5L

    private data class State(
        val restartTeam: TeamSide,
        val takerUuid: UUID,
        val sourceKind: SetPieceKind,
        var phase: Phase,
        /** 宽限截止 tick（含）；此时间前主罚再次触球不计二次触球。 */
        var takerGraceUntilTick: Long = 0L,
    )

    private var state: State? = null

    fun isActive(): Boolean = state != null

    fun clear() {
        state = null
    }

    fun begin(restartTeam: TeamSide, takerUuid: UUID, sourceKind: SetPieceKind) {
        state = State(restartTeam, takerUuid, sourceKind, Phase.OPENING)
    }

    /**
     * 主罚已完成开球触球后启动监测（如球门球：踢球在前、球出大禁区后比赛才恢复）。
     */
    fun beginAfterOpeningPlay(
        restartTeam: TeamSide,
        takerUuid: UUID,
        sourceKind: SetPieceKind,
        resumeGameTick: Long,
    ) {
        state = State(
            restartTeam = restartTeam,
            takerUuid = takerUuid,
            sourceKind = sourceKind,
            phase = Phase.AWAITING_OTHER,
            takerGraceUntilTick = resumeGameTick + TAKER_GRACE_TICKS,
        )
    }

    private fun grantTakerGrace(current: State, gameTick: Long) {
        current.takerGraceUntilTick = gameTick + TAKER_GRACE_TICKS
    }

    private fun isWithinTakerGrace(current: State, player: ServerPlayer, gameTick: Long): Boolean =
        player.uuid == current.takerUuid && gameTick <= current.takerGraceUntilTick

    /**
     * 球权/触球事件。返回 true 表示已判罚间接任意球。
     */
    fun onBallTouched(player: ServerPlayer, ballPos: Vec3, level: ServerLevel): Boolean {
        val current = state ?: return false
        val team = MatchState.getPlayerTeam(player.uuid) ?: return false

        when (current.phase) {
            Phase.OPENING -> {
                if (player.uuid == current.takerUuid) {
                    current.phase = Phase.AWAITING_OTHER
                    grantTakerGrace(current, level.gameTime)
                    return false
                }
                clear()
                return false
            }
            Phase.AWAITING_OTHER -> {
                if (team != current.restartTeam) {
                    clear()
                    return false
                }
                if (isWithinTakerGrace(current, player, level.gameTime)) {
                    return false
                }
                if (player.uuid == current.takerUuid) {
                    val foulingTeam = current.restartTeam
                    clear()
                    return FreeKickAwards.awardIndirectFreeKick(
                        level,
                        ballPos,
                        foulingTeam,
                        player.uuid,
                        FreeKickFoulReason.SECOND_TOUCH,
                    )
                }
                clear()
                return false
            }
        }
    }

    fun beginFromKickoffTouch(player: ServerPlayer) {
        val ctx = SetPieceState.active
        val restartTeam = MatchState.kickoffTeam ?: ctx?.restartTeam ?: return
        val kind = ctx?.kind ?: SetPieceKind.CENTER_KICKOFF
        val takerUuid = when (kind) {
            SetPieceKind.GOAL_KICK -> ctx?.goalKickPickerUuid ?: player.uuid
            SetPieceKind.CORNER_KICK -> ctx?.cornerKickTakerUuid ?: player.uuid
            SetPieceKind.FREE_KICK -> ctx?.freeKickTakerUuid ?: player.uuid
            SetPieceKind.CENTER_KICKOFF -> player.uuid
            else -> player.uuid
        }
        begin(restartTeam, takerUuid, kind)
    }
}
