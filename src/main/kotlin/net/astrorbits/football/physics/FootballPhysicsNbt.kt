package net.astrorbits.football.physics

import com.mojang.serialization.Codec
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf

/**
 * 足球物理状态与原版 [MOTION] 的 NBT 序列化，供实体存档与网络数据包共用。
 */
object FootballPhysicsNbt {
    /** 原版实体 NBT 中的速度列表键。 */
    const val MOTION = "Motion"

    const val LINEAR_VELOCITY = "linear_velocity"
    const val ANGULAR_VELOCITY = "angular_velocity"
    const val ORIENTATION = "orientation"

    private const val ON_GROUND = "on_ground"
    private const val WALL_BOUNCE_COOLDOWN = "wall_bounce_cooldown"

    fun write(state: FootballPhysicsState, motion: Vec3, output: ValueOutput) {
        writeVec3List(output, LINEAR_VELOCITY, state.linearVelocity)
        writeVec3List(output, ANGULAR_VELOCITY, state.angularVelocity)
        writeOrientationList(output, state.orientation)
        output.putBoolean(ON_GROUND, state.onGround)
        output.putInt(WALL_BOUNCE_COOLDOWN, state.wallBounceCooldown)
        writeMotion(output, motion)
    }

    /**
     * 从 NBT 恢复物理状态。
     *
     * @return 实体存档路径不读 [MOTION]；CompoundTag 路径若含 [MOTION] 则返回该 Vec3。
     */
    fun read(input: ValueInput, state: FootballPhysicsState): Vec3? {
        state.linearVelocity = readVec3List(input, LINEAR_VELOCITY) ?: Vec3.ZERO
        state.angularVelocity = readVec3List(input, ANGULAR_VELOCITY) ?: Vec3.ZERO
        state.onGround = input.getBooleanOr(ON_GROUND, false)
        readOrientationList(input, state.orientation)
        state.wallBounceCooldown = input.getIntOr(WALL_BOUNCE_COOLDOWN, 0)
        val motion = readMotion(input)
        return motion
    }

    private fun writeVec3List(output: ValueOutput, key: String, velocity: Vec3) {
        val list = output.list(key, Codec.DOUBLE)
        list.add(velocity.x)
        list.add(velocity.y)
        list.add(velocity.z)
    }

    private fun writeOrientationList(output: ValueOutput, orientation: Quaternionf) {
        val list = output.list(ORIENTATION, Codec.FLOAT)
        list.add(orientation.x)
        list.add(orientation.y)
        list.add(orientation.z)
        list.add(orientation.w)
    }

    private fun readVec3List(input: ValueInput, key: String): Vec3? {
        val list = input.list(key, Codec.DOUBLE).orElse(null)?.stream()?.toList()
        if (list == null || list.size < 3) {
            return null
        }
        return Vec3(list[0], list[1], list[2])
    }

    private fun readOrientationList(input: ValueInput, orientation: Quaternionf) {
        val list = input.list(ORIENTATION, Codec.FLOAT).orElse(null)?.stream()?.toList()
        if (list == null || list.size < 4) {
            orientation.identity()
            return
        }
        orientation.set(list[0], list[1], list[2], list[3])
    }

    private fun writeMotion(output: ValueOutput, motion: Vec3) {
        writeVec3List(output, MOTION, motion)
    }

    private fun readMotion(input: ValueInput): Vec3? {
        return readVec3List(input, MOTION)
    }
}
