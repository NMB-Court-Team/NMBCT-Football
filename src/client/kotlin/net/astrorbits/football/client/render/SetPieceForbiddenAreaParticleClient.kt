package net.astrorbits.football.client.render

import net.astrorbits.football.client.GoalkeeperStateClient
import net.astrorbits.football.client.SetPieceClient
import net.astrorbits.football.client.match.MatchStartClient
import net.astrorbits.football.client.match.PenaltyShootoutClient
import net.astrorbits.football.match.MatchConfigHolder
import net.astrorbits.football.match.MatchParticipation
import net.astrorbits.football.match.MatchState
import net.astrorbits.football.match.SetPieceForbiddenZoneResolver
import net.astrorbits.football.match.SetPieceKind
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.TrailParticleOption
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.phys.Vec3

/**
 * 当本地球员靠近或处于定位球禁止区域时，在区域边缘显示红色 trail 粒子。
 * 相邻采样点首尾相连；首尾间距超过 [MAX_HEAD_TAIL_CONNECT_DISTANCE] 格时不闭合。
 */
object SetPieceForbiddenAreaParticleClient {
    private const val GROUND_OFFSET = 0.5
    private const val MAX_RENDER_DISTANCE_SQ = 48.0 * 48.0
    private const val MAX_HEAD_TAIL_CONNECT_DISTANCE = 8.0
    private const val MAX_HEAD_TAIL_CONNECT_DISTANCE_SQ =
        MAX_HEAD_TAIL_CONNECT_DISTANCE * MAX_HEAD_TAIL_CONNECT_DISTANCE
    private const val PARTICLE_SUBSAMPLE = 2
    private const val TRAIL_COLOR = 0xFFFF3B3B.toInt()
    private const val TRAIL_DURATION_TICKS = 20

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register(::tick)
    }

    private fun tick(client: Minecraft) {
        val player = client.player ?: return
        val level = client.level ?: return
        if (player.isSpectator || !MatchState.isDuringMatch() || !MatchParticipation.isParticipating(player)) {
            return
        }

        val penaltyDefendingTeam = when {
            PenaltyShootoutClient.active -> PenaltyShootoutClient.penaltyGoalTeam
            SetPieceClient.kind == SetPieceKind.PENALTY_KICK -> SetPieceClient.defendingSide
            else -> null
        }
        val penaltyGoalkeeperTeam = when {
            PenaltyShootoutClient.active -> PenaltyShootoutClient.activeDefendingTeam
            else -> penaltyDefendingTeam
        }
        val penaltyKickerUuid = when {
            PenaltyShootoutClient.active -> PenaltyShootoutClient.currentKickerUuid
            SetPieceClient.kind == SetPieceKind.PENALTY_KICK -> SetPieceClient.penaltyKickerUuid
            else -> null
        }
        val zones = SetPieceForbiddenZoneResolver.resolve(
            playerX = player.x,
            playerZ = player.z,
            playerUuid = player.uuid,
            playerTeam = MatchStartClient.playerTeam,
            isGoalkeeper = GoalkeeperStateClient.isGoalkeeper,
            isHoldingBall = GoalkeeperStateClient.isHoldingBall,
            kickoffTouched = MatchStartClient.kickoffTouched,
            kickoffTeam = MatchStartClient.kickoffTeam,
            setPieceKind = SetPieceClient.kind,
            restartTeam = SetPieceClient.restartTeam,
            defendingSide = SetPieceClient.defendingSide,
            ballPos = SetPieceClient.ballPos,
            cornerPos = SetPieceClient.ballPos,
            penaltyShootoutActive = PenaltyShootoutClient.active,
            penaltyKickerUuid = penaltyKickerUuid,
            penaltyDefendingTeam = penaltyDefendingTeam,
            isDefendingGoalkeeper = GoalkeeperStateClient.isGoalkeeper &&
                penaltyGoalkeeperTeam != null &&
                MatchStartClient.playerTeam == penaltyGoalkeeperTeam,
            config = MatchConfigHolder.current,
        )
        if (zones.isEmpty()) {
            return
        }

        val px = player.x
        val pz = player.z
        for (zone in zones) {
            val points = zone.sampleBoundaryPoints(MatchConfigHolder.current)
            val sampled = points.filterIndexed { index, _ -> index % PARTICLE_SUBSAMPLE == 0 }
            if (sampled.size < 2) {
                continue
            }
            val first = sampled.first()
            val last = sampled.last()
            val headTailDx = first.x - last.x
            val headTailDz = first.z - last.z
            val closeLoop = headTailDx * headTailDx + headTailDz * headTailDz <= MAX_HEAD_TAIL_CONNECT_DISTANCE_SQ
            val segmentCount = if (closeLoop) sampled.size else sampled.size - 1
            for (index in 0 until segmentCount) {
                val current = sampled[index]
                val next = sampled[(index + 1) % sampled.size]
                val dx = current.x - px
                val dz = current.z - pz
                if (dx * dx + dz * dz > MAX_RENDER_DISTANCE_SQ) {
                    continue
                }
                spawnTrailSegment(level, current.x, current.z, next.x, next.z)
            }
        }
    }

    private fun spawnTrailSegment(
        level: ClientLevel,
        startX: Double,
        startZ: Double,
        endX: Double,
        endZ: Double,
    ) {
        val startY = groundY(level, startX, startZ)
        val endY = groundY(level, endX, endZ)
        val trail = TrailParticleOption(
            Vec3(endX, endY, endZ),
            TRAIL_COLOR,
            TRAIL_DURATION_TICKS,
        )
        level.addParticle(trail, startX, startY, startZ, 0.0, 0.0, 0.0)
    }

    private fun groundY(level: ClientLevel, x: Double, z: Double): Double {
        val blockPos = BlockPos.containing(x, 0.0, z)
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockPos.x, blockPos.z).toDouble() +
            GROUND_OFFSET
    }
}
