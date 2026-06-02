package net.astrorbits.football

import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf

/** [Football] 在 [Football.isImmovable] 为 true 时锚定的位置与物理状态。 */
internal data class FootballImmovableSnapshot(
    val x: Double,
    val y: Double,
    val z: Double,
    val linearVelocity: Vec3,
    val angularVelocity: Vec3,
    val orientation: Quaternionf,
    val onGround: Boolean,
    val wallBounceCooldown: Int,
)
