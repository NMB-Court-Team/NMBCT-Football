package net.astrorbits.football.client.render

import net.astrorbits.football.client.compat.IrisIntegration
import net.astrorbits.football.config.client.GoalNetRenderMode

fun GoalNetRenderMode.resolveRenderMode(): GoalNetRenderMode = when (this) {
    GoalNetRenderMode.AUTO ->
        if (IrisIntegration.isShaderPackInUse()) GoalNetRenderMode.SHADER_COMPAT else GoalNetRenderMode.VANILLA_COMPAT
    else -> this
}
