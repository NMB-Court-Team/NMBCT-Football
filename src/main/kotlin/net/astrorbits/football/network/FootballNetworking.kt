package net.astrorbits.football.network

import net.astrorbits.football.config.server.FootballServerConfigHolder
import net.astrorbits.football.input.FootballPlayerActions
import net.astrorbits.football.match.MatchConfigHolder
import net.astrorbits.football.match.PlayerRoleState
import net.astrorbits.football.match.TeamSide
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.PlayerLookup
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

    fun broadcastKickoffBallTouched(server: MinecraftServer) {
        for (player in server.playerList.players) {
            ServerPlayNetworking.send(player, KickoffBallTouchedS2CPayload.INSTANCE)
        }
    }

    /** 向玩家同步连接器当前已选锚点（客户端预览粒子用）。 */
    fun sendGoalNetConnectorSelection(player: ServerPlayer, anchorBlocks: List<net.minecraft.core.BlockPos>) {
        ServerPlayNetworking.send(player, GoalNetConnectorSelectionS2CPayload(anchorBlocks))
    }

    /** 向所有正在跟踪该球网实体的玩家同步节点形变。 */
    fun broadcastGoalNetState(
        entity: net.minecraft.world.entity.Entity,
        cols: Int,
        rows: Int,
        relativePositions: FloatArray,
    ) {
        val payload = GoalNetStateS2CPayload(entity.id, cols, rows, relativePositions.copyOf())
        for (player in PlayerLookup.tracking(entity)) {
            ServerPlayNetworking.send(player, payload)
        }
    }
}
