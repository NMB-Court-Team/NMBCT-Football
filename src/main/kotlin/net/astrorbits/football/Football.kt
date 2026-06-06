package net.astrorbits.football

import net.astrorbits.football.input.*
import net.astrorbits.football.item.Items
import net.astrorbits.football.match.*
import net.astrorbits.football.network.FootballNetworking
import net.astrorbits.football.physics.FootballNetInteraction
import net.astrorbits.football.physics.FootballPhysicsConfig
import net.astrorbits.football.physics.FootballPhysicsNbt
import net.astrorbits.football.physics.FootballPhysicsState
import net.astrorbits.football.physics.FootballPlayerBallCollision
import net.astrorbits.football.util.*
import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricEntityDataRegistry
import net.minecraft.core.Registry
import net.minecraft.core.UUIDUtil
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializer
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.permissions.Permissions
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.entity.MoverType
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import org.joml.Vector3f
import org.joml.Vector3fc
import java.util.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class Football(type: EntityType<*>, level: Level) : Entity(type, level) {
    /** 最后物理触球玩家，用于出界/角球/门球/掷界外球。 */
    var lastPhysicalTouch: UUID? = null
    /** 当前进球应归属的玩家；被动触球时可能不同于 [lastPhysicalTouch]。 */
    var goalAttributionPlayer: UUID? = null
    /** 最近一次主动踢球是否大致朝向对方球门（调试用）。 */
    var lastActiveKickTowardGoal: Boolean = false
    private val physicsState = FootballPhysicsState()
    private val previousOrientation = Quaternionf()
    /** 本 tick 渲染用的速度快照，避免帧内同步改动导致位置/朝向抖动。 */
    private var renderLinearVelocity = Vec3.ZERO
    private var renderAngularVelocity = Vec3.ZERO
    /** [Entity] 构造过程中会调用 [setPos]，此时尚未执行属性初始化器。 */
    private var fieldsInitialized = false
    private var holderEntityId: Int = -1
    private var holdStartTick: Long = 0L
    private var immovableSnapshot: FootballImmovableSnapshot? = null

    init {
        isNoGravity = true
        requiresPrecisePosition = true
        blocksBuilding = false
        fieldsInitialized = true
    }

    override fun defineSynchedData(entityData: SynchedEntityData.Builder) {
        entityData.define(DATA_LINEAR_VEL, Vector3f())
        entityData.define(DATA_ANGULAR_VEL, Vector3f())
        entityData.define(DATA_ON_GROUND, false)
        entityData.define(DATA_HOLDER_ID, -1)
        entityData.define(DATA_HOLD_START_GAME_TIME, -1L)
        entityData.define(DATA_IMMOVABLE, false)
        entityData.define(DATA_IMMOVABLE_TARGET_PLAYERS, emptySet())
    }

    /**
     * 对这些玩家而言球不可被移动（踢、推、持球、运球辅助等）；[teleportBall] 不受影响
     */
    var immovableTargetPlayers: Set<UUID>
        get() = entityData.get(DATA_IMMOVABLE_TARGET_PLAYERS)
        set(value) {
            if (level().isClientSide) {
                return
            }
            val normalized = value.toSet()
            if (entityData.get(DATA_IMMOVABLE_TARGET_PLAYERS) == normalized) {
                return
            }
            entityData.set(DATA_IMMOVABLE_TARGET_PLAYERS, normalized)
            if (holderEntityId >= 0) {
                val holder = (level() as? ServerLevel)?.getEntity(holderEntityId) as? ServerPlayer
                if (holder != null && normalized.contains(holder.uuid)) {
                    releaseHold()
                }
            }
        }

    /** 物理线速度（服务端判例/静止检测；客户端为同步值）。 */
    fun simulationVelocity(): Vec3 = physicsState.linearVelocity

    /** 指定玩家是否被禁止以任何方式移动此球（传送除外）。含开球锁定、比赛暂停与复位延迟。 */
    fun isPlayerBallMovementForbidden(player: Player): Boolean {
        if (isMatchPaused()) return true
        if (immovableTargetPlayers.contains(player.uuid)) return true
        if (player is ServerPlayer && MatchState.isKickoffInteractionLocked(player)) return true
        if (player is ServerPlayer && SetPieceRestrictionCoordinator.isPlayerBallMovementForbidden(player)) return true
        return false
    }

    private fun notifySetPieceGoalKickBallMoved(actingPlayer: ServerPlayer?) {
        if (SetPieceState.active?.kind != SetPieceKind.GOAL_KICK) return
        val server = (level() as? ServerLevel)?.server ?: return
        GoalKickSetPieceFlow.onBallMoved(server, this)
    }

    private fun isMatchPaused(): Boolean =
        !level().isClientSide && MatchState.isMatchTimerPaused()

    /**
     * 是否固定：不可踢、不可推，物理与位置保持锚点；仅 [teleportBall] / [teleportBallCenter] 可改变位置。
     */
    var isImmovable: Boolean
        get() = entityData.get(DATA_IMMOVABLE)
        set(value) {
            if (level().isClientSide) {
                return
            }
            if (entityData.get(DATA_IMMOVABLE) == value) {
                return
            }
            entityData.set(DATA_IMMOVABLE, value)
            if (value) {
                if (holderEntityId >= 0) {
                    releaseHold()
                }
                captureImmovableSnapshot()
            } else {
                immovableSnapshot = null
            }
        }

    /** 传送足球实体脚点位置；固定状态下会更新锚点。 */
    fun teleportBall(x: Double, y: Double, z: Double) {
        if (level().isClientSide) {
            return
        }
        setPos(x, y, z)
        syncPacketPositionCodec(x, y, z)
        if (isImmovable) {
            captureImmovableSnapshot()
        }
    }

    /** 传送足球球心到世界坐标。 */
    fun teleportBallCenter(center: Vec3) {
        val radius = FootballPhysicsConfig.RADIUS
        teleportBall(center.x, center.y - radius, center.z)
    }

    /**
     * 使玩家无法以任何方式移动此球（踢、推、持球、运球辅助等）；[teleportBall] 不受影响。
     */
    fun makePlayerImmovable(player: Player) {
        immovableTargetPlayers += player.uuid
    }

    /** 进入比赛暂停：速度清零，解除固定锚点以便仅受重力下坠，并对所有玩家禁止操作。 */
    fun enterMatchPause(lockedPlayers: Set<UUID>) {
        if (level().isClientSide) return
        if (holderEntityId >= 0) {
            releaseHold()
        }
        isImmovable = false
        physicsState.linearVelocity = Vec3.ZERO
        physicsState.angularVelocity = Vec3.ZERO
        physicsState.wallBounceCooldown = 0
        immovableTargetPlayers = lockedPlayers
        syncPhysicsToEntityData()
        syncPacketPositionCodec(x, y, z)
    }

    /** 比赛继续：恢复暂停前的不可操作/固定状态（速度保持暂停结束时的值）。 */
    fun restoreFromMatchPause(immovableTargets: Set<UUID>, isImmovable: Boolean) {
        if (level().isClientSide) return
        immovableTargetPlayers = immovableTargets
        this.isImmovable = isImmovable
        syncPhysicsToEntityData()
        syncPacketPositionCodec(x, y, z)
    }

    /**
     * 使一系列玩家无法以任何方式移动此球（踢、推、持球、运球辅助等）；[teleportBall] 不受影响。
     */
    fun makePlayersImmovable(players: Collection<Player>) {
        immovableTargetPlayers += players.map { it.uuid }
    }

    /**
     * 使玩家可以正常移动此球
     */
    fun makePlayerMovable(player: Player) {
        immovableTargetPlayers -= player.uuid
    }

    /**
     * 使一系列玩家可以正常移动此球
     */
    fun makePlayersMovable(players: Collection<Player>) {
        immovableTargetPlayers = immovableTargetPlayers.filter { it !in players.map { player -> player.uuid } }.toSet()
    }

    override fun tick() {
        super.tick()

        if (level().isClientSide) {
            clientTick()
            return
        }

        serverTick()
    }

    private fun clientTick() {
        if (tickCount == 1) {
            loadPhysicsFromEntityData()
        } else {
            applySyncedPhysicsFromEntityData()
        }

        if (isImmovable) {
            renderLinearVelocity = Vec3.ZERO
            renderAngularVelocity = Vec3.ZERO
            deltaMovement = Vec3.ZERO
            return
        }

        renderLinearVelocity = physicsState.linearVelocity
        renderAngularVelocity = physicsState.angularVelocity
        if (isRenderStationaryOnGround()) {
            renderLinearVelocity = Vec3.ZERO
            renderAngularVelocity = Vec3.ZERO
        } else {
            if (renderLinearVelocity.lengthSqr() < FootballPhysicsConfig.RENDER_STATIONARY_SPEED_SQR) {
                renderLinearVelocity = Vec3.ZERO
            }
            if (renderAngularVelocity.lengthSqr() < FootballPhysicsConfig.RENDER_STATIONARY_SPEED_SQR) {
                renderAngularVelocity = Vec3.ZERO
            }
        }

        previousOrientation.set(physicsState.orientation)
        if (renderAngularVelocity.lengthSqr() > 0.0) {
            FootballPhysicsSimulator.integrateOrientation(physicsState)
        }
        deltaMovement = renderLinearVelocity
    }

    private fun serverTick() {
        if (tickCount == 1) {
            loadPhysicsFromEntityData()
        }

        if (holderEntityId >= 0) {
            if (isImmovable) {
                releaseHold()
            } else {
                tickHeld()
                return
            }
        }

        if (isImmovable) {
            tickImmovable()
            return
        }

        if (isMatchPaused()) {
            tickWhileMatchPaused()
            return
        }

        FootballPhysicsSimulator.applyAirForces(physicsState)

        val movement = physicsState.linearVelocity
        val worldMotion = advanceWithWorldCollisions(movement)

        FootballPlayerCollisionScheduler.schedule(
            this,
            worldMotion.beforeMove,
            position(),
            movement,
        )

        physicsState.inCobweb = false
        if (CobwebUtil.isIntersectingCobweb(level(), boundingBox)) {
            CobwebUtil.applyCobwebDrag(physicsState)
        }

        FootballParticles.playHighSpeedDrag(
            level(),
            FootballParticles.centerOfFootball(this),
            physicsState.linearVelocity
        )

        previousOrientation.set(physicsState.orientation)
        FootballPhysicsSimulator.integrateOrientation(physicsState)

        deltaMovement = physicsState.linearVelocity
        syncPhysicsToEntityData()
        val now = (level() as? ServerLevel)?.gameTime ?: 0L
        FootballKickPushGrace.cleanupExpired(now)
        FootballPlayerBallContactGrace.cleanupExpired(now)
    }

    private fun tickImmovable() {
        restoreImmovableSnapshot()
        deltaMovement = Vec3.ZERO
        syncPhysicsToEntityData()
        syncPacketPositionCodec(x, y, z)
    }

    /** 暂停期间：无水平速度、无自转，仅重力下坠与场地碰撞。 */
    private fun tickWhileMatchPaused() {
        physicsState.angularVelocity = Vec3.ZERO
        var vy = physicsState.linearVelocity.y
        if (!physicsState.onGround || vy > 0.0) {
            vy -= FootballPhysicsConfig.GRAVITY
        }
        vy *= FootballPhysicsConfig.AIR_DRAG
        physicsState.linearVelocity = Vec3(0.0, vy, 0.0)

        advanceWithWorldCollisions(physicsState.linearVelocity, detectGoals = false)

        previousOrientation.set(physicsState.orientation)
        deltaMovement = physicsState.linearVelocity
        syncPhysicsToEntityData()
        syncPacketPositionCodec(x, y, z)
    }

    private fun captureImmovableSnapshot() {
        immovableSnapshot = FootballImmovableSnapshot(
            x = x,
            y = y,
            z = z,
            linearVelocity = physicsState.linearVelocity,
            angularVelocity = physicsState.angularVelocity,
            orientation = Quaternionf(physicsState.orientation),
            onGround = physicsState.onGround,
            wallBounceCooldown = physicsState.wallBounceCooldown,
        )
    }

    private fun restoreImmovableSnapshot() {
        val snapshot = immovableSnapshot ?: run {
            captureImmovableSnapshot()
            return
        }
        if (x != snapshot.x || y != snapshot.y || z != snapshot.z) {
            setPos(snapshot.x, snapshot.y, snapshot.z)
        }
        physicsState.linearVelocity = snapshot.linearVelocity
        physicsState.angularVelocity = snapshot.angularVelocity
        physicsState.orientation.set(snapshot.orientation)
        physicsState.onGround = snapshot.onGround
        physicsState.wallBounceCooldown = snapshot.wallBounceCooldown
        physicsState.inCobweb = false
        previousOrientation.set(physicsState.orientation)
    }

    private data class WorldMotionResult(val beforeMove: Vec3, val actualMotion: Vec3)

    private fun advanceWithWorldCollisions(movement: Vec3, detectGoals: Boolean = true): WorldMotionResult {
        deltaMovement = movement
        val beforeMove = position()
        move(MoverType.SELF, movement)
        val actualMotion = position().subtract(beforeMove)

        val bounce = FootballPhysicsSimulator.resolveCollisions(
            physicsState,
            horizontalCollision,
            verticalCollisionBelow,
            onGround(),
            movement,
            actualMotion
        )
        FootballSounds.playCollisionBounces(level(), blockPosition(), bounce, level().random)
        FootballParticles.playCollisionBounces(level(), blockPosition(), bounce)

        if (detectGoals) {
            detectGoal(beforeMove, position())
        }
        applyWorldContactGuards(beforeMove, position())

        return WorldMotionResult(beforeMove, actualMotion)
    }

    private fun applyWorldContactGuards(beforeMove: Vec3, afterMove: Vec3) {
        val radius = FootballPhysicsConfig.RADIUS
        val prevCenter = beforeMove.add(0.0, radius, 0.0)
        val currCenter = afterMove.add(0.0, radius, 0.0)
        val netContact = FootballNetInteraction.apply(level(), physicsState, prevCenter, currCenter)
        if (netContact != null) {
            val restCenter = netContact.restCenter
            if (restCenter != null) {
                val target = restCenter.subtract(0.0, radius, 0.0)
                setPos(target.x, target.y, target.z)
            }
            deltaMovement = physicsState.linearVelocity
        }

        // 通用兜底：无论是否触网，最终位置都再做一次方块防穿透修正。
        val blockDepenetration = FootballBlockDepenetration.depenetrateSphere(
            level(),
            position().add(0.0, radius, 0.0),
            radius
        )
        if (blockDepenetration.correction.lengthSqr() > 1.0e-9) {
            val target = blockDepenetration.center.subtract(0.0, radius, 0.0)
            setPos(target.x, target.y, target.z)
            val correctionLength = sqrt(blockDepenetration.correction.lengthSqr())
            if (correctionLength > 1.0e-9) {
                val outward = blockDepenetration.correction.scale(1.0 / correctionLength)
                val inward = physicsState.linearVelocity.dot(outward)
                if (inward < 0.0) {
                    physicsState.linearVelocity = physicsState.linearVelocity.subtract(outward.scale(inward))
                }
            }
            deltaMovement = physicsState.linearVelocity
        }
    }

    private fun setCenterWithWorldContactGuards(center: Vec3) {
        val beforeCorrection = position()
        val radius = FootballPhysicsConfig.RADIUS
        setPos(center.x, center.y - radius, center.z)
        applyWorldContactGuards(beforeCorrection, position())
    }

    private fun applyPlayerPushFromPlayer(
        player: ServerPlayer,
        contactNormal: Vec3,
        repositionCenter: Vec3? = null,
        now: Long = (level() as? ServerLevel)?.gameTime ?: 0L,
    ): Boolean {
        if (isImmovable || isPlayerBallMovementForbidden(player) || isHoldStealProtectedFrom(player)) {
            return false
        }
        if (contactNormal.lengthSqr() <= 1.0e-8) {
            return false
        }
        if (shouldSuppressPlayerBodyImpulse(player, now)) {
            return false
        }
        if (holderEntityId >= 0) {
            releaseHold()
        }
        if (repositionCenter != null) {
            setCenterWithWorldContactGuards(repositionCenter)
        }

        val resolvedBallCenter = position().add(0.0, FootballPhysicsConfig.RADIUS, 0.0)
        val pushNormal = FootballPhysicsSimulator.orientContactNormalTowardBall(
            contactNormal,
            player.position(),
            resolvedBallCenter,
        )
        val verticalStep = player.y - player.yOld
        val playerVelocity = effectivePlayerVelocity(player, verticalStep)
        val preCollisionBallVelocity = physicsState.linearVelocity

        val momentum = FootballPlayerBallCollision.resolveMomentum(
            ballVelocity = preCollisionBallVelocity,
            playerVelocity = playerVelocity,
            normal = pushNormal,
        ) ?: FootballPlayerBallCollision.resolveQuasiStaticPush(
            ballVelocity = preCollisionBallVelocity,
            playerVelocity = playerVelocity,
            normal = pushNormal,
        ) ?: return false

        val playerRecoil = FootballPlayerBallCollision.resolvePlayerRecoil(
            ballVelocity = preCollisionBallVelocity,
            playerVelocity = playerVelocity,
            normal = pushNormal,
        )

        applyBallMomentumResult(momentum)

        val playerDelta = FootballPlayerBallCollision.capPlayerKnockback(
            playerRecoil,
            FootballInputConfig.BALL_PLAYER_MAX_PUSH,
        )
        applyPlayerKnockback(player, playerDelta)

        deltaMovement = physicsState.linearVelocity
        MatchState.tryNotifyKickoffBallTouched(player)
        FootballPlayerBallContactGrace.record(player, this, now, sliding = false)
        return true
    }

    /** 滑铲触球：沿铲向施加踢球（力度见 [FootballKickUtil.resolveSlideKickParams]，散布同传球）。 */
    private fun applySlideBallKickFromPlayer(
        player: ServerPlayer,
        repositionCenter: Vec3? = null,
        now: Long,
    ): Boolean {
        if (isImmovable || isPlayerBallMovementForbidden(player) || isHoldStealProtectedFrom(player)) {
            return false
        }
        if (shouldSuppressPlayerBodyImpulse(player, now)) {
            return false
        }
        val kickDir = SlideTackleSessions.slideKickDirection(player) ?: return false

        if (repositionCenter != null) {
            setCenterWithWorldContactGuards(repositionCenter)
        }

        val params = FootballKickUtil.resolveSlideKickParams()
        val kickImpulse = FootballKickUtil.buildKickDirection(kickDir, kickDir, params.force, params.angleDegrees)
        recordActiveKick(player, kickImpulse)
        FootballKickUtil.applyKickWithHorizontalDirection(
            football = this,
            horizontalLook = kickDir,
            verticalReference = kickDir,
            params = params,
            random = player.random,
            spreadInaccuracy = FootballInputConfig.KICK_SPREAD_INACCURACY,
            actingPlayer = player,
        )
        deltaMovement = physicsState.linearVelocity
        MatchState.tryNotifyKickoffBallTouched(player)
        FootballSounds.playKick(player, params.force)
        FootballParticles.playKick(player, this, params.force)
        FootballPlayerBallContactGrace.record(player, this, now, sliding = true)
        return true
    }

    private fun applyBallMomentumResult(momentum: FootballPlayerBallCollision.MomentumResult) {
        physicsState.linearVelocity = momentum.ballVelocity
        FootballPhysicsSimulator.resetRollingOrientation(physicsState)
        val rolling = Vec3Math.rollingAngularVelocity(
            Vec3Math.horizontal(physicsState.linearVelocity),
            FootballPhysicsConfig.RADIUS,
        )
        physicsState.angularVelocity = Vec3(rolling.x, physicsState.angularVelocity.y, rolling.z)
    }

    private fun applyPlayerKnockback(player: ServerPlayer, playerDelta: Vec3) {
        val horizontal = Vec3Math.horizontal(playerDelta)
        if (horizontal.lengthSqr() <= FootballPhysicsConfig.EPSILON * FootballPhysicsConfig.EPSILON) {
            return
        }
        player.deltaMovement = player.deltaMovement.add(playerDelta)
        player.hurtMarked = true
    }

    private fun setGoalAttribution(player: ServerPlayer) {
        val previous = goalAttributionPlayer
        goalAttributionPlayer = player.uuid
        if (previous != player.uuid) {
            MatchState.onGoalAttributionChanged(player.uuid)
        }
    }

    /** 主动踢球：重置进球归属链（含滑铲推球）。 */
    fun recordActiveKick(player: ServerPlayer, kickDirection: Vec3?) {
        if (!MatchParticipation.isParticipating(player)) {
            return
        }
        MatchPenaltyKickState.onKickerTouchedBall(player, this)
        if (MatchState.currentPhase == MatchPhase.PENALTIES) {
            PenaltyShootoutState.onKickerTouchedBall(player, this)
        }
        lastPhysicalTouch = player.uuid
        setGoalAttribution(player)
        val ballCenter = position().add(0.0, FootballPhysicsConfig.RADIUS, 0.0)
        lastActiveKickTowardGoal = kickDirection != null &&
            GoalCrossingUtil.isKickTowardOpponentGoal(player, ballCenter, kickDirection)
        if (lastActiveKickTowardGoal) {
            OffsideDetector.recordSnapshot(player, ballCenter)
        }
        val serverLevel = level() as? ServerLevel
        if (serverLevel != null) {
            OffsideDetector.tryAwardOnTouch(player, ballCenter, serverLevel)
        }
    }

    /** 带球触球：仅更新最后物理触球，不重置进球归属链。 */
    fun recordDribbleTouch(player: ServerPlayer) {
        if (!MatchParticipation.isParticipating(player)) {
            return
        }
        lastPhysicalTouch = player.uuid
    }

    /**
     * 被动身体触球：始终更新最后物理触球；仅当球路预测显示本不会进门时，
     * 才将进球归属改为触球者。
     */
    fun recordPassiveBodyTouch(
        player: ServerPlayer,
        preTouchPhysics: FootballPhysicsState,
        preTouchBottomPos: Vec3,
    ) {
        if (!MatchParticipation.isParticipating(player)) {
            return
        }
        lastPhysicalTouch = player.uuid
        val serverLevel = level() as? ServerLevel ?: return
        val prediction = FootballTrajectoryPredictor.predictWouldScore(
            serverLevel,
            preTouchBottomPos,
            preTouchPhysics,
            MatchConfigHolder.current,
        )
        if (!prediction.wouldScore) {
            setGoalAttribution(player)
        }
        val ballCenter = position().add(0.0, FootballPhysicsConfig.RADIUS, 0.0)
        OffsideDetector.tryAwardOnTouch(player, ballCenter, serverLevel)
    }

    private fun resolveGoalScorerUuid(): UUID? = goalAttributionPlayer ?: lastPhysicalTouch

    private fun shouldSkipPlayerBodyCollisionDetection(player: ServerPlayer): Boolean {
        return FootballDribbleSessions.hasActiveSessionBlockingCollision(player, this)
    }

    private fun shouldSuppressPlayerBodyImpulse(player: ServerPlayer, now: Long): Boolean {
        if (FootballKickPushGrace.shouldSuppressPlayerPush(player, this, now)) {
            return true
        }
        if (FootballPlayerBallContactGrace.shouldSuppressBodyImpulse(player, this, now)) {
            return true
        }
        if (FootballDribbleSessions.shouldSuppressCollisionImpulse(player, this, now)) {
            return true
        }
        return false
    }

    /** 动量 / 准静态推球 / 后坐力共用：最佳水平速度 + 垂直步进（滑铲 session 优先）。 */
    private fun effectivePlayerVelocity(player: ServerPlayer, verticalStep: Double): Vec3 {
        val horizontal = effectivePlayerHorizontalMotion(player)
        return Vec3(horizontal.x, verticalStep, horizontal.z)
    }

    private fun effectivePlayerHorizontalMotion(player: ServerPlayer): Vec3 {
        val best = FootballMovementInputUtil.bestHorizontalVelocity(player)
        val slide = SlideTackleSessions.effectiveHorizontalVelocity(player) ?: return best
        return if (slide.lengthSqr() >= best.lengthSqr()) slide else best
    }

    /** 碰撞扫掠用本 tick 玩家位移；位移尚未写入时用速度/意图作为预测步长。 */
    private fun resolvePlayerStepForCollision(player: ServerPlayer): Vec3 {
        val positionStep = Vec3(player.x - player.xOld, player.y - player.yOld, player.z - player.zOld)
        if (positionStep.lengthSqr() > 1.0e-12) {
            return positionStep
        }
        val horizontal = effectivePlayerHorizontalMotion(player)
        if (horizontal.lengthSqr() > 1.0e-12) {
            return Vec3(horizontal.x, player.deltaMovement.y, horizontal.z)
        }
        return player.deltaMovement
    }

    /** 由 [FootballPlayerCollisionScheduler] 在本 server tick 末尾调用（所有实体已 tick）。 */
    internal fun runDeferredPlayerCollisions(
        beforeMove: Vec3,
        afterMove: Vec3,
        intendedMotion: Vec3,
    ) {
        val pushedPlayersThisTick = hashSetOf<UUID>()
        resolvePlayerCollisions(beforeMove, afterMove, intendedMotion, pushedPlayersThisTick)
        deltaMovement = physicsState.linearVelocity
        syncPhysicsToEntityData()
    }

    private fun resolvePlayerCollisions(
        beforeMove: Vec3,
        afterMove: Vec3,
        intendedMotion: Vec3,
        pushedPlayersThisTick: MutableSet<UUID>,
    ) {
        if (isImmovable) {
            return
        }
        val serverLevel = level() as? ServerLevel ?: return
        val now = serverLevel.gameTime
        val radius = FootballPhysicsConfig.RADIUS
        val previousCenter = beforeMove.add(0.0, radius, 0.0)
        val currentCenter = afterMove.add(0.0, radius, 0.0)
        val previousBallBox = boundingBox.move(beforeMove.subtract(afterMove))
        val ballMotionLength = currentCenter.distanceTo(previousCenter)
        val searchInflateH = 1.4 + min(ballMotionLength, 4.0) * 0.25
        val searchBox = previousBallBox.minmax(boundingBox).inflate(searchInflateH, 0.9, searchInflateH)
        val players = serverLevel.getEntitiesOfClass(ServerPlayer::class.java, searchBox) { player ->
            player.isAlive && !player.isSpectator && !player.noPhysics
        }

        for (player in players) {
            if (isPlayerBallMovementForbidden(player)) {
                continue
            }
            if (shouldSkipPlayerBodyCollisionDetection(player)) {
                continue
            }

            val playerStep = resolvePlayerStepForCollision(player)
            val playerEnvelope = FootballPlayerBallCollision.playerMotionEnvelope(player.boundingBox, playerStep)

            val ballDelta = currentCenter.subtract(previousCenter)
            val sweepEnd = if (ballDelta.lengthSqr() > 1.0e-12) {
                currentCenter
            } else {
                previousCenter.subtract(playerStep)
            }
            var sweepHit = FootballPlayerBallCollision.sweepBallCenter(
                previousCenter,
                sweepEnd,
                playerEnvelope,
                radius,
            )
            if (sweepHit == null) {
                sweepHit = FootballPlayerBallCollision.overlapHitAt(currentCenter, playerEnvelope, radius)
            }
            if (sweepHit == null) {
                continue
            }

            val repositionCenter = if (sweepHit.t > 1.0e-6) sweepHit.contactCenter else null
            val impactBallCenter = repositionCenter ?: currentCenter
            val pushNormal = FootballPhysicsSimulator.orientContactNormalTowardBall(
                sweepHit.normal,
                player.position(),
                impactBallCenter,
            )
            val sliding = SlideTackleSessions.isSliding(player)
            val preTouchBottomPos = position()
            val preTouchPhysics = FootballTrajectoryPredictor.copyState(physicsState)
            if (player.uuid !in pushedPlayersThisTick) {
                val applied = if (sliding) {
                    applySlideBallKickFromPlayer(player, repositionCenter, now)
                } else {
                    applyPlayerPushFromPlayer(player, pushNormal, repositionCenter, now)
                }
                if (applied) {
                    pushedPlayersThisTick.add(player.uuid)
                    if (!sliding) {
                        recordPassiveBodyTouch(player, preTouchPhysics, preTouchBottomPos)
                    }
                }
            }
        }
    }

    private data class PlayerDepenetration(val push: Vec3, val normal: Vec3)

    /** 球心相对玩家 AABB 的最小平移分离，解决球心落在玩家体内时只沿水平推不够的问题。 */
    private fun computeSpherePlayerDepenetration(
        center: Vec3,
        playerBox: AABB,
        radius: Double,
        epsilon: Double = PLAYER_SEPARATION_EPSILON,
    ): PlayerDepenetration? {
        if (center.x < playerBox.minX - radius || center.x > playerBox.maxX + radius ||
            center.y < playerBox.minY - radius || center.y > playerBox.maxY + radius ||
            center.z < playerBox.minZ - radius || center.z > playerBox.maxZ + radius
        ) {
            return null
        }

        val pushLeft = (playerBox.minX - radius) - center.x - epsilon
        val pushRight = (playerBox.maxX + radius) - center.x + epsilon
        val pushDown = (playerBox.minY - radius) - center.y - epsilon
        val pushUp = (playerBox.maxY + radius) - center.y + epsilon
        val pushBack = (playerBox.minZ - radius) - center.z - epsilon
        val pushFront = (playerBox.maxZ + radius) - center.z + epsilon
        val candidates = arrayOf(
            Vec3(pushLeft, 0.0, 0.0),
            Vec3(pushRight, 0.0, 0.0),
            Vec3(0.0, pushDown, 0.0),
            Vec3(0.0, pushUp, 0.0),
            Vec3(0.0, 0.0, pushBack),
            Vec3(0.0, 0.0, pushFront),
        )

        var bestPush: Vec3? = null
        var bestPushLenSqr = Double.MAX_VALUE
        for (candidate in candidates) {
            val lenSqr = candidate.lengthSqr()
            if (lenSqr < bestPushLenSqr) {
                bestPushLenSqr = lenSqr
                bestPush = candidate
            }
        }
        if (bestPush == null || bestPushLenSqr < 1.0e-12) {
            return null
        }
        return PlayerDepenetration(bestPush, Vec3Math.normalizeSafe(bestPush))
    }

    private fun detectGoal(prevPos: Vec3, currPos: Vec3) {
        if (MatchState.currentPhase == MatchPhase.PRE_MATCH
            || MatchState.currentPhase == MatchPhase.PRE_MATCH_PREP
            || MatchState.currentPhase == MatchPhase.FINISHED
        ) return
        if (MatchState.postGoalResetPending) return

        val radius = FootballPhysicsConfig.RADIUS
        val prevCenter = prevPos.add(0.0, radius, 0.0)
        val currCenter = currPos.add(0.0, radius, 0.0)

        if (MatchPenaltyKickState.isActive()) {
            detectMatchPenaltyKick(prevCenter, currCenter)
            return
        }

        if (MatchState.currentPhase == MatchPhase.PENALTIES) {
            detectPenaltyKick(prevCenter, currCenter)
            return
        }

        val config = MatchConfigHolder.current
        if (tickPendingGoalLineOut(prevCenter, currCenter)) {
            return
        }
        // 球门 A 由 A 队防守，球入门则 B 队得分；出底线则视触球方判角球/球门球
        checkGoalOrOut(config.goalA, prevCenter, currCenter, TeamSide.A, TeamSide.B)
        // 球门 B 由 B 队防守，球入门则 A 队得分
        checkGoalOrOut(config.goalB, prevCenter, currCenter, TeamSide.B, TeamSide.A)
        // 边线出界
        checkSidelineOut(config.sidelineA, prevCenter, currCenter)
        checkSidelineOut(config.sidelineB, prevCenter, currCenter)
    }

    /**
     * 门柱外侧底线出界待确认：若数 tick 内球心进入门框则改判进球。
     * @return 本 tick 已处理完毕（进球或已确认出界），不再做常规门线检测
     */
    private fun tickPendingGoalLineOut(prevCenter: Vec3, currCenter: Vec3): Boolean {
        val pending = MatchState.pendingGoalLineOut ?: return false
        if (GoalCrossingUtil.isCenterInGoal(pending.goal, currCenter)) {
            MatchState.clearPendingGoalLineOut()
            checkGoalOrOut(
                pending.goal,
                prevCenter,
                currCenter,
                pending.defendingTeam,
                pending.attackingTeam,
            )
            return true
        }
        pending.ticksRemaining--
        if (pending.ticksRemaining > 0) {
            return true
        }
        MatchState.clearPendingGoalLineOut()
        val server = (level() as? ServerLevel)?.server ?: return true
        scheduleOutOfBoundsRestart(
            level() as ServerLevel,
            server,
            pending.ballPos,
            pending.restartTeam,
            pending.outType,
            defendingSide = pending.defendingTeam,
        )
        return true
    }

    private fun detectMatchPenaltyKick(prevCenter: Vec3, currCenter: Vec3) {
        if (!MatchPenaltyKickState.isResolving()) return
        val config = MatchConfigHolder.current
        if (checkMatchPenaltyGoalLine(config.goalA, prevCenter, currCenter, TeamSide.A, TeamSide.B)) return
        if (checkMatchPenaltyGoalLine(config.goalB, prevCenter, currCenter, TeamSide.B, TeamSide.A)) return
        if (checkMatchPenaltySidelineOut(config.sidelineA, prevCenter, currCenter)) return
        checkMatchPenaltySidelineOut(config.sidelineB, prevCenter, currCenter)
    }

    private fun checkMatchPenaltyGoalLine(
        goal: GoalConfig,
        prevCenter: Vec3,
        currCenter: Vec3,
        defendingTeam: TeamSide,
        attackingTeam: TeamSide,
    ): Boolean {
        val crossing = GoalCrossingUtil.segmentCrossesGoalLine(
            goal, prevCenter, currCenter, defendingTeam, attackingTeam,
        ) ?: return false
        val effectiveInGoal = crossing.inGoal || GoalCrossingUtil.isCenterInGoal(goal, currCenter)
        val isPenaltyTargetGoal = defendingTeam == MatchPenaltyKickState.defendingTeam &&
            attackingTeam == MatchPenaltyKickState.kickingTeam
        if (effectiveInGoal && isPenaltyTargetGoal) {
            MatchPenaltyKickState.onGoalLineCrossing(
                crossing.copy(inGoal = true, definiteGoalLineOut = false),
            )
            return true
        }
        if (!effectiveInGoal && crossing.definiteGoalLineOut) {
            val server = (level() as? ServerLevel)?.server ?: return true
            val lastTouchTeam = lastPhysicalTouch?.let { MatchState.getPlayerTeam(it) }
            val facing = Vec3(goal.facingX, goal.facingY, goal.facingZ)
            val outType: GoalLineOutType
            val restartTeam: TeamSide
            val ballPos: Vec3
            if (lastTouchTeam == attackingTeam) {
                outType = GoalLineOutType.GOAL_KICK
                restartTeam = defendingTeam
                val gk = goal.goalKick
                ballPos = Vec3(gk.x, gk.y, gk.z)
            } else {
                outType = GoalLineOutType.CORNER_KICK
                restartTeam = attackingTeam
                ballPos = GoalCrossingUtil.cornerKickPosition(
                    goal,
                    facing,
                    crossing.intersection.x,
                    crossing.intersection.z,
                )
            }
            MatchPenaltyKickState.clear(server)
            scheduleOutOfBoundsRestart(
                level() as ServerLevel,
                server,
                ballPos,
                restartTeam,
                outType,
                defendingSide = defendingTeam,
            )
            return true
        }
        return false
    }

    private fun checkMatchPenaltySidelineOut(
        sideline: SidelineConfig,
        prevCenter: Vec3,
        currCenter: Vec3,
    ): Boolean {
        val facing = sideline.facing()
        val facingLenSqr = facing.lengthSqr()
        if (facingLenSqr < 1e-6) return false

        val origin = sideline.origin()
        val refX = origin.x
        val refY = origin.y
        val refZ = origin.z
        val d1 = (prevCenter.x - refX) * facing.x + (prevCenter.y - refY) * facing.y + (prevCenter.z - refZ) * facing.z
        val d2 = (currCenter.x - refX) * facing.x + (currCenter.y - refY) * facing.y + (currCenter.z - refZ) * facing.z

        if (d1 * d2 >= 0) return false
        if (d2 - d1 >= 0) return false

        val t = d1 / (d1 - d2)
        val movement = currCenter.subtract(prevCenter)
        val ix = prevCenter.x + movement.x * t
        val iy = prevCenter.y + movement.y * t
        val iz = prevCenter.z + movement.z * t

        val server = (level() as? ServerLevel)?.server ?: return true
        val lastTouchTeam = lastPhysicalTouch?.let { MatchState.getPlayerTeam(it) }
        val restartTeam: TeamSide = when (lastTouchTeam) {
            TeamSide.A -> TeamSide.B
            TeamSide.B -> TeamSide.A
            null -> TeamSide.A
        }

        MatchPenaltyKickState.clear(server)
        scheduleOutOfBoundsRestart(
            level() as ServerLevel,
            server,
            Vec3(ix, iy, iz),
            restartTeam,
            GoalLineOutType.THROW_IN,
        )
        return true
    }

    private fun detectPenaltyKick(prevCenter: Vec3, currCenter: Vec3) {
        if (!PenaltyShootoutState.isActive()) return
        val goal = PenaltyShootoutState.defendingGoal()
        val defending = PenaltyShootoutState.activeDefendingTeam
        val attacking = defending.opponent()
        val crossing = GoalCrossingUtil.segmentCrossesGoalLine(
            goal, prevCenter, currCenter, defending, attacking,
        ) ?: return
        val effectiveInGoal = crossing.inGoal || GoalCrossingUtil.isCenterInGoal(goal, currCenter)
        PenaltyShootoutState.onGoalLineCrossing(
            crossing.copy(inGoal = effectiveInGoal, definiteGoalLineOut = crossing.definiteGoalLineOut && !effectiveInGoal),
        )
    }

    /** 检测球是否穿越边线出界 */
    private fun checkSidelineOut(
        sideline: SidelineConfig,
        prevCenter: Vec3,
        currCenter: Vec3,
    ) {
        val facing = sideline.facing()
        val facingLenSqr = facing.lengthSqr()
        if (facingLenSqr < 1e-6) return

        val origin = sideline.origin()
        val refX = origin.x
        val refY = origin.y
        val refZ = origin.z
        val d1 = (prevCenter.x - refX) * facing.x + (prevCenter.y - refY) * facing.y + (prevCenter.z - refZ) * facing.z
        val d2 = (currCenter.x - refX) * facing.x + (currCenter.y - refY) * facing.y + (currCenter.z - refZ) * facing.z

        if (d1 * d2 >= 0) return
        if (d2 - d1 >= 0) return

        // 穿越点
        val t = d1 / (d1 - d2)
        val movement = currCenter.subtract(prevCenter)
        val ix = prevCenter.x + movement.x * t
        val iy = prevCenter.y + movement.y * t
        val iz = prevCenter.z + movement.z * t

        val server = (level() as? ServerLevel)?.server ?: return

        // 最后触球方 = 对方发球
        val lastTouchTeam = lastPhysicalTouch?.let { MatchState.getPlayerTeam(it) }
        val restartTeam: TeamSide = when (lastTouchTeam) {
            TeamSide.A -> TeamSide.B
            TeamSide.B -> TeamSide.A
            null -> TeamSide.A
        }

        val ballPos = Vec3(ix, iy, iz)
        scheduleOutOfBoundsRestart(level() as ServerLevel, server, ballPos, restartTeam, GoalLineOutType.THROW_IN)
    }

    /** 检测球是否穿越门线：进球或出底线 */
    private fun checkGoalOrOut(
        goal: GoalConfig,
        prevCenter: Vec3,
        currCenter: Vec3,
        defendingTeam: TeamSide,
        attackingTeam: TeamSide,
    ) {
        val crossing = GoalCrossingUtil.segmentCrossesGoalLine(
            goal,
            prevCenter,
            currCenter,
            defendingTeam,
            attackingTeam,
        )
        val scored = crossing?.inGoal == true
            || GoalCrossingUtil.isCenterInGoal(goal, currCenter)
            || GoalCrossingUtil.segmentEnteredGoal(goal, prevCenter, currCenter)
        if (!scored && crossing == null) {
            return
        }

        val server = (level() as? ServerLevel)?.server ?: return
        val ix = crossing?.intersection?.x ?: currCenter.x
        val iz = crossing?.intersection?.z ?: currCenter.z
        val facing = Vec3(goal.facingX, goal.facingY, goal.facingZ)

        if (scored) {
            MatchState.clearPendingGoalLineOut()
            if (MatchState.postGoalResetPending) return
            val scorerUuid = resolveGoalScorerUuid()
            val scorerName = scorerUuid?.let { server.playerList.getPlayer(it)?.gameProfile?.name } ?: "?"
            val scorerTeam = scorerUuid?.let { MatchState.getPlayerTeam(it) } ?: attackingTeam
            if (MatchState.isDirectGoalInvalid(goalAttributionPlayer, lastPhysicalTouch)) {
                handleInvalidDirectGoal(
                    server,
                    goal,
                    ix,
                    iz,
                    facing,
                    defendingTeam,
                    attackingTeam,
                    scorerName,
                    scorerTeam,
                )
                return
            }
            MatchState.clearDirectGoalRestriction()
            MatchState.clearPendingOffsideSnapshot()
            MatchState.postGoalResetPending = true
            MatchState.onGoal(attackingTeam)
            val ownGoal = scorerTeam != attackingTeam
            FootballNetworking.broadcastGoalScored(
                server, attackingTeam, scorerName, scorerTeam, MatchState.teamAScore, MatchState.teamBScore, ownGoal,
            )
            FootballParticles.playGoal(level(), FootballParticles.centerOfFootball(this))
            PostGoalBallResetScheduler.schedule(
                level() as ServerLevel,
                afterReset = PendingAfterReset.PostGoal(defendingTeam),
            )
        } else if (crossing != null && crossing.definiteGoalLineOut) {
            val lastTouchTeam = lastPhysicalTouch?.let { MatchState.getPlayerTeam(it) }
            val outType: GoalLineOutType
            val restartTeam: TeamSide

            if (lastTouchTeam == attackingTeam) {
                outType = GoalLineOutType.GOAL_KICK
                restartTeam = defendingTeam
            } else {
                outType = GoalLineOutType.CORNER_KICK
                restartTeam = attackingTeam
            }

            val ballPos = if (outType == GoalLineOutType.GOAL_KICK) {
                val gk = goal.goalKick
                Vec3(gk.x, gk.y, gk.z)
            } else {
                GoalCrossingUtil.cornerKickPosition(goal, facing, ix, iz)
            }

            if (MatchState.pendingGoalLineOut != null) {
                return
            }
            MatchState.pendingGoalLineOut = MatchState.PendingGoalLineOut(
                goal = goal,
                ballPos = ballPos,
                restartTeam = restartTeam,
                outType = outType,
                defendingTeam = defendingTeam,
                attackingTeam = attackingTeam,
            )
        }
    }

    private fun scheduleOutOfBoundsRestart(
        level: ServerLevel,
        server: MinecraftServer,
        ballPos: Vec3,
        restartTeam: TeamSide,
        outType: GoalLineOutType,
        defendingSide: TeamSide? = null,
        broadcastOutHud: Boolean = true,
    ) {
        if (MatchState.postGoalResetPending) return
        MatchState.clearPendingOffsideSnapshot()
        MatchState.postGoalResetPending = true
        PostGoalBallResetScheduler.schedule(
            level,
            ballPos,
            PendingAfterReset.GoalLineOut(
                kickoffTeam = restartTeam,
                outType = outType,
                ballPos = ballPos,
                defendingSide = defendingSide,
                throwInDirectGoalRestrict = outType == GoalLineOutType.THROW_IN,
            ),
        )
        if (broadcastOutHud) {
            val (touchName, touchTeam) = resolveLastTouch(server)
            FootballNetworking.broadcastGoalLineOut(
                server, outType, restartTeam, ballPos.x, ballPos.y, ballPos.z, touchName, touchTeam,
            )
        }
    }

    private fun resolveLastTouch(server: MinecraftServer): Pair<String, TeamSide?> {
        val uuid = lastPhysicalTouch ?: return "" to null
        val name = server.playerList.getPlayer(uuid)?.gameProfile?.name ?: "?"
        return name to MatchState.getPlayerTeam(uuid)
    }

    /**
     * 掷界外球后的无效直接进球：按射入的球门判定角球/球门球（与底线出界规则一致，以进球归属方代最后触球方）。
     * - 打进对方球门 → 球门球，守方发球；
     * - 打进自己球门（乌龙）→ 角球，攻方发球。
     */
    private fun handleInvalidDirectGoal(
        server: MinecraftServer,
        goal: GoalConfig,
        intersectionX: Double,
        intersectionZ: Double,
        facing: Vec3,
        defendingTeam: TeamSide,
        attackingTeam: TeamSide,
        scorerName: String,
        scorerTeam: TeamSide,
    ) {
        if (MatchState.postGoalResetPending) return
        if (!MatchState.directGoalRestricted) return

        val intoOwnGoal = scorerTeam != attackingTeam
        val outType: GoalLineOutType
        val restartTeam: TeamSide
        if (intoOwnGoal) {
            outType = GoalLineOutType.CORNER_KICK
            restartTeam = attackingTeam
        } else {
            outType = GoalLineOutType.GOAL_KICK
            restartTeam = defendingTeam
        }

        val ballPos = if (outType == GoalLineOutType.GOAL_KICK) {
            val gk = goal.goalKick
            Vec3(gk.x, gk.y, gk.z)
        } else {
            GoalCrossingUtil.cornerKickPosition(goal, facing, intersectionX, intersectionZ)
        }

        MatchState.clearDirectGoalRestriction()
        FootballNetworking.broadcastInvalidGoal(
            server,
            scorerName,
            scorerTeam,
            MatchState.teamAScore,
            MatchState.teamBScore,
        )
        scheduleOutOfBoundsRestart(
            level() as ServerLevel,
            server,
            ballPos,
            restartTeam,
            outType,
            defendingSide = defendingTeam,
            broadcastOutHud = false,
        )
    }

    /**
     * @param ignoreImmovableTargets 为 true 时跳过 [immovableTargetPlayers] 检查（如命令踢球）。
     * @param actingPlayer 执行踢球的玩家；用于 [isPlayerBallMovementForbidden] 判定。
     * @return 踢球是否已施加（被禁用时为 false）。
     */
    fun kick(
        kickPoint: Vec3,
        direction: Vec3,
        ignoreImmovableTargets: Boolean = false,
        actingPlayer: ServerPlayer? = null,
    ): Boolean {
        if (level().isClientSide || isImmovable) {
            return false
        }
        if (!ignoreImmovableTargets) {
            val forbidden = when {
                actingPlayer != null -> isPlayerBallMovementForbidden(actingPlayer)
                else -> {
                    val kickerUuid = lastPhysicalTouch
                    kickerUuid != null && kickerUuid in immovableTargetPlayers
                }
            }
            if (forbidden) {
                return false
            }
        }
        if (isHoldStealProtectedFrom(actingPlayer)) {
            return false
        }

        if (holderEntityId >= 0) {
            releaseHold()
        }
        val center = position().add(0.0, FootballPhysicsConfig.RADIUS, 0.0)
        FootballPhysicsSimulator.applyKick(physicsState, kickPoint, direction, center)
        previousOrientation.set(physicsState.orientation)
        deltaMovement = physicsState.linearVelocity
        val serverLevel = level() as? ServerLevel
        if (serverLevel != null) {
            val kickerUuid = lastPhysicalTouch
            val kicker = kickerUuid?.let { serverLevel.server.playerList.getPlayer(it) }
            if (kicker != null) {
                FootballKickPushGrace.record(kicker, this, serverLevel.gameTime)
                separateBallFromKicker(kicker, direction)
            }
        }
        syncPhysicsToEntityData()
        syncPacketPositionCodec(x, y, z)
        actingPlayer?.let { notifySetPieceGoalKickBallMoved(it) }
        return true
    }

    /** 踢球后把球挪到踢球方向外侧，减轻与踢球者重叠导致的每 tick 回拉。 */
    private fun separateBallFromKicker(kicker: ServerPlayer, kickDirection: Vec3) {
        val radius = FootballPhysicsConfig.RADIUS
        val center = position().add(0.0, radius, 0.0)
        val depenetration = computeSpherePlayerDepenetration(
            center = center,
            playerBox = kicker.boundingBox,
            radius = radius,
        ) ?: return

        var corrected = center.add(depenetration.push)
        val horizKick = Vec3Math.normalizeSafe(Vec3Math.horizontal(kickDirection))
        if (horizKick.lengthSqr() > 1.0e-8) {
            corrected = corrected.add(horizKick.scale(radius + PLAYER_SEPARATION_EPSILON + 0.08))
        }
        setCenterWithWorldContactGuards(corrected)
        deltaMovement = physicsState.linearVelocity
    }

    fun trap(player: ServerPlayer): Boolean {
        if (level().isClientSide || isImmovable) {
            return false
        }
        if (isPlayerBallMovementForbidden(player)) {
            return false
        }
        if (isHoldStealProtectedFrom(player)) {
            return false
        }
        if (holderEntityId >= 0) {
            releaseHold()
        }
        physicsState.linearVelocity = Vec3.ZERO
        physicsState.angularVelocity = Vec3.ZERO
        deltaMovement = Vec3.ZERO
        previousOrientation.set(physicsState.orientation)
        syncPhysicsToEntityData()
        notifySetPieceGoalKickBallMoved(player)
        return true
    }

    fun applyDribbleAssist(horizontalVelocity: Vec3, player: ServerPlayer) {
        if (level().isClientSide || isImmovable || isPlayerBallMovementForbidden(player)) {
            return
        }
        if (isHoldStealProtectedFrom(player)) {
            return
        }

        val previousHoriz = Vec3Math.horizontal(physicsState.linearVelocity)
        val newHoriz = Vec3Math.horizontal(horizontalVelocity)
        physicsState.linearVelocity = Vec3(
            horizontalVelocity.x,
            physicsState.linearVelocity.y,
            horizontalVelocity.z
        )
        val rolling = Vec3Math.rollingAngularVelocity(horizontalVelocity, FootballPhysicsConfig.RADIUS)
        physicsState.angularVelocity = Vec3(rolling.x, 0.0, rolling.z)
        deltaMovement = physicsState.linearVelocity
        if (newHoriz.subtract(previousHoriz).lengthSqr() > 1.0e-8) {
            recordDribbleTouch(player)
        }
        syncPhysicsToEntityData()
        syncPacketPositionCodec(x, y, z)
        notifySetPieceGoalKickBallMoved(player)
    }

    fun getRollingDirection(): Vec3 = FootballPhysicsSimulator.getRollingDirection(physicsState)

    fun getHolderEntityId(): Int = entityData.get(DATA_HOLDER_ID)

    fun isHeld(): Boolean = getHolderEntityId() >= 0

    fun isHeldBy(player: ServerPlayer): Boolean = getHolderEntityId() == player.id

    /**
     * 守门员持球后的抢球保护：仅在 [MatchState.isDuringMatch] 且 [GoalkeeperInputConfig.GK_HOLD_STEAL_PROTECTION_TICKS] 内，
     * 除持球者外的玩家无法通过踢球、停球、碰撞推球等方式把球从守门员手上夺走。
     */
    /** 持球开始时的关卡 gameTime（同步到客户端，供抢球保护 HUD 与判定对齐）。 */
    fun getHoldStartGameTime(): Long = entityData.get(DATA_HOLD_START_GAME_TIME)

    /** 抢球保护是否仍在生效（不含“是否针对某球员”的持球者豁免）。 */
    fun isHoldStealProtectionActive(gameTime: Long = level().gameTime): Boolean {
        if (getHolderEntityId() < 0 || !MatchState.isDuringMatch()) {
            return false
        }
        val protectionTicks = GoalkeeperInputConfig.GK_HOLD_STEAL_PROTECTION_TICKS
        if (protectionTicks <= 0) {
            return false
        }
        val startTick = getHoldStartGameTime()
        if (startTick < 0L) {
            return false
        }
        return gameTime - startTick < protectionTicks
    }

    fun isHoldStealProtectedFrom(actingPlayer: ServerPlayer?): Boolean {
        if (level().isClientSide) {
            return false
        }
        val holderId = getHolderEntityId()
        if (holderId < 0) {
            return false
        }
        if (actingPlayer != null && actingPlayer.id == holderId) {
            return false
        }
        return isHoldStealProtectionActive()
    }

    fun enterHold(player: ServerPlayer) {
        val goalKickCatchAllowed = SetPieceRestrictionCoordinator.allowsGoalKickCatch(player)
        val throwInHoldAllowed = SetPieceRestrictionCoordinator.allowsThrowInHold(player)
        if (level().isClientSide || isImmovable ||
            (isPlayerBallMovementForbidden(player) && !goalKickCatchAllowed && !throwInHoldAllowed) ||
            !MatchParticipation.isParticipating(player)
        ) {
            return
        }
        if (holderEntityId == player.id) {
            updateHeldPosition(player)
            updateHeldOrientation(player)
            syncPhysicsToEntityData()
            syncPacketPositionCodec(x, y, z)
            return
        }
        holderEntityId = player.id
        holdStartTick = level().gameTime
        physicsState.linearVelocity = Vec3.ZERO
        physicsState.angularVelocity = Vec3.ZERO
        physicsState.onGround = false
        deltaMovement = Vec3.ZERO
        syncHolderToEntityData()
        GoalkeeperHoldPoseUtil.alignBodyToHead(player)
        updateHeldPosition(player)
        updateHeldOrientation(player)
        syncPhysicsToEntityData()
        syncPacketPositionCodec(x, y, z)
        GoalkeeperHoldLock.beginLock(player, level().gameTime)
    }

    fun releaseHold() {
        if (level().isClientSide || holderEntityId < 0) {
            return
        }
        notifyHoldEnded(holderEntityId)
        holderEntityId = -1
        holdStartTick = 0L
        syncHolderToEntityData()
    }

    fun dropAt(player: ServerPlayer) {
        val goalKickDropAllowed = SetPieceRestrictionCoordinator.allowsGoalKickDrop(player)
        if (level().isClientSide || isImmovable ||
            (isPlayerBallMovementForbidden(player) && !goalKickDropAllowed)
        ) {
            return
        }
        forceDropAt(player)
    }

    /** 服务端强制放下持球（忽略开球锁定等移动限制，仍受 [isImmovable] 约束）。 */
    fun forceDropAt(player: ServerPlayer) {
        if (level().isClientSide || isImmovable || holderEntityId != player.id) {
            return
        }
        val look = Vec3Math.normalizeSafe(Vec3Math.horizontal(player.lookAngle))
        val dropPos = player.position().add(look.scale(GoalkeeperInputConfig.GK_DROP_DISTANCE))
        releaseHold()
        setPos(dropPos.x, dropPos.y, dropPos.z)
        physicsState.linearVelocity = Vec3.ZERO
        physicsState.angularVelocity = Vec3.ZERO
        deltaMovement = Vec3.ZERO
        syncPhysicsToEntityData()
        syncPacketPositionCodec(x, y, z)
    }

    private fun notifyHoldEnded(holderId: Int) {
        val holder = level().getEntity(holderId)
        if (holder is ServerPlayer) {
            GoalkeeperHoldLock.onHoldEnded(holder)
        }
    }

    private fun tickHeld() {
        if (holderEntityId < 0) {
            return
        }
        val holder = level().getEntity(holderEntityId)
        if (holder !is ServerPlayer || !holder.isAlive) {
            releaseHold()
            return
        }

        updateHeldPosition(holder)
        updateHeldOrientation(holder)
        physicsState.linearVelocity = Vec3.ZERO
        physicsState.angularVelocity = Vec3.ZERO
        deltaMovement = Vec3.ZERO
        syncPhysicsToEntityData()
        syncPacketPositionCodec(x, y, z)
    }

    fun syncHeldPose(player: ServerPlayer, lookYaw: Float? = null, lookPitch: Float? = null) {
        if (level().isClientSide || !isHeldBy(player)) {
            return
        }
        updateHeldPosition(player, lookYaw, lookPitch)
        updateHeldOrientation(player, lookYaw, lookPitch)
        syncPhysicsToEntityData()
        syncPacketPositionCodec(x, y, z)
    }

    private fun updateHeldPosition(player: ServerPlayer, lookYaw: Float? = null, lookPitch: Float? = null) {
        val pos = if (lookYaw != null && lookPitch != null) {
            GoalkeeperHoldPoseUtil.computeThrowReleaseEntityPos(player, lookYaw, lookPitch)
        } else {
            GoalkeeperHoldPoseUtil.computeBallEntityPos(player)
        }
        setPos(pos.x, pos.y, pos.z)
    }

    private fun updateHeldOrientation(player: ServerPlayer, lookYaw: Float? = null, lookPitch: Float? = null) {
        val orientation = if (lookYaw != null && lookPitch != null) {
            GoalkeeperHoldPoseUtil.computeHeldOrientationFromLook(lookYaw, lookPitch)
        } else {
            GoalkeeperHoldPoseUtil.computeHeldOrientation(player, 1.0f)
        }
        physicsState.orientation.set(orientation)
        previousOrientation.set(orientation)
    }

    private fun syncHolderToEntityData() {
        entityData.set(DATA_HOLDER_ID, holderEntityId)
        entityData.set(
            DATA_HOLD_START_GAME_TIME,
            if (holderEntityId >= 0) holdStartTick else -1L,
        )
    }

    fun getPhysicsState(): FootballPhysicsState = physicsState

    /** 将当前物理状态与 [deltaMovement] 写入 NBT（含原版 [FootballPhysicsNbt.MOTION]），供数据包序列化。 */
    fun writePhysicsNbt(tag: CompoundTag) {
        NMBCTFootball.withErrReporter({ "FootballPhysicsNbt" }) { errReporter ->
            val output = ValueIOUtil.createNbtOutput(errReporter, level().registryAccess(), tag)
            FootballPhysicsNbt.write(physicsState, deltaMovement, output)
        }
    }

    /**
     * 从 NBT 恢复物理状态；若存在 [FootballPhysicsNbt.MOTION] 则覆盖线速度并同步 [deltaMovement]。
     * 服务端会同步到 [SynchedEntityData]。
     */
    fun applyPhysicsNbt(tag: CompoundTag) {
        NMBCTFootball.withErrReporter({ "FootballPhysicsNbt" }) { errReporter ->
            val input = ValueIOUtil.createNbtInput(errReporter, level().registryAccess(), tag)
            val motion = FootballPhysicsNbt.read(input, physicsState)
            val velocity = motion ?: physicsState.linearVelocity
            physicsState.linearVelocity = velocity
            deltaMovement = velocity
            previousOrientation.set(physicsState.orientation)
            renderLinearVelocity = physicsState.linearVelocity
            renderAngularVelocity = physicsState.angularVelocity
            if (!level().isClientSide) {
                syncPhysicsToEntityData()
                syncPacketPositionCodec(x, y, z)
            }
        }
    }

    fun getOrientation(): Quaternionf = Quaternionf(physicsState.orientation)

    fun getOrientation(partialTick: Float): Quaternionf {
        if (partialTick <= 0.0f) {
            return Quaternionf(previousOrientation)
        }
        if (partialTick >= 1.0f) {
            return getOrientation()
        }
        val omega = if (level().isClientSide) renderAngularVelocity else physicsState.angularVelocity
        if (omega.lengthSqr() < FootballPhysicsConfig.RENDER_STATIONARY_SPEED_SQR) {
            return Quaternionf(previousOrientation)
        }
        return QuaternionMath.integrate(previousOrientation, omega.scale(partialTick.toDouble()))
    }

    /**
     * 客户端渲染位置：运动时用 [xOld] + v·partialTick 外推；静止时回退原版插值，避免微速度导致上下抖。
     */
    fun getRenderPosition(partialTick: Float): Vec3 {
        if (!level().isClientSide) {
            return getPosition(partialTick)
        }
        if (isRenderStationaryOnGround()) {
            val t = partialTick.toDouble()
            return Vec3(
                xOld + (x - xOld) * t,
                y,
                zOld + (z - zOld) * t
            )
        }
        if (renderLinearVelocity.lengthSqr() < FootballPhysicsConfig.RENDER_STATIONARY_SPEED_SQR) {
            return getPosition(partialTick)
        }
        val t = partialTick.toDouble()
        val velocity = renderLinearVelocity
        return Vec3(
            xOld + velocity.x * t,
            yOld + velocity.y * t,
            zOld + velocity.z * t
        )
    }

    /** 接地且水平方向已静止：渲染时不应再对 Y 做插值/外推，否则微弹跳会在帧间表现为上下抖。 */
    private fun isRenderStationaryOnGround(): Boolean {
        if (!level().isClientSide || !physicsState.onGround) {
            return false
        }
        return Vec3Math.horizontal(physicsState.linearVelocity).lengthSqr() <
            FootballPhysicsConfig.RENDER_STATIONARY_SPEED_SQR
    }

    override fun isPickable(): Boolean = true

    override fun getPickResult(): ItemStack = ItemStack(Items.FOOTBALL)

    override fun interact(player: Player, hand: InteractionHand, location: Vec3): InteractionResult {
        return handlePlayerInteract(player)
    }

    override fun push(entity: Entity) {
        if (entity is ServerPlayer) {
            if (isImmovable || isPlayerBallMovementForbidden(entity) || isHoldStealProtectedFrom(entity)) {
                return
            }
            val now = (level() as? ServerLevel)?.gameTime ?: 0L
            if (shouldSkipPlayerBodyCollisionDetection(entity)) {
                return
            }
            val radius = FootballPhysicsConfig.RADIUS
            val ballCenter = position().add(0.0, radius, 0.0)
            val playerBox = entity.boundingBox
            val geometricContact = FootballPlayerBallCollision.contactAtSphereCenter(ballCenter, playerBox, radius)
                ?: FootballPlayerBallCollision.overlapHitAt(ballCenter, playerBox, radius)
            val contactNormal = geometricContact?.normal
                ?: FootballPhysicsSimulator.orientContactNormalTowardBall(
                    Vec3Math.normalizeSafe(Vec3Math.horizontal(ballCenter.subtract(entity.position()))),
                    entity.position(),
                    ballCenter,
                )
            val applied = if (SlideTackleSessions.isSliding(entity)) {
                applySlideBallKickFromPlayer(entity, now = now)
            } else {
                applyPlayerPushFromPlayer(entity, contactNormal, now = now)
            }
            if (applied) {
                syncPhysicsToEntityData()
            }
            return
        }
        if (entity is Player) {
            return
        }
        super.push(entity)
    }

    fun handlePlayerInteract(player: Player): InteractionResult {
        if (level().isClientSide) {
            return InteractionResult.SUCCESS
        }
        if (player !is ServerPlayer || !player.isCreative || !player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            return InteractionResult.PASS
        }
        return tryPickUpAsItem(player)
    }

    /** 潜行（Shift）+ 右键将足球收回物品栏。 */
    private fun tryPickUpAsItem(player: ServerPlayer): InteractionResult {
        if (!player.isShiftKeyDown) {
            return InteractionResult.PASS
        }

        releaseHold()
        val stack = ItemStack(Items.FOOTBALL)
        if (!player.addItem(stack)) {
            player.drop(stack, false)
        }
        val level = player.level()
        val pitch = 1.0f + (level.random.nextFloat() - level.random.nextFloat()) * 0.4f
        level.playSound(
            null,
            blockPosition(),
            SoundEvents.ITEM_PICKUP,
            SoundSource.PLAYERS,
            0.2f,
            pitch,
        )
        discard()
        return InteractionResult.SUCCESS
    }

    override fun hurtServer(
        level: ServerLevel,
        source: DamageSource,
        damage: Float
    ): Boolean = false

    override fun readAdditionalSaveData(input: ValueInput) {
        FootballPhysicsNbt.read(input, physicsState)
        deltaMovement = physicsState.linearVelocity
        previousOrientation.set(physicsState.orientation)
        val immovable = input.getBooleanOr(NBT_IMMOVABLE, false)
        this.isImmovable = immovable
        if (immovable) {
            captureImmovableSnapshot()
        } else {
            immovableSnapshot = null
        }
    }

    override fun addAdditionalSaveData(output: ValueOutput) {
        FootballPhysicsNbt.write(physicsState, deltaMovement, output)
        output.putBoolean(NBT_IMMOVABLE, isImmovable)
    }

    private fun loadPhysicsFromEntityData() {
        physicsState.linearVelocity = entityData.get(DATA_LINEAR_VEL).toVec3()
        physicsState.angularVelocity = entityData.get(DATA_ANGULAR_VEL).toVec3()
        physicsState.onGround = entityData.get(DATA_ON_GROUND)
        renderLinearVelocity = physicsState.linearVelocity
        renderAngularVelocity = physicsState.angularVelocity
        previousOrientation.set(physicsState.orientation)
    }

    private fun syncPhysicsToEntityData() {
        entityData.set(DATA_LINEAR_VEL, physicsState.linearVelocity.toVector3f())
        entityData.set(DATA_ANGULAR_VEL, physicsState.angularVelocity.toVector3f())
        entityData.set(DATA_ON_GROUND, physicsState.onGround)
    }

    private fun applySyncedPhysicsFromEntityData() {
        val syncedLinear = entityData.get(DATA_LINEAR_VEL).toVec3()
        val syncedAngular = entityData.get(DATA_ANGULAR_VEL).toVec3()
        val syncedOnGround = entityData.get(DATA_ON_GROUND)

        val linearDelta = physicsState.linearVelocity.distanceTo(syncedLinear)
        val angularDelta = physicsState.angularVelocity.distanceTo(syncedAngular)

        if (linearDelta > FootballPhysicsConfig.ORIENTATION_RESET_VELOCITY_DELTA ||
            angularDelta > FootballPhysicsConfig.ORIENTATION_RESET_OMEGA_DELTA
        ) {
            FootballPhysicsSimulator.resetRollingOrientation(physicsState)
            previousOrientation.set(physicsState.orientation)
        }

        physicsState.linearVelocity = syncedLinear
        physicsState.angularVelocity = syncedAngular
        physicsState.onGround = syncedOnGround
    }

    companion object {
        /** 球-玩家分离时的皮肤厚度，避免下一 tick 再次嵌入。 */
        private const val PLAYER_SEPARATION_EPSILON = 0.015
        /** 玩家主动推球的接触余量，用于覆盖玩家本 tick 扫过球边缘但尚未深度重叠的情况。 */
        private const val PLAYER_PUSH_CONTACT_MARGIN = 0.08

        fun registerSerializers() {
            FabricEntityDataRegistry.register(ENTITY_ID, SERIALIZER_IMMOVABLE_TARGET_PLAYERS)
        }

        private val SERIALIZER_IMMOVABLE_TARGET_PLAYERS: EntityDataSerializer<Set<UUID>> =
            EntityDataSerializer.forValueType(StreamCodec.of<FriendlyByteBuf, Set<UUID>>(
                { buf, uuidSet -> buf.writeCollection(uuidSet, UUIDUtil.STREAM_CODEC) },
                { buf -> buf.readCollection(::HashSet, UUIDUtil.STREAM_CODEC) }
            ))

        private val DATA_LINEAR_VEL: EntityDataAccessor<Vector3fc> = SynchedEntityData.defineId(
            Football::class.java,
            EntityDataSerializers.VECTOR3
        )
        private val DATA_ANGULAR_VEL: EntityDataAccessor<Vector3fc> = SynchedEntityData.defineId(
            Football::class.java,
            EntityDataSerializers.VECTOR3
        )
        private val DATA_ON_GROUND: EntityDataAccessor<Boolean> = SynchedEntityData.defineId(
            Football::class.java,
            EntityDataSerializers.BOOLEAN
        )
        private val DATA_HOLDER_ID: EntityDataAccessor<Int> = SynchedEntityData.defineId(
            Football::class.java,
            EntityDataSerializers.INT
        )
        private val DATA_HOLD_START_GAME_TIME: EntityDataAccessor<Long> = SynchedEntityData.defineId(
            Football::class.java,
            EntityDataSerializers.LONG
        )
        private val DATA_IMMOVABLE: EntityDataAccessor<Boolean> = SynchedEntityData.defineId(
            Football::class.java,
            EntityDataSerializers.BOOLEAN
        )
        private val DATA_IMMOVABLE_TARGET_PLAYERS: EntityDataAccessor<Set<UUID>> = SynchedEntityData.defineId(
            Football::class.java,
            SERIALIZER_IMMOVABLE_TARGET_PLAYERS
        )

        private const val NBT_IMMOVABLE = "immovable"

        private val ENTITY_ID = NMBCTFootball.id("football")
        private val ENTITY_KEY: ResourceKey<EntityType<*>> = ResourceKey.create(Registries.ENTITY_TYPE, ENTITY_ID)

        val ENTITY_TYPE: EntityType<Football> = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            ENTITY_KEY,
            EntityType.Builder.of(::Football, MobCategory.MISC)
                .sized(0.5f, 0.5f)
                // 让客户端在“实体渲染距离=100%”时可从最远约 128 格看到足球。
                .clientTrackingRange(128)
                .updateInterval(1)
                .build(ENTITY_KEY)
        )

        private fun Vector3fc.toVec3(): Vec3 = Vec3(x().toDouble(), y().toDouble(), z().toDouble())
    }
}
