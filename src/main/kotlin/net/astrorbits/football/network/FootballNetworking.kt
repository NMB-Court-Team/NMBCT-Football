package net.astrorbits.football.network

import net.astrorbits.football.config.server.FootballServerConfig
import net.astrorbits.football.config.server.FootballServerConfigHolder
import net.astrorbits.football.FootballSounds
import net.astrorbits.football.input.FootballPlayerActions
import net.astrorbits.football.input.GoalkeeperHoldLock
import net.astrorbits.football.util.GoalkeeperUtil
import net.astrorbits.football.match.KickoffWhistleContext
import net.astrorbits.football.match.MatchKickoffTiming
import net.astrorbits.football.match.MatchConfig
import net.astrorbits.football.match.MatchConfigHolder
import net.astrorbits.football.match.MatchPhase
import net.astrorbits.football.match.PenaltyShootoutState
import net.astrorbits.football.match.MatchPauseFootballState
import net.astrorbits.football.match.MatchState
import net.astrorbits.football.match.PlayerRoleState
import net.astrorbits.football.match.TeamSide
import net.astrorbits.football.stamina.BoostSprintState
import net.astrorbits.football.stamina.StaminaState
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
        registry.register(BoostSprintToggleC2SPayload.TYPE, BoostSprintToggleC2SPayload.CODEC)
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
		registry.register(InvalidGoalS2CPayload.TYPE, InvalidGoalS2CPayload.CODEC)
		registry.register(PostGoalKickoffS2CPayload.TYPE, PostGoalKickoffS2CPayload.CODEC)
		registry.register(MatchResetS2CPayload.TYPE, MatchResetS2CPayload.CODEC)
		registry.register(HalfKickoffRequestC2SPayload.TYPE, HalfKickoffRequestC2SPayload.CODEC)
		registry.register(HalfKickoffS2CPayload.TYPE, HalfKickoffS2CPayload.CODEC)
		registry.register(MatchResultRequestC2SPayload.TYPE, MatchResultRequestC2SPayload.CODEC)
		registry.register(MatchResultS2CPayload.TYPE, MatchResultS2CPayload.CODEC)
        registry.register(SlideTackleStateS2CPayload.TYPE, SlideTackleStateS2CPayload.CODEC)
        registry.register(WhistleUseS2CPayload.TYPE, WhistleUseS2CPayload.CODEC)
        registry.register(GoalLineOutS2CPayload.TYPE, GoalLineOutS2CPayload.CODEC)
        registry.register(MatchHudDebugS2CPayload.TYPE, MatchHudDebugS2CPayload.CODEC)
        registry.register(MatchTimerSyncS2CPayload.TYPE, MatchTimerSyncS2CPayload.CODEC)
        registry.register(StaminaSyncS2CPayload.TYPE, StaminaSyncS2CPayload.CODEC)
        registry.register(PenaltyShootoutSyncS2CPayload.TYPE, PenaltyShootoutSyncS2CPayload.CODEC)
        registry.register(PenaltyKickStartS2CPayload.TYPE, PenaltyKickStartS2CPayload.CODEC)
        registry.register(MatchPauseS2CPayload.TYPE, MatchPauseS2CPayload.CODEC)
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
                broadcastServerConfig(context.server(), openEditor = false)
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
                // 立即推送到所有客户端，不等下个定时同步周期
                broadcastTimerSync(context.server())
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
                val winner = PenaltyShootoutState.lastWinner
                if (winner != null) {
                    broadcastMatchResult(
                        server,
                        MatchState.teamAScore,
                        MatchState.teamBScore,
                        nameA,
                        nameB,
                        isDraw = false,
                        wonByPenalties = true,
                        penaltyScoreA = PenaltyShootoutState.penaltyScoreA,
                        penaltyScoreB = PenaltyShootoutState.penaltyScoreB,
                        penaltyWinner = winner,
                    )
                } else {
                    val isDraw = MatchState.teamAScore == MatchState.teamBScore
                    broadcastMatchResult(server, MatchState.teamAScore, MatchState.teamBScore, nameA, nameB, isDraw)
                }
            }
        }
        ServerPlayNetworking.registerGlobalReceiver(BoostSprintToggleC2SPayload.TYPE) { payload, context ->
            context.server().execute {
                BoostSprintState.setRequested(context.player(), payload.enabled)
            }
        }
    }

    private var serverTickCounter = 0
    private var finishedPhaseTicks = 0

    /** 结算阶段展示结束后，自动回到未开始（与客户端原 16s 一致）。 */
    private const val FINISHED_TO_PRE_MATCH_TICKS = 320

    fun registerServerTick() {
        ServerTickEvents.END_SERVER_TICK.register { server ->
            if (MatchState.currentPhase == MatchPhase.FINISHED) {
                finishedPhaseTicks++
                if (finishedPhaseTicks >= FINISHED_TO_PRE_MATCH_TICKS) {
                    resetMatchToPreMatch(server)
                    finishedPhaseTicks = 0
                }
            } else {
                finishedPhaseTicks = 0
            }

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
                        if (MatchState.currentPhase == MatchPhase.PRE_MATCH_PREP) {
                            MatchState.finishPreMatchPreparation(server)
                        } else {
                            val next = MatchState.getNextPhaseForAutoAdvance()
                            if (next != null) {
                                MatchState.setPhase(next, server)
                            }
                        }
                    }
                }
            }

            PenaltyShootoutState.tick(server)

            MatchState.tickKickoffWhistles(server)
            MatchState.tickDynamicStoppageAccumulation()
            StaminaState.tickServer(server)

            // ── 定时同步所有客户端 ──
            serverTickCounter++
            if (serverTickCounter >= 20) {
                serverTickCounter = 0
                broadcastTimerSync(server)
            }
        }
    }

    fun syncTimerToClients(server: MinecraftServer) {
        broadcastTimerSync(server)
    }

    private fun broadcastTimerSync(server: MinecraftServer) {
        val cfg = MatchConfigHolder.current
        val payload = MatchTimerSyncS2CPayload(
            timerTicks = MatchState.timerTicks,
            stoppageTimerTicks = MatchState.stoppageTimerTicks,
            currentPhase = MatchState.currentPhase,
            teamAScore = MatchState.teamAScore,
            teamBScore = MatchState.teamBScore,
            teamAName = cfg.teamAName,
            teamBName = cfg.teamBName,
            isRunning = MatchState.isRunning,
            halfTimeMinutes = cfg.halfTimeMinutes,
            stoppageTimeMaxMinutes = cfg.stoppageTimeMaxMinutes,
            extraTimeHalfMinutes = cfg.extraTimeHalfMinutes,
            enableStoppageTime = cfg.enableStoppageTime,
            enableExtraTime = cfg.enableExtraTime,
            enablePenaltyShootout = cfg.enablePenaltyShootout,
            enableFootballPositionIndicator = cfg.accessibility.enableFootballPositionIndicator,
            dynamicStoppageTicks = MatchState.dynamicStoppageTicks,
        )
        for (player in server.playerList.players) {
            ServerPlayNetworking.send(player, payload)
        }
    }

    private fun handleHalfKickoffRequest(level: ServerLevel, server: MinecraftServer) {
        triggerHalfKickoff(server, level)
    }

    /** 进入新半场时广播半场开球 HUD 并吹 whistle_1（不含上半场——上半场走开场开球 MatchStart）。 */
    fun triggerHalfKickoff(server: MinecraftServer, level: ServerLevel) {
        val ms = MatchState
        if (ms.halfKickoffBroadcasted) return
        val phaseKey = when (ms.currentPhase) {
            MatchPhase.SECOND_HALF -> "match.phase.second_half"
            MatchPhase.EXTRA_FIRST -> "match.phase.extra_first"
            MatchPhase.EXTRA_SECOND -> "match.phase.extra_second"
            else -> return
        }
        val lastHalf = ms.lastHalfKickoffTeam ?: TeamSide.A
        val kickoffTeam = if (lastHalf == TeamSide.A) TeamSide.B else TeamSide.A
        ms.halfKickoffBroadcasted = true
        ms.lastHalfKickoffTeam = kickoffTeam
        ms.kickoffTeam = kickoffTeam
        ms.beginKickoffPhase(MatchKickoffTiming.POST_GOAL_LOCK_MS, KickoffWhistleContext.HALF)
        ms.resetFootball(level)
        val nameA = ms.getTeamName(TeamSide.A).string
        val nameB = ms.getTeamName(TeamSide.B).string
        StaminaState.onHalfSwitch(server)
        broadcastHalfKickoff(server, kickoffTeam, phaseKey, nameA, nameB)
    }

    fun broadcastHalfKickoff(server: MinecraftServer, kickoffTeam: TeamSide, phaseKey: String, teamAName: String, teamBName: String) {
        FootballSounds.playMatchWhistle(server, 1)
        for (uuid in MatchState.teamAPlayers) {
            val player = server.playerList.getPlayer(uuid) ?: continue
            ServerPlayNetworking.send(player, HalfKickoffS2CPayload(kickoffTeam, kickoffTeam == TeamSide.A, phaseKey, teamAName, teamBName))
        }
        for (uuid in MatchState.teamBPlayers) {
            val player = server.playerList.getPlayer(uuid) ?: continue
            ServerPlayNetworking.send(player, HalfKickoffS2CPayload(kickoffTeam, kickoffTeam == TeamSide.B, phaseKey, teamAName, teamBName))
        }
    }

    fun broadcastMatchResult(
        server: MinecraftServer,
        teamAScore: Int,
        teamBScore: Int,
        teamAName: String,
        teamBName: String,
        isDraw: Boolean,
        wonByPenalties: Boolean = false,
        penaltyScoreA: Int = 0,
        penaltyScoreB: Int = 0,
        penaltyWinner: TeamSide? = null,
    ) {
        if (!wonByPenalties) {
            FootballSounds.playMatchWhistle(server, 2)
        }
        val payload = MatchResultS2CPayload(
            teamAScore, teamBScore, teamAName, teamBName, isDraw,
            wonByPenalties, penaltyScoreA, penaltyScoreB, penaltyWinner,
        )
        for (player in server.playerList.players) {
            ServerPlayNetworking.send(player, payload)
        }
    }

    fun broadcastPenaltyShootoutSync(server: MinecraftServer) {
        val kickerName = PenaltyShootoutState.currentKickerUuid?.let { uuid ->
            server.playerList.getPlayer(uuid)?.gameProfile?.name
        } ?: ""
        val payload = PenaltyShootoutSyncS2CPayload(
            active = PenaltyShootoutState.isActive(),
            penaltyScoreA = PenaltyShootoutState.penaltyScoreA,
            penaltyScoreB = PenaltyShootoutState.penaltyScoreB,
            suddenDeath = PenaltyShootoutState.suddenDeath,
            totalKicksTaken = PenaltyShootoutState.totalKicksTaken,
            currentKickerTeam = PenaltyShootoutState.currentKickerTeam,
            kickerName = kickerName,
            kickPhase = PenaltyShootoutState.kickPhase,
            activeDefendingTeam = PenaltyShootoutState.activeDefendingTeam,
            firstKickTeam = PenaltyShootoutState.firstKickTeam,
        )
        for (player in server.playerList.players) {
            ServerPlayNetworking.send(player, payload)
        }
    }

    fun broadcastPenaltyKickStart(server: MinecraftServer) {
        val kickerName = PenaltyShootoutState.currentKickerUuid?.let { uuid ->
            server.playerList.getPlayer(uuid)?.gameProfile?.name
        } ?: "?"
        val cfg = MatchConfigHolder.current
        val payload = PenaltyKickStartS2CPayload(
            kickerTeam = PenaltyShootoutState.currentKickerTeam,
            kickerName = kickerName,
            penaltyScoreA = PenaltyShootoutState.penaltyScoreA,
            penaltyScoreB = PenaltyShootoutState.penaltyScoreB,
            kickNumber = PenaltyShootoutState.totalKicksTaken + 1,
            suddenDeath = PenaltyShootoutState.suddenDeath,
            teamAName = cfg.teamAName,
            teamBName = cfg.teamBName,
        )
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
            teamAName = cfg.teamAName,
            teamBName = cfg.teamBName,
            isRunning = MatchState.isRunning,
            halfTimeMinutes = cfg.halfTimeMinutes,
            stoppageTimeMaxMinutes = cfg.stoppageTimeMaxMinutes,
            extraTimeHalfMinutes = cfg.extraTimeHalfMinutes,
            enableStoppageTime = cfg.enableStoppageTime,
            enableExtraTime = cfg.enableExtraTime,
            enablePenaltyShootout = cfg.enablePenaltyShootout,
            enableFootballPositionIndicator = cfg.accessibility.enableFootballPositionIndicator,
            dynamicStoppageTicks = MatchState.dynamicStoppageTicks,
        ))
    }

    fun sendServerConfigSync(player: ServerPlayer, config: FootballServerConfig, openEditor: Boolean = false) {
        ServerPlayNetworking.send(player, ServerConfigSyncS2CPayload(config, openEditor))
    }

    fun broadcastServerConfig(server: MinecraftServer, openEditor: Boolean = false) {
        val payload = ServerConfigSyncS2CPayload(FootballServerConfigHolder.current, openEditor)
        for (player in server.playerList.players) {
            ServerPlayNetworking.send(player, payload)
        }
    }

    fun sendStaminaSync(player: ServerPlayer, stamina: Float, maxStamina: Float, boostSprintActive: Boolean = false) {
        ServerPlayNetworking.send(player, StaminaSyncS2CPayload(stamina, maxStamina, boostSprintActive))
    }

    /** 玩家加入时同步服务端配置与体力。 */
    fun syncPlayerJoin(player: ServerPlayer) {
        sendServerConfigSync(player, FootballServerConfigHolder.current, openEditor = false)
        StaminaState.syncToPlayer(player)
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

    fun syncSlideTackleState(player: ServerPlayer, sliding: Boolean, cooldownUntilTick: Long = 0L) {
        val payload = SlideTackleStateS2CPayload(player.id, sliding, cooldownUntilTick)
        val server = player.level().server ?: return
        for (target in server.playerList.players) {
            ServerPlayNetworking.send(target, payload)
        }
    }

    /** 同步哨子吹哨：各客户端在吹哨玩家实体上播放绑定音效。 */
    fun syncWhistleUse(player: ServerPlayer) {
        val payload = WhistleUseS2CPayload(player.id)
        val server = player.level().server ?: return
        for (target in server.playerList.players) {
            ServerPlayNetworking.send(target, payload)
        }
    }

    fun syncGoalkeeperRole(uuid: UUID, server: MinecraftServer?) {
        val player = server?.playerList?.getPlayer(uuid) ?: return
        PlayerRoleState.syncRoleToPlayer(player)
    }

    fun broadcastRestartKickoff(server: MinecraftServer, kickoffTeam: TeamSide, goalLineOut: Boolean) {
        for (uuid in MatchState.teamAPlayers) {
            val player = server.playerList.getPlayer(uuid) ?: continue
            ServerPlayNetworking.send(player, PostGoalKickoffS2CPayload(kickoffTeam, kickoffTeam == TeamSide.A, goalLineOut))
        }
        for (uuid in MatchState.teamBPlayers) {
            val player = server.playerList.getPlayer(uuid) ?: continue
            ServerPlayNetworking.send(player, PostGoalKickoffS2CPayload(kickoffTeam, kickoffTeam == TeamSide.B, goalLineOut))
        }
    }

    fun resetMatchToPreMatch(server: MinecraftServer) {
        for (player in server.playerList.players) {
            GoalkeeperUtil.findHeldFootball(player)?.dropAt(player)
        }
        GoalkeeperHoldLock.clearAll(server)
        MatchPauseFootballState.onResume(server)
        MatchState.reset()
        syncAllGoalkeeperRoles(server)
        broadcastMatchReset(server)
        broadcastTimerSync(server)
    }

    private fun syncAllGoalkeeperRoles(server: MinecraftServer) {
        for (player in server.playerList.players) {
            sendGoalkeeperRole(player, PlayerRoleState.isGoalkeeper(player))
        }
    }

    fun broadcastMatchReset(server: MinecraftServer) {
        for (player in server.playerList.players) {
            ServerPlayNetworking.send(player, MatchResetS2CPayload.INSTANCE)
        }
    }

    /** 比赛暂停/继续：全场 whistle_1 + Banner（与哨声同范围，所有在线玩家）。 */
    fun broadcastMatchPause(server: MinecraftServer, paused: Boolean) {
        if (paused) {
            MatchPauseFootballState.onPause(server)
        } else {
            MatchPauseFootballState.onResume(server)
        }
        FootballSounds.playMatchWhistle(server, 1)
        val payload = MatchPauseS2CPayload(paused)
        for (player in server.playerList.players) {
            ServerPlayNetworking.send(player, payload)
        }
    }

    fun broadcastGoalLineOut(
        server: MinecraftServer,
        outType: net.astrorbits.football.match.GoalLineOutType,
        restartTeam: TeamSide,
        ballX: Double,
        ballY: Double,
        ballZ: Double,
        lastTouchPlayerName: String,
        lastTouchTeam: TeamSide?,
    ) {
        FootballSounds.playMatchWhistle(server, 6)
        val touchCode = GoalLineOutS2CPayload.encodeTouchTeam(lastTouchTeam)
        for (uuid in MatchState.teamAPlayers) {
            val player = server.playerList.getPlayer(uuid) ?: continue
            ServerPlayNetworking.send(
                player,
                GoalLineOutS2CPayload(
                    outType, restartTeam, restartTeam == TeamSide.A,
                    ballX, ballY, ballZ, lastTouchPlayerName, touchCode,
                ),
            )
        }
        for (uuid in MatchState.teamBPlayers) {
            val player = server.playerList.getPlayer(uuid) ?: continue
            ServerPlayNetworking.send(
                player,
                GoalLineOutS2CPayload(
                    outType, restartTeam, restartTeam == TeamSide.B,
                    ballX, ballY, ballZ, lastTouchPlayerName, touchCode,
                ),
            )
        }
    }

    fun sendMatchHudDebugPreview(player: ServerPlayer) {
        ServerPlayNetworking.send(player, MatchHudDebugS2CPayload.INSTANCE)
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
        FootballSounds.playMatchWhistle(server, 4)
        val payload = GoalScoredS2CPayload(
            scoringTeam,
            scorerName,
            scorerTeam,
            teamAScore,
            teamBScore,
            ownGoal,
            MatchState.getTeamName(TeamSide.A).string,
            MatchState.getTeamName(TeamSide.B).string,
        )
        StaminaState.onGoalScored(server)
        for (player in server.playerList.players) {
            ServerPlayNetworking.send(player, payload)
        }
    }

    fun broadcastInvalidGoal(
        server: MinecraftServer,
        scorerName: String,
        scorerTeam: TeamSide,
        teamAScore: Int,
        teamBScore: Int,
    ) {
        val payload = InvalidGoalS2CPayload(
            scorerName,
            scorerTeam,
            teamAScore,
            teamBScore,
            MatchState.getTeamName(TeamSide.A).string,
            MatchState.getTeamName(TeamSide.B).string,
        )
        for (player in server.playerList.players) {
            ServerPlayNetworking.send(player, payload)
        }
    }
}
