package net.astrorbits.football.match

import net.astrorbits.football.Football
import net.astrorbits.football.input.GoalkeeperHoldActionPermissions
import net.astrorbits.football.network.FootballActionType
import net.astrorbits.football.network.FootballNetworking
import net.astrorbits.football.physics.FootballPhysicsConfig
import net.astrorbits.football.util.GoalkeeperUtil
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.math.floor
import kotlin.math.sqrt

object ThrowInSetPieceFlow {
    private val anchorPositions = mutableMapOf<java.util.UUID, Vec3>()
    private const val POSITION_SNAP_THRESHOLD_SQ = 0.01

    data class GroundSpot(
        val ballPos: Vec3,
        val takerFeetY: Double,
    )

    /** 将出界交点落到场地地面：球心贴地，主罚员脚踩地面。 */
    fun resolveGroundSpot(level: ServerLevel, approximatePos: Vec3): GroundSpot {
        val groundY = fieldGroundY(level, approximatePos.x, approximatePos.z)
        val ballY = groundY + FootballPhysicsConfig.RADIUS
        return GroundSpot(
            ballPos = Vec3(approximatePos.x, ballY, approximatePos.z),
            takerFeetY = groundY,
        )
    }

    fun resolveGroundBallPosition(level: ServerLevel, approximatePos: Vec3): Vec3 =
        resolveGroundSpot(level, approximatePos).ballPos

    fun begin(
        level: ServerLevel,
        restartTeam: TeamSide,
        ballPos: Vec3,
        preferredTakerUuid: UUID? = null,
    ) {
        val server = level.server
        val spot = resolveGroundSpot(level, ballPos)
        val groundBallPos = spot.ballPos
        val taker = resolveThrowInTaker(server, restartTeam, groundBallPos, preferredTakerUuid) ?: return
        val football = findFootballNear(level, groundBallPos) ?: return

        football.setPos(groundBallPos.x, groundBallPos.y, groundBallPos.z)

        taker.teleportTo(
            level,
            groundBallPos.x,
            spot.takerFeetY,
            groundBallPos.z,
            java.util.HashSet(),
            taker.yRot,
            taker.xRot,
            false,
        )
        taker.setDeltaMovement(Vec3.ZERO)

        GoalkeeperHoldActionPermissions.setAll(taker, catch = false, drop = false, throwBall = true)

        SetPieceState.begin(
            SetPieceContext(
                kind = SetPieceKind.THROW_IN,
                restartTeam = restartTeam,
                ballPos = groundBallPos,
                throwInTakerUuid = taker.uuid,
            ),
        )
        SetPieceState.active?.let { SetPiecePlayerRepositioner.repositionInitialViolators(server, it) }

        anchorPositions[taker.uuid] = Vec3(taker.x, taker.y, taker.z)
        ensureTakerHoldingBall(taker, football)
        FootballNetworking.broadcastSetPieceState(server)
    }

    fun onBallThrown(player: ServerPlayer) {
        val ctx = SetPieceState.active ?: return
        if (ctx.kind != SetPieceKind.THROW_IN) return
        if (player.uuid != ctx.throwInTakerUuid) return
        clear(player.level().server)
        MatchState.notifyKickoffBallTouched(player)
    }

    fun tickMovementFreeze(server: MinecraftServer) {
        val ctx = SetPieceState.active ?: return
        if (ctx.kind != SetPieceKind.THROW_IN) return
        val takerUuid = ctx.throwInTakerUuid ?: return
        val taker = server.playerList.getPlayer(takerUuid) ?: return
        val level = taker.level() as? ServerLevel ?: return
        val anchor = anchorPositions[takerUuid] ?: ctx.ballPos

        taker.setDeltaMovement(Vec3.ZERO)

        val dx = taker.x - anchor.x
        val dy = taker.y - anchor.y
        val dz = taker.z - anchor.z
        if (dx * dx + dy * dy + dz * dz > POSITION_SNAP_THRESHOLD_SQ) {
            taker.setPos(anchor.x, anchor.y, anchor.z)
        }

        val football = GoalkeeperUtil.findHeldFootball(taker) ?: findFootballNear(level, anchor)
        if (football != null) {
            ensureTakerHoldingBall(taker, football)
        }
    }

    fun isMovementFrozen(player: ServerPlayer): Boolean {
        val ctx = SetPieceState.active ?: return false
        return ctx.kind == SetPieceKind.THROW_IN && player.uuid == ctx.throwInTakerUuid
    }

    /** 界外球主罚员在开球锁定倒计时结束后方可右键抛球（短按/长按蓄力均可）。 */
    fun allowsThrowAction(player: ServerPlayer, action: FootballActionType?): Boolean {
        if (!isMovementFrozen(player)) return false
        if (action != FootballActionType.GK_THROW_SHORT && action != FootballActionType.GK_THROW_LONG) {
            return false
        }
        return !MatchState.isKickoffCountdownActive()
    }

    /** 出界瞬间：距出界点最近的在线非门将队员。 */
    fun pickThrowInTaker(server: MinecraftServer, restartTeam: TeamSide, outPos: Vec3): ServerPlayer? {
        val roster = when (restartTeam) {
            TeamSide.A -> MatchState.teamAPlayers
            TeamSide.B -> MatchState.teamBPlayers
        }
        return roster.mapNotNull { server.playerList.getPlayer(it) }
            .filter { MatchParticipation.isParticipating(it) }
            .filter { !PlayerRoleState.isDesignatedGoalkeeper(it) }
            .minByOrNull { distanceToOutPoint(it, outPos) }
    }

    fun clear(server: MinecraftServer?) {
        if (SetPieceState.active?.kind != SetPieceKind.THROW_IN) return
        val takerUuid = SetPieceState.active?.throwInTakerUuid
        if (takerUuid != null) {
            anchorPositions.remove(takerUuid)
            server?.playerList?.getPlayer(takerUuid)?.let {
                GoalkeeperHoldActionPermissions.resetToDefaults(it)
            }
        }
        SetPieceState.clear()
        server?.let { FootballNetworking.broadcastSetPieceState(it) }
    }

    private fun resolveThrowInTaker(
        server: MinecraftServer,
        restartTeam: TeamSide,
        outPos: Vec3,
        preferredTakerUuid: UUID?,
    ): ServerPlayer? {
        preferredTakerUuid?.let { uuid ->
            server.playerList.getPlayer(uuid)?.let { player ->
                if (MatchParticipation.isParticipating(player) &&
                    MatchState.getPlayerTeam(player.uuid) == restartTeam &&
                    !PlayerRoleState.isDesignatedGoalkeeper(player)
                ) {
                    return player
                }
            }
        }
        return pickThrowInTaker(server, restartTeam, outPos)
    }

    private fun distanceToOutPoint(player: ServerPlayer, outPos: Vec3): Double {
        val dx = player.x - outPos.x
        val dz = player.z - outPos.z
        return sqrt(dx * dx + dz * dz)
    }

    private fun fieldGroundY(level: ServerLevel, x: Double, z: Double): Double {
        val configured = MatchConfigHolder.current.kickOff.y
        val surfaceY = level.getHeight(
            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            floor(x).toInt(),
            floor(z).toInt(),
        ).toDouble()
        return maxOf(configured, surfaceY)
    }

    private fun findFootballNear(level: ServerLevel, pos: Vec3): Football? {
        val box = AABB.ofSize(pos, 4.0, 4.0, 4.0)
        return level.getEntitiesOfClass(Football::class.java, box).minByOrNull { it.distanceToSqr(pos) }
    }

    private fun ensureTakerHoldingBall(taker: ServerPlayer, football: Football) {
        if (!football.isHeldBy(taker)) {
            football.enterHold(taker)
        } else {
            football.syncHeldPose(taker)
        }
    }
}
