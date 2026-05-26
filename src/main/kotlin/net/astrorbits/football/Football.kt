package net.astrorbits.football

import net.astrorbits.football.physics.FootballPhysicsConfig
import net.astrorbits.football.physics.FootballPhysicsState
import net.astrorbits.football.util.CobwebUtil
import net.astrorbits.football.util.FootballPhysicsSimulator
import net.astrorbits.football.util.QuaternionMath
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.resources.ResourceKey
import net.minecraft.core.Registry
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
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

    init {
        isNoGravity = true
        requiresPrecisePosition = true
        blocksBuilding = false
    }

    override fun defineSynchedData(entityData: SynchedEntityData.Builder) {
        entityData.define(DATA_LINEAR_VEL, Vector3f())
        entityData.define(DATA_ANGULAR_VEL, Vector3f())
        entityData.define(DATA_ON_GROUND, false)
    }

    override fun tick() {
        super.tick()

        if (tickCount == 1) {
            loadPhysicsFromEntityData()
        }

        FootballPhysicsSimulator.applyAirForces(physicsState)

        val movement = physicsState.linearVelocity
        deltaMovement = movement
        move(MoverType.SELF, movement)

        FootballPhysicsSimulator.resolveCollisions(
            physicsState,
            horizontalCollision,
            verticalCollisionBelow,
            onGround()
        )

        physicsState.inCobweb = false
        if (CobwebUtil.isIntersectingCobweb(level(), boundingBox)) {
            CobwebUtil.applyCobwebDrag(physicsState)
        }

        previousOrientation.set(physicsState.orientation)
        FootballPhysicsSimulator.integrateOrientation(physicsState)

        deltaMovement = physicsState.linearVelocity

        if (!level().isClientSide) {
            syncPhysicsToEntityData()
        }
    }

    fun kick(kickPoint: Vec3, direction: Vec3) {
        if (level().isClientSide) {
            return
        }

        FootballPhysicsSimulator.applyKick(physicsState, kickPoint, direction, position())
        deltaMovement = physicsState.linearVelocity
        syncPhysicsToEntityData()
        syncPacketPositionCodec(x, y, z)
    }

    fun getRollingDirection(): Vec3 = FootballPhysicsSimulator.getRollingDirection(physicsState)

    fun getPhysicsState(): FootballPhysicsState = physicsState

    fun getOrientation(): Quaternionf = Quaternionf(physicsState.orientation)

    fun getOrientation(partialTick: Float): Quaternionf {
        if (partialTick <= 0.0f) {
            return Quaternionf(previousOrientation)
        }
        if (partialTick >= 1.0f) {
            return getOrientation()
        }
        return QuaternionMath.slerp(previousOrientation, physicsState.orientation, partialTick)
    }

    override fun onSyncedDataUpdated(data: EntityDataAccessor<*>) {
        super.onSyncedDataUpdated(data)

        if (!level().isClientSide) {
            return
        }

        when (data) {
            DATA_LINEAR_VEL, DATA_ANGULAR_VEL, DATA_ON_GROUND -> correctClientStateIfNeeded()
        }
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
        setDeltaMovement(physicsState.linearVelocity)
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
        previousOrientation.set(physicsState.orientation)
    }

    private fun syncPhysicsToEntityData() {
        entityData.set(DATA_LINEAR_VEL, physicsState.linearVelocity.toVector3f())
        entityData.set(DATA_ANGULAR_VEL, physicsState.angularVelocity.toVector3f())
        entityData.set(DATA_ON_GROUND, physicsState.onGround)
    }

    private fun correctClientStateIfNeeded() {
        val syncedLinear = entityData.get(DATA_LINEAR_VEL).toVec3()
        val syncedAngular = entityData.get(DATA_ANGULAR_VEL).toVec3()
        val syncedOnGround = entityData.get(DATA_ON_GROUND)

        if (physicsState.linearVelocity.distanceTo(syncedLinear) > FootballPhysicsConfig.CLIENT_CORRECTION_THRESHOLD) {
            physicsState.linearVelocity = syncedLinear
            lerpMotion(syncedLinear)
        }

        if (physicsState.angularVelocity.distanceTo(syncedAngular) > FootballPhysicsConfig.CLIENT_CORRECTION_THRESHOLD) {
            physicsState.angularVelocity = syncedAngular
        }

        physicsState.onGround = syncedOnGround
    }

    override fun setPos(x: Double, y: Double, z: Double) {
        super.setPos(x, y, z)
        if (level().isClientSide) {
            previousOrientation.set(physicsState.orientation)
        }
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
