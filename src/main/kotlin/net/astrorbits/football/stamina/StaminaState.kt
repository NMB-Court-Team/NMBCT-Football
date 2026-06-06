package net.astrorbits.football.stamina

import net.astrorbits.football.config.FootballConfigs
import net.astrorbits.football.config.server.StaminaMechanismSettings
import net.astrorbits.football.input.FootballMovementInputUtil
import net.astrorbits.football.network.FootballNetworking
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * 服务端权威的玩家体力状态。
 */
object StaminaState {
    private val staminaByPlayer = ConcurrentHashMap<UUID, Float>()
    private val ticksSinceConsume = ConcurrentHashMap<UUID, Int>()
    private val sprintDrainAccumulator = ConcurrentHashMap<UUID, Float>()
    private val recoveryAccumulator = ConcurrentHashMap<UUID, Float>()
    private val wasOnGround = ConcurrentHashMap<UUID, Boolean>()

    private fun settings(): StaminaMechanismSettings = FootballConfigs.server.staminaMechanism

    fun getStamina(playerId: UUID): Float = staminaByPlayer[playerId] ?: settings().maxStamina

    /**
     * 扣除体力；成功时重置回复计时并同步客户端。
     * @return 是否扣减成功（当前体力 > 0 且扣后仍 >= 0，或部分扣到 0）
     */
    fun tryConsume(player: ServerPlayer, amount: Float): Boolean {
        if (amount <= 0f || player.isSpectator || player.isCreative) {
            return true
        }
        val cfg = settings()
        val id = player.uuid
        var stamina = staminaByPlayer.getOrPut(id) { cfg.maxStamina }.coerceIn(0f, cfg.maxStamina)
        if (stamina <= 0f) {
            return false
        }
        stamina = (stamina - amount).coerceAtLeast(0f)
        ticksSinceConsume[id] = 0
        recoveryAccumulator[id] = 0f
        setStamina(player, stamina)
        if (stamina <= 0f) {
            BoostSprintState.setRequested(player, false)
        }
        return true
    }

    fun tickServer(server: MinecraftServer) {
        for (player in server.playerList.players) {
            tickPlayer(player)
            BoostSprintState.tickPlayer(player)
        }
    }

    fun tickPlayer(player: ServerPlayer) {
        if (player.isSpectator || player.isCreative) {
            resetPlayer(player.uuid, sync = true, player)
            return
        }

        val cfg = settings()
        val id = player.uuid
        var stamina = staminaByPlayer.getOrPut(id) { cfg.maxStamina }.coerceIn(0f, cfg.maxStamina)
        var consumed = false

        if (player.isSprinting && FootballMovementInputUtil.hasSprintForwardImpulse(player)) {
            var perSecond = cfg.sprintDrainPerSecond
            if (BoostSprintState.isActive(id)) {
                perSecond *= cfg.boostSprintStaminaDrainMultiplier
            }
            val perTick = perSecond / StaminaMechanismSettings.TICKS_PER_SECOND
            var acc = sprintDrainAccumulator.getOrDefault(id, 0f) + perTick
            while (acc >= 1f) {
                acc -= 1f
                stamina = (stamina - 1f).coerceAtLeast(0f)
                consumed = true
            }
            sprintDrainAccumulator[id] = acc
            if (stamina <= 0f) {
                BoostSprintState.setRequested(player, false)
            }
        } else {
            sprintDrainAccumulator[id] = 0f
        }

        val onGround = player.onGround()
        val was = wasOnGround.getOrDefault(id, onGround)
        if (!onGround && was && player.deltaMovement.y > 0.2) {
            stamina = (stamina - cfg.jumpCost).coerceAtLeast(0f)
            consumed = true
            if (stamina <= 0f) {
                BoostSprintState.setRequested(player, false)
            }
        }
        wasOnGround[id] = onGround

        if (consumed) {
            ticksSinceConsume[id] = 0
            recoveryAccumulator[id] = 0f
        } else {
            val ticks = ticksSinceConsume.compute(id) { _, old -> (old ?: 0) + 1 } ?: 1
            if (ticks > cfg.recoveryDelayTicks) {
                val perTick = cfg.recoveryPerSecond / StaminaMechanismSettings.TICKS_PER_SECOND
                var acc = recoveryAccumulator.getOrDefault(id, 0f) + perTick
                while (acc >= 1f) {
                    acc -= 1f
                    stamina = (stamina + 1f).coerceAtMost(cfg.maxStamina)
                }
                recoveryAccumulator[id] = acc
            }
        }

        setStamina(player, stamina)
    }

    fun onMatchStart(server: MinecraftServer) {
        val max = settings().maxStamina
        for (player in server.playerList.players) {
            applyStamina(player, max, clearTimers = true)
        }
    }

    fun onHalfSwitch(server: MinecraftServer) {
        val cfg = settings()
        for (player in server.playerList.players) {
            val next = (getStamina(player.uuid) + cfg.halfTimeRecoveryAmount()).coerceAtMost(cfg.maxStamina)
            applyStamina(player, next, clearTimers = true)
        }
    }

    fun onGoalScored(server: MinecraftServer) {
        val cfg = settings()
        for (player in server.playerList.players) {
            val next = (getStamina(player.uuid) + cfg.goalRecoveryAmount()).coerceAtMost(cfg.maxStamina)
            applyStamina(player, next, clearTimers = true)
        }
    }

    fun syncToPlayer(player: ServerPlayer) {
        FootballNetworking.sendStaminaSync(
            player,
            getStamina(player.uuid),
            settings().maxStamina,
            BoostSprintState.isActive(player.uuid),
        )
    }

    fun removePlayer(playerId: UUID) {
        staminaByPlayer.remove(playerId)
        ticksSinceConsume.remove(playerId)
        sprintDrainAccumulator.remove(playerId)
        recoveryAccumulator.remove(playerId)
        wasOnGround.remove(playerId)
        BoostSprintState.removePlayer(playerId)
    }

    private fun resetPlayer(playerId: UUID, sync: Boolean, player: ServerPlayer?) {
        staminaByPlayer[playerId] = settings().maxStamina
        ticksSinceConsume.remove(playerId)
        sprintDrainAccumulator.remove(playerId)
        recoveryAccumulator.remove(playerId)
        wasOnGround.remove(playerId)
        if (sync && player != null) {
            syncToPlayer(player)
        }
    }

    private fun setStamina(player: ServerPlayer, value: Float) {
        val id = player.uuid
        val previous = staminaByPlayer.put(id, value)
        if (previous == null || abs(previous - value) > 1e-3f) {
            FootballNetworking.sendStaminaSync(
                player,
                value,
                settings().maxStamina,
                BoostSprintState.isActive(id),
            )
        }
    }

    private fun applyStamina(player: ServerPlayer, value: Float, clearTimers: Boolean) {
        val id = player.uuid
        staminaByPlayer[id] = value
        if (clearTimers) {
            ticksSinceConsume[id] = 0
            sprintDrainAccumulator[id] = 0f
            recoveryAccumulator[id] = 0f
            wasOnGround[id] = player.onGround()
        }
        FootballNetworking.sendStaminaSync(
            player,
            value,
            settings().maxStamina,
            BoostSprintState.isActive(id),
        )
    }
}
