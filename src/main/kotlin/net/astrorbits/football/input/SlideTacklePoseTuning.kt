package net.astrorbits.football.input

import kotlin.math.PI

object SlideTacklePoseTuning {
    // 按用户提供配置硬编码。
    val rootX: Float = degToRad(-159.99999534548672)
    val rootY: Float = degToRad(60.000001669652114)
    val rootZ: Float = degToRad(0.0)
    val headX: Float = degToRad(45.00000125223908)
    val headY: Float = degToRad(30.000000834826057)
    val headZ: Float = degToRad(0.0)
    val bodyX: Float = degToRad(0.0)
    val bodyY: Float = degToRad(0.0)
    val bodyZ: Float = degToRad(0.0)
    val leftArmX: Float = degToRad(0.0)
    val leftArmY: Float = degToRad(0.0)
    val leftArmZ: Float = degToRad(-149.99999734394112)
    val rightArmX: Float = degToRad(-30.000000834826057)
    val rightArmY: Float = degToRad(0.0)
    val rightArmZ: Float = degToRad(-45.00000125223908)
    val leftLegX: Float = degToRad(0.0)
    val leftLegY: Float = degToRad(0.0)
    val leftLegZ: Float = degToRad(0.0)
    val rightLegX: Float = degToRad(0.0)
    val rightLegY: Float = degToRad(0.0)
    val rightLegZ: Float = degToRad(0.0)

    val rootOffsetX: Float = 6.0f
    val rootOffsetY: Float = 22.0f
    val rootOffsetZ: Float = 1.0f
    val headOffsetX: Float = 0.0f
    val headOffsetY: Float = 0.0f
    val headOffsetZ: Float = 0.0f
    val bodyOffsetX: Float = 0.0f
    val bodyOffsetY: Float = 0.0f
    val bodyOffsetZ: Float = 0.0f
    val leftArmOffsetX: Float = 0.0f
    val leftArmOffsetY: Float = 0.0f
    val leftArmOffsetZ: Float = 0.0f
    val rightArmOffsetX: Float = 0.0f
    val rightArmOffsetY: Float = 0.0f
    val rightArmOffsetZ: Float = 0.0f
    val leftLegOffsetX: Float = 0.0f
    val leftLegOffsetY: Float = 0.0f
    val leftLegOffsetZ: Float = 0.0f
    val rightLegOffsetX: Float = 0.0f
    val rightLegOffsetY: Float = 0.0f
    val rightLegOffsetZ: Float = 0.0f

    private fun degToRad(degrees: Double): Float = (degrees * PI / 180.0).toFloat()
}
