package net.astrorbits.football.network

import net.astrorbits.football.config.server.FootballServerConfigHolder
import net.astrorbits.football.input.FootballPlayerActions
import net.astrorbits.football.match.MatchConfigHolder
import net.astrorbits.football.match.PlayerRoleState
import net.astrorbits.football.match.TeamSide
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

object FootballNetworking {
    fun registerPayloadType() {
        registerC2SPayloadType(PayloadTypeRegistry.serverboundPlay())
        registerS2CPayloadType(PayloadTypeRegistry.clientboundPlay())
    }

    private fun registerC2SPayloadType(registry: PayloadTypeRegistry<RegistryFriendlyByteBuf>) {
        registry.register(FootballActionC2SPayload.TYPE, FootballActionC2SPayload.CODEC)
        registry.register(ServerConfigApplyC2SPayload.TYPE, ServerConfigApplyC2SPayload.CODEC)
	        registry.register(MatchConfigApplyC2SPayload.TYPE, MatchConfigApplyC2SPayload.CODEC)
		registry.register(HalfKickoffRequestC2SPayload.TYPE, HalfKickoffRequestC2SPayload.CODEC)
    }

    private fun registerS2CPayloadType(registry: PayloadTypeRegistry<RegistryFriendlyByteBuf>) {
        registry.register(GoalkeeperRoleS2CPayload.TYPE, GoalkeeperRoleS2CPayload.CODEC)
        registry.register(GoalkeeperHoldLockS2CPayload.TYPE, GoalkeeperHoldLockS2CPayload.CODEC)
        registry.register(ServerConfigSyncS2CPayload.TYPE, ServerConfigSyncS2CPayload.CODEC)
	        registry.register(MatchConfigSyncS2CPayload.TYPE, MatchConfigSyncS2CPayload.CODEC)
		registry.register(MatchFieldConfigSyncS2CPayload.TYPE, MatchFieldConfigSyncS2CPayload.CODEC)
		registry.register(MatchStartS2CPayload.TYPE, MatchStartS2CPayload.CODEC)
		registry.register(KickoffBallTouchedS2CPayload.TYPE, KickoffBallTouchedS2CPayload.CODEC)
		registry.register(GoalScoredS2CPayload.TYPE, GoalScoredS2CPayload.CODEC)
		registry.register(PostGoalKickoffS2CPayload.TYPE, PostGoalKickoffS2CPayload.CODEC)
		registry.register(MatchResetS2CPayload.TYPE, MatchResetS2CPayload.CODEC)
		registry.register(HalfKickoffRequestC2SPayload.TYPE, HalfKickoffRequestC2SPayload.CODEC)
		registry.register(HalfKickoffS2CPayload.TYPE, HalfKickoffS2CPayload.CODEC)
    }

    fun registerServerReceiver() {
        ServerPlayNetworking.registerGlobalReceiver(FootballActionC2SPayload.TYPE) { payload, context ->
            context.server().execute {
                FootballPlayerActions.handle(context.player(), payload)
            }
        }
        ServerPlayNetworking.registerGlobalReceiver(ServerConfigApplyC2SPayload.TYPE) { payload, context ->
            context.server().execute {
                val player = context.player()
                if (!context.server().playerList.isOp(player.nameAndId())) {
                    return@execute
                }
                FootballServerConfigHolder.apply(payload.config)
                player.sendSystemMessage(
                    net.minecraft.network.chat.Component.translatable("command.nmbct-football.config.applied"),
                )
            }
        }
        ServerPlayNetworking.registerGlobalReceiver(MatchConfigApplyC2SPayload.TYPE) { payload, context ->
            context.server().execute {
                val player = context.player()
                if (!context.server().playerList.isOp(player.nameAndId())) {
                    return@execute
                }
                MatchConfigHolder.apply(payload.config)
                player.sendSystemMessage(
                    net.minecraft.network.chat.Component.translatable("command.nmbct-football.match.config_applied"),
                )
            }
        }
        ServerPlayNetworking.registerGlobalReceiver(HalfKickoffRequestC2SPayload.TYPE) { _, context ->
            context.server().execute {
                val server = context.server()
                val level = context.player().level() as? net.minecraft.server.level.ServerLevel ?: return@execute
                handleHalfKickoffRequest(level, server)
            }
        }
    }

    private fun handleHalfKickoffRequest(level: net.minecraft.server.level.ServerLevel, server: MinecraftServer) {
        val ms = net.astrorbits.football.match.MatchState
        val lastHalf = ms.lastHalfKickoffTeam ?: TeamSide.A
        val nextKickoff = if (lastHalf == TeamSide.A) TeamSide.B else TeamSide.A
        if (!ms.halfKickoffBroadcasted) {
            ms.halfKickoffBroadcasted = true
            ms.lastHalfKickoffTeam = nextKickoff
            ms.kickoffTeam = nextKickoff
            ms.kickoffTouched = false
            ms.resetFootball(level)
            val nameA = ms.getTeamName(TeamSide.A).string
            val nameB = ms.getTeamName(TeamSide.B).string
            val phaseKey = when (ms.currentPhase) {
                net.astrorbits.football.match.MatchPhase.SECOND_HALF -> "match.phase.second_half"
                net.astrorbits.football.match.MatchPhase.EXTRA_FIRST -> "match.phase.extra_first"
                net.astrorbits.football.match.MatchPhase.EXTRA_SECOND -> "match.phase.extra_second"
                else -> return
            }
            broadcastHalfKickoff(server, nextKickoff, phaseKey, nameA, nameB)
        }
    }

    fun broadcastHalfKickoff(server: MinecraftServer, kickoffTeam: TeamSide, phaseKey: String, teamAName: String, teamBName: String) {
        val payload = HalfKickoffS2CPayload(kickoffTeam, phaseKey, teamAName, teamBName)
        for (player in server.playerList.players) {
            ServerPlayNetworking.send(player, payload)
        }
    }

    fun sendServerConfigSync(player: ServerPlayer, config: net.astrorbits.football.config.server.FootballServerConfig) {
        ServerPlayNetworking.send(player, ServerConfigSyncS2CPayload(config))
    }

    fun sendMatchConfigSync(player: ServerPlayer, config: net.astrorbits.football.match.MatchConfig) {
        ServerPlayNetworking.send(player, MatchConfigSyncS2CPayload(config))
    }

    fun sendMatchFieldConfigSync(player: ServerPlayer, config: net.astrorbits.football.match.MatchConfig) {
        ServerPlayNetworking.send(player, MatchFieldConfigSyncS2CPayload(config))
    }

    fun sendGoalkeeperRole(player: ServerPlayer, isGoalkeeper: Boolean) {
        ServerPlayNetworking.send(player, GoalkeeperRoleS2CPayload(isGoalkeeper))
    }

    fun sendMatchStart(player: ServerPlayer, playerTeam: TeamSide, isGk: Boolean, kickoffTeam: TeamSide,
                       teamAName: String, teamBName: String) {
        ServerPlayNetworking.send(player, MatchStartS2CPayload(playerTeam, isGk, kickoffTeam, teamAName, teamBName))
    }

    fun sendHoldReleaseLock(player: ServerPlayer, lockTicksRemaining: Int) {
        ServerPlayNetworking.send(player, GoalkeeperHoldLockS2CPayload(lockTicksRemaining))
    }

    fun syncGoalkeeperRole(uuid: UUID, server: MinecraftServer?) {
        val player = server?.playerList?.getPlayer(uuid) ?: return
        PlayerRoleState.syncRoleToPlayer(player)
    }

    fun broadcastPostGoalKickoff(server: MinecraftServer, kickoffTeam: TeamSide) {
        val payload = PostGoalKickoffS2CPayload(kickoffTeam)
        for (player in server.playerList.players) {
            ServerPlayNetworking.send(player, payload)
        }
    }

    fun broadcastMatchReset(server: MinecraftServer) {
        for (player in server.playerList.players) {
            ServerPlayNetworking.send(player, MatchResetS2CPayload.INSTANCE)
        }
    }

    fun broadcastKickoffBallTouched(server: MinecraftServer) {
        for (player in server.playerList.players) {
            ServerPlayNetworking.send(player, KickoffBallTouchedS2CPayload.INSTANCE)
        }
    }

    fun broadcastGoalScored(server: MinecraftServer, scoringTeam: TeamSide, scorerName: String,
                            scorerTeam: TeamSide, teamAScore: Int, teamBScore: Int, ownGoal: Boolean) {
        val payload = GoalScoredS2CPayload(scoringTeam, scorerName, scorerTeam, teamAScore, teamBScore, ownGoal)
        for (player in server.playerList.players) {
            ServerPlayNetworking.send(player, payload)
        }
    }
}
