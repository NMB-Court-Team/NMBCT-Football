package net.astrorbits.football.match

import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3

object OffsideDetector {

    fun recordSnapshot(passer: ServerPlayer, ballCenter: Vec3) {
        val config = MatchConfigHolder.current
        if (!config.enableOffside) return
        if (!MatchState.isDuringMatch()) return
        if (MatchState.currentPhase == MatchPhase.PENALTIES) return
        if (MatchState.postGoalResetPending) return
        if (PenaltyFoulGoalWatchState.isActive()) return

        val attackingTeam = MatchParticipation.participatingTeam(passer) ?: return
        val server = passer.level().server ?: return

        val ballLine = MatchFieldAreaUtil.longitudinalTowardOpponentGoal(ballCenter.x, ballCenter.z, attackingTeam, config)
            ?: return

        val defendingTeam = attackingTeam.opponent()
        val defenderRoster = when (defendingTeam) {
            TeamSide.A -> MatchState.teamAPlayers
            TeamSide.B -> MatchState.teamBPlayers
        }
        val defenderLines = MatchParticipation.onlineParticipating(server, defenderRoster)
            .mapNotNull { MatchFieldAreaUtil.longitudinalTowardOpponentGoal(it, attackingTeam, config) }
            .sortedDescending()

        val secondLastDefenderLine = when {
            defenderLines.size >= 2 -> defenderLines[1]
            defenderLines.size == 1 -> defenderLines[0]
            else -> return
        }

        val attackerRoster = when (attackingTeam) {
            TeamSide.A -> MatchState.teamAPlayers
            TeamSide.B -> MatchState.teamBPlayers
        }
        val offsidePlayers = MatchParticipation.onlineParticipating(server, attackerRoster)
            .mapNotNull { attacker ->
                if (attacker.uuid == passer.uuid) return@mapNotNull null
                if (!MatchFieldAreaUtil.isPlayerInOpponentHalf(attacker, attackingTeam, config)) return@mapNotNull null
                val line = MatchFieldAreaUtil.longitudinalTowardOpponentGoal(attacker, attackingTeam, config)
                    ?: return@mapNotNull null
                if (line > ballLine && line > secondLastDefenderLine) attacker.uuid else null
            }
            .toSet()

        if (offsidePlayers.isEmpty()) {
            MatchState.clearPendingOffsideSnapshot()
            return
        }

        MatchState.pendingOffsideSnapshot = MatchState.OffsideSnapshot(
            passerUuid = passer.uuid,
            attackingTeam = attackingTeam,
            offsidePlayers = offsidePlayers,
            gameTime = server.overworld().gameTime,
        )
    }

    fun tryAwardOnTouch(player: ServerPlayer, ballCenter: Vec3, level: ServerLevel): Boolean {
        val snapshot = MatchState.pendingOffsideSnapshot ?: return false

        val toucherTeam = MatchParticipation.participatingTeam(player)
        if (toucherTeam != snapshot.attackingTeam) {
            MatchState.clearPendingOffsideSnapshot()
            return false
        }

        if (player.uuid !in snapshot.offsidePlayers) return false
        if (player.uuid == snapshot.passerUuid) return false
        if (MatchState.postGoalResetPending) return false
        if (PenaltyFoulGoalWatchState.isActive()) return false

        val awarded = FreeKickAwards.awardIndirectFreeKick(
            level,
            Vec3(player.x, ballCenter.y, player.z),
            foulingTeam = snapshot.attackingTeam,
            foulingPlayerUuid = player.uuid,
            foulReason = FreeKickFoulReason.OFFSIDE,
        )
        if (awarded) {
            MatchState.clearPendingOffsideSnapshot()
        }
        return awarded
    }
}
