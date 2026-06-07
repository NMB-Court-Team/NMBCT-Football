package net.astrorbits.football.match

import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import kotlin.math.atan2
import kotlin.math.sqrt

/** 定位球主罚员站位：球门球门将、角球主罚员等。 */
object SetPieceTakerPlacement {
    /** 门将站位：沿球→球门方向的比例（0=球心，1=球门中心）。 */
    private const val GOAL_KICK_KEEPER_GOALWARD_RATIO = 0.4
    private const val CORNER_TAKER_OUTWARD_OFFSET = 1.0

    fun resolveGoalkeeper(server: MinecraftServer, team: TeamSide): ServerPlayer? {
        val uuid = when (team) {
            TeamSide.A -> PlayerRoleState.teamAGoalkeeper
            TeamSide.B -> PlayerRoleState.teamBGoalkeeper
        } ?: return null
        val player = server.playerList.getPlayer(uuid) ?: return null
        if (!MatchParticipation.isParticipating(player)) return null
        if (MatchState.getPlayerTeam(player.uuid) != team) return null
        return player
    }

    /** 门将站在足球与球门之间，面向场内。 */
    fun goalKickKeeperStand(restartTeam: TeamSide, ballPos: Vec3): Pair<Vec3, Float> {
        val goal = MatchFieldAreaUtil.goalForSide(MatchConfigHolder.current, restartTeam)
        val goalCenter = goal.goalCenter()
        val towardField = goal.penaltyKickBehindBall()
        val ratio = GOAL_KICK_KEEPER_GOALWARD_RATIO
        val pos = Vec3(
            ballPos.x + (goalCenter.x - ballPos.x) * ratio,
            ballPos.y,
            ballPos.z + (goalCenter.z - ballPos.z) * ratio,
        )
        return pos to horizontalYaw(towardField)
    }

    private const val FREE_KICK_TAKER_OFFSET = 1.5

    /** 任意球主罚员站在球后方（背向防守方球门），面向球。 */
    fun freeKickTakerStand(ballPos: Vec3, defendingSide: TeamSide): Pair<Vec3, Float> {
        val goal = MatchFieldAreaUtil.goalForSide(MatchConfigHolder.current, defendingSide)
        val towardGoal = goal.penaltyKickBehindBall().scale(-1.0)
        val awayFromGoal = horizontalUnit(-towardGoal.x, -towardGoal.z)
        val pos = Vec3(
            ballPos.x + awayFromGoal.x * FREE_KICK_TAKER_OFFSET,
            ballPos.y,
            ballPos.z + awayFromGoal.z * FREE_KICK_TAKER_OFFSET,
        )
        return pos to horizontalYaw(Vec3(towardGoal.x, 0.0, towardGoal.z))
    }

    /** 角球主罚员站在角球点略靠外（远离场地中心一侧），面向场内。 */
    fun cornerTakerStand(cornerPos: Vec3): Pair<Vec3, Float> {
        val center = MatchConfigHolder.current.kickOff
        val outward = horizontalUnit(cornerPos.x - center.x, cornerPos.z - center.z)
        val pos = Vec3(
            cornerPos.x + outward.x * CORNER_TAKER_OUTWARD_OFFSET,
            cornerPos.y,
            cornerPos.z + outward.z * CORNER_TAKER_OUTWARD_OFFSET,
        )
        val inward = horizontalUnit(center.x - cornerPos.x, center.z - cornerPos.z)
        return pos to horizontalYaw(Vec3(inward.x, 0.0, inward.z))
    }

    fun teleportPlayer(level: ServerLevel, player: ServerPlayer, pos: Vec3, yaw: Float) {
        player.teleportTo(level, pos.x, pos.y, pos.z, java.util.HashSet(), yaw, 0f, false)
        player.setDeltaMovement(Vec3.ZERO)
    }

    private fun horizontalYaw(direction: Vec3): Float =
        Math.toDegrees(atan2(-direction.x, direction.z)).toFloat()

    private fun horizontalUnit(dx: Double, dz: Double): Vec3 {
        val lenSq = dx * dx + dz * dz
        if (lenSq < 1.0e-8) {
            return Vec3(1.0, 0.0, 0.0)
        }
        val len = sqrt(lenSq)
        return Vec3(dx / len, 0.0, dz / len)
    }
}
