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

    private data class State(
        val restartTeam: TeamSide,
        val takerUuid: UUID,
        val sourceKind: SetPieceKind,
        var phase: Phase,
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
     * 球权/触球事件。返回 true 表示已判罚间接任意球。
     */
    fun onBallTouched(player: ServerPlayer, ballPos: Vec3, level: ServerLevel): Boolean {
        val current = state ?: return false
        val team = MatchState.getPlayerTeam(player.uuid) ?: return false

        when (current.phase) {
            Phase.OPENING -> {
                if (player.uuid == current.takerUuid) {
                    current.phase = Phase.AWAITING_OTHER
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
