package net.astrorbits.football.client.util

import com.mojang.blaze3d.vertex.PoseStack

inline fun PoseStack.use(block: PoseStack.() -> Unit) {
    pushPose()
    try {
        block()
    } finally {
        popPose()
    }
}
