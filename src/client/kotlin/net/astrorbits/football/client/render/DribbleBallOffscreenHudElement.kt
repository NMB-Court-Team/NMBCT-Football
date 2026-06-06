package net.astrorbits.football.client.render

import net.astrorbits.football.NMBCTFootball
import net.astrorbits.football.client.DribbleBallIndicatorClient
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.Camera
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import org.joml.Vector2f
import org.joml.Vector3f
import kotlin.math.*

/**
 * 足球不在视野内时绘制屏幕边缘指示箭头与足球图标。
 * 箭头位置/方向由 [computeOffscreenArrowScreenPlacement] 计算；本类负责可见性判断、位置平滑与绘制。
 */
class DribbleBallOffscreenHudElement : HudElement {
    override fun extractRenderState(extra: GuiGraphicsExtractor, delta: DeltaTracker) {
        val center = DribbleBallIndicatorClient.trackedBallCenter() ?: run {
            resetSmoothState()
            return
        }
        val client = Minecraft.getInstance()
        val player = client.player ?: return
        val camera = client.gameRenderer.mainCamera

        val width = client.window.guiScaledWidth
        val height = client.window.guiScaledHeight

        val view = toCameraSpace(camera, center)
        if (isBallVisibleOnScreen(client, view, width, height)) {
            resetSmoothState()
            return
        }

        val placement = BallOffscreenArrowCalc.calcOffscreenArrowScreenPlacement(
            eyePos = camera.position(),
            yawDeg = player.yRot,
            pitchDeg = player.xRot,
            ballCenter = center,
            screenWidth = width,
            screenHeight = height,
        ) ?: return

        val edge = smoothEdge(placement.position.x, placement.position.y, delta)

        val arrowW = ARROW_DISPLAY_SIZE
        val arrowH = ARROW_DISPLAY_SIZE
        val ballSize = 12
        val margin = EDGE_MARGIN.toFloat()
        val maxX = width - margin - arrowW
        val maxY = height - margin - arrowH
        val ax = (edge.first - arrowW / 2f).coerceIn(margin, maxX)
        val ay = (edge.second - arrowH / 2f).coerceIn(margin, maxY)

        val dirAngle = atan2(
            placement.direction.y.toDouble(),
            placement.direction.x.toDouble(),
        )
        blitRotatedArrow(extra, ax.roundToInt(), ay.roundToInt(), arrowW, arrowH, dirAngle)

        val cosA = cos(dirAngle).toFloat()
        val sinA = sin(dirAngle).toFloat()
        val arrowCenterX = ax + arrowW / 2f
        val arrowCenterY = ay + arrowH / 2f
        val alongAxis = maxOf(arrowW, arrowH) / 2f + BALL_ALONG_ARROW_GAP + ballSize / 2f
        val ballCenterX = arrowCenterX - cosA * alongAxis
        val ballCenterY = arrowCenterY - sinA * alongAxis
        val bx = (ballCenterX - ballSize / 2f).coerceIn(0f, (width - ballSize).toFloat()).roundToInt()
        val by = (ballCenterY - ballSize / 2f).coerceIn(0f, (height - ballSize).toFloat()).roundToInt()
        extra.blit(
            RenderPipelines.GUI_TEXTURED,
            FOOTBALL_ICON,
            bx,
            by,
            0f,
            0f,
            ballSize,
            ballSize,
            FOOTBALL_TEX_SIZE,
            FOOTBALL_TEX_SIZE,
            FOOTBALL_TEX_SIZE,
            FOOTBALL_TEX_SIZE,
        )
    }

    /** 世界坐标 → 相机视线空间（z &lt; 0 为相机前方）。 */
    private fun toCameraSpace(camera: Camera, worldPos: Vec3): Vector3f {
        val rel = worldPos.subtract(camera.position())
        val q = Quaternionf(camera.rotation())
        q.conjugate()
        return Vector3f(rel.x.toFloat(), rel.y.toFloat(), rel.z.toFloat()).apply { rotate(q) }
    }

    private fun isBallVisibleOnScreen(
        client: Minecraft,
        view: Vector3f,
        width: Int,
        height: Int,
    ): Boolean {
        if (view.z >= 0f) {
            return false
        }
        val fov = client.options.fov().get().toFloat()
        val aspect = client.window.width.toFloat() / client.window.height.coerceAtLeast(1).toFloat()
        val h = tan(Math.toRadians(fov.toDouble() * 0.5)).toFloat()
        val w = h * aspect
        val ndcX = view.x / (-view.z * w)
        val ndcY = view.y / (-view.z * h)
        val margin = VISIBLE_NDC_MARGIN
        return ndcX in -1f + margin..1f - margin && ndcY in -1f + margin..1f - margin
    }

    private fun smoothEdge(targetX: Float, targetY: Float, delta: DeltaTracker): Pair<Float, Float> {
        if (!smoothEdgeX.isFinite()) {
            smoothEdgeX = targetX
            smoothEdgeY = targetY
            return targetX to targetY
        }
        val tickDelta = delta.getGameTimeDeltaPartialTick(false).coerceIn(0f, 1f)
        val t = 1f - exp(-EDGE_SMOOTH_SPEED * tickDelta)
        smoothEdgeX += (targetX - smoothEdgeX) * t
        smoothEdgeY += (targetY - smoothEdgeY) * t
        return smoothEdgeX to smoothEdgeY
    }

    private fun resetSmoothState() {
        smoothEdgeX = Float.NaN
        smoothEdgeY = Float.NaN
    }

    private fun blitRotatedArrow(
        extra: GuiGraphicsExtractor,
        x: Int,
        y: Int,
        displayW: Int,
        displayH: Int,
        pointAngleRad: Double,
    ) {
        val centerX = x + displayW / 2f
        val centerY = y + displayH / 2f
        val rotationRad = (pointAngleRad - Math.PI).toFloat()
        val pose = extra.pose()
        pose.pushMatrix()
        pose.translate(centerX, centerY)
        pose.rotate(rotationRad)
        pose.translate(-displayW / 2f, -displayH / 2f)
        extra.blit(
            RenderPipelines.GUI_TEXTURED,
            ARROW_TEXTURE,
            0,
            0,
            0f,
            0f,
            displayW,
            displayH,
            ARROW_TEX_W,
            ARROW_TEX_H,
            ARROW_TEX_W,
            ARROW_TEX_H,
        )
        pose.popMatrix()
    }

    companion object {
        private var smoothEdgeX = Float.NaN
        private var smoothEdgeY = Float.NaN

        private const val EDGE_SMOOTH_SPEED = 14f
        private const val EDGE_MARGIN = 24
        private const val VISIBLE_NDC_MARGIN = 0.05f

        private const val ARROW_TEX_W = 11
        private const val ARROW_TEX_H = 11
        private const val ARROW_DISPLAY_SIZE = 16
        private const val BALL_ALONG_ARROW_GAP = 4f
        private const val FOOTBALL_TEX_SIZE = 32

        val ARROW_TEXTURE: Identifier =
            Identifier.fromNamespaceAndPath(NMBCTFootball.MOD_ID, "textures/gui/sprites/hud/dribble_offscreen_arrow.png")

        val FOOTBALL_ICON: Identifier =
            Identifier.fromNamespaceAndPath(NMBCTFootball.MOD_ID, "textures/item/football_32x.png")
    }
}

object BallOffscreenArrowCalc {
    /**
     * 视野外足球指示箭头在屏幕上的位置与方向（GUI 缩放坐标）。
     *
     * @property position 箭头锚点目标坐标（与 HUD 元素内平滑后的 edge 一致，一般为箭头中心目标点）。
     * @property direction 箭头朝向，用于旋转绘制（不必单位化）。
     */
    data class OffscreenArrowScreenPlacement(
        val position: Vector2f,
        val direction: Vector2f,
    )

    /**
     * 根据玩家视点与足球世界坐标，计算指示箭头在屏幕上的位置与方向。
     *
     * @return 返回 `null` 表示本帧不绘制箭头。
     */
    fun calcOffscreenArrowScreenPlacement(
        eyePos: Vec3,
        yawDeg: Float,
        pitchDeg: Float,
        ballCenter: Vec3,
        screenWidth: Int,
        screenHeight: Int,
    ): OffscreenArrowScreenPlacement? {
        /*
        玩家眼睛位置为O，假设O位于原点；玩家视线为OD，假设OD之间的距离为1；目标（足球中心）的位置为T。
        1. 构造辅助线与辅助平面
        构造水平面：过O点作与xOz平行的面ph，该面为水平面；
        构造视平面：在ph上作射线OD的垂线lv, 作lv与OD构成的平面pv，该平面为视平面；
        构造投影：作OD在ph上的投影OD'，作射线OT，作OT在ph上的投影OT'；
        构造方向判断辅助线：过D'点在ph上作OD'的垂线ld'，以D'点为球心作任意半径的球，球与ld'有两个交点A, B，作直线OA, OB，过A, B点在ph上作ld'的垂线，分别交OB, OA于点D, C，此时ABCD是一个正方形；
        确定方向判断辅助线的方位：右手大拇指指向y轴正方向，其余四指绕向为水平面ph上旋转的正方向，假设OA可以由OD'在水平面上旋转+45°得到（直观左侧），那么AB代表前方，BC代表右侧，CD代表后方，DA代表左侧（直观理解上，正方形左上角为A，右上角为B，右下角为C，左下角为D）
        其余辅助点：BC中点为M_r, DA中点为M_l，OT'一定交正方形ABCD于唯一一点T_0，T_0用于映射箭头位置
        定义屏幕的四条边：屏幕左上角为P，右上角为Q，右下角为R，左下角为S，右边缘的中点为M_sr，左边缘的中点为M_sl，假设箭头位于点T_1
        定义映射：假设T_0位于AB上，将AB映射到PQ上则意味着，T_1在PQ上，且\frac{A T_0}{T_0 B} = \frac{P T_1}{T_1 Q}；将AB映射到QP上则意味着，\frac{A T_0}{T_0 B} = \frac{Q T_1}{T_1 P}
        2. 确定箭头的位置
        i. 如果视平面与水平面不垂直
        首先确定目标在视平面的上方还是下方：过点T作ph的垂线，垂线交pv于点T_v。如果T.y < T_v.y，则说明目标在视平面下方；如果T.y > T_v.y，则说明目标在视平面上方；如果T.y == T_v.y，则说明目标刚好在视平面上，此时将目标视为在视平面下方
        如果T_0位于AB上，说明目标位于前方：如果目标在视平面下方，则将AB映射到SR上；如果目标在视平面上方，则将AB映射到PQ上
        如果T_0位于CD上，说明目标位于后方：（与前方相同）如果目标在视平面下方，则将DC映射到SR上；如果目标在视平面上方，则将DC映射到PQ上
        如果T_0位于BC上，说明目标位于右侧：如果目标在视平面下方，则将B M_r和C M_r都映射到R M_sr上；如果目标在视平面上方，则将B M_r和C M_r都映射到Q M_sr上
        如果T_0位于DA上，说明目标位于左侧：（与右侧对称）如果目标在视平面下方，则将A M_l和D M_l都映射到S M_sl上；如果目标在视平面上方，则将A M_l和D M_l都映射到P M_sl上
        ii. 如果视平面与水平面垂直
        实际上玩家此时还有yaw，yaw可以确定OD'的方向。过T'作lv的垂线，交lv于点T_lv。
        若点T_lv与点T'重合，若T_0在BC上，则T_1与M_sr重合，若T_0在AD上，则T_1与M_sl重合
        如果OD方向与y轴正方向同向：如果向量T_lv T'与向量OD'同向，则沿用2.i.的目标位于视平面下方时的情况；若向量T_lv T'与向量OD'异向，则沿用2.i.的目标位于视平面上方时的情况
        如果OD方向与y轴负方向同向：（与上一种情况相反）如果向量T_lv T'与向量OD'同向，则沿用2.i.的目标位于视平面上方时的情况；若向量T_lv T'与向量OD'异向，则沿用2.i.的目标位于视平面下方时的情况
        3. 确定箭头的方向
        假设屏幕中心点为O_s，向量O_s T_1为箭头的方向

        上述几何表述很复杂，实际上计算可以大幅度简化

        另外，为了避免玩家pitch接近垂直时导致的箭头位置跳变，作出如下优化：
        计算玩家视线射线前，玩家的pitch值需要经过一段处理。假设玩家实体位置到目标位置的距离为target_dist，判定足球在近距离范围内的阈值为CLOSE_DIST_THRESHOLD = 6.0，起始最大pitch为START_MAX_PITCH_DEG = 60f，那么
        fun lerp(x0: Float, x1: Float, t: Float): Float
        val pitchMax = lerp(START_MAX_PITCH_DEG, 90f, (target_dist / CLOSE_DIST_THRESHOLD).toFloat())
        val processedPitch = player.pitch.coerceIn(-pitchMax, pitchMax)
         */
        val toBall = ballCenter.subtract(eyePos)

        if (toBall.lengthSqr() < EPS * EPS) {
            return null
        }

        // pitch处理
        val targetDist = toBall.length()

        val pitchMax = lerp(
            START_MAX_PITCH_DEG,
            90f,
            (targetDist / CLOSE_DIST_THRESHOLD)
                .coerceIn(0.0, 1.0)
                .toFloat()
        )

        val processedPitch = pitchDeg.coerceIn(-pitchMax, pitchMax)

        val yawRad = Math.toRadians(yawDeg.toDouble())
        val pitchRad = Math.toRadians(processedPitch.toDouble())

        // 玩家视线
        val viewDir = Vec3(
            -sin(yawRad) * cos(pitchRad),
            -sin(pitchRad),
            cos(yawRad) * cos(pitchRad)
        ).normalize()

        // 水平前方向 OD'
        val forwardH = Vec3(-sin(yawRad), 0.0, cos(yawRad)).normalize()

        // 右方向
        val rightH = Vec3(-forwardH.z, 0.0, forwardH.x).normalize()

        // 视平面上方向
        val upView = rightH.cross(viewDir).normalize()

        // 水平投影坐标;  hz > 0: 前;  hx > 0: 右
        val hx = toBall.dot(rightH).toFloat()
        val hz = toBall.dot(forwardH).toFloat()

        if (abs(hx) < EPS && abs(hz) < EPS) {  // should not happen
            return null
        }

        // 上方/下方
        val isUponViewPlane = toBall.dot(upView) > 0.0

        val width = screenWidth.toFloat()
        val height = screenHeight.toFloat()
        val top = 0f; val bottom = height; val left = 0f; val right = width

        // 用于计算箭头方向
        val centerX = width * 0.5f
        val centerY = height * 0.5f

        val absHx = abs(hx)
        val absHz = abs(hz)

        // 箭头最终位置
        val posX: Float
        val posY: Float

        // z轴: 前后； x轴: 左右
        if (absHz >= absHx) {  // AB 或 CD
            val t = (hx + absHz) / (2f * absHz)  // A -> B: 0 -> 1; D -> C: 0 -> 1
            posX = t * width
            posY = if (isUponViewPlane) { // AB/DC -> PQ
                top
            } else {  // AB/DC -> SR
                bottom
            }
        } else {  // BC 或 DA
            val t = (hz + absHx) / (2f * absHx)  // B -> C: 0 -> 1; A -> D: 0 -> 1
            val reverseT = 1f - t

            posX = if (hx >= 0f) {  // BC
                right
            } else {  // DA
                left
            }

            posY = if (isUponViewPlane) {
                if (t < 0.5f) {  // BMr -> QMsr
                    t * height
                } else {  // CMr -> QMsr
                    reverseT * height
                }
            } else {
                if (t < 0.5f) {  // BMr -> RMsr
                    reverseT * height
                } else {  // CMr -> RMsr
                    t * height
                }
            }
        }

        return OffscreenArrowScreenPlacement(
            position = Vector2f(posX, posY),
            direction = Vector2f(
                posX - centerX,
                posY - centerY
            )
        )
    }

    private fun lerp(x0: Float, x1: Float, t: Float): Float = x0 + (x1 - x0) * t

    private const val EPS = 1e-4f
    private const val CLOSE_DIST_THRESHOLD = 6.0
    private const val START_MAX_PITCH_DEG = 60f
}