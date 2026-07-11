package net.astrorbits.football.match

import net.astrorbits.football.Football
import net.astrorbits.football.input.GoalkeeperHoldActionPermissions
import net.astrorbits.football.network.FootballActionType
import net.astrorbits.football.network.FootballNetworking
import net.astrorbits.football.physics.FootballPhysicsConfig
import net.astrorbits.football.util.GoalkeeperUtil
import net.astrorbits.football.util.Vec3Math
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sqrt

object ThrowInSetPieceFlow {
    private val anchorPositions = mutableMapOf<java.util.UUID, Vec3>()
    private var foulThrowWatch: FoulThrowWatch? = null
    private const val POSITION_SNAP_THRESHOLD_SQ = 0.01
    /** 抛球后若球仍越过边线出界，在此 tick 数内改判同位置重掷。 */
    private const val FOUL_THROW_WATCH_TICKS = 80L
    /** 水平抛球方向与场内法向点积低于此值视为往外扔。 */
    private const val INWARD_THROW_MIN_DOT = 0.08

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
        football.setDeltaMovement(Vec3.ZERO)

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
        foulThrowWatch = FoulThrowWatch(
            spot = ctx.ballPos,
            restartTeam = ctx.restartTeam,
            takerUuid = player.uuid,
            expireTick = player.level().gameTime + FOUL_THROW_WATCH_TICKS,
        )
        SecondTouchTracker.beginAfterThrowIn(ctx.restartTeam, player.uuid)
        clear(player.level().server, preserveFoulThrowWatch = true)
        MatchState.notifyKickoffBallTouched(player)
    }

    /** 界外球抛球方向须朝向场内；往外扔则同位置改判对方发球（球不离开双手）。 */
    fun isInwardThrow(lookYaw: Float, lookPitch: Float): Boolean {
        val ctx = SetPieceState.active ?: return true
        if (ctx.kind != SetPieceKind.THROW_IN) return true
        val look = Vec3.directionFromRotation(lookPitch, lookYaw)
        val horizontal = Vec3Math.horizontal(look)
        if (horizontal.lengthSqr() < 1.0e-6) return false
        val dir = Vec3Math.normalizeSafe(horizontal)
        val sideline = sidelineAt(ctx.ballPos) ?: return true
        val facing = sideline.facing()
        val inward = dir.x * facing.x + dir.z * facing.z
        return inward >= INWARD_THROW_MIN_DOT
    }

    fun onFoulThrow(player: ServerPlayer) {
        val ctx = SetPieceState.active ?: return
        if (ctx.kind != SetPieceKind.THROW_IN) return
        if (player.uuid != ctx.throwInTakerUuid) return
        awardOpponentThrowInAtSameSpot(
            player.level() as ServerLevel,
            ctx.ballPos,
            ctx.restartTeam,
            releaseHeldBall = true,
            lastTouchUuid = player.uuid,
        )
    }

    /**
     * 界外球掷出后球仍越过同一边线出界：同位置改判对方发球。
     * @return 已按犯规重掷处理
     */
    fun tryRetakeFoulThrowSidelineOut(
        level: ServerLevel,
        server: MinecraftServer,
        sideline: SidelineConfig,
        lastTouchUuid: UUID?,
    ): Boolean {
        val watch = foulThrowWatch ?: return false
        if (level.gameTime > watch.expireTick) {
            foulThrowWatch = null
            return false
        }
        if (lastTouchUuid != watch.takerUuid) return false
        val spotSideline = sidelineAt(watch.spot) ?: return false
        if (!isSameSideline(sideline, spotSideline)) return false

        awardOpponentThrowInAtSameSpot(
            level,
            watch.spot,
            watch.restartTeam,
            releaseHeldBall = false,
            lastTouchUuid = watch.takerUuid,
        )
        return true
    }

    /** 界外球犯规（往外扔 / 掷出后再出界）：延迟复位后同位置改判对方发球。 */
    private fun awardOpponentThrowInAtSameSpot(
        level: ServerLevel,
        spot: Vec3,
        foulingTeam: TeamSide,
        releaseHeldBall: Boolean,
        lastTouchUuid: UUID?,
    ) {
        if (MatchState.postGoalResetPending) return
        val server = level.server
        val opponentTeam = foulingTeam.opponent()
        val groundBallPos = resolveGroundBallPosition(level, spot)

        if (releaseHeldBall) {
            SetPieceState.active?.throwInTakerUuid?.let { uuid ->
                server.playerList.getPlayer(uuid)?.let { taker ->
                    GoalkeeperUtil.findHeldFootball(taker)?.releaseHold()
                    GoalkeeperHoldActionPermissions.resetToDefaults(taker)
                }
            }
        }

        foulThrowWatch = null
        clear(server)

        MatchState.clearPendingOffsideSnapshot()
        MatchState.postGoalResetPending = true

        val throwInTakerUuid = pickThrowInTaker(server, opponentTeam, groundBallPos)?.uuid
        PostGoalBallResetScheduler.schedule(
            level,
            groundBallPos,
            PendingAfterReset.GoalLineOut(
                kickoffTeam = opponentTeam,
                outType = GoalLineOutType.THROW_IN,
                ballPos = groundBallPos,
                throwInDirectGoalRestrict = true,
                throwInTakerUuid = throwInTakerUuid,
            ),
        )

        val lastTouchName = lastTouchUuid?.let { server.playerList.getPlayer(it)?.gameProfile?.name } ?: "?"
        FootballNetworking.broadcastGoalLineOut(
            server,
            GoalLineOutType.THROW_IN,
            opponentTeam,
            groundBallPos.x,
            groundBallPos.y,
            groundBallPos.z,
            lastTouchName,
            foulingTeam,
        )
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

    /** 出界瞬间：距出界点最近的在线队员；优先非门将，外场全不可用则门将主罚。 */
    fun pickThrowInTaker(server: MinecraftServer, restartTeam: TeamSide, outPos: Vec3): ServerPlayer? {
        val eligible = eligibleSetPiecePlayers(server, restartTeam)
        return eligible.filter { !PlayerRoleState.isDesignatedGoalkeeper(it) }
            .minByOrNull { distanceToOutPoint(it, outPos) }
            ?: eligible.filter { PlayerRoleState.isDesignatedGoalkeeper(it) }
                .minByOrNull { distanceToOutPoint(it, outPos) }
    }

    fun clear(server: MinecraftServer?) {
        clear(server, preserveFoulThrowWatch = false)
    }

    private fun clear(server: MinecraftServer?, preserveFoulThrowWatch: Boolean) {
        if (!preserveFoulThrowWatch) {
            foulThrowWatch = null
        }
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
                if (!MatchParticipation.isEligibleForSetPiece(player)) return@let
                if (MatchState.getPlayerTeam(player.uuid) != restartTeam) return@let
                if (!PlayerRoleState.isDesignatedGoalkeeper(player) || !hasEligibleOutfieldTaker(server, restartTeam)) {
                    return player
                }
            }
        }
        return pickThrowInTaker(server, restartTeam, outPos)
    }

    private fun eligibleSetPiecePlayers(server: MinecraftServer, restartTeam: TeamSide): List<ServerPlayer> {
        val roster = when (restartTeam) {
            TeamSide.A -> MatchState.teamAPlayers
            TeamSide.B -> MatchState.teamBPlayers
        }
        return roster.mapNotNull { server.playerList.getPlayer(it) }
            .filter { MatchParticipation.isEligibleForSetPiece(it) }
    }

    private fun hasEligibleOutfieldTaker(server: MinecraftServer, restartTeam: TeamSide): Boolean =
        eligibleSetPiecePlayers(server, restartTeam).any { !PlayerRoleState.isDesignatedGoalkeeper(it) }

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

    private data class FoulThrowWatch(
        val spot: Vec3,
        val restartTeam: TeamSide,
        val takerUuid: UUID,
        val expireTick: Long,
    )

    private fun sidelineAt(
        pos: Vec3,
        config: MatchConfig = MatchConfigHolder.current,
    ): SidelineConfig? {
        val distA = abs(sidelineSignedDistance(pos, config.sidelineA))
        val distB = abs(sidelineSignedDistance(pos, config.sidelineB))
        return if (distA <= distB) config.sidelineA else config.sidelineB
    }

    private fun sidelineSignedDistance(pos: Vec3, sideline: SidelineConfig): Double {
        val origin = sideline.origin()
        val facing = sideline.facing()
        return (pos.x - origin.x) * facing.x + (pos.z - origin.z) * facing.z
    }

    private fun isSameSideline(a: SidelineConfig, b: SidelineConfig): Boolean =
        a.axis.equals(b.axis, ignoreCase = true) && abs(a.coord - b.coord) < 1.0e-3
}
