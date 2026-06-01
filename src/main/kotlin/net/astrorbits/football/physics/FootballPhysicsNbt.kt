package net.astrorbits.football.physics

import com.mojang.serialization.Codec
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.DoubleTag
import net.minecraft.nbt.FloatTag
import net.minecraft.nbt.ListTag
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
    }

    fun write(state: FootballPhysicsState, motion: Vec3, tag: CompoundTag) {
        writeVec3List(tag, LINEAR_VELOCITY, state.linearVelocity)
        writeVec3List(tag, ANGULAR_VELOCITY, state.angularVelocity)
        writeOrientationList(tag, state.orientation)
        tag.putBoolean(ON_GROUND, state.onGround)
        tag.putInt(WALL_BOUNCE_COOLDOWN, state.wallBounceCooldown)
        writeMotion(tag, motion)
    }

    /**
     * 从 NBT 恢复物理状态。
     *
     * @return 实体存档路径不读 [MOTION]；CompoundTag 路径若含 [MOTION] 则返回该 Vec3。
     */
    fun read(input: ValueInput, state: FootballPhysicsState): Vec3? {
        state.linearVelocity = readVec3List(input, LINEAR_VELOCITY)
        state.angularVelocity = readVec3List(input, ANGULAR_VELOCITY)
        state.onGround = input.getBooleanOr(ON_GROUND, false)
        readOrientationList(input, state.orientation)
        state.wallBounceCooldown = input.getIntOr(WALL_BOUNCE_COOLDOWN, 0)
        return null
    }

    fun read(tag: CompoundTag, state: FootballPhysicsState): Vec3? {
        state.linearVelocity = readVec3List(tag, LINEAR_VELOCITY)
        state.angularVelocity = readVec3List(tag, ANGULAR_VELOCITY)
        state.onGround = tag.getBooleanOr(ON_GROUND, false)
        readOrientationList(tag, state.orientation)
        state.wallBounceCooldown = tag.getIntOr(WALL_BOUNCE_COOLDOWN, 0)
        val motion = readMotion(tag)
        if (motion != null) {
            state.linearVelocity = motion
        }
        return motion
    }

    private fun writeVec3List(output: ValueOutput, key: String, velocity: Vec3) {
        val list = output.list(key, Codec.DOUBLE)
        list.add(velocity.x)
        list.add(velocity.y)
        list.add(velocity.z)
    }

    private fun writeVec3List(tag: CompoundTag, key: String, velocity: Vec3) {
        val list = ListTag()
        list.add(DoubleTag.valueOf(velocity.x))
        list.add(DoubleTag.valueOf(velocity.y))
        list.add(DoubleTag.valueOf(velocity.z))
        tag.put(key, list)
    }

    private fun writeOrientationList(output: ValueOutput, orientation: Quaternionf) {
        val list = output.list(ORIENTATION, Codec.FLOAT)
        list.add(orientation.x)
        list.add(orientation.y)
        list.add(orientation.z)
        list.add(orientation.w)
    }

    private fun writeOrientationList(tag: CompoundTag, orientation: Quaternionf) {
        val list = ListTag()
        list.add(FloatTag.valueOf(orientation.x))
        list.add(FloatTag.valueOf(orientation.y))
        list.add(FloatTag.valueOf(orientation.z))
        list.add(FloatTag.valueOf(orientation.w))
        tag.put(ORIENTATION, list)
    }

    private fun readVec3List(input: ValueInput, key: String): Vec3 {
        val components = readDoubleComponents(input, key, 3)
        if (components == null) {
            return Vec3.ZERO
        }
        return Vec3(components[0], components[1], components[2])
    }

    private fun readVec3List(tag: CompoundTag, key: String): Vec3 {
        val list = tag.getList(key).orElse(null) ?: return Vec3.ZERO
        if (list.size < 3) {
            return Vec3.ZERO
        }
        return Vec3(
            list.getDouble(0).orElse(0.0),
            list.getDouble(1).orElse(0.0),
            list.getDouble(2).orElse(0.0),
        )
    }

    private fun readOrientationList(input: ValueInput, orientation: Quaternionf) {
        val components = readFloatComponents(input, ORIENTATION, 4)
        if (components == null) {
            orientation.identity()
            return
        }
        orientation.set(components[0], components[1], components[2], components[3])
    }

    private fun readDoubleComponents(input: ValueInput, key: String, count: Int): DoubleArray? {
        val out = DoubleArray(count)
        var index = 0
        for (value in input.listOrEmpty(key, Codec.DOUBLE)) {
            if (index >= count) {
                break
            }
            out[index++] = value
        }
        return if (index < count) null else out
    }

    private fun readFloatComponents(input: ValueInput, key: String, count: Int): FloatArray? {
        val out = FloatArray(count)
        var index = 0
        for (value in input.listOrEmpty(key, Codec.FLOAT)) {
            if (index >= count) {
                break
            }
            out[index++] = value
        }
        return if (index < count) null else out
    }

    private fun readOrientationList(tag: CompoundTag, orientation: Quaternionf) {
        val list = tag.getList(ORIENTATION).orElse(null)
        if (list == null || list.size < 4) {
            orientation.identity()
            return
        }
        orientation.set(
            list.getFloat(0).orElse(0.0f),
            list.getFloat(1).orElse(0.0f),
            list.getFloat(2).orElse(0.0f),
            list.getFloat(3).orElse(1.0f),
        )
    }

    private fun writeMotion(tag: CompoundTag, motion: Vec3) {
        val list = ListTag()
        list.add(DoubleTag.valueOf(motion.x))
        list.add(DoubleTag.valueOf(motion.y))
        list.add(DoubleTag.valueOf(motion.z))
        tag.put(MOTION, list)
    }

    private fun readMotion(tag: CompoundTag): Vec3? {
        val list = tag.getList(MOTION).orElse(null) ?: return null
        if (list.size < 3) {
            return null
        }
        return Vec3(
            list.getDouble(0).orElse(0.0),
            list.getDouble(1).orElse(0.0),
            list.getDouble(2).orElse(0.0),
        )
    }
}
