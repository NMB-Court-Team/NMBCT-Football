package net.astrorbits.football

import net.astrorbits.football.input.GoalkeeperHoldLock
import net.astrorbits.football.input.GoalkeeperInputConfig
import net.astrorbits.football.input.FootballDribbleSessions
import net.astrorbits.football.input.FootballInputConfig
import net.astrorbits.football.item.Items
import net.astrorbits.football.match.MatchConfigHolder
import net.astrorbits.football.match.PostGoalBallResetScheduler
import net.astrorbits.football.match.MatchPhase
import net.astrorbits.football.match.MatchState
import net.astrorbits.football.match.TeamSide
import net.astrorbits.football.physics.FootballNetInteraction
import net.astrorbits.football.physics.FootballPhysicsConfig
import net.astrorbits.football.physics.FootballPhysicsState
import net.astrorbits.football.util.CobwebUtil
import net.astrorbits.football.util.FootballBlockDepenetration
import net.astrorbits.football.util.FootballPhysicsSimulator
import net.astrorbits.football.util.QuaternionMath
import net.astrorbits.football.util.GoalkeeperHoldPoseUtil
import net.astrorbits.football.util.Vec3Math
import net.minecraft.core.registries.BuiltInRegistries
import java.util.UUID
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

        val radius = FootballPhysicsConfig.RADIUS
        val prevCenter = beforeMove.add(0.0, radius, 0.0)
        val currCenter = position().add(0.0, radius, 0.0)
        val netContact = FootballNetInteraction.apply(level(), physicsState, prevCenter, currCenter)
        if (netContact != null) {
            val target = netContact.restCenter.subtract(0.0, radius, 0.0)
            setPos(target.x, target.y, target.z)
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

        resolvePlayerCollisions(beforeMove, position(), movement)

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

    private fun resolvePlayerCollisions(beforeMove: Vec3, afterMove: Vec3, intendedMotion: Vec3) {
        val serverLevel = level() as? ServerLevel ?: return
        val now = serverLevel.gameTime
        val radius = FootballPhysicsConfig.RADIUS
        val previousCenter = beforeMove.add(0.0, radius, 0.0)
        val currentCenter = afterMove.add(0.0, radius, 0.0)
        val searchBox = boundingBox.expandTowards(intendedMotion).inflate(1.4, 0.9, 1.4)
        val players = serverLevel.getEntitiesOfClass(ServerPlayer::class.java, searchBox) { player ->
            player.isAlive && !player.isSpectator && !player.noPhysics
        }

        for (player in players) {
            if (FootballDribbleSessions.shouldIgnoreCollision(player, this, now)) {
                continue
            }

            val contact = resolvePlayerContact(player, previousCenter, currentCenter, radius) ?: continue

            if (contact.penetration > 0.0) {
                val corrected = position().add(contact.normal.scale(contact.penetration))
                setPos(corrected.x, corrected.y, corrected.z)
            }

            val velocity = physicsState.linearVelocity
            val normalVelocity = velocity.dot(contact.normal)
            if (normalVelocity < 0.0) {
                val restitution = FootballInputConfig.BALL_PLAYER_RESTITUTION.coerceIn(0.0, 1.25)
                physicsState.linearVelocity = velocity.subtract(contact.normal.scale(normalVelocity * (1.0 + restitution)))
                deltaMovement = physicsState.linearVelocity
            }

            val approachSpeed = max((-normalVelocity), -intendedMotion.dot(contact.normal)).coerceAtLeast(0.0)
            if (approachSpeed < FootballInputConfig.BALL_PLAYER_RECOIL_MIN_SPEED) {
                continue
            }
            val pushDir = Vec3Math.horizontal(contact.normal).scale(-1.0)
            val direction = if (pushDir.lengthSqr() > 1.0e-8) Vec3Math.normalizeSafe(pushDir) else contact.normal.scale(-1.0)
            val pushMagnitude = (approachSpeed * FootballInputConfig.BALL_PLAYER_PUSH_SCALE)
                .coerceAtMost(FootballInputConfig.BALL_PLAYER_MAX_PUSH)
                .coerceAtLeast(0.0)
            if (pushMagnitude <= 1.0e-6) {
                continue
            }
            player.setDeltaMovement(player.deltaMovement.add(direction.scale(pushMagnitude)))
            player.hurtMarked = true
        }
    }

    private data class PlayerContact(val normal: Vec3, val penetration: Double)

    private fun resolvePlayerContact(
        player: ServerPlayer,
        previousCenter: Vec3,
        currentCenter: Vec3,
        radius: Double,
    ): PlayerContact? {
        val playerBox = player.boundingBox
        val overlapContact = overlapContactAgainstBox(player, playerBox, currentCenter, radius)
        if (overlapContact != null) {
            return overlapContact
        }

        val hitT = segmentAabbHitT(previousCenter, currentCenter, playerBox.inflate(radius)) ?: return null
        val impactPoint = previousCenter.add(currentCenter.subtract(previousCenter).scale(hitT))
        val closest = Vec3(
            impactPoint.x.coerceIn(playerBox.minX, playerBox.maxX),
            impactPoint.y.coerceIn(playerBox.minY, playerBox.maxY),
            impactPoint.z.coerceIn(playerBox.minZ, playerBox.maxZ),
        )
        val delta = impactPoint.subtract(closest)
        val normal = if (delta.lengthSqr() > 1.0e-8) {
            Vec3Math.normalizeSafe(delta)
        } else {
            val fallback = Vec3Math.horizontal(impactPoint.subtract(player.position()))
            if (fallback.lengthSqr() > 1.0e-8) Vec3Math.normalizeSafe(fallback) else Vec3(1.0, 0.0, 0.0)
        }
        return PlayerContact(normal, 0.0)
    }

    private fun overlapContactAgainstBox(
        player: ServerPlayer,
        playerBox: net.minecraft.world.phys.AABB,
        center: Vec3,
        radius: Double,
    ): PlayerContact? {
        val closest = Vec3(
            center.x.coerceIn(playerBox.minX, playerBox.maxX),
            center.y.coerceIn(playerBox.minY, playerBox.maxY),
            center.z.coerceIn(playerBox.minZ, playerBox.maxZ),
        )
        val offset = center.subtract(closest)
        val distSqr = offset.lengthSqr()
        if (distSqr > radius * radius) return null

        val normal = if (distSqr > 1.0e-8) {
            offset.scale(1.0 / sqrt(distSqr))
        } else {
            val fallback = Vec3Math.horizontal(center.subtract(player.position()))
            if (fallback.lengthSqr() > 1.0e-8) Vec3Math.normalizeSafe(fallback) else Vec3(1.0, 0.0, 0.0)
        }
        val distance = sqrt(distSqr.coerceAtLeast(0.0))
        val penetration = (radius - distance + 1.0e-3).coerceAtLeast(0.0)
        return PlayerContact(normal, penetration)
    }

    private fun segmentAabbHitT(start: Vec3, end: Vec3, box: net.minecraft.world.phys.AABB): Double? {
        val direction = end.subtract(start)
        var tMin = 0.0
        var tMax = 1.0

        fun updateAxis(startValue: Double, dirValue: Double, minBound: Double, maxBound: Double): Boolean {
            if (abs(dirValue) < 1.0e-9) {
                return startValue >= minBound && startValue <= maxBound
            }
            var t1 = (minBound - startValue) / dirValue
            var t2 = (maxBound - startValue) / dirValue
            if (t1 > t2) {
                val temp = t1
                t1 = t2
                t2 = temp
            }
            tMin = max(tMin, t1)
            tMax = min(tMax, t2)
            return tMin <= tMax
        }

        if (!updateAxis(start.x, direction.x, box.minX, box.maxX)) return null
        if (!updateAxis(start.y, direction.y, box.minY, box.maxY)) return null
        if (!updateAxis(start.z, direction.z, box.minZ, box.maxZ)) return null
        if (tMax < 0.0 || tMin > 1.0) return null
        return tMin.coerceIn(0.0, 1.0)
    }

    private fun detectGoal(prevPos: Vec3, currPos: Vec3) {
        if (MatchState.currentPhase == MatchPhase.PRE_MATCH || MatchState.currentPhase == MatchPhase.FINISHED) return

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
        sideline: net.astrorbits.football.match.SidelineConfig,
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

        val server = (level() as? net.minecraft.server.level.ServerLevel)?.server ?: return

        // 最后触球方 = 对方发球
        val lastTouchTeam = lastKicker?.let { MatchState.getPlayerTeam(it) }
        val restartTeam: TeamSide = when (lastTouchTeam) {
            TeamSide.A -> TeamSide.B
            TeamSide.B -> TeamSide.A
            null -> TeamSide.A
        }

        // 球放在线上
        val ballPos = Vec3(ix, iy, iz)
        MatchState.resetFootballAt(level() as net.minecraft.server.level.ServerLevel, ballPos)
        MatchState.kickoffTeam = restartTeam
        MatchState.kickoffTouched = false
        net.astrorbits.football.network.FootballNetworking.broadcastGoalLineOut(
            server, net.astrorbits.football.match.GoalLineOutType.THROW_IN, restartTeam, ballPos.x, ballPos.y, ballPos.z,
        )
    }

    /** 检测球是否穿越门线：进球或出底线 */
    private fun checkGoalOrOut(
        goal: net.astrorbits.football.match.GoalConfig,
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
        val inGoal = ix >= minX && ix <= maxX
                && iy >= minY && iy <= maxY
                && iz >= minZ - 1.01 && iz <= maxZ + 1.01

        val server = (level() as? net.minecraft.server.level.ServerLevel)?.server ?: return

        if (inGoal) {
            if (MatchState.postGoalResetPending) return
            MatchState.postGoalResetPending = true
            // 进球
            MatchState.onGoal(attackingTeam)
            val scorerName = lastKicker?.let { server.playerList.getPlayer(it)?.gameProfile?.name } ?: "?"
            val scorerTeam = lastKicker?.let { MatchState.getPlayerTeam(it) } ?: attackingTeam
            val ownGoal = scorerTeam != attackingTeam
            net.astrorbits.football.network.FootballNetworking.broadcastGoalScored(
                server, attackingTeam, scorerName, scorerTeam, MatchState.teamAScore, MatchState.teamBScore, ownGoal,
            )
            FootballParticles.playGoal(level(), FootballParticles.centerOfFootball(this))
            PostGoalBallResetScheduler.schedule(level() as net.minecraft.server.level.ServerLevel)
            MatchState.kickoffTeam = defendingTeam
            MatchState.kickoffTouched = false
            net.astrorbits.football.network.FootballNetworking.broadcastPostGoalKickoff(server, defendingTeam)
        } else {
            // 穿越门线平面但不在门框内 → 出底线
            val lastTouchTeam = lastKicker?.let { MatchState.getPlayerTeam(it) }
            val outType: net.astrorbits.football.match.GoalLineOutType
            val restartTeam: TeamSide

            if (lastTouchTeam == attackingTeam) {
                // 攻方最后触球 → 球门球（守方开球）
                outType = net.astrorbits.football.match.GoalLineOutType.GOAL_KICK
                restartTeam = defendingTeam
            } else {
                // 守方最后触球（或无归属） → 角球（攻方开球）
                outType = net.astrorbits.football.match.GoalLineOutType.CORNER_KICK
                restartTeam = attackingTeam
            }

            // 使用配置中的开球点
            val ballPos = if (outType == net.astrorbits.football.match.GoalLineOutType.GOAL_KICK) {
                val gk = goal.goalKick
                Vec3(gk.x, gk.y, gk.z)
            } else {
                // 角球：根据穿越点在球门哪一侧决定用左角旗还是右角旗
                val goalCenterX = (gx1 + gx2) / 2.0
                val goalCenterZ = (gz1 + gz2) / 2.0
                val onRight = if (Math.abs(facing.x) > Math.abs(facing.z))
                    iz > goalCenterZ
                else
                    ix > goalCenterX
                val corner = if (onRight) goal.cornerKickRight else goal.cornerKickLeft
                Vec3(corner.x, corner.y, corner.z)
            }

            MatchState.resetFootballAt(level() as net.minecraft.server.level.ServerLevel, ballPos)
            MatchState.kickoffTeam = restartTeam
            MatchState.kickoffTouched = false
            net.astrorbits.football.network.FootballNetworking.broadcastGoalLineOut(server, outType, restartTeam, ballPos.x, ballPos.y, ballPos.z)
        }
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

    override fun getPickResult(): ItemStack = ItemStack(Items.FOOTBALL)

    override fun interact(player: Player, hand: InteractionHand, location: Vec3): InteractionResult {
        return handlePlayerInteract(player)
    }

    override fun push(entity: Entity) {
        if (entity is ServerPlayer) {
            val now = (level() as? ServerLevel)?.gameTime ?: 0L
            if (FootballDribbleSessions.shouldIgnoreCollision(entity, this, now)) {
                return
            }
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
                // 让客户端在“实体渲染距离=100%”时可从最远约 128 格看到足球。
                .clientTrackingRange(128)
                .updateInterval(1)
                .build(ENTITY_KEY)
        )

        private fun Vector3fc.toVec3(): Vec3 = Vec3(x().toDouble(), y().toDouble(), z().toDouble())
    }
}
