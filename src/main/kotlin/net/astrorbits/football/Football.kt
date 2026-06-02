package net.astrorbits.football

import net.astrorbits.football.input.FootballDribbleSessions
import net.astrorbits.football.input.FootballInputConfig
import net.astrorbits.football.input.FootballKickPushGrace
import net.astrorbits.football.input.FootballPlayerBallContactGrace
import net.astrorbits.football.input.SlideTackleSessions
import net.astrorbits.football.input.GoalkeeperHoldLock
import net.astrorbits.football.input.GoalkeeperInputConfig
import net.astrorbits.football.item.Items
import net.astrorbits.football.match.*
import net.astrorbits.football.network.FootballNetworking
import net.astrorbits.football.physics.FootballNetInteraction
import net.astrorbits.football.physics.FootballPhysicsConfig
import net.astrorbits.football.physics.FootballPhysicsNbt
import net.astrorbits.football.physics.FootballPhysicsState
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
import kotlin.jvm.optionals.getOrNull
import kotlin.jvm.optionals.toSet
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class Football(type: EntityType<*>, level: Level) : Entity(type, level) {
    /** 最后踢球/触球玩家的 UUID，用于进球归属。 */
    var lastKicker: UUID? = null
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

    /** 指定玩家是否被禁止以任何方式移动此球（传送除外）。含开球锁定与复位延迟。 */
    fun isPlayerBallMovementForbidden(player: Player): Boolean {
        if (immovableTargetPlayers.contains(player.uuid)) return true
        if (player is ServerPlayer && MatchState.isKickoffInteractionLocked(player)) return true
        return false
    }

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

        FootballPhysicsSimulator.applyAirForces(physicsState)
        val pushedPlayersThisTick = hashSetOf<UUID>()

        val movement = physicsState.linearVelocity
        val worldMotion = advanceWithWorldCollisions(movement)

        resolvePlayerCollisions(
            worldMotion.beforeMove,
            position(),
            movement,
            pushedPlayersThisTick,
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

    private fun advanceWithWorldCollisions(movement: Vec3): WorldMotionResult {
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

        detectGoal(beforeMove, position())
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

    private fun playerBallPushDirection(player: ServerPlayer, ballCenter: Vec3): Vec3 {
        val fromPlayer = Vec3Math.horizontal(ballCenter.subtract(player.position()))
        val fallback = Vec3Math.normalizeSafe(effectivePlayerHorizontalMotion(player))
        val baseDir = Vec3Math.normalizeSafe(fromPlayer, fallback)
        return applyBodyPushDeflection(player, baseDir)
    }

    private fun applyBodyPushDeflection(player: ServerPlayer, pushDir: Vec3): Vec3 {
        val moveDir = Vec3Math.normalizeSafe(effectivePlayerHorizontalMotion(player))
        if (moveDir.lengthSqr() <= 1.0e-8 || pushDir.lengthSqr() <= 1.0e-8) {
            return pushDir
        }

        val lateral = Vec3(-moveDir.z, 0.0, moveDir.x)
        val side = pushDir.dot(lateral)
        if (abs(side) <= PLAYER_PUSH_DEFLECTION_DEADZONE) {
            return pushDir
        }

        val glancing = (1.0 - pushDir.dot(moveDir).coerceIn(0.0, 1.0)).coerceIn(0.0, 1.0)
        val bias = PLAYER_PUSH_DEFLECTION_BIAS * (0.45 + glancing * 0.55)
        return Vec3Math.normalizeSafe(pushDir.add(lateral.scale(kotlin.math.sign(side) * bias)), pushDir)
    }

    private fun applyPlayerPushFromPlayer(
        player: ServerPlayer,
        contactNormal: Vec3,
        ballCenter: Vec3 = position().add(0.0, FootballPhysicsConfig.RADIUS, 0.0),
    ): Boolean {
        if (isImmovable || isPlayerBallMovementForbidden(player)) {
            return false
        }
        if (contactNormal.lengthSqr() <= 1.0e-8) {
            return false
        }

        val sliding = SlideTackleSessions.isSliding(player)
        val playerHorizontal = effectivePlayerHorizontalMotion(player)
        val applied = FootballPhysicsSimulator.applyPlayerBodyBallPush(
            physicsState,
            playerHorizontal,
            velocityTransfer = 1.0,
        )
        val slideBallImpactSpeed = if (sliding) playerHorizontal.horizontalDistance() else 0.0
        if (applied) {
            deltaMovement = physicsState.linearVelocity
            MatchState.tryNotifyKickoffBallTouched(player)
            if (sliding) {
                FootballSounds.playSlideTackleBallHit(player, blockPosition(), slideBallImpactSpeed)
            }
            val now = (level() as? ServerLevel)?.gameTime ?: 0L
            FootballPlayerBallContactGrace.record(player, this, now, sliding)
        }
        return applied
    }

    private fun shouldSkipPlayerBodyInteraction(player: ServerPlayer, now: Long): Boolean {
        if (FootballDribbleSessions.shouldIgnoreCollision(player, this, now)) {
            return true
        }
        if (FootballKickPushGrace.shouldSuppressPlayerPush(player, this, now)) {
            return true
        }
        return FootballPlayerBallContactGrace.shouldIgnoreBodyCollision(player, this, now)
    }

    private fun effectivePlayerHorizontalMotion(player: ServerPlayer): Vec3 {
        val deltaMovement = Vec3Math.horizontal(player.deltaMovement)
        val positionDelta = Vec3(player.x - player.xOld, 0.0, player.z - player.zOld)
        val measured = if (positionDelta.lengthSqr() > deltaMovement.lengthSqr()) positionDelta else deltaMovement
        val slide = SlideTackleSessions.effectiveHorizontalVelocity(player) ?: return measured
        return if (slide.lengthSqr() >= measured.lengthSqr()) slide else measured
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
        val searchBox = previousBallBox.minmax(boundingBox).inflate(1.4, 0.9, 1.4)
        val players = serverLevel.getEntitiesOfClass(ServerPlayer::class.java, searchBox) { player ->
            player.isAlive && !player.isSpectator && !player.noPhysics
        }

        for (player in players) {
            val suppressBodyInteraction =
                isPlayerBallMovementForbidden(player) || shouldSkipPlayerBodyInteraction(player, now)

            val playerBox = player.boundingBox
            var contactNormal: Vec3? = null
            var hadBodyContact = false
            val motion = currentCenter.subtract(previousCenter)
            if (!suppressBodyInteraction) {
                val segmentEnd = if (motion.lengthSqr() > 1.0e-12) {
                    currentCenter
                } else {
                    val playerStep = Vec3(player.x - player.xOld, player.y - player.yOld, player.z - player.zOld)
                    if (playerStep.lengthSqr() > 1.0e-12) previousCenter.add(playerStep) else currentCenter
                }
                if (segmentEnd.distanceToSqr(previousCenter) > 1.0e-12) {
                    val hit = segmentAabbHit(previousCenter, segmentEnd, playerBox.inflate(radius))
                    if (hit != null) {
                        val segment = segmentEnd.subtract(previousCenter)
                        val impactCenter = previousCenter.add(segment.scale(hit.t))
                            .add(hit.normal.scale(PLAYER_SEPARATION_EPSILON))
                        setCenterWithWorldContactGuards(impactCenter)
                        contactNormal = hit.normal
                        hadBodyContact = true
                    }
                }
            }

            val depenetration = computeSpherePlayerDepenetration(
                center = position().add(0.0, radius, 0.0),
                playerBox = playerBox,
                radius = radius,
            )
            if (depenetration != null && !suppressBodyInteraction) {
                val correctedCenter = position().add(0.0, radius, 0.0).add(depenetration.push)
                setCenterWithWorldContactGuards(correctedCenter)
                contactNormal = depenetration.normal
                hadBodyContact = true
            }

            val normal = contactNormal ?: continue
            val impactBallCenter = position().add(0.0, radius, 0.0)
            val pushNormal = FootballPhysicsSimulator.orientContactNormalTowardBall(
                normal,
                player.position(),
                impactBallCenter,
            )
            if (!suppressBodyInteraction && player.uuid !in pushedPlayersThisTick) {
                if (applyPlayerPushFromPlayer(player, pushNormal, impactBallCenter)) {
                    pushedPlayersThisTick.add(player.uuid)
                    hadBodyContact = true
                }
            }

            if (!suppressBodyInteraction && hadBodyContact) {
                MatchState.tryNotifyKickoffBallTouched(player)
                FootballPlayerBallContactGrace.record(
                    player,
                    this,
                    now,
                    SlideTackleSessions.isSliding(player),
                )
            }

            val velocity = physicsState.linearVelocity
            val normalVelocity = velocity.dot(pushNormal)
            val approachSpeed = max((-normalVelocity), -intendedMotion.dot(pushNormal)).coerceAtLeast(0.0)
            if (approachSpeed < FootballInputConfig.BALL_PLAYER_RECOIL_MIN_SPEED) {
                continue
            }
            val recoilDir = Vec3Math.horizontal(pushNormal).scale(-1.0)
            val direction = if (recoilDir.lengthSqr() > 1.0e-8) Vec3Math.normalizeSafe(recoilDir) else pushNormal.scale(-1.0)
            val pushMagnitude = (approachSpeed * FootballInputConfig.BALL_PLAYER_PUSH_SCALE)
                .coerceAtMost(FootballInputConfig.BALL_PLAYER_MAX_PUSH)
                .coerceAtLeast(0.0)
            if (pushMagnitude <= 1.0e-6) {
                continue
            }
            player.deltaMovement = player.deltaMovement.add(direction.scale(pushMagnitude))
            player.hurtMarked = true
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

    private data class SegmentAabbHit(val t: Double, val normal: Vec3)

    private fun segmentAabbHit(start: Vec3, end: Vec3, box: AABB): SegmentAabbHit? {
        val direction = end.subtract(start)
        var tMin = 0.0
        var tMax = 1.0
        var enterNormal = Vec3.ZERO

        fun updateAxis(
            startValue: Double,
            dirValue: Double,
            minBound: Double,
            maxBound: Double,
            negativeNormal: Vec3,
            positiveNormal: Vec3,
        ): Boolean {
            if (abs(dirValue) < 1.0e-9) {
                return startValue in minBound..maxBound
            }

            val t1 = (minBound - startValue) / dirValue
            val t2 = (maxBound - startValue) / dirValue
            val axisEnterT: Double
            val axisExitT: Double
            val axisEnterNormal: Vec3

            if (t1 <= t2) {
                axisEnterT = t1
                axisExitT = t2
                axisEnterNormal = negativeNormal
            } else {
                axisEnterT = t2
                axisExitT = t1
                axisEnterNormal = positiveNormal
            }

            if (axisEnterT > tMin) {
                tMin = axisEnterT
                enterNormal = axisEnterNormal
            }
            tMax = min(tMax, axisExitT)
            return tMin <= tMax
        }

        if (!updateAxis(start.x, direction.x, box.minX, box.maxX, Vec3(-1.0, 0.0, 0.0), Vec3(1.0, 0.0, 0.0))) return null
        if (!updateAxis(start.y, direction.y, box.minY, box.maxY, Vec3(0.0, -1.0, 0.0), Vec3(0.0, 1.0, 0.0))) return null
        if (!updateAxis(start.z, direction.z, box.minZ, box.maxZ, Vec3(0.0, 0.0, -1.0), Vec3(0.0, 0.0, 1.0))) return null
        if (tMax < 0.0 || tMin > 1.0) return null
        return SegmentAabbHit(t = tMin.coerceIn(0.0, 1.0), normal = enterNormal)
    }

    private fun detectGoal(prevPos: Vec3, currPos: Vec3) {
        if (MatchState.currentPhase == MatchPhase.PRE_MATCH || MatchState.currentPhase == MatchPhase.FINISHED) return
        if (MatchState.postGoalResetPending) return

        val config = MatchConfigHolder.current
        val radius = FootballPhysicsConfig.RADIUS
        val prevCenter = prevPos.add(0.0, radius, 0.0)
        val currCenter = currPos.add(0.0, radius, 0.0)

        // 球门 A 由 A 队防守，球入门则 B 队得分；出底线则视触球方判角球/球门球
        checkGoalOrOut(config.goalA, prevCenter, currCenter, TeamSide.A, TeamSide.B)
        // 球门 B 由 B 队防守，球入门则 A 队得分
        checkGoalOrOut(config.goalB, prevCenter, currCenter, TeamSide.B, TeamSide.A)
        // 边线出界
        checkSidelineOut(config.sidelineA, prevCenter, currCenter)
        checkSidelineOut(config.sidelineB, prevCenter, currCenter)
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
        val lastTouchTeam = lastKicker?.let { MatchState.getPlayerTeam(it) }
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
        val facing = Vec3(goal.facingX, goal.facingY, goal.facingZ)
        val facingLenSqr = facing.lengthSqr()
        if (facingLenSqr < 1e-6) return

        val gx1 = goal.x1; val gy1 = goal.y1; val gz1 = goal.z1
        val gx2 = goal.x2; val gy2 = goal.y2; val gz2 = goal.z2

        val refX = gx1 + facing.x
        val refY = gy1 + facing.y
        val refZ = gz1 + facing.z
        val d1 = (prevCenter.x - refX) * facing.x + (prevCenter.y - refY) * facing.y + (prevCenter.z - refZ) * facing.z
        val d2 = (currCenter.x - refX) * facing.x + (currCenter.y - refY) * facing.y + (currCenter.z - refZ) * facing.z

        if (d1 * d2 >= 0) return
        if (d2 - d1 <= 0) return

        val t = d1 / (d1 - d2)
        val movement = currCenter.subtract(prevCenter)
        val ix = prevCenter.x + movement.x * t
        val iy = prevCenter.y + movement.y * t
        val iz = prevCenter.z + movement.z * t

        val minX = minOf(gx1, gx2)
        val maxX = maxOf(gx1, gx2)
        val minY = minOf(gy1, gy2)
        val maxY = maxOf(gy1, gy2)
        val minZ = minOf(gz1, gz2)
        val maxZ = maxOf(gz1, gz2)

        // 球门框内 → 进球
        val inGoal = ix in minX..maxX
                && iy in minY..maxY
                && iz in minZ - 1.01..maxZ + 1.01

        val server = (level() as? ServerLevel)?.server ?: return

        if (inGoal) {
            if (MatchState.postGoalResetPending) return
            MatchState.postGoalResetPending = true
            // 进球
            MatchState.onGoal(attackingTeam)
            val scorerName = lastKicker?.let { server.playerList.getPlayer(it)?.gameProfile?.name } ?: "?"
            val scorerTeam = lastKicker?.let { MatchState.getPlayerTeam(it) } ?: attackingTeam
            val ownGoal = scorerTeam != attackingTeam
            FootballNetworking.broadcastGoalScored(
                server, attackingTeam, scorerName, scorerTeam, MatchState.teamAScore, MatchState.teamBScore, ownGoal,
            )
            FootballParticles.playGoal(level(), FootballParticles.centerOfFootball(this))
            PostGoalBallResetScheduler.schedule(
                level() as ServerLevel,
                afterReset = PendingAfterReset.PostGoal(defendingTeam),
            )
        } else {
            // 穿越门线平面但不在门框内 → 出底线
            val lastTouchTeam = lastKicker?.let { MatchState.getPlayerTeam(it) }
            val outType: GoalLineOutType
            val restartTeam: TeamSide

            if (lastTouchTeam == attackingTeam) {
                // 攻方最后触球 → 球门球（守方开球）
                outType = GoalLineOutType.GOAL_KICK
                restartTeam = defendingTeam
            } else {
                // 守方最后触球（或无归属） → 角球（攻方开球）
                outType = GoalLineOutType.CORNER_KICK
                restartTeam = attackingTeam
            }

            // 使用配置中的开球点
            val ballPos = if (outType == GoalLineOutType.GOAL_KICK) {
                val gk = goal.goalKick
                Vec3(gk.x, gk.y, gk.z)
            } else {
                // 角球：根据穿越点在球门哪一侧决定用左角旗还是右角旗
                val goalCenterX = (gx1 + gx2) / 2.0
                val goalCenterZ = (gz1 + gz2) / 2.0
                val onRight = if (abs(facing.x) > abs(facing.z))
                    iz > goalCenterZ
                else
                    ix > goalCenterX
                val corner = if (onRight) goal.cornerKickRight else goal.cornerKickLeft
                Vec3(corner.x, corner.y, corner.z)
            }

            scheduleOutOfBoundsRestart(level() as ServerLevel, server, ballPos, restartTeam, outType)
        }
    }

    private fun scheduleOutOfBoundsRestart(
        level: ServerLevel,
        server: MinecraftServer,
        ballPos: Vec3,
        restartTeam: TeamSide,
        outType: GoalLineOutType,
    ) {
        if (MatchState.postGoalResetPending) return
        MatchState.postGoalResetPending = true
        PostGoalBallResetScheduler.schedule(level, ballPos, PendingAfterReset.GoalLineOut(restartTeam))
        val (touchName, touchTeam) = resolveLastTouch(server)
        FootballNetworking.broadcastGoalLineOut(
            server, outType, restartTeam, ballPos.x, ballPos.y, ballPos.z, touchName, touchTeam,
        )
    }

    private fun resolveLastTouch(server: MinecraftServer): Pair<String, TeamSide?> {
        val uuid = lastKicker ?: return "" to null
        val name = server.playerList.getPlayer(uuid)?.gameProfile?.name ?: "?"
        return name to MatchState.getPlayerTeam(uuid)
    }

    /**
     * @param ignoreImmovableTargets 为 true 时跳过 [immovableTargetPlayers] 检查（如命令踢球）。
     */
    fun kick(kickPoint: Vec3, direction: Vec3, ignoreImmovableTargets: Boolean = false) {
        if (level().isClientSide || isImmovable) {
            return
        }
        if (!ignoreImmovableTargets) {
            val kickerUuid = lastKicker
            if (kickerUuid != null && kickerUuid in immovableTargetPlayers) {
                return
            }
        }

        releaseHold()
        val center = position().add(0.0, FootballPhysicsConfig.RADIUS, 0.0)
        FootballPhysicsSimulator.applyKick(physicsState, kickPoint, direction, center)
        previousOrientation.set(physicsState.orientation)
        deltaMovement = physicsState.linearVelocity
        val serverLevel = level() as? ServerLevel
        if (serverLevel != null) {
            val kickerUuid = lastKicker
            val kicker = kickerUuid?.let { serverLevel.server?.playerList?.getPlayer(it) }
            if (kicker != null) {
                FootballKickPushGrace.record(kicker, this, serverLevel.gameTime)
                separateBallFromKicker(kicker, direction)
            }
        }
        syncPhysicsToEntityData()
        syncPacketPositionCodec(x, y, z)
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

    fun trap() {
        if (level().isClientSide || isImmovable) {
            return
        }
        val kickerUuid = lastKicker
        if (kickerUuid != null && kickerUuid in immovableTargetPlayers) {
            return
        }
        releaseHold()
        physicsState.linearVelocity = Vec3.ZERO
        physicsState.angularVelocity = Vec3.ZERO
        deltaMovement = Vec3.ZERO
        previousOrientation.set(physicsState.orientation)
        syncPhysicsToEntityData()
    }

    fun applyDribbleAssist(horizontalVelocity: Vec3, player: ServerPlayer) {
        if (level().isClientSide || isImmovable || isPlayerBallMovementForbidden(player)) {
            return
        }

        physicsState.linearVelocity = Vec3(
            horizontalVelocity.x,
            physicsState.linearVelocity.y,
            horizontalVelocity.z
        )
        val rolling = Vec3Math.rollingAngularVelocity(horizontalVelocity, FootballPhysicsConfig.RADIUS)
        physicsState.angularVelocity = Vec3(rolling.x, 0.0, rolling.z)
        deltaMovement = physicsState.linearVelocity
        syncPhysicsToEntityData()
    }

    fun getRollingDirection(): Vec3 = FootballPhysicsSimulator.getRollingDirection(physicsState)

    fun getHolderEntityId(): Int = entityData.get(DATA_HOLDER_ID)

    fun isHeld(): Boolean = getHolderEntityId() >= 0

    fun isHeldBy(player: ServerPlayer): Boolean = getHolderEntityId() == player.id

    fun enterHold(player: ServerPlayer) {
        if (level().isClientSide || isImmovable || isPlayerBallMovementForbidden(player)) {
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
        if (level().isClientSide || isImmovable || isPlayerBallMovementForbidden(player)) {
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

        if (level().gameTime - holdStartTick > GoalkeeperInputConfig.GK_HOLD_MAX_TICKS) {
            dropAt(holder)
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
            if (isImmovable || isPlayerBallMovementForbidden(entity)) {
                return
            }
            val now = (level() as? ServerLevel)?.gameTime ?: 0L
            if (shouldSkipPlayerBodyInteraction(entity, now)) {
                return
            }
            val radius = FootballPhysicsConfig.RADIUS
            val ballCenter = position().add(0.0, radius, 0.0)
            val pushDir = playerBallPushDirection(entity, ballCenter)
            if (applyPlayerPushFromPlayer(entity, pushDir)) {
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
        /** 身体推球的刻意侧向偏移：普通移动控球不应像带球技能一样稳定。 */
        private const val PLAYER_PUSH_DEFLECTION_BIAS = 0.62
        private const val PLAYER_PUSH_DEFLECTION_DEADZONE = 0.02

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
