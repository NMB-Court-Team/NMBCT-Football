package net.astrorbits.football.client

import net.astrorbits.football.Football
import net.astrorbits.football.match.MatchConfigHolder
import net.astrorbits.football.match.MatchFieldBounds
import net.astrorbits.football.match.MatchPhase
import net.astrorbits.football.match.MatchState
import net.minecraft.client.Minecraft
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/**
 * 足球方位 HUD 跟踪：带球 session 或（比赛配置开启时）全场参赛玩家。
 */
object DribbleBallIndicatorClient {
    private const val SCOREBOARD_TEAM_A = "football_A"
    private const val SCOREBOARD_TEAM_B = "football_B"

    var activeFootballId: Int = -1
        private set

    fun onDribbleHold() {
        val client = Minecraft.getInstance()
        val player = client.player ?: return
        val level = client.level ?: return
        val football = level.getEntitiesOfClass(Football::class.java, player.boundingBox.inflate(8.0))
            .minByOrNull { it.distanceToSqr(player) }
        activeFootballId = football?.id ?: -1
    }

    fun onDribbleEnd() {
        activeFootballId = -1
    }

    /** 当前应显示视野外指示的足球中心；带球优先，其次为比赛全场指示。 */
    fun trackedBallCenter(): Vec3? {
        activeBallCenter()?.let { return it }
        if (!isMatchPositionIndicatorActive()) {
            return null
        }
        return nearestFootballInIndicatorRange()
    }

    fun activeBallCenter(): Vec3? {
        if (activeFootballId < 0) {
            return null
        }
        val level = Minecraft.getInstance().level ?: return null
        val entity = level.getEntity(activeFootballId) as? Football ?: run {
            activeFootballId = -1
            return null
        }
        return entity.position().add(0.0, entity.bbHeight * 0.5, 0.0)
    }

    private fun isMatchPositionIndicatorActive(): Boolean {
        if (!MatchConfigHolder.current.accessibility.enableFootballPositionIndicator) {
            return false
        }
        val phase = MatchState.currentPhase
        if (phase == MatchPhase.PRE_MATCH || phase == MatchPhase.FINISHED) {
            return false
        }
        return isLocalPlayerMatchParticipant()
    }

    private fun isLocalPlayerMatchParticipant(): Boolean {
        val player = Minecraft.getInstance().player ?: return false
        val team = player.level().scoreboard.getPlayersTeam(player.gameProfile.name)
        return team?.name == SCOREBOARD_TEAM_A || team?.name == SCOREBOARD_TEAM_B
    }

    private fun nearestFootballInIndicatorRange(): Vec3? {
        val rect = MatchFieldBounds.indicatorRect(MatchConfigHolder.current) ?: return null
        val client = Minecraft.getInstance()
        val level = client.level ?: return null
        val player = client.player ?: return null
        val searchBox = AABB(
            rect.minX,
            Double.NEGATIVE_INFINITY,
            rect.minZ,
            rect.maxX,
            Double.POSITIVE_INFINITY,
            rect.maxZ,
        )
        val football = level.getEntitiesOfClass(Football::class.java, searchBox)
            .asSequence()
            .filter { rect.containsHorizontal(it.x, it.z) }
            .minByOrNull { it.distanceToSqr(player) }
            ?: return null
        return football.position().add(0.0, football.bbHeight * 0.5, 0.0)
    }
}
