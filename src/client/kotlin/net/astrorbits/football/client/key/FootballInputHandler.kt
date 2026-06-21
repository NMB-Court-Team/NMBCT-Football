package net.astrorbits.football.client.key
import net.astrorbits.football.client.*
import net.astrorbits.football.client.match.MatchStartClient
import net.astrorbits.football.client.match.PenaltyShootoutClient
import net.astrorbits.football.config.FootballConfigs
import net.astrorbits.football.input.FootballInputConfig
import net.astrorbits.football.input.FootballMovementInputUtil
import net.astrorbits.football.match.MatchPhase
import net.astrorbits.football.match.MatchState
import net.astrorbits.football.match.SetPieceKind
import net.astrorbits.football.network.FootballActionC2SPayload
import net.astrorbits.football.network.FootballActionType
import net.astrorbits.football.util.KickChargeUtil
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.player.LocalPlayer

object FootballInputHandler {
    private var kickPrevTickPressed = false
    private var divePrevTickPressed = false
    private var catchPrevTickPressed = false
    private var trapPrevTickPressed = false
    private var chipPrevTickPressed = false
    private var dribblePrevTickPressed = false
    private var slidePrevTickPressed = false
    private var interruptPrevTickPressed = false
    private var consecutiveSprintTicks = 0
    private var wasSlidingLastTick = false
    private var kickPressStartMs: Long? = null
    private var kickReleaseHeldMsOverride: Long? = null
    private var kickRealtimePrevDown = false
    private var divePressStartMs: Long? = null
    private var diveReleaseHeldMsOverride: Long? = null
    private var diveRealtimePrevDown = false
    /** 按 Shift 打断鱼跃蓄力后，忽略本次右键松开触发的鱼跃。 */
    private var diveChargeCancelled = false
    private var fullChargeHoldTicks = 0
    var shootChargeRatio: Float = 0f
        private set
    var isChargingShoot: Boolean = false
        private set
    var shootChargePhase: KickChargeUtil.Phase = KickChargeUtil.Phase.NONE
        private set
    var throwChargeRatio: Float = 0f
        private set
    var isChargingThrow: Boolean = false
        private set
    var throwChargePhase: KickChargeUtil.Phase = KickChargeUtil.Phase.NONE
        private set
    data class KickChargeDisplayState(
        val ratio: Float,
        val phase: KickChargeUtil.Phase,
    )
    /** 正在按住右键进行鱼跃或抛出蓄力且尚未被 Shift 打断（供 HUD 提示用）。 */
    fun isDiveOrThrowChargeActive(): Boolean {
        syncDivePressRealtimeClock()
        return FootballKeyBindings.GK_DIVE.isDown &&
            divePressStartMs != null &&
            !diveChargeCancelled
    }
    /** 正在按住 R 进行传球/射门蓄力。 */
    fun isKickChargeActive(): Boolean {
        syncKickPressRealtimeClock()
        return !GoalkeeperStateClient.isHoldingBall &&
            FootballKeyBindings.KICK.isDown &&
            kickPressStartMs != null
    }
    fun isAnyChargeActive(): Boolean = isDiveOrThrowChargeActive() || isKickChargeActive()
    /** 基于实时时钟采样蓄力（供 HUD 每帧调用，避免仅 tick 更新导致进度条卡顿）。 */
    fun liveKickChargeDisplay(): KickChargeDisplayState? {
        val player = net.minecraft.client.Minecraft.getInstance().player
        if (player != null && FootballOperabilityClient.isKickoffChargeFrozen(player)) {
            return null
        }
        syncDivePressRealtimeClock()
        syncKickPressRealtimeClock()
        val holdingBall = GoalkeeperStateClient.isHoldingBall
        val diveDown = FootballKeyBindings.GK_DIVE.isDown
        val kickDown = FootballKeyBindings.KICK.isDown
        if (holdingBall && diveDown) {
            val start = divePressStartMs ?: return null
            val heldMs = System.currentTimeMillis() - start
            val settings = chargeSettings()
            val phase = KickChargeUtil.computePhase(heldMs, settings)
            if (phase == KickChargeUtil.Phase.NONE) return null
            return KickChargeDisplayState(
                ratio = KickChargeUtil.computeRatio(heldMs, settings),
                phase = phase,
            )
        }
        if (!holdingBall && diveDown && !isPassShootChargeBlockingDive() && player != null &&
            FootballOperabilityClient.canPrepareGoalkeeperDiveCharge(player)
        ) {
            val start = divePressStartMs ?: return null
            if (diveChargeCancelled) return null
            val heldMs = System.currentTimeMillis() - start
            val settings = chargeSettings()
            if (!KickChargeUtil.isLinearCharging(heldMs, settings)) return null
            return KickChargeDisplayState(
                ratio = KickChargeUtil.computeLinearRatio(heldMs, settings),
                phase = KickChargeUtil.Phase.RISING,
            )
        }
        if (!holdingBall && kickDown && !isDiveChargeBlockingPassShoot()) {
            val start = kickPressStartMs ?: return null
            val heldMs = System.currentTimeMillis() - start
            val settings = chargeSettings()
            val phase = KickChargeUtil.computePhase(heldMs, settings)
            if (phase == KickChargeUtil.Phase.NONE) return null
            return KickChargeDisplayState(
                ratio = KickChargeUtil.computeRatio(heldMs, settings),
                phase = phase,
            )
        }
        return null
    }
    private var dribbleTickCounter = 0
    /** 传球/射门/挑球后须松开带球键再按，避免仍按住时立刻重新拉球。 */
    private var dribbleResumeBlocked = false
    fun registerTickEvent() {
        ClientTickEvents.END_CLIENT_TICK.register reg@{ client ->
            val player = client.player
            val level = client.level
            if (player == null || level == null) {
                resetTransientState(notifyDribbleEnd = false)
                resetSlideSprintTicks()
                wasSlidingLastTick = false
                return@reg
            }
            tickSprintCounter(player)
            if (client.screen != null || client.isPaused) {
                resetTransientState(player)
                updatePrevTickPressed()
                return@reg
            }
            tickBoostSprint(player)
            clearKickoffFrozenChargeState(player)
            if (MatchStartClient.isLocked &&
                !FootballOperabilityClient.bypassesKickoffLockForFootballInput(player)
            ) {
                handleSlideTacklePress(player)
                updatePrevTickPressed()
                primeLockedRestartKey(player)
                return@reg
            }
            if (!player.mainHandItem.isEmpty) {
                resetFootballActionState(player)
                updatePrevTickPressed()
                return@reg
            }
            try {
                handleFootballInput(player)
                KickCurveClient.tick(player)
            } finally {
                updatePrevTickPressed()
            }
        }
    }
    fun sendItemThrow(player: LocalPlayer) {
        sendAction(player, FootballActionType.ITEM_THROW, 0f, 0L, 0)
    }
    private fun handleFootballInput(player: LocalPlayer) {
        if (SlideTackleStateClient.isSliding(player.id)) {
            kickPressStartMs = null
            divePressStartMs = null
            resetChargeDisplay()
            endDribbleForSlide(player)
            handleSlideTacklePress(player)
            return
        }
        val throwInTaker = FootballOperabilityClient.isThrowInTaker(player)
        val goalKickPlacedKicker = FootballOperabilityClient.isGoalKickPlacedKicker(player)
        val holdingBall = (GoalkeeperStateClient.isHoldingBall && !goalKickPlacedKicker) ||
            (throwInTaker && SetPieceClient.isMovementFrozen(player.uuid))
        tryInterruptCharges(player)
        val releaseLocked = holdingBall &&
            GoalkeeperStateClient.isHoldReleaseLocked() &&
            !throwInTaker
        val canGk = FootballOperabilityClient.canUseGoalkeeperActions()
        val canGoalKickCatch = FootballOperabilityClient.canUseGoalKickCatch(player)
        if (holdingBall) {
            if (!releaseLocked) {
                handleThrowLongPress(player)
            } else {
                handleThrowLongPressBlocked()
            }
            handleDropPress(player)
        } else {
            if (canGk) {
                tickGoalkeeperDiveChargeDrain(player)
                handleDivePress(player)
            }
            if (canGk || canGoalKickCatch) {
                handleCatchPress(player)
            }
            handleKickLongPress(player)
            handleTrapPress(player, FootballActionType.TRAP)
            handleChipPress(player)
            handleDribbleHold(player)
            handleSlideTacklePress(player)
        }
    }
    fun onGoalkeeperBeganHoldingBall() {
        kickPressStartMs = null
        divePressStartMs = null
        resetChargeDisplay()
    }
    private fun tickGoalkeeperDiveChargeDrain(player: LocalPlayer) {
        if (!FootballOperabilityClient.canPrepareGoalkeeperDiveCharge(player)) {
            if (isDiveOrThrowChargeActive() && !GoalkeeperStateClient.isHoldingBall) {
                cancelDiveCharge()
            }
            return
        }
        val sm = FootballConfigs.server.staminaMechanism
        val display = liveKickChargeDisplay()
        if (
            isDiveOrThrowChargeActive() &&
            !GoalkeeperStateClient.isHoldingBall &&
            display != null &&
            display.ratio >= 1f - 1e-4f
        ) {
            fullChargeHoldTicks++
            if (fullChargeHoldTicks > sm.gkDiveFullChargeHoldDrainDelayTicks) {
                sendAction(player, FootballActionType.GK_DIVE_CHARGE_DRAIN, 1f, 0L, 0)
            }
        } else {
            fullChargeHoldTicks = 0
        }
        if (StaminaClient.stamina <= 0f && isDiveOrThrowChargeActive() && !GoalkeeperStateClient.isHoldingBall) {
            cancelDiveCharge()
        }
    }
    /** 传球/射门蓄力进行中时，禁止开始鱼跃扑救蓄力。 */
    private fun isPassShootChargeBlockingDive(): Boolean {
        if (GoalkeeperStateClient.isHoldingBall) {
            return false
        }
        return kickPressStartMs != null && FootballKeyBindings.KICK.isDown
    }

    /** 鱼跃扑救蓄力进行中时，禁止开始传球/射门蓄力。 */
    private fun isDiveChargeBlockingPassShoot(): Boolean {
        if (GoalkeeperStateClient.isHoldingBall) {
            return false
        }
        return divePressStartMs != null &&
            FootballKeyBindings.GK_DIVE.isDown &&
            !diveChargeCancelled
    }

    private fun handleDivePress(player: LocalPlayer) {
        if (FootballOperabilityClient.isKickoffChargeFrozen(player)) return
        when (getDiveLongPressState()) {
            LongPressState.STARTED -> {
                if (isPassShootChargeBlockingDive()) {
                    return
                }
                diveChargeCancelled = false
                if (divePressStartMs == null) {
                    divePressStartMs = System.currentTimeMillis()
                }
            }
            LongPressState.BEING_PRESSED -> {
                if (isPassShootChargeBlockingDive()) {
                    cancelDiveCharge()
                }
            }
            LongPressState.FINISHED -> {
                val heldMs = diveReleaseHeldMsOverride ?: divePressStartMs?.let { System.currentTimeMillis() - it } ?: 0L
                diveReleaseHeldMsOverride = null
                divePressStartMs = null
                resetThrowChargeDisplay()
                if (diveChargeCancelled) {
                    diveChargeCancelled = false
                    return
                }
                if (!FootballOperabilityClient.canExecuteGoalkeeperDive(player)) {
                    return
                }
                val settings = chargeSettings()
                val chargeRatio = KickChargeUtil.computeLinearRatio(heldMs.coerceAtLeast(0L), settings)
                maybeSendFootballAction(player, FootballActionType.GK_DIVE, chargeRatio, heldMs.coerceAtLeast(0L), buildDiveFlags(player))
            }
            LongPressState.NONE -> Unit
        }
    }
    private fun handleThrowLongPress(player: LocalPlayer) {
        if (FootballOperabilityClient.isKickoffChargeFrozen(player)) return
        when (getDiveLongPressState()) {
            LongPressState.STARTED -> {
                if (divePressStartMs == null) {
                    divePressStartMs = System.currentTimeMillis()
                }
                resetKickChargeDisplay()
            }
            LongPressState.BEING_PRESSED -> {
                if (!canChargeThrow(player)) {
                    cancelDiveOrThrowCharge(notifyServer = false)
                    return
                }
                val start = divePressStartMs ?: return
                updateThrowCharge(System.currentTimeMillis() - start)
            }
            LongPressState.FINISHED -> {
                val start = divePressStartMs
                if (start != null) {
                    val heldMs = diveReleaseHeldMsOverride ?: (System.currentTimeMillis() - start)
                    diveReleaseHeldMsOverride = null
                    val flags = buildFlags(player)
                    when {
                        GoalkeeperStateClient.isHoldReleaseLocked() &&
                            !FootballOperabilityClient.isThrowInTaker(player) -> Unit
                        heldMs < FootballInputConfig.TAP_MAX_MS ->
                            maybeSendFootballAction(player, FootballActionType.GK_THROW_SHORT, 0f, heldMs, flags)
                        KickChargeUtil.isCharging(heldMs, chargeSettings()) ->
                            maybeSendFootballAction(
                                player,
                                FootballActionType.GK_THROW_LONG,
                                throwChargeRatio,
                                heldMs,
                                flags,
                            )
                        else ->
                            maybeSendFootballAction(player, FootballActionType.GK_THROW_SHORT, 0f, heldMs, flags)
                    }
                }
                divePressStartMs = null
                resetThrowChargeDisplay()
            }
            LongPressState.NONE -> Unit
        }
    }
    private fun handleThrowLongPressBlocked() {
        when (getDiveLongPressState()) {
            LongPressState.FINISHED -> {
                divePressStartMs = null
                resetThrowChargeDisplay()
            }
            LongPressState.NONE -> Unit
            else -> Unit
        }
    }
    private fun handleCatchPress(player: LocalPlayer) {
        if (FootballKeyBindings.GK_CATCH.isDown && !catchPrevTickPressed && canSendFootballAction(player, FootballActionType.GK_CATCH)) {
            sendAction(player, FootballActionType.GK_CATCH, 0f, 0L, 0)
        }
    }
    private fun handleDropPress(player: LocalPlayer) {
        if (FootballKeyBindings.GK_CATCH.isDown && !catchPrevTickPressed && canSendFootballAction(player, FootballActionType.GK_DROP)) {
            sendAction(player, FootballActionType.GK_DROP, 0f, 0L, 0)
        }
    }
    private fun tryInterruptCharges(player: LocalPlayer) {
        if (!FootballKeyBindings.INTERRUPT_CHARGE.isDown || interruptPrevTickPressed) {
            return
        }
        if (kickPressStartMs != null && FootballKeyBindings.KICK.isDown) {
            cancelKickCharge()
        }
        if (divePressStartMs != null && FootballKeyBindings.GK_DIVE.isDown) {
            if (GoalkeeperStateClient.isHoldingBall) {
                cancelDiveOrThrowCharge(notifyServer = false)
            } else {
                cancelDiveCharge()
            }
        }
    }
    private fun cancelDiveCharge() {
        diveChargeCancelled = true
        fullChargeHoldTicks = 0
        divePressStartMs = null
        diveReleaseHeldMsOverride = null
        resetThrowChargeDisplay()
        if (FootballOperabilityClient.canUseGoalkeeperActions() && !GoalkeeperStateClient.isHoldingBall) {
            val player = net.minecraft.client.Minecraft.getInstance().player
            if (player != null) {
                sendAction(player, FootballActionType.GK_DIVE_CHARGE_CANCEL, 0f, 0L, 0)
            }
        }
    }
    private fun cancelDiveOrThrowCharge(notifyServer: Boolean) {
        diveChargeCancelled = true
        fullChargeHoldTicks = 0
        divePressStartMs = null
        diveReleaseHeldMsOverride = null
        resetThrowChargeDisplay()
        if (notifyServer && FootballOperabilityClient.canUseGoalkeeperActions() && !GoalkeeperStateClient.isHoldingBall) {
            val player = net.minecraft.client.Minecraft.getInstance().player
            if (player != null) {
                sendAction(player, FootballActionType.GK_DIVE_CHARGE_CANCEL, 0f, 0L, 0)
            }
        }
    }
    private fun handleKickLongPress(player: LocalPlayer) {
        if (FootballOperabilityClient.isKickoffChargeFrozen(player)) return
        when (getKickLongPressState()) {
            LongPressState.STARTED -> {
                if (isDiveChargeBlockingPassShoot()) {
                    return
                }
                if (kickPressStartMs == null) {
                    kickPressStartMs = System.currentTimeMillis()
                }
                resetKickChargeDisplay()
            }
            LongPressState.BEING_PRESSED -> {
                if (isDiveChargeBlockingPassShoot()) {
                    cancelKickCharge()
                    return
                }
                if (!canChargeKick(player)) {
                    cancelKickCharge()
                    return
                }
                val start = kickPressStartMs ?: return
                updateShootCharge(System.currentTimeMillis() - start)
            }
            LongPressState.FINISHED -> {
                val start = kickPressStartMs
                if (start != null) {
                    val heldMs = kickReleaseHeldMsOverride ?: (System.currentTimeMillis() - start)
                    kickReleaseHeldMsOverride = null
                    val flags = buildFlags(player)
                    when {
                        heldMs < FootballInputConfig.TAP_MAX_MS -> {
                            maybeSendFootballAction(player, FootballActionType.PASS, 0f, heldMs, flags)
                            blockDribbleResume(player)
                        }
                        KickChargeUtil.isCharging(heldMs, chargeSettings()) -> {
                            if (canSendFootballAction(player, FootballActionType.SHOOT)) {
                                maybeSendFootballAction(player, FootballActionType.SHOOT, shootChargeRatio, heldMs, flags)
                                KickCurveClient.begin(player.yHeadRot, shootChargeRatio)
                            }
                            blockDribbleResume(player)
                        }
                        else -> {
                            maybeSendFootballAction(player, FootballActionType.PASS, 0f, heldMs, flags)
                            blockDribbleResume(player)
                        }
                    }
                }
                kickPressStartMs = null
                resetKickChargeDisplay()
            }
            LongPressState.NONE -> Unit
        }
    }
    private fun canChargeKick(player: LocalPlayer): Boolean =
        FootballOperabilityClient.canUseFootballHint(player, player.level(), FootballKeyBindings.KICK)
    private fun canChargeThrow(player: LocalPlayer): Boolean =
        FootballOperabilityClient.canUseFootballHint(player, player.level(), FootballKeyBindings.GK_DIVE)
    private fun cancelKickCharge() {
        kickPressStartMs = null
        kickReleaseHeldMsOverride = null
        resetKickChargeDisplay()
    }
    private fun handleTrapPress(player: LocalPlayer, action: FootballActionType) {
        if (FootballKeyBindings.TRAP.isDown && !trapPrevTickPressed && canSendFootballAction(player, action)) {
            sendAction(player, action, 0f, 0L, 0)
        }
    }
    private fun handleChipPress(player: LocalPlayer) {
        if (FootballKeyBindings.CHIP.isDown && !chipPrevTickPressed && canSendFootballAction(player, FootballActionType.CHIP)) {
            sendAction(player, FootballActionType.CHIP, 0f, 0L, buildFlags(player))
            blockDribbleResume(player)
        }
    }
    private fun handleDribbleHold(player: LocalPlayer) {
        if (FootballOperabilityClient.canUseGoalkeeperActions() &&
            !GoalkeeperStateClient.isHoldingBall &&
            isDiveOrThrowChargeActive()
        ) {
            if (getDribbleLongPressState() == LongPressState.STARTED) {
                cancelDiveCharge()
            }
            return
        }
        if (!FootballKeyBindings.DRIBBLE.isDown) {
            dribbleResumeBlocked = false
            if (dribblePrevTickPressed) {
                DribbleBallIndicatorClient.onDribbleEnd()
                sendAction(player, FootballActionType.DRIBBLE_END, 0f, 0L, 0)
            }
            dribbleTickCounter = 0
            return
        }
        if (dribbleResumeBlocked) {
            return
        }
        if (!FootballMovementInputUtil.hasMovementInput(player, LookAroundClient.movementYaw(player))) {
            return
        }
        if (getDribbleLongPressState() == LongPressState.STARTED) {
            dribbleTickCounter = FootballInputConfig.DRIBBLE_HOLD_PACKET_INTERVAL
        }
        dribbleTickCounter++
        if (dribbleTickCounter < FootballInputConfig.DRIBBLE_HOLD_PACKET_INTERVAL) {
            return
        }
        dribbleTickCounter = 0
        if (!canSendFootballAction(player, FootballActionType.DRIBBLE_HOLD)) {
            return
        }
        sendDribbleAction(player, FootballActionType.DRIBBLE_HOLD, buildDribbleFlags(player))
    }
    private fun handleSlideTacklePress(player: LocalPlayer) {
        val down = FootballKeyBindings.SLIDE_TACKLE.isDown
        val nowTick = player.level().gameTime
        if (down && !slidePrevTickPressed && player.isSprinting && canSlideTackle(nowTick)) {
            resetSlideSprintTicks()
            endDribbleForSlide(player)
            sendAction(player, FootballActionType.SLIDE_TACKLE, 0f, 0L, buildFlags(player))
            return
        }
        if (!down && slidePrevTickPressed && SlideTackleStateClient.isSliding(player.id)) {
            resetSlideSprintTicks()
            sendAction(player, FootballActionType.SLIDE_TACKLE_END, 0f, 0L, 0)
        }
    }
    fun canSlideTackle(nowTick: Long = 0L): Boolean {
        if (isPenaltyKickSetPieceActive()) {
            return false
        }
        if (SlideTackleStateClient.isOnCooldown(nowTick)) {
            return false
        }
        return consecutiveSprintTicks >= FootballInputConfig.SLIDE_MIN_SPRINT_TICKS
    }

    private fun isPenaltyKickSetPieceActive(): Boolean =
        (MatchState.currentPhase == MatchPhase.PENALTIES && PenaltyShootoutClient.active) ||
            SetPieceClient.kind == SetPieceKind.PENALTY_KICK
    fun resetSlideSprintTicks() {
        consecutiveSprintTicks = 0
    }
    private fun tickSprintCounter(player: LocalPlayer) {
        val sliding = SlideTackleStateClient.isSliding(player.id)
        if (sliding) {
            resetSlideSprintTicks()
            wasSlidingLastTick = true
            return
        }
        if (wasSlidingLastTick) {
            resetSlideSprintTicks()
            wasSlidingLastTick = false
        }
        consecutiveSprintTicks = if (player.isSprinting) {
            consecutiveSprintTicks + 1
        } else {
            0
        }
    }
    private fun updateShootCharge(heldMs: Long) {
        val settings = chargeSettings()
        val phase = KickChargeUtil.computePhase(heldMs, settings)
        shootChargePhase = phase
        if (phase == KickChargeUtil.Phase.NONE) {
            isChargingShoot = false
            shootChargeRatio = 0f
            return
        }
        isChargingShoot = true
        shootChargeRatio = KickChargeUtil.computeRatio(heldMs, settings)
    }
    private fun updateThrowCharge(heldMs: Long) {
        val settings = chargeSettings()
        val phase = KickChargeUtil.computePhase(heldMs, settings)
        throwChargePhase = phase
        if (phase == KickChargeUtil.Phase.NONE) {
            isChargingThrow = false
            throwChargeRatio = 0f
            return
        }
        isChargingThrow = true
        throwChargeRatio = KickChargeUtil.computeRatio(heldMs, settings)
    }
    private fun chargeSettings() = FootballConfigs.server.playerInput.charge
    private fun getKickLongPressState(): LongPressState {
        val key = FootballKeyBindings.KICK
        if (key.isDown && !kickPrevTickPressed) return LongPressState.STARTED
        if (key.isDown && kickPrevTickPressed) return LongPressState.BEING_PRESSED
        if (!key.isDown && kickPrevTickPressed) return LongPressState.FINISHED
        return LongPressState.NONE
    }
    private fun getDiveLongPressState(): LongPressState {
        val key = FootballKeyBindings.GK_DIVE
        if (key.isDown && !divePrevTickPressed) return LongPressState.STARTED
        if (key.isDown && divePrevTickPressed) return LongPressState.BEING_PRESSED
        if (!key.isDown && divePrevTickPressed) return LongPressState.FINISHED
        return LongPressState.NONE
    }
    private fun getDribbleLongPressState(): LongPressState {
        val key = FootballKeyBindings.DRIBBLE
        if (key.isDown && !dribblePrevTickPressed) return LongPressState.STARTED
        if (key.isDown && dribblePrevTickPressed) return LongPressState.BEING_PRESSED
        if (!key.isDown && dribblePrevTickPressed) return LongPressState.FINISHED
        return LongPressState.NONE
    }
    private fun updatePrevTickPressed() {
        kickPrevTickPressed = FootballKeyBindings.KICK.isDown
        divePrevTickPressed = FootballKeyBindings.GK_DIVE.isDown
        catchPrevTickPressed = FootballKeyBindings.GK_CATCH.isDown
        trapPrevTickPressed = FootballKeyBindings.TRAP.isDown
        chipPrevTickPressed = FootballKeyBindings.CHIP.isDown
        dribblePrevTickPressed = FootballKeyBindings.DRIBBLE.isDown
        slidePrevTickPressed = FootballKeyBindings.SLIDE_TACKLE.isDown
        interruptPrevTickPressed = FootballKeyBindings.INTERRUPT_CHARGE.isDown
    }

    private fun primeLockedRestartKey(player: LocalPlayer) {
        if (FootballOperabilityClient.shouldPrimeRestartKeyWhileKickoffLocked(player, FootballKeyBindings.KICK)) {
            kickPrevTickPressed = false
        }
        if (FootballOperabilityClient.shouldPrimeRestartKeyWhileKickoffLocked(player, FootballKeyBindings.GK_DIVE)) {
            divePrevTickPressed = false
        }
    }
    private fun buildFlags(player: LocalPlayer): Int {
        var flags = 0
        if (player.isSprinting) {
            flags = flags or FootballInputConfig.FLAG_SPRINT
        }
        return flags
    }
    private fun buildDiveFlags(player: LocalPlayer): Int {
        var flags = 0
        if (!FootballMovementInputUtil.hasMovementInput(player, LookAroundClient.movementYaw(player))) {
            flags = flags or FootballInputConfig.FLAG_DIVE_USE_LOOK
        }
        return flags
    }
    private fun buildDribbleFlags(player: LocalPlayer): Int {
        var flags = buildFlags(player)
        if (LookAroundClient.active) {
            flags = flags or FootballInputConfig.FLAG_LOOK_AROUND
        }
        return flags
    }
    private fun sendDribbleAction(player: LocalPlayer, action: FootballActionType, flags: Int) {
        if (action == FootballActionType.DRIBBLE_HOLD) {
            DribbleBallIndicatorClient.onDribbleHold()
        } else if (action == FootballActionType.DRIBBLE_END) {
            DribbleBallIndicatorClient.onDribbleEnd()
        }
        val lookYaw = if (LookAroundClient.active) {
            LookAroundClient.movementYaw(player)
        } else {
            player.yHeadRot
        }
        sendAction(
            player = player,
            action = action,
            chargeRatio = 0f,
            chargeHeldMs = 0L,
            flags = flags,
            lookYaw = lookYaw,
            lookPitch = player.xRot,
        )
    }
    private fun maybeSendFootballAction(
        player: LocalPlayer,
        action: FootballActionType,
        chargeRatio: Float,
        chargeHeldMs: Long,
        flags: Int,
    ) {
        if (canSendFootballAction(player, action)) {
            sendAction(player, action, chargeRatio, chargeHeldMs, flags)
        }
    }
    private fun canSendFootballAction(player: LocalPlayer, action: FootballActionType): Boolean {
        if (!FootballOperabilityClient.canShowFootballHints(player)) {
            return false
        }
        if (FootballOperabilityClient.isPenaltyKicker(player)) {
            if (!FootballOperabilityClient.isPenaltyKickerAwaitingKick(player)) return false
            if (action != FootballActionType.PASS && action != FootballActionType.SHOOT) return false
        }
        if (FootballOperabilityClient.isSetPieceKickTakerAwaitingKick(player)) {
            if (action != FootballActionType.PASS && action != FootballActionType.SHOOT) return false
        }
        if (action == FootballActionType.DRIBBLE_HOLD) {
            return !FootballOperabilityClient.isPenaltyKicker(player)
        }
        when (action) {
            FootballActionType.GK_DIVE_CHARGE_DRAIN,
            FootballActionType.GK_DIVE_CHARGE_CANCEL,
            -> return FootballOperabilityClient.canPrepareGoalkeeperDiveCharge(player)
            FootballActionType.GK_DIVE,
            -> return FootballOperabilityClient.canExecuteGoalkeeperDive(player)
            else -> Unit
        }
        val key = when (action) {
            FootballActionType.PASS,
            FootballActionType.SHOOT,
            -> FootballKeyBindings.KICK
            FootballActionType.GK_THROW_SHORT,
            FootballActionType.GK_THROW_LONG,
            -> FootballKeyBindings.GK_DIVE
            FootballActionType.GK_DIVE,
            -> FootballKeyBindings.GK_DIVE
            FootballActionType.TRAP,
            -> FootballKeyBindings.TRAP
            FootballActionType.GK_CATCH,
            -> FootballKeyBindings.GK_CATCH
            FootballActionType.GK_DROP,
            -> FootballKeyBindings.GK_CATCH
            FootballActionType.CHIP,
            -> FootballKeyBindings.CHIP
            FootballActionType.DRIBBLE_HOLD,
            -> FootballKeyBindings.DRIBBLE
            else -> return true
        }
        return FootballOperabilityClient.canUseFootballHint(player, player.level(), key)
    }
    private fun sendAction(
        player: LocalPlayer,
        action: FootballActionType,
        chargeRatio: Float,
        chargeHeldMs: Long,
        flags: Int,
        lookYaw: Float = player.yHeadRot,
        lookPitch: Float = player.xRot,
    ) {
        ClientPlayNetworking.send(
            FootballActionC2SPayload(
                action = action,
                chargeRatio = chargeRatio,
                chargeHeldMs = chargeHeldMs,
                flags = flags,
                lookYaw = lookYaw,
                lookPitch = lookPitch,
            )
        )
    }
    private fun blockDribbleResume(player: LocalPlayer) {
        dribbleResumeBlocked = true
        sendAction(player, FootballActionType.DRIBBLE_END, 0f, 0L, 0)
        dribbleTickCounter = 0
    }

    private fun endDribbleForSlide(player: LocalPlayer) {
        dribbleResumeBlocked = true
        dribbleTickCounter = 0
        if (!dribblePrevTickPressed) {
            return
        }
        DribbleBallIndicatorClient.onDribbleEnd()
        sendAction(player, FootballActionType.DRIBBLE_END, 0f, 0L, 0)
    }
    private fun tickBoostSprint(player: LocalPlayer) {
        if (!player.isSpectator) {
            BoostSprintClient.tick(player)
        }
    }
    private fun resetFootballActionState(player: LocalPlayer? = null, notifyDribbleEnd: Boolean = true) {
        if (notifyDribbleEnd && player != null &&
            (dribblePrevTickPressed || FootballKeyBindings.DRIBBLE.isDown)
        ) {
            sendAction(player, FootballActionType.DRIBBLE_END, 0f, 0L, 0)
        }
        kickPressStartMs = null
        kickReleaseHeldMsOverride = null
        divePressStartMs = null
        diveReleaseHeldMsOverride = null
        diveChargeCancelled = false
        fullChargeHoldTicks = 0
        resetChargeDisplay()
        dribbleTickCounter = 0
        dribbleResumeBlocked = false
        DribbleBallIndicatorClient.onDribbleEnd()
        resetSlideSprintTicks()
        wasSlidingLastTick = false
        KickCurveClient.reset()
    }
    private fun resetTransientState(player: LocalPlayer? = null, notifyDribbleEnd: Boolean = true) {
        resetFootballActionState(player, notifyDribbleEnd)
        BoostSprintClient.reset()
    }
    private fun clearKickoffFrozenChargeState(player: LocalPlayer) {
        if (!FootballOperabilityClient.isKickoffChargeFrozen(player)) return
        kickPressStartMs = null
        kickReleaseHeldMsOverride = null
        divePressStartMs = null
        diveReleaseHeldMsOverride = null
        diveChargeCancelled = false
        fullChargeHoldTicks = 0
        resetChargeDisplay()
    }

    private fun syncKickPressRealtimeClock() {
        val player = net.minecraft.client.Minecraft.getInstance().player
        if (player != null && FootballOperabilityClient.isKickoffChargeFrozen(player)) {
            kickPressStartMs = null
            kickReleaseHeldMsOverride = null
            resetKickChargeDisplay()
            kickRealtimePrevDown = FootballKeyBindings.KICK.isDown
            return
        }
        val down = FootballKeyBindings.KICK.isDown
        val now = System.currentTimeMillis()
        val canTrackCharge = !GoalkeeperStateClient.isHoldingBall && !isDiveChargeBlockingPassShoot()
        if (down && !kickRealtimePrevDown && canTrackCharge && kickPressStartMs == null) {
            kickPressStartMs = now
            kickReleaseHeldMsOverride = null
        } else if (!down && kickRealtimePrevDown) {
            val start = kickPressStartMs
            if (start != null) {
                kickReleaseHeldMsOverride = (now - start).coerceAtLeast(0L)
            }
        }
        kickRealtimePrevDown = down
    }
    private fun syncDivePressRealtimeClock() {
        val player = net.minecraft.client.Minecraft.getInstance().player
        if (player != null && FootballOperabilityClient.isKickoffChargeFrozen(player)) {
            divePressStartMs = null
            diveReleaseHeldMsOverride = null
            diveChargeCancelled = false
            resetThrowChargeDisplay()
            diveRealtimePrevDown = FootballKeyBindings.GK_DIVE.isDown
            return
        }
        val down = FootballKeyBindings.GK_DIVE.isDown
        val now = System.currentTimeMillis()
        val holding = GoalkeeperStateClient.isHoldingBall
        val canTrackDive = !holding && !isPassShootChargeBlockingDive() && player != null &&
            FootballOperabilityClient.canPrepareGoalkeeperDiveCharge(player)
        val canTrackThrow = holding && !GoalkeeperStateClient.isHoldReleaseLocked()
        val canTrackCharge = canTrackDive || canTrackThrow
        if (down && !diveRealtimePrevDown && canTrackCharge && divePressStartMs == null) {
            divePressStartMs = now
            diveReleaseHeldMsOverride = null
        } else if (!down && diveRealtimePrevDown) {
            val start = divePressStartMs
            if (start != null) {
                diveReleaseHeldMsOverride = (now - start).coerceAtLeast(0L)
            }
        }
        diveRealtimePrevDown = down
    }
    private fun resetKickChargeDisplay() {
        isChargingShoot = false
        shootChargeRatio = 0f
        shootChargePhase = KickChargeUtil.Phase.NONE
    }
    private fun resetThrowChargeDisplay() {
        isChargingThrow = false
        throwChargeRatio = 0f
        throwChargePhase = KickChargeUtil.Phase.NONE
    }
    private fun resetChargeDisplay() {
        resetKickChargeDisplay()
        resetThrowChargeDisplay()
    }
    private enum class LongPressState {
        STARTED,
        BEING_PRESSED,
        FINISHED,
        NONE,
    }
}
