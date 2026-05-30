package net.astrorbits.football

import net.astrorbits.football.input.GoalkeeperHoldLock
import net.astrorbits.football.input.GoalkeeperInputConfig
import net.astrorbits.football.item.Items
import net.astrorbits.football.match.MatchConfigHolder
import net.astrorbits.football.match.MatchPhase
import net.astrorbits.football.match.MatchState
import net.astrorbits.football.match.TeamSide
import net.astrorbits.football.physics.FootballPhysicsConfig
import net.astrorbits.football.physics.FootballPhysicsState
import net.astrorbits.football.util.CobwebUtil
import net.astrorbits.football.util.FootballPhysicsSimulator
import net.astrorbits.football.util.QuaternionMath
import net.astrorbits.football.util.GoalkeeperHoldPoseUtil
import net.astrorbits.football.util.Vec3Math
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.resources.ResourceKey
import net.minecraft.core.Registry
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.permissions.Permissions
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.entity.MoverType
import net.minecraft.world.level.Level
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import org.joml.Vector3f
import org.joml.Vector3fc

class Football(type: EntityType<*>, level: Level) : Entity(type, level) {
    private val physicsState = FootballPhysicsState()
    private val previousOrientation = Quaternionf()
    /** 本 tick 渲染用的速度快照，避免帧内同步改动导致位置/朝向抖动。 */
    private var renderLinearVelocity = Vec3.ZERO
    private var renderAngularVelocity = Vec3.ZERO
    /** [Entity] 构造过程中会调用 [setPos]，此时尚未执行属性初始化器。 */
    private var fieldsInitialized = false
    private var holderEntityId: Int = -1
    private var holdStartTick: Long = 0L

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
            tickHeld()
            return
        }

        FootballPhysicsSimulator.applyAirForces(physicsState)

        val movement = physicsState.linearVelocity
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
    }

    private fun detectGoal(prevPos: Vec3, currPos: Vec3) {
        if (MatchState.currentPhase == MatchPhase.PRE_MATCH || MatchState.currentPhase == MatchPhase.FINISHED) return

        val config = MatchConfigHolder.current
        val radius = FootballPhysicsConfig.RADIUS
        val prevCenter = prevPos.add(0.0, radius, 0.0)
        val currCenter = currPos.add(0.0, radius, 0.0)

        // 球门 A 由 A 队防守，球入门则 B 队得分
        checkGoal(config.goalA, prevCenter, currCenter, TeamSide.B)
        // 球门 B 由 B 队防守，球入门则 A 队得分
        checkGoal(config.goalB, prevCenter, currCenter, TeamSide.A)
    }

    private fun checkGoal(
        goal: net.astrorbits.football.match.GoalConfig,
        prevCenter: Vec3,
        currCenter: Vec3,
        scoringTeam: TeamSide,
    ) {
        val facing = Vec3(goal.facingX, goal.facingY, goal.facingZ)
        val facingLenSqr = facing.lengthSqr()
        if (facingLenSqr < 1e-6) return

        val gx1 = goal.x1; val gy1 = goal.y1; val gz1 = goal.z1
        val gx2 = goal.x2; val gy2 = goal.y2; val gz2 = goal.z2

        // 判定面向球门内移 1 格
        val refX = gx1 + facing.x
        val refY = gy1 + facing.y
        val refZ = gz1 + facing.z
        val d1 = (prevCenter.x - refX) * facing.x + (prevCenter.y - refY) * facing.y + (prevCenter.z - refZ) * facing.z
        val d2 = (currCenter.x - refX) * facing.x + (currCenter.y - refY) * facing.y + (currCenter.z - refZ) * facing.z

        if (d1 * d2 >= 0) return
        if (d2 - d1 <= 0) return

        // 穿越点
        val t = d1 / (d1 - d2)
        val movement = currCenter.subtract(prevCenter)
        val ix = prevCenter.x + movement.x * t
        val iy = prevCenter.y + movement.y * t
        val iz = prevCenter.z + movement.z * t

        // 穿越点是否在门框范围内
        val minX = minOf(gx1, gx2)
        val maxX = maxOf(gx1, gx2)
        val minY = minOf(gy1, gy2)
        val maxY = maxOf(gy1, gy2)
        val minZ = minOf(gz1, gz2)
        val maxZ = maxOf(gz1, gz2)

        if (ix < minX || ix > maxX) return
        if (iy < minY || iy > maxY) return
        if (iz < minZ - 1.01 || iz > maxZ + 1.01) return

        MatchState.onGoal(scoringTeam)
        FootballParticles.playGoal(level(), FootballParticles.centerOfFootball(this))
    }

    fun kick(kickPoint: Vec3, direction: Vec3) {
        if (level().isClientSide) {
            return
        }

        releaseHold()
        val center = position().add(0.0, FootballPhysicsConfig.RADIUS, 0.0)
        FootballPhysicsSimulator.applyKick(physicsState, kickPoint, direction, center)
        previousOrientation.set(physicsState.orientation)
        deltaMovement = physicsState.linearVelocity
        syncPhysicsToEntityData()
        syncPacketPositionCodec(x, y, z)
    }

    fun trap() {
        if (level().isClientSide) {
            return
        }
        releaseHold()
        physicsState.linearVelocity = Vec3.ZERO
        physicsState.angularVelocity = Vec3.ZERO
        deltaMovement = Vec3.ZERO
        previousOrientation.set(physicsState.orientation)
        syncPhysicsToEntityData()
    }

    fun applyDribbleAssist(horizontalVelocity: Vec3) {
        if (level().isClientSide) {
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
        if (level().isClientSide) {
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
        if (level().isClientSide) {
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

    override fun interact(player: Player, hand: InteractionHand, location: Vec3): InteractionResult {
        return handlePlayerInteract(player)
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
        physicsState.linearVelocity = Vec3(
            input.getDoubleOr("lv_x", 0.0),
            input.getDoubleOr("lv_y", 0.0),
            input.getDoubleOr("lv_z", 0.0)
        )
        physicsState.angularVelocity = Vec3(
            input.getDoubleOr("av_x", 0.0),
            input.getDoubleOr("av_y", 0.0),
            input.getDoubleOr("av_z", 0.0)
        )
        physicsState.onGround = input.getBooleanOr("on_ground", false)
        deltaMovement = physicsState.linearVelocity
    }

    override fun addAdditionalSaveData(output: ValueOutput) {
        output.putDouble("lv_x", physicsState.linearVelocity.x)
        output.putDouble("lv_y", physicsState.linearVelocity.y)
        output.putDouble("lv_z", physicsState.linearVelocity.z)
        output.putDouble("av_x", physicsState.angularVelocity.x)
        output.putDouble("av_y", physicsState.angularVelocity.y)
        output.putDouble("av_z", physicsState.angularVelocity.z)
        output.putBoolean("on_ground", physicsState.onGround)
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
        fun init() {
            // static init
        }

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

        private val ENTITY_ID = NMBCTFootball.id("football")
        private val ENTITY_KEY: ResourceKey<EntityType<*>> = ResourceKey.create(Registries.ENTITY_TYPE, ENTITY_ID)

        val ENTITY_TYPE: EntityType<Football> = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            ENTITY_KEY,
            EntityType.Builder.of(::Football, MobCategory.MISC)
                .sized(0.5f, 0.5f)
                .clientTrackingRange(64)
                .updateInterval(1)
                .build(ENTITY_KEY)
        )

        private fun Vector3fc.toVec3(): Vec3 = Vec3(x().toDouble(), y().toDouble(), z().toDouble())
    }
}
