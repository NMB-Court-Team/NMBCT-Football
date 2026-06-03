package net.astrorbits.football

import net.astrorbits.football.FootballParticles.playDribble
import net.astrorbits.football.FootballParticles.playFootballPlace
import net.astrorbits.football.FootballParticles.playGkCatch
import net.astrorbits.football.FootballParticles.playGkDive
import net.astrorbits.football.FootballParticles.playGkPunch
import net.astrorbits.football.FootballParticles.playGkThrow
import net.astrorbits.football.FootballParticles.playGroundBounce
import net.astrorbits.football.FootballParticles.playHighSpeedDrag
import net.astrorbits.football.FootballParticles.playKick
import net.astrorbits.football.FootballParticles.playTrap
import net.astrorbits.football.FootballParticles.playWallBounce
import net.astrorbits.football.input.FootballInputConfig
import net.astrorbits.football.input.FootballInputConfig.SHOOT_FORCE_MAX
import net.astrorbits.football.input.GoalkeeperInputConfig
import net.astrorbits.football.physics.CollisionBounceResult
import net.astrorbits.football.physics.FootballPhysicsConfig
import net.astrorbits.football.util.Vec3Math
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.*
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 本 mod 足球相关的全部粒子效果播放入口。
 *
 * ## 如何调整
 * 1. 在下方找到对应场景的 [ParticleBurst] 或 `playXxx` 方法，修改粒子类型、数量、散布与速度。
 * 2. 触发阈值与力度缩放见 [FootballParticleConfig]。
 * 3. 将 [ParticleBurst.options] 换为任意 [net.minecraft.core.particles.ParticleOptions]（含 [BlockParticleOption]、[net.minecraft.core.particles.DustParticleOptions] 等）。
 *
 * ## 粒子一览
 * | 方法 | 触发场景 | 默认粒子 |
 * |------|----------|----------|
 * | [playKick] | 传球 / 射门 / 挑球 | 双层云环 + 暴击 + 烟尘 |
 * | [playDribble] | 带球周期性触球 | 轻微烟尘 |
 * | [playTrap] | 停球 | 云雾吸收感 |
 * | [playFootballPlace] | 放置足球物品 | 放置烟尘 |
 * | [playGroundBounce] | 球落地反弹 | 脚下方块碎屑 + 尘土 |
 * | [playWallBounce] | 球撞墙反弹 | 暴击 + 白烟 |
 * | [playHighSpeedDrag] | 足球高速运动 | 随速度方向裹挟的彩色粉末 |
 * | [playGkCatch] | 守门员接球 / 放球 | 云雾 |
 * | [playGkDive] | 守门员鱼跃 | 云 + 白烟拖尾 |
 * | [playGkPunch] | 守门员拳击 / 挡出 | 暴击 |
 * | [playGkThrow] | 守门员手抛 | 横扫 + 轻尘 |
 * | [playGoal] | 进球 | 闪光 + 庆祝 + 烟花 + 暴击 + 金色粉尘 |
 *
 * 所有播放均在服务端 [ServerLevel.sendParticles] 执行，会自动同步给附近客户端。
 */
object FootballParticles {
    fun init() {
        // static init
        // 若将来注册自定义 ParticleType，可在此初始化
    }

    /**
     * 单次粒子喷发配置。
     *
     * @param options 粒子类型（原版或 mod 注册）
     * @param count 粒子数量
     * @param spreadX/Y/Z 以中心点为原点的随机偏移盒半宽（格）
     * @param speed 粒子初速度随机幅度
     */
    data class ParticleBurst(
        val options: ParticleOptions,
        val count: Int,
        val spreadX: Double = FootballParticleConfig.SPREAD_HORIZONTAL,
        val spreadY: Double = FootballParticleConfig.SPREAD_VERTICAL,
        val spreadZ: Double = FootballParticleConfig.SPREAD_HORIZONTAL,
        val speed: Double = 0.0,
    )

    fun centerOfFootball(football: Football): Vec3 =
        football.position().add(0.0, FootballPhysicsConfig.RADIUS, 0.0)

    fun playKick(level: Level, center: Vec3, force: Double, kickDirection: Vec3 = Vec3(0.0, 0.0, 1.0)) {
        val scale = kickForceScale(force)
        val countBoost = (FootballParticleConfig.KICK_COUNT_FORCE_EXTRA * scale).toInt()
        val cloudRingCount = FootballParticleConfig.KICK_CLOUD_RING_BASE_COUNT +
            (FootballParticleConfig.KICK_CLOUD_RING_FORCE_EXTRA * scale).toInt()
        emitCloudRing(level, center, kickDirection, FootballParticleConfig.KICK_CLOUD_RING_INNER_RADIUS, cloudRingCount)
        emitCloudRing(level, center, kickDirection, FootballParticleConfig.KICK_CLOUD_RING_OUTER_RADIUS, cloudRingCount)
        emitBurst(level, center, critBurst((FootballParticleConfig.KICK_COUNT_BASE / 2) + countBoost / 2))
        emitBurst(level, center, poofBurst(2 + countBoost / 3, FootballParticleConfig.KICK_PARTICLE_SPEED))
        if (force >= SHOOT_FORCE_MAX)
            emitBurst(level, center, crimsonBurst((FootballParticleConfig.KICK_COUNT_BASE * 100) + countBoost / 2))
    }

    fun playKick(player: ServerPlayer, football: Football, force: Double) {
        val velocity = football.getPhysicsState().linearVelocity
        val kickDirection = Vec3Math.normalizeSafe(
            velocity,
            Vec3Math.normalizeSafe(player.lookAngle, Vec3(0.0, 0.0, 1.0)),
        )
        playKick(player.level(), centerOfFootball(football), force, kickDirection)
        emitBurst(
            player.level(),
            kickSweepCenter(player),
            ParticleBurst(
                options = ParticleTypes.SWEEP_ATTACK,
                count = 1,
                spreadX = 0.0,
                spreadY = 0.0,
                spreadZ = 0.0,
                speed = 0.0,
            ),
        )
    }

    fun playDribble(level: Level, center: Vec3) {
        emitBurst(level, center, poofBurst(FootballParticleConfig.DRIBBLE_COUNT, 0.02))
    }

    fun playDribble(player: ServerPlayer, football: Football) {
        playDribble(player.level(), centerOfFootball(football))
    }

    fun playTrap(level: Level, center: Vec3) {
        emitBurst(level, center, cloudBurst(FootballParticleConfig.TRAP_COUNT, 0.02))
        emitBurst(level, center, poofBurst(3, 0.01))
    }

    fun playTrap(player: ServerPlayer, football: Football) {
        playTrap(player.level(), centerOfFootball(football))
    }

    fun playFootballPlace(level: Level, pos: BlockPos) {
        val center = Vec3(pos.x + 0.5, pos.y + FootballPhysicsConfig.RADIUS, pos.z + 0.5)
        emitBurst(level, center, poofBurst(FootballParticleConfig.PLACE_COUNT, 0.03))
        emitBurst(level, center, cloudBurst(4, 0.02))
    }

    fun playCollisionBounces(level: Level, pos: BlockPos, bounce: CollisionBounceResult) {
        val center = Vec3(pos.x + 0.5, pos.y + FootballPhysicsConfig.RADIUS, pos.z + 0.5)
        if (bounce.hasGroundBounce) {
            playGroundBounce(level, center, pos, bounce.groundImpactSpeed)
        }
        if (bounce.hasWallBounce) {
            playWallBounce(level, center, bounce.wallImpactSpeed)
        }
    }

    fun playGroundBounce(level: Level, center: Vec3, blockPos: BlockPos, impactSpeed: Double) {
        if (impactSpeed < FootballParticleConfig.BOUNCE_PARTICLE_MIN_GROUND_VY) {
            return
        }
        val t = impactScale(
            impactSpeed,
            FootballParticleConfig.BOUNCE_PARTICLE_MIN_GROUND_VY,
            FootballParticleConfig.BOUNCE_PARTICLE_REFERENCE_SPEED,
        )
        val blockState = level.getBlockState(blockPos.below())
        if (!blockState.isAir) {
            emitBurst(
                level,
                center,
                ParticleBurst(
                    options = BlockParticleOption(ParticleTypes.BLOCK, blockState),
                    count = (4 + 10 * t).toInt(),
                    spreadX = 0.28,
                    spreadY = 0.08,
                    spreadZ = 0.28,
                    speed = FootballParticleConfig.BOUNCE_PARTICLE_SPEED * (0.5 + t),
                ),
            )
        }
        emitBurst(level, center, poofBurst((3 + 6 * t).toInt(), FootballParticleConfig.BOUNCE_PARTICLE_SPEED))
        emitBurst(level, center, dustPuffBurst((2 + 4 * t).toInt()))
    }

    fun playWallBounce(level: Level, center: Vec3, impactSpeed: Double) {
        if (impactSpeed < FootballParticleConfig.BOUNCE_PARTICLE_MIN_WALL_SPEED) {
            return
        }
        val t = impactScale(
            impactSpeed,
            FootballParticleConfig.BOUNCE_PARTICLE_MIN_WALL_SPEED,
            FootballParticleConfig.BOUNCE_PARTICLE_REFERENCE_SPEED,
        )
        emitBurst(level, center, critBurst((4 + 8 * t).toInt(), FootballParticleConfig.BOUNCE_PARTICLE_SPEED))
        emitBurst(level, center, whiteSmokeBurst((3 + 5 * t).toInt(), FootballParticleConfig.BOUNCE_PARTICLE_SPEED))
    }

    /**
     * 足球高速移动时，在球周围产生沿速度方向被“裹挟”的彩色粉末粒子。
     * 该效果是连续触发型：每 tick 依据当前速度决定是否播放与数量。
     */
    fun playHighSpeedDrag(level: Level, center: Vec3, linearVelocity: Vec3) {
        val server = level as? ServerLevel ?: return
        val speed = linearVelocity.length()
        if (speed < FootballParticleConfig.HIGH_SPEED_DRAG_MIN_SPEED) {
            return
        }

        val t = impactScale(
            speed,
            FootballParticleConfig.HIGH_SPEED_DRAG_MIN_SPEED,
            FootballParticleConfig.HIGH_SPEED_DRAG_REFERENCE_SPEED,
        )
        val dir = linearVelocity.scale(1.0 / speed)
        val (axisU, axisV) = orthonormalBasis(dir)
        val baseCenter = center.add(0.0, FootballParticleConfig.HIGH_SPEED_DRAG_VERTICAL_OFFSET, 0.0)
        val count = FootballParticleConfig.HIGH_SPEED_DRAG_COUNT_BASE +
            (FootballParticleConfig.HIGH_SPEED_DRAG_COUNT_EXTRA * t).toInt()
        val trailEnd = baseCenter.add(dir.scale(FootballParticleConfig.HIGH_SPEED_DRAG_TRAIL_FORWARD_DISTANCE))
        val redStart = FootballParticleConfig.HIGH_SPEED_DRAG_COLOR_RED_START.coerceIn(0f, 0.95f)
        val colorT = ((t - redStart) / (1f - redStart)).coerceIn(0f, 1f)
        val trailColor = lerpRgb(
            FootballParticleConfig.HIGH_SPEED_DRAG_TRAIL_COLOR_LOW_RGB,
            FootballParticleConfig.HIGH_SPEED_DRAG_TRAIL_COLOR_HIGH_RGB,
            colorT,
        )
        val random = server.random
        val radius = FootballParticleConfig.HIGH_SPEED_DRAG_RING_RADIUS
        repeat(count) {
            val angle = 2.0 * PI * (it.toDouble() / count.toDouble()) + (random.nextDouble() - 0.5) * 0.18
            val radial = axisU.scale(cos(angle)).add(axisV.scale(sin(angle)))
            val spawnPos = baseCenter.add(
                radial.scale(radius * (0.92 + random.nextDouble() * 0.16)),
            )
            val trail = TrailParticleOption(
                trailEnd,
                trailColor,
                FootballParticleConfig.HIGH_SPEED_DRAG_TRAIL_DURATION_TICKS,
            )
            emitBurst(
                server,
                spawnPos,
                ParticleBurst(
                    options = trail,
                    count = 1,
                    spreadX = 0.0,
                    spreadY = 0.0,
                    spreadZ = 0.0,
                    speed = 0.0,
                ),
            )
        }
    }

    fun playGkCatch(level: Level, center: Vec3, incomingSpeed: Double = 0.0) {
        val t = goalkeeperCatchScale(incomingSpeed)
        emitBurst(level, center, cloudBurst(FootballParticleConfig.GK_CATCH_COUNT, 0.02))
        emitBurst(level, center, poofBurst(3, 0.015))
        emitBurst(level, center, critBurst((4 + 8 * t).toInt(), 0.03 + 0.04 * t))
        emitGoalkeeperCatchRedRing(level, center, t)
    }

    fun playGkCatch(player: ServerPlayer, football: Football? = null, incomingSpeed: Double = 0.0) {
        val center = football?.let { centerOfFootball(it) }
            ?: player.position().add(0.0, 1.0, 0.0)
        playGkCatch(player.level(), center, incomingSpeed)
    }

    fun playGkDive(player: ServerPlayer) {
        val center = player.position().add(0.0, 0.35, 0.0)
        emitBurst(player.level(), center, cloudBurst(FootballParticleConfig.GK_DIVE_COUNT, FootballParticleConfig.GK_DIVE_PARTICLE_SPEED))
        emitBurst(player.level(), center, whiteSmokeBurst(6, FootballParticleConfig.GK_DIVE_PARTICLE_SPEED))
    }

    fun playGkPunch(level: Level, center: Vec3) {
        emitBurst(level, center, critBurst(FootballParticleConfig.GK_PUNCH_COUNT, FootballParticleConfig.KICK_PARTICLE_SPEED))
        emitBurst(level, center, sweepKickBurst(4))
    }

    fun playGkPunch(player: ServerPlayer, football: Football) {
        playGkPunch(player.level(), centerOfFootball(football))
    }

    fun playGkThrow(level: Level, center: Vec3) {
        emitBurst(level, center, sweepKickBurst(FootballParticleConfig.GK_THROW_COUNT))
        emitBurst(level, center, poofBurst(4, 0.025))
    }

    fun playGkThrow(player: ServerPlayer, football: Football) {
        playGkThrow(player.level(), centerOfFootball(football))
    }

    /**
     * 进球庆祝粒子效果：闪光 + 绿色庆祝火花 + 烟花 + 暴击 + 金色粉尘。
     * 使用 force=true 确保所有玩家无论距离远近都能看到。
     */
    fun playGoal(level: Level, center: Vec3) {
        val server = level as? ServerLevel ?: return
        // 中心爆炸
        emitForcedBurst(server, center, ParticleBurst(ParticleTypes.EXPLOSION, 3, spreadX = 0.3, spreadY = 0.3, spreadZ = 0.3, speed = 0.3))
        // 绿色庆祝粒子（村民快乐）
        emitForcedBurst(server, center, ParticleBurst(ParticleTypes.HAPPY_VILLAGER, 80, spreadX = 2.0, spreadY = 2.0, spreadZ = 2.0, speed = 0.6))
        // 烟花火花
        emitForcedBurst(server, center, ParticleBurst(ParticleTypes.FIREWORK, 60, spreadX = 1.5, spreadY = 1.5, spreadZ = 1.5, speed = 0.5))
        // 暴击粒子
        emitForcedBurst(server, center, critBurst(50, 0.35))
        // 金色粉尘环
        val goldDust = DustParticleOptions(0xFFFFD700.toInt(), 1.5f)
        emitForcedBurst(server, center, ParticleBurst(goldDust, 50, spreadX = 1.2, spreadY = 1.2, spreadZ = 1.2, speed = 0.3))
    }

    /**
     * 发送无视距离的粒子（force=true），对所有在线玩家可见。
     */
    private fun emitForcedBurst(server: ServerLevel, center: Vec3, burst: ParticleBurst) {
        if (burst.count <= 0) return
        val packet = ClientboundLevelParticlesPacket(
            burst.options,
            true,   // overrideLimiter
            true,   // alwaysShow
            center.x, center.y, center.z,
            burst.spreadX.toFloat(), burst.spreadY.toFloat(), burst.spreadZ.toFloat(),
            burst.speed.toFloat(),
            burst.count,
        )
        val players = server.server?.playerList?.players ?: return
        for (player in players) {
            player.connection.send(packet)
        }
    }

    fun playSlideTackle(player: ServerPlayer) {
        val center = player.position().add(0.0, 0.2, 0.0)
        emitBurst(player.level(), center, cloudBurst(8, 0.04))
        emitBurst(player.level(), center, dustPuffBurst(7))
    }

    fun playBoostSprintTrail(player: ServerPlayer) {
        val center = player.position().add(0.0, 0.13, 0.0)
        val purpleDust = DustParticleOptions(0xFFCB11D4.toInt(), 1.5f)
        emitBurst(
            player.level(),
            center,
            ParticleBurst(purpleDust, 3, spreadX = 0.25, spreadY = 0.05, spreadZ = 0.25, speed = 0.02),
        )
    }

    private fun emitBurst(level: Level, center: Vec3, burst: ParticleBurst) {
        val server = level as? ServerLevel ?: return
        if (burst.count <= 0) {
            return
        }
        server.sendParticles(
            burst.options,
            center.x,
            center.y,
            center.z,
            burst.count,
            burst.spreadX,
            burst.spreadY,
            burst.spreadZ,
            burst.speed,
        )
    }

    private fun emitDirectedParticle(server: ServerLevel, options: ParticleOptions, pos: Vec3, velocity: Vec3) {
        server.sendParticles(
            options,
            pos.x,
            pos.y,
            pos.z,
            0,
            velocity.x,
            velocity.y,
            velocity.z,
            1.0,
        )
    }

    private fun sweepKickBurst(count: Int): ParticleBurst =
        ParticleBurst(ParticleTypes.SWEEP_ATTACK, count, spreadX = 0.35, spreadY = 0.2, spreadZ = 0.35)

    private fun critBurst(count: Int, speed: Double = FootballParticleConfig.KICK_PARTICLE_SPEED): ParticleBurst =
        ParticleBurst(ParticleTypes.CRIT, count, speed = speed)

    private fun poofBurst(count: Int, speed: Double = 0.0): ParticleBurst =
        ParticleBurst(ParticleTypes.POOF, count, speed = speed)

    private fun cloudBurst(count: Int, speed: Double = 0.0): ParticleBurst =
        ParticleBurst(ParticleTypes.CLOUD, count, speed = speed)

    private fun whiteSmokeBurst(count: Int, speed: Double = 0.0): ParticleBurst =
        ParticleBurst(ParticleTypes.WHITE_SMOKE, count, spreadX = 0.25, spreadY = 0.15, spreadZ = 0.25, speed = speed)

    private fun crimsonBurst(count: Int, speed: Double = 0.0): ParticleBurst =
        ParticleBurst(ParticleTypes.CRIMSON_SPORE, count, spreadX = 0.5, spreadY = 0.5, spreadZ = 0.5, speed = speed)

    private fun dustPuffBurst(count: Int): ParticleBurst {
        // 棕褐色尘土（RGB 0x8C6B47）
        val dust = DustParticleOptions(0xFF8C6B47.toInt(), 0.9f)
        return ParticleBurst(
            dust,
            count,
            spreadX = 0.2,
            spreadY = 0.06,
            spreadZ = 0.2,
            speed = 0.02,
        )
    }

    private fun emitCloudRing(level: Level, center: Vec3, normal: Vec3, radius: Double, count: Int) {
        val server = level as? ServerLevel ?: return
        if (count <= 0) {
            return
        }
        val unitNormal = Vec3Math.normalizeSafe(normal, Vec3(0.0, 0.0, 1.0))
        val (axisU, axisV) = orthonormalBasis(unitNormal)
        repeat(count) { i ->
            val angle = 2.0 * PI * (i.toDouble() / count.toDouble())
            val radial = axisU.scale(cos(angle)).add(axisV.scale(sin(angle)))
            val pos = center.add(radial.scale(radius))
            val velocity = radial.scale(FootballParticleConfig.KICK_CLOUD_RING_RADIAL_SPEED)
            emitDirectedParticle(server, ParticleTypes.CLOUD, pos, velocity)
        }
    }

    private fun emitGoalkeeperCatchRedRing(level: Level, center: Vec3, speedScale: Float) {
        val server = level as? ServerLevel ?: return
        val t = speedScale.coerceIn(0f, 1f)
        val count = 10 + (12 * t).toInt()
        val radius = 0.18 + 0.28 * t
        val radialSpeed = 0.04 + 0.05 * t
        val redDust = DustParticleOptions(0xFFFF3B3B.toInt(), 0.85f + 0.35f * t)
        repeat(count) { i ->
            val angle = 2.0 * PI * (i.toDouble() / count.toDouble())
            val radial = Vec3(cos(angle), 0.0, sin(angle))
            val pos = center.add(radial.scale(radius))
            val velocity = radial.scale(radialSpeed)
            emitDirectedParticle(server, redDust, pos, velocity)
        }
    }

    private fun orthonormalBasis(direction: Vec3): Pair<Vec3, Vec3> {
        val helper = if (kotlin.math.abs(direction.y) < 0.95) Vec3(0.0, 1.0, 0.0) else Vec3(1.0, 0.0, 0.0)
        val u = direction.cross(helper).normalize()
        val v = direction.cross(u).normalize()
        return u to v
    }

    private fun kickSweepCenter(player: ServerPlayer): Vec3 {
        val horizontal = Vec3Math.horizontal(player.lookAngle)
        val forward = Vec3Math.normalizeSafe(
            horizontal,
            Vec3(
                -sin(player.yRot * (PI / 180.0)),
                0.0,
                cos(player.yRot * (PI / 180.0)),
            ),
        )
        return player.position().add(
            forward.scale(FootballParticleConfig.KICK_SWEEP_FOOT_FORWARD),
        ).add(0.0, FootballParticleConfig.KICK_SWEEP_FOOT_HEIGHT, 0.0)
    }

    private fun lerpRgb(from: Int, to: Int, t: Float): Int {
        val a = t.coerceIn(0f, 1f)
        val fr = (from shr 16) and 0xFF
        val fg = (from shr 8) and 0xFF
        val fb = from and 0xFF
        val tr = (to shr 16) and 0xFF
        val tg = (to shr 8) and 0xFF
        val tb = to and 0xFF
        val r = (fr + ((tr - fr) * a)).toInt().coerceIn(0, 255)
        val g = (fg + ((tg - fg) * a)).toInt().coerceIn(0, 255)
        val b = (fb + ((tb - fb) * a)).toInt().coerceIn(0, 255)
        return (r shl 16) or (g shl 8) or b
    }

    private fun goalkeeperCatchScale(incomingSpeed: Double): Float {
        val reference = kotlin.math.max(GoalkeeperInputConfig.GK_CATCH_MAX_SPEED, GoalkeeperInputConfig.GK_DIVE_CATCH_MAX_SPEED)
            .coerceAtLeast(0.1)
        return (incomingSpeed / reference).toFloat().coerceIn(0f, 1f)
    }

    private fun impactScale(impactSpeed: Double, minSpeed: Double, referenceSpeed: Double): Float =
        ((impactSpeed - minSpeed) / referenceSpeed).toFloat().coerceIn(0f, 1f)

    private fun kickForceScale(force: Double): Float {
        val min = FootballInputConfig.CHIP_FORCE
        val max = SHOOT_FORCE_MAX * FootballInputConfig.SHOOT_SPRINT_BONUS
        if (max <= min) {
            return 1f
        }
        return ((force - min) / (max - min)).toFloat().coerceIn(0f, 1f)
    }
}
