package net.astrorbits.football.client.config.yacl.controller

import net.minecraft.world.phys.Vec3

data class PositionAndFacing(
    val pos: Vec3,
    val yaw: Float,
    val pitch: Float,
)
