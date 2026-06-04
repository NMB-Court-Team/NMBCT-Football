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
import org.joml.Vector3f
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * 带球时足球不在视野内：水平罗盘分箱 + 视平面上下选边 + 沿边比例映射到屏幕；
 * 仰视/俯视退化时用相机空间投影兜底，并对边缘点做相对屏幕中心的上下镜像。箭头方向为 O_s → T_1。
 */
class DribbleBallOffscreenHudElement : HudElement {
    override fun extractRenderState(extra: GuiGraphicsExtractor, delta: DeltaTracker) {
        val center = DribbleBallIndicatorClient.trackedBallCenter() ?: run {
            resetSmoothState()
            lastPathFallback = false
            return
        }
        val client = Minecraft.getInstance()
        if (client.player == null) {
            return
        }
        val camera = client.gameRenderer.mainCamera

        val width = client.window.guiScaledWidth
        val height = client.window.guiScaledHeight
        val cx = width / 2f
        val cy = height / 2f

        val view = toCameraSpace(camera, center)
        if (isBallVisibleOnScreen(client, view, width, height)) {
            resetSmoothState()
            lastPathFallback = false
            return
        }

        val placement = computeArrowPlacement(camera, center, view, cx, cy, width, height) ?: return
        if (placement.usedFallback != lastPathFallback) {
            resetSmoothState()
        }
        lastPathFallback = placement.usedFallback

        val edge = smoothEdge(placement.x, placement.y, delta)

        val arrowW = ARROW_DISPLAY_SIZE
        val arrowH = ARROW_DISPLAY_SIZE
        val ballSize = 12
        val margin = EDGE_MARGIN.toFloat()
        val maxX = width - margin - arrowW
        val maxY = height - margin - arrowH
        val ax = (edge.first - arrowW / 2f).coerceIn(margin, maxX)
        val ay = (edge.second - arrowH / 2f).coerceIn(margin, maxY)

        val dirAngle = atan2((edge.second - cy).toDouble(), (edge.first - cx).toDouble())
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

    private data class Placement(val x: Float, val y: Float, val usedFallback: Boolean)

    private data class HorizontalBasis(
        val forwardX: Double,
        val forwardZ: Double,
        val rightX: Double,
        val rightZ: Double,
    )

    private enum class Sector {
        FRONT,
        BACK,
        RIGHT,
        LEFT,
    }

    private fun computeArrowPlacement(
        camera: Camera,
        target: Vec3,
        view: Vector3f,
        cx: Float,
        cy: Float,
        width: Int,
        height: Int,
    ): Placement? {
        val basis = horizontalBasis(camera)
        val pitchDeg = Math.toDegrees(cameraPitchRad(camera).toDouble())
        val useFallback = basis == null || abs(pitchDeg) >= PITCH_DEGENERATE_THRESHOLD_DEG

        if (useFallback) {
            val fallback = fallbackPlacement(camera, view, cx, cy, width, height) ?: return null
            return Placement(fallback.first, fallback.second, true)
        }
        val basisChecked = checkNotNull(basis)

        val eye = camera.position()
        val toX = target.x - eye.x
        val toZ = target.z - eye.z
        val lenH = sqrt(toX * toX + toZ * toZ)
        if (lenH < 1e-8) {
            return fallbackPlacement(camera, view, cx, cy, width, height)?.let { Placement(it.first, it.second, true) }
        }
        val toHx = toX / lenH
        val toHz = toZ / lenH
        val f = (toHx * basisChecked.forwardX + toHz * basisChecked.forwardZ).toFloat()
        val r = (toHx * basisChecked.rightX + toHz * basisChecked.rightZ).toFloat()

        val sector = classifySector(f, r)
        val below = belowViewPlane(eye, target, basisChecked, camera)
        val u = sectorEdgeU(sector, f, r)
        val (x, y) = mapToScreenSegment(sector, below, u, width, height)
        return Placement(x, y, false)
    }

    private fun horizontalBasis(camera: Camera): HorizontalBasis? {
        val look = Vector3f(0f, 0f, -1f).rotate(camera.rotation())
        val fx = look.x.toDouble()
        val fz = look.z.toDouble()
        val len = sqrt(fx * fx + fz * fz)
        if (len < FORWARD_H_MIN_LEN) {
            return null
        }
        val forwardX = fx / len
        val forwardZ = fz / len
        val rightX = -forwardZ
        val rightZ = forwardX
        return HorizontalBasis(forwardX, forwardZ, rightX, rightZ)
    }

    private fun cameraPitchRad(camera: Camera): Float {
        val look = Vector3f(0f, 0f, -1f).rotate(camera.rotation())
        val horizontal = sqrt(look.x * look.x + look.z * look.z)
        return atan2(-look.y, horizontal)
    }

    /** 目标在视平面下方（含等高）时为 true。 */
    private fun belowViewPlane(eye: Vec3, target: Vec3, basis: HorizontalBasis, camera: Camera): Boolean {
        val dx = target.x - eye.x
        val dz = target.z - eye.z
        val t = dx * basis.forwardX + dz * basis.forwardZ
        val pitch = cameraPitchRad(camera).toDouble()
        val yView = eye.y + t * tan(pitch)
        return target.y <= yView + 1e-4
    }

    private fun classifySector(f: Float, r: Float): Sector {
        val af = abs(f)
        val ar = abs(r)
        return when {
            af >= ar && f >= 0f -> Sector.FRONT
            af >= ar && f < 0f -> Sector.BACK
            r > 0f -> Sector.RIGHT
            else -> Sector.LEFT
        }
    }

    /**
     * 沿罗盘边 [0,1]：前/后为整条边；左/右在 BMr/CMr/AMl/DMl 半段内用 f 符号折半。
     */
    private fun sectorEdgeU(sector: Sector, f: Float, r: Float): Float {
        return when (sector) {
            Sector.FRONT -> {
                (0.5 + atan2(r.toDouble(), f.toDouble()) / (PI / 2)).toFloat().coerceIn(0f, 1f)
            }
            Sector.BACK -> {
                (0.5 + atan2((-r).toDouble(), (-f).toDouble()) / (PI / 2)).toFloat().coerceIn(0f, 1f)
            }
            Sector.RIGHT -> {
                val uFull = ((atan2(r.toDouble(), f.toDouble()) - PI / 4) / (PI / 2)).toFloat().coerceIn(0f, 1f)
                if (f > 0f) {
                    (uFull * 2f).coerceIn(0f, 1f)
                } else {
                    ((uFull - 0.5f) * 2f).coerceIn(0f, 1f)
                }
            }
            Sector.LEFT -> {
                val uFull = ((-atan2(r.toDouble(), f.toDouble()) - PI / 4) / (PI / 2)).toFloat().coerceIn(0f, 1f)
                if (f > 0f) {
                    (uFull * 2f).coerceIn(0f, 1f)
                } else {
                    ((uFull - 0.5f) * 2f).coerceIn(0f, 1f)
                }
            }
        }
    }

    private fun mapToScreenSegment(
        sector: Sector,
        belowViewPlane: Boolean,
        u: Float,
        width: Int,
        height: Int,
    ): Pair<Float, Float> {
        val m = EDGE_MARGIN.toFloat()
        val pX = m
        val pY = m
        val qX = width - m
        val qY = m
        val rX = width - m
        val rY = height - m
        val sX = m
        val sY = height - m
        val msrX = width - m
        val msrY = height / 2f
        val mslX = m
        val mslY = height / 2f

        return when (sector) {
            Sector.FRONT -> if (belowViewPlane) {
                lerp(sX, sY, rX, rY, u)
            } else {
                lerp(pX, pY, qX, qY, u)
            }
            Sector.BACK -> if (belowViewPlane) {
                lerp(rX, rY, sX, sY, u)
            } else {
                lerp(pX, pY, qX, qY, u)
            }
            Sector.RIGHT -> if (belowViewPlane) {
                lerp(rX, rY, msrX, msrY, u)
            } else {
                lerp(qX, qY, msrX, msrY, u)
            }
            Sector.LEFT -> if (belowViewPlane) {
                lerp(sX, sY, mslX, mslY, u)
            } else {
                lerp(pX, pY, mslX, mslY, u)
            }
        }
    }

    private fun lerp(x0: Float, y0: Float, x1: Float, y1: Float, t: Float): Pair<Float, Float> {
        val s = t.coerceIn(0f, 1f)
        return x0 + (x1 - x0) * s to y0 + (y1 - y0) * s
    }

    /**
     * 俯仰接近 ±90° 时的兜底：相机空间方向打屏幕边缘，再相对 [cy] 上下镜像以符合仰视/俯视直觉。
     */
    private fun fallbackPlacement(
        camera: Camera,
        view: Vector3f,
        cx: Float,
        cy: Float,
        width: Int,
        height: Int,
    ): Pair<Float, Float>? {
        val (screenDx, screenDy) = screenPlaneDirection(view) ?: return null
        val angle = atan2(screenDy.toDouble(), screenDx.toDouble())
        val (x, y) = screenEdgePoint(cx, cy, width, height, angle)
        return if (isExtremePitch(camera)) {
            x to mirrorScreenY(y, cy)
        } else {
            x to y
        }
    }

    private fun isExtremePitch(camera: Camera): Boolean =
        abs(Math.toDegrees(cameraPitchRad(camera).toDouble())) >= PITCH_DEGENERATE_THRESHOLD_DEG

    private fun mirrorScreenY(y: Float, centerY: Float): Float = 2f * centerY - y

    /** 世界坐标 → 相机视线空间（z &lt; 0 为相机前方）。 */
    private fun toCameraSpace(camera: Camera, worldPos: Vec3): Vector3f {
        val rel = worldPos.subtract(camera.position())
        val q = Quaternionf(camera.rotation())
        q.conjugate()
        return Vector3f(rel.x.toFloat(), rel.y.toFloat(), rel.z.toFloat()).apply { rotate(q) }
    }

    private fun screenPlaneDirection(view: Vector3f): Pair<Float, Float>? {
        var sx = view.x
        var sy = -view.y
        if (view.z >= 0f) {
            sx = -sx
            sy = -sy
        }
        if (sx * sx + sy * sy < 1e-10f) {
            return null
        }
        return sx to sy
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

    private fun screenEdgePoint(
        cx: Float,
        cy: Float,
        width: Int,
        height: Int,
        angle: Double,
    ): Pair<Float, Float> {
        val cosA = cos(angle).toFloat()
        val sinA = sin(angle).toFloat()
        val maxX = width / 2f - EDGE_INSET
        val maxY = height / 2f - EDGE_INSET
        val tX = if (cosA != 0f) maxX / abs(cosA) else Float.MAX_VALUE
        val tY = if (sinA != 0f) maxY / abs(sinA) else Float.MAX_VALUE
        val t = minOf(tX, tY)
        return cx + cosA * t to cy + sinA * t
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
        private var lastPathFallback = false

        private const val EDGE_SMOOTH_SPEED = 14f
        private const val EDGE_INSET = 28f
        private const val EDGE_MARGIN = 24
        private const val VISIBLE_NDC_MARGIN = 0.05f
        private const val PITCH_DEGENERATE_THRESHOLD_DEG = 85f
        private const val FORWARD_H_MIN_LEN = 0.08

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
