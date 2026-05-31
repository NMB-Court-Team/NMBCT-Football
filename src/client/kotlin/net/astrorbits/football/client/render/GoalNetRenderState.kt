package net.astrorbits.football.client.render

import net.minecraft.client.renderer.entity.state.EntityRenderState

class GoalNetRenderState : EntityRenderState() {
    var cols: Int = 0
    var rows: Int = 0
    /** 相对实体原点的节点偏移，长度 = cols*rows*3。 */
    var relative: FloatArray? = null
}
