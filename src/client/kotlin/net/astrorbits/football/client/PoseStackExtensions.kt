package net.astrorbits.football.client

import com.mojang.blaze3d.vertex.PoseStack

inline fun PoseStack.use(block: PoseStack.() -> Unit) {
    pushPose()
    try {
        block()
    } finally {
        popPose()
    }
}
