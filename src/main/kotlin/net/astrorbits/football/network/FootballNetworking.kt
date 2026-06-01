package net.astrorbits.football.network

import net.astrorbits.football.config.server.FootballServerConfig
import net.astrorbits.football.config.server.FootballServerConfigHolder
import net.astrorbits.football.input.FootballPlayerActions
import net.astrorbits.football.match.MatchConfig
import net.astrorbits.football.match.MatchConfigHolder
import net.astrorbits.football.match.MatchPhase
import net.astrorbits.football.match.MatchState
import net.astrorbits.football.match.PlayerRoleState
import net.astrorbits.football.match.TeamSide
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
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
		registry.register(MatchResultRequestC2SPayload.TYPE, MatchResultRequestC2SPayload.CODEC)
    }

    private fun registerS2CPayloadType(registry: PayloadTypeRegistry<RegistryFriendlyByteBuf>) {
        registry.register(GoalkeeperRoleS2CPayload.TYPE, GoalkeeperRoleS2CPayload.CODEC)
        registry.register(GoalkeeperHoldLockS2CPayload.TYPE, GoalkeeperHoldLockS2CPayload.CODEC)
        registry.register(ServerConfigSyncS2CPayload.TYPE, ServerConfigSyncS2CPayload.CODEC)
        registry.register(MatchConfigSyncS2CPayload.TYPE, MatchConfigSyncS2CPayload.CODEC)
		registry.register(MatchFieldConfigSyncS2CPayload.TYPE, MatchFieldConfigSyncS2CPayload.CODEC)
		registry.register(MatchStartS2CPayload.TYPE, MatchStartS2CPayload.CODEC)
		registry.register(KickoffBallTouchedS2CPayload.TYPE, KickoffBallTouchedS2CPayload.CODEC)
		registry.register(GoalNetStateS2CPayload.TYPE, GoalNetStateS2CPayload.CODEC)
		registry.register(GoalNetConnectorSelectionS2CPayload.TYPE, GoalNetConnectorSelectionS2CPayload.CODEC)
		registry.register(GoalScoredS2CPayload.TYPE, GoalScoredS2CPayload.CODEC)
		registry.register(PostGoalKickoffS2CPayload.TYPE, PostGoalKickoffS2CPayload.CODEC)
		registry.register(MatchResetS2CPayload.TYPE, MatchResetS2CPayload.CODEC)
		registry.register(HalfKickoffRequestC2SPayload.TYPE, HalfKickoffRequestC2SPayload.CODEC)
		registry.register(HalfKickoffS2CPayload.TYPE, HalfKickoffS2CPayload.CODEC)
		registry.register(MatchResultRequestC2SPayload.TYPE, MatchResultRequestC2SPayload.CODEC)
		registry.register(MatchResultS2CPayload.TYPE, MatchResultS2CPayload.CODEC)
        registry.register(SlideTackleStateS2CPayload.TYPE, SlideTackleStateS2CPayload.CODEC)
        registry.register(GoalLineOutS2CPayload.TYPE, GoalLineOutS2CPayload.CODEC)
        registry.register(MatchTimerSyncS2CPayload.TYPE, MatchTimerSyncS2CPayload.CODEC)
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
                    Component.translatable("command.nmbct-football.config.applied"),
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
                    Component.translatable("command.nmbct-football.match.config_applied"),
                )
            }
        }
        ServerPlayNetworking.registerGlobalReceiver(HalfKickoffRequestC2SPayload.TYPE) { _, context ->
            context.server().execute {
                val server = context.server()
                val level = context.player().level() ?: return@execute
                handleHalfKickoffRequest(level, server)
            }
        }
        ServerPlayNetworking.registerGlobalReceiver(MatchResultRequestC2SPayload.TYPE) { _, context ->
            context.server().execute {
                val server = context.server()
                val nameA = MatchState.getTeamName(TeamSide.A).string
                val nameB = MatchState.getTeamName(TeamSide.B).string
                val isDraw = MatchState.teamAScore == MatchState.teamBScore
                broadcastMatchResult(server, MatchState.teamAScore, MatchState.teamBScore, nameA, nameB, isDraw)
            }
        }
    }

    private var serverTickCounter = 0

    fun registerServerTick() {
        ServerTickEvents.END_SERVER_TICK.register { server ->
            // ── 计时器（服务端权威）──
            if (MatchState.currentPhase != MatchPhase.PRE_MATCH
                && MatchState.currentPhase != MatchPhase.FINISHED
                && MatchState.isRunning
            ) {
                if (MatchState.isStoppagePhase()) {
                    MatchState.stoppageTimerTicks++
                } else {
                    MatchState.timerTicks++
                }
                if (MatchState.currentPhase != MatchPhase.PENALTIES) {
                    val remaining = MatchState.getPhaseRemainingTicks()
                    if (remaining <= 0) {
                        val next = MatchState.getNextPhaseForAutoAdvance()
                        if (next != null) {
                            MatchState.setPhase(next)
                        }
                    }
                }
            }

            // ── 定时同步所有客户端 ──
            serverTickCounter++
            if (serverTickCounter >= 20) {
                serverTickCounter = 0
                broadcastTimerSync(server)
            }
        }
    }

    private fun broadcastTimerSync(server: MinecraftServer) {
        val cfg = MatchConfigHolder.current
        val payload = MatchTimerSyncS2CPayload(
            timerTicks = MatchState.timerTicks,
            stoppageTimerTicks = MatchState.stoppageTimerTicks,
            currentPhase = MatchState.currentPhase,
            teamAScore = MatchState.teamAScore,
            teamBScore = MatchState.teamBScore,
            isRunning = MatchState.isRunning,
            halfTimeMinutes = cfg.halfTimeMinutes,
            stoppageTimeMaxMinutes = cfg.stoppageTimeMaxMinutes,
            extraTimeHalfMinutes = cfg.extraTimeHalfMinutes,
            enableStoppageTime = cfg.enableStoppageTime,
            enableExtraTime = cfg.enableExtraTime,
            enablePenaltyShootout = cfg.enablePenaltyShootout,
        )
        for (player in server.playerList.players) {
            ServerPlayNetworking.send(player, payload)
        }
    }

    private fun handleHalfKickoffRequest(level: ServerLevel, server: MinecraftServer) {
        val ms = MatchState
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
                MatchPhase.SECOND_HALF -> "match.phase.second_half"
                MatchPhase.EXTRA_FIRST -> "match.phase.extra_first"
                MatchPhase.EXTRA_SECOND -> "match.phase.extra_second"
                else -> return
            }
            broadcastHalfKickoff(server, nextKickoff, phaseKey, nameA, nameB)
        }
    }

    fun broadcastHalfKickoff(server: MinecraftServer, kickoffTeam: TeamSide, phaseKey: String, teamAName: String, teamBName: String) {
        for (uuid in MatchState.teamAPlayers) {
            val player = server.playerList.getPlayer(uuid) ?: continue
            ServerPlayNetworking.send(player, HalfKickoffS2CPayload(kickoffTeam, kickoffTeam == TeamSide.A, phaseKey, teamAName, teamBName))
        }
        for (uuid in MatchState.teamBPlayers) {
            val player = server.playerList.getPlayer(uuid) ?: continue
            ServerPlayNetworking.send(player, HalfKickoffS2CPayload(kickoffTeam, kickoffTeam == TeamSide.B, phaseKey, teamAName, teamBName))
        }
    }

    fun broadcastMatchResult(server: MinecraftServer, teamAScore: Int, teamBScore: Int, teamAName: String, teamBName: String, isDraw: Boolean) {
        val payload = MatchResultS2CPayload(teamAScore, teamBScore, teamAName, teamBName, isDraw)
        for (player in server.playerList.players) {
            ServerPlayNetworking.send(player, payload)
        }
    }

    /** 玩家加入时即时同步服务端配置，不等下个定时周期。 */
    fun syncConfigToPlayer(player: ServerPlayer) {
        val cfg = MatchConfigHolder.current
        ServerPlayNetworking.send(player, MatchTimerSyncS2CPayload(
            timerTicks = MatchState.timerTicks,
            stoppageTimerTicks = MatchState.stoppageTimerTicks,
            currentPhase = MatchState.currentPhase,
            teamAScore = MatchState.teamAScore,
            teamBScore = MatchState.teamBScore,
            isRunning = MatchState.isRunning,
            halfTimeMinutes = cfg.halfTimeMinutes,
            stoppageTimeMaxMinutes = cfg.stoppageTimeMaxMinutes,
            extraTimeHalfMinutes = cfg.extraTimeHalfMinutes,
            enableStoppageTime = cfg.enableStoppageTime,
            enableExtraTime = cfg.enableExtraTime,
            enablePenaltyShootout = cfg.enablePenaltyShootout,
        ))
    }

    fun sendServerConfigSync(player: ServerPlayer, config: FootballServerConfig) {
        ServerPlayNetworking.send(player, ServerConfigSyncS2CPayload(config))
    }

    fun sendMatchConfigSync(player: ServerPlayer, config: MatchConfig) {
        ServerPlayNetworking.send(player, MatchConfigSyncS2CPayload(config))
    }

    fun sendMatchFieldConfigSync(player: ServerPlayer, config: MatchConfig) {
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

    fun syncSlideTackleState(player: ServerPlayer, sliding: Boolean) {
        val payload = SlideTackleStateS2CPayload(player.id, sliding)
        val server = player.level().server ?: return
        for (target in server.playerList.players) {
            ServerPlayNetworking.send(target, payload)
        }
    }

    fun syncGoalkeeperRole(uuid: UUID, server: MinecraftServer?) {
        val player = server?.playerList?.getPlayer(uuid) ?: return
        PlayerRoleState.syncRoleToPlayer(player)
    }

    fun broadcastPostGoalKickoff(server: MinecraftServer, kickoffTeam: TeamSide) {
        for (uuid in MatchState.teamAPlayers) {
            val player = server.playerList.getPlayer(uuid) ?: continue
            ServerPlayNetworking.send(player, PostGoalKickoffS2CPayload(kickoffTeam, kickoffTeam == TeamSide.A))
        }
        for (uuid in MatchState.teamBPlayers) {
            val player = server.playerList.getPlayer(uuid) ?: continue
            ServerPlayNetworking.send(player, PostGoalKickoffS2CPayload(kickoffTeam, kickoffTeam == TeamSide.B))
        }
    }

    fun broadcastMatchReset(server: MinecraftServer) {
        for (player in server.playerList.players) {
            ServerPlayNetworking.send(player, MatchResetS2CPayload.INSTANCE)
        }
    }

    fun broadcastGoalLineOut(server: MinecraftServer, outType: net.astrorbits.football.match.GoalLineOutType, restartTeam: TeamSide, ballX: Double, ballY: Double, ballZ: Double) {
        for (uuid in MatchState.teamAPlayers) {
            val player = server.playerList.getPlayer(uuid) ?: continue
            ServerPlayNetworking.send(player, GoalLineOutS2CPayload(outType, restartTeam, restartTeam == TeamSide.A, ballX, ballY, ballZ))
        }
        for (uuid in MatchState.teamBPlayers) {
            val player = server.playerList.getPlayer(uuid) ?: continue
            ServerPlayNetworking.send(player, GoalLineOutS2CPayload(outType, restartTeam, restartTeam == TeamSide.B, ballX, ballY, ballZ))
        }
    }

    fun broadcastKickoffBallTouched(server: MinecraftServer) {
        for (player in server.playerList.players) {
            ServerPlayNetworking.send(player, KickoffBallTouchedS2CPayload.INSTANCE)
        }
    }

    /** 向玩家同步连接器当前已选锚点（客户端预览粒子用）。 */
    fun sendGoalNetConnectorSelection(player: ServerPlayer, anchorBlocks: List<BlockPos>) {
        ServerPlayNetworking.send(player, GoalNetConnectorSelectionS2CPayload(anchorBlocks))
    }

    /** 向所有正在跟踪该球网实体的玩家同步节点形变。 */
    fun broadcastGoalNetState(
        entity: Entity,
        cols: Int,
        rows: Int,
        relativePositions: FloatArray,
    ) {
        val payload = GoalNetStateS2CPayload(entity.id, cols, rows, relativePositions.copyOf())
        for (player in PlayerLookup.tracking(entity)) {
            ServerPlayNetworking.send(player, payload)
        }
    }

    fun broadcastGoalScored(
        server: MinecraftServer,
        scoringTeam: TeamSide,
        scorerName: String,
        scorerTeam: TeamSide,
        teamAScore: Int,
        teamBScore: Int,
        ownGoal: Boolean
    ) {
        val payload = GoalScoredS2CPayload(scoringTeam, scorerName, scorerTeam, teamAScore, teamBScore, ownGoal)
        for (player in server.playerList.players) {
            ServerPlayNetworking.send(player, payload)
        }
    }
}
