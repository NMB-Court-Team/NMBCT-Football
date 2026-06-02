package net.astrorbits.football

import net.astrorbits.football.block.Blocks
import net.astrorbits.football.item.Items
import net.astrorbits.football.network.FootballNetworking
import net.astrorbits.football.physics.FootballPhysicsConfig
import net.astrorbits.football.physics.GoalNetConfig
import net.astrorbits.football.physics.GoalNetMesh
import net.astrorbits.football.physics.GoalNetMeshNbt
import net.astrorbits.football.util.GoalNetAnchorLinks
import net.astrorbits.football.util.GoalNetAnchorsNbt
import net.astrorbits.football.util.GoalNetDrops
import net.astrorbits.football.util.GoalNetGeometry
import net.minecraft.core.BlockPos
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/**
 * 实体化球网：由四个锚点构成的矩形质点-弹簧网，服务端权威模拟，客户端仅做渲染。
 */
class GoalNetEntity(type: EntityType<*>, level: Level) : Entity(type, level) {
    private var anchors: List<BlockPos> = emptyList()
    private var rectangle: GoalNetGeometry.NetRectangle? = null
    private var mesh: GoalNetMesh? = null
    private var slack: Double = GoalNetConfig.DEFAULT_SLACK

    /** 剩余“活跃”tick：>0 时每 tick 模拟并同步。 */
    private var activeTicks: Int = 0
    private var cachedBox: AABB? = null
    private var syncBuffer: FloatArray = FloatArray(0)

    // 客户端渲染数据（由 S2C 包填充）。
    var clientCols: Int = 0
        private set
    var clientRows: Int = 0
        private set
    var clientRelative: FloatArray? = null
        private set

    init {
        isNoGravity = true
        blocksBuilding = false
    }

    override fun defineSynchedData(builder: SynchedEntityData.Builder) {
        // 形变通过自定义包同步，无需 synched data。
    }

    /** 由连接器在创建时调用，传入四个锚点方块与初始松弛度。返回是否构造成功。 */
    fun setup(level: Level, anchorBlocks: List<BlockPos>, slack: Double): Boolean {
        val anchorPositions = GoalNetGeometry.resolveAnchorPositions(level, anchorBlocks) ?: return false
        val result = GoalNetGeometry.validate(anchorPositions)
        if (result !is GoalNetGeometry.Result.Success) return false
        this.anchors = anchorBlocks.toList()
        this.slack = slack.coerceIn(GoalNetConfig.MIN_SLACK, GoalNetConfig.MAX_SLACK)
        applyRectangle(result.rectangle)
        GoalNetAnchorLinks.register(this, this.anchors)
        return true
    }

    private fun applyRectangle(rect: GoalNetGeometry.NetRectangle) {
        rectangle = rect
        val newMesh = GoalNetMesh(rect, slack)
        mesh = newMesh
        syncBuffer = FloatArray(newMesh.nodeCount * 3)
        setPos(rect.origin.x, rect.origin.y, rect.origin.z)
        refreshServerBox(newMesh, rect)
        activeTicks = GoalNetConfig.ACTIVE_TICKS_AFTER_DISTURB
    }

    private fun computeBox(rect: GoalNetGeometry.NetRectangle): AABB {
        val corners = listOf(
            rect.origin,
            rect.origin.add(rect.uAxis.scale(rect.uLength)),
            rect.origin.add(rect.vAxis.scale(rect.vLength)),
            rect.origin.add(rect.uAxis.scale(rect.uLength)).add(rect.vAxis.scale(rect.vLength)),
        )
        var minX = corners[0].x; var minY = corners[0].y; var minZ = corners[0].z
        var maxX = minX; var maxY = minY; var maxZ = minZ
        for (c in corners) {
            minX = minOf(minX, c.x); minY = minOf(minY, c.y); minZ = minOf(minZ, c.z)
            maxX = maxOf(maxX, c.x); maxY = maxOf(maxY, c.y); maxZ = maxOf(maxZ, c.z)
        }
        // 预留下垂与厚度空间。
        val sag = (rect.uLength + rect.vLength) * 0.5 * (GoalNetConfig.MAX_SLACK + 0.3) + 1.0
        return AABB(minX - 1.0, minY - sag, minZ - 1.0, maxX + 1.0, maxY + 1.0, maxZ + 1.0)
    }

    /**
     * 用实时网格节点刷新服务端实体 AABB，避免超大网或形变后仅部分区域生效。
     */
    private fun refreshServerBox(mesh: GoalNetMesh, fallbackRect: GoalNetGeometry.NetRectangle? = rectangle) {
        val margin = FootballPhysicsConfig.RADIUS + GoalNetConfig.CONTACT_MARGIN + GoalNetConfig.BALL_PUSH_RADIUS + 0.5
        var minX = Double.MAX_VALUE
        var minY = Double.MAX_VALUE
        var minZ = Double.MAX_VALUE
        var maxX = -Double.MAX_VALUE
        var maxY = -Double.MAX_VALUE
        var maxZ = -Double.MAX_VALUE
        for (k in 0 until mesh.nodeCount) {
            val p = mesh.nodeWorld(k)
            minX = minOf(minX, p.x); minY = minOf(minY, p.y); minZ = minOf(minZ, p.z)
            maxX = maxOf(maxX, p.x); maxY = maxOf(maxY, p.y); maxZ = maxOf(maxZ, p.z)
        }
        val box = if (minX.isFinite() && minY.isFinite() && minZ.isFinite() &&
            maxX.isFinite() && maxY.isFinite() && maxZ.isFinite()
        ) {
            AABB(minX - margin, minY - margin, minZ - margin, maxX + margin, maxY + margin, maxZ + margin)
        } else {
            val rect = fallbackRect ?: return
            computeBox(rect)
        }
        cachedBox = box
        boundingBox = box
    }

    override fun makeBoundingBox(pos: Vec3): AABB {
        return cachedBox ?: AABB(pos.x - 0.5, pos.y - 0.5, pos.z - 0.5, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5)
    }

    fun getMesh(): GoalNetMesh? = mesh

    fun getRectangle(): GoalNetGeometry.NetRectangle? = rectangle

    /** 标记网被扰动，需要继续模拟与同步。 */
    fun markDisturbed() {
        activeTicks = GoalNetConfig.ACTIVE_TICKS_AFTER_DISTURB
    }

    fun increaseSlack(): Double = changeSlack(GoalNetConfig.SLACK_STEP)
    fun decreaseSlack(): Double = changeSlack(-GoalNetConfig.SLACK_STEP)

    /**
     * 重置为创建时的初始状态：默认松弛度 + 初始网格形态。
     */
    fun resetToInitialState() {
        val rect = rectangle ?: return
        slack = GoalNetConfig.DEFAULT_SLACK
        applyRectangle(rect)
    }

    private fun changeSlack(delta: Double): Double {
        val m = mesh ?: return slack
        slack = (slack + delta).coerceIn(GoalNetConfig.MIN_SLACK, GoalNetConfig.MAX_SLACK)
        m.setSlack(slack)
        markDisturbed()
        return slack
    }

    override fun tick() {
        if (level().isClientSide) {
            return
        }
        if (anchors.isNotEmpty() && tickCount % ANCHOR_CHECK_INTERVAL == 0) {
            val level = level()
            for (pos in anchors) {
                if (!level.getBlockState(pos).`is`(Blocks.GOAL_NET_ANCHOR)) {
                    discardFromAnchorBreak(GoalNetAnchorLinks.takePendingBreaker(level, pos))
                    return
                }
            }
        }
        val m = mesh
        if (m == null) {
            // 数据缺失（异常情况），移除以免占用。
            if (rectangle == null && tickCount > 5) discard()
            return
        }

        val idle = activeTicks <= 0
        if (!idle) {
            val motion = m.step()
            m.resolveBlockCollisions(level())
            refreshServerBox(m)
            if (motion < GoalNetConfig.SETTLE_SPEED_SQR) {
                activeTicks--
            }
            broadcastState()
        } else {
            // 静止时低频同步，确保新进入跟踪范围的玩家也能收到形态。
            if (tickCount % IDLE_SYNC_INTERVAL == 0) {
                refreshServerBox(m)
                broadcastState()
            }
        }
    }

    private fun broadcastState() {
        val m = mesh ?: return
        if (syncBuffer.size != m.nodeCount * 3) {
            syncBuffer = FloatArray(m.nodeCount * 3)
        }
        m.writeRelative(position(), syncBuffer)
        FootballNetworking.broadcastGoalNetState(this, m.cols, m.rows, syncBuffer)
    }

    /** 客户端：写入同步来的节点数据并更新包围盒以保证渲染。 */
    fun applyClientState(cols: Int, rows: Int, relative: FloatArray) {
        clientCols = cols
        clientRows = rows
        clientRelative = relative
        updateClientBox(relative)
    }

    private fun updateClientBox(relative: FloatArray) {
        if (relative.isEmpty()) return
        val origin = position()
        var minX = Double.MAX_VALUE; var minY = Double.MAX_VALUE; var minZ = Double.MAX_VALUE
        var maxX = -Double.MAX_VALUE; var maxY = -Double.MAX_VALUE; var maxZ = -Double.MAX_VALUE
        var k = 0
        while (k + 2 < relative.size) {
            val x = origin.x + relative[k]; val y = origin.y + relative[k + 1]; val z = origin.z + relative[k + 2]
            minX = minOf(minX, x); minY = minOf(minY, y); minZ = minOf(minZ, z)
            maxX = maxOf(maxX, x); maxY = maxOf(maxY, y); maxZ = maxOf(maxZ, z)
            k += 3
        }
        cachedBox = AABB(minX - 0.5, minY - 0.5, minZ - 0.5, maxX + 0.5, maxY + 0.5, maxZ + 0.5)
        boundingBox = cachedBox!!
    }

    override fun isPickable(): Boolean {
        val nearestPlayer = level().getNearestPlayer(this, 16.0) ?: return false
        return nearestPlayer.mainHandItem.`is`(Items.GOAL_NET_CONNECTOR) ||
            nearestPlayer.offhandItem.`is`(Items.GOAL_NET_CONNECTOR)
    }

    fun discardFromAnchorBreak(breaker: Player?) {
        if (!level().isClientSide && level() is ServerLevel) {
            GoalNetDrops.dropAnchorBreakLoot(level() as ServerLevel, this, breaker)
        }
        discard()
    }

    fun discardFromConnectorDestroy(player: ServerPlayer) {
        if (!level().isClientSide) {
            GoalNetDrops.returnConnectorDestroyString(player)
        }
        discard()
    }

    override fun remove(reason: RemovalReason) {
        if (!level().isClientSide) {
            GoalNetAnchorLinks.unregister(this)
        }
        super.remove(reason)
    }

    override fun hurtServer(
        level: ServerLevel,
        source: DamageSource,
        damage: Float,
    ): Boolean = false

    override fun readAdditionalSaveData(input: ValueInput) {
        slack = input.getDoubleOr("slack", GoalNetConfig.DEFAULT_SLACK)
        val anchorBlocks = GoalNetAnchorsNbt.read(input) ?: return
        setup(level(), anchorBlocks, slack)
        val m = mesh ?: return
        val restoredTicks = GoalNetMeshNbt.read(input, m, position())
        if (restoredTicks != null) {
            activeTicks = restoredTicks.coerceAtLeast(0)
            refreshServerBox(m)
            broadcastState()
        }
    }

    override fun addAdditionalSaveData(output: ValueOutput) {
        GoalNetAnchorsNbt.write(anchors, output)
        output.putDouble("slack", slack)
        val m = mesh ?: return
        GoalNetMeshNbt.write(m, position(), activeTicks, output)
    }

    companion object {
        fun init() {
            // static init
        }

        private const val IDLE_SYNC_INTERVAL = 20
        /** 锚点方块完整性检查间隔（20 tick = 1 秒）。 */
        private const val ANCHOR_CHECK_INTERVAL = 20

        private val ENTITY_ID = NMBCTFootball.id("goal_net")
        private val ENTITY_KEY: ResourceKey<EntityType<*>> = ResourceKey.create(Registries.ENTITY_TYPE, ENTITY_ID)

        val ENTITY_TYPE: EntityType<GoalNetEntity> = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            ENTITY_KEY,
            EntityType.Builder.of(::GoalNetEntity, MobCategory.MISC)
                .sized(0.5f, 0.5f)
                .clientTrackingRange(96)
                .updateInterval(20)
                .build(ENTITY_KEY)
        )
    }
}
