package net.astrorbits.football.client.render

import net.astrorbits.football.client.GoalkeeperStateClient
import net.astrorbits.football.client.SetPieceClient
import net.astrorbits.football.client.match.MatchStartClient
import net.astrorbits.football.client.match.PenaltyShootoutClient
import net.astrorbits.football.match.MatchConfigHolder
import net.astrorbits.football.match.MatchParticipation
import net.astrorbits.football.match.MatchState
import net.astrorbits.football.match.SetPieceForbiddenZoneResolver
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.DustParticleOptions
import net.minecraft.world.level.levelgen.Heightmap
/**
 * 当本地球员靠近或处于定位球禁止区域时，在区域边缘显示红色 dust 粒子。
 */
object SetPieceForbiddenAreaParticleClient {
    private const val PARTICLE_SIZE = 1.5f
    private const val GROUND_OFFSET = 0.5
    private const val MAX_RENDER_DISTANCE_SQ = 48.0 * 48.0
    private const val PARTICLE_SUBSAMPLE = 2

    private val redDust = DustParticleOptions(0xFFFF3B3B.toInt(), PARTICLE_SIZE)

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register(::tick)
    }

    private fun tick(client: Minecraft) {
        val player = client.player ?: return
        val level = client.level ?: return
        if (player.isSpectator || !MatchState.isDuringMatch() || !MatchParticipation.isParticipating(player)) {
            return
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
            penaltyKickerUuid = PenaltyShootoutClient.currentKickerUuid,
            penaltyDefendingTeam = PenaltyShootoutClient.activeDefendingTeam,
            isDefendingGoalkeeper = GoalkeeperStateClient.isGoalkeeper &&
                MatchStartClient.playerTeam == PenaltyShootoutClient.activeDefendingTeam,
            config = MatchConfigHolder.current,
        )
        if (zones.isEmpty()) {
            return
        }

        val px = player.x
        val pz = player.z
        for (zone in zones) {
            val points = zone.sampleBoundaryPoints(MatchConfigHolder.current)
            var index = 0
            for (point in points) {
                if (index++ % PARTICLE_SUBSAMPLE != 0) {
                    continue
                }
                val dx = point.x - px
                val dz = point.z - pz
                if (dx * dx + dz * dz > MAX_RENDER_DISTANCE_SQ) {
                    continue
                }
                spawnGroundParticle(level, player, point.x, point.z)
            }
        }
    }

    private fun spawnGroundParticle(level: ClientLevel, player: LocalPlayer, x: Double, z: Double) {
        val groundY = level.getHeight(
            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            BlockPos.containing(x, 0.0, z).x,
            BlockPos.containing(x, 0.0, z).z,
        ).toDouble() + GROUND_OFFSET
        level.addParticle(
            redDust,
            x,
            groundY,
            z,
            0.0,
            0.0,
            0.0,
        )
    }
}
