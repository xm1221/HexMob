package pub.pigeon.yggdyy.hexmob.content.ur_circle.renderers

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import org.joml.Matrix3f
import org.joml.Matrix4f
import pub.pigeon.yggdyy.hexmob.content.ur_circle.CircleState
import pub.pigeon.yggdyy.hexmob.content.ur_circle.UrCircleEntity
import pub.pigeon.yggdyy.hexmob.util.rotateDA
import kotlin.math.cos
import kotlin.math.sin

/**
 * 大环程序化 3D 光效渲染器：
 * - 四环连续发光奥术环带 + 亮金边线
 * - 二阶段外环天穹遮蔽光环（遮天蔽日感）
 * - 核心逆转日冕、媒质虹吸护环、断路警报环
 * - 环面切割 3D 旋转光刃圆盘
 */
object UrCircleVfxRenderer {
    private val BEACON_TEX = ResourceLocation("textures/entity/beacon_beam.png")
    private const val RING_SEGMENTS = 48

    fun renderCircleVfx(
        entity: UrCircleEntity,
        partialTick: Float,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
    ) {
        if (entity.circleState == CircleState.DYING && entity.stateTicks >= UrCircleEntity.CORE_BREAK_TICK) return

        val t = entity.tickCount + partialTick
        val localCenter = Vec3(0.0, entity.bbHeight * 0.5, 0.0)
        val scale = entity.totalScale()
        val df = entity.dormantFold
        val p2 = entity.phase2Reveal
        val fp = entity.foldProgress
        val shortCircuited = entity.circleState == CircleState.SHORT_CIRCUIT
        val siphoning = entity.isSiphoning()

        val pose = poseStack.last()
        val m4 = pose.pose()
        val m3 = pose.normal()
        val beamBuf = buffer.getBuffer(RenderType.beaconBeam(BEACON_TEX, true))
        val lineBuf = buffer.getBuffer(RenderType.lightning())

        // 1. 各可见环的连续奥术光带与亮金边线
        for (track in entity.rings) {
            if (!track.isVisible(entity.phase, p2)) continue
            val effN = track.effectiveNormal(fp, df)
            val radAxis = track.radialAxis(effN)
            val r = track.effectiveRadius(scale, df, p2)
            if (r < 0.8) continue

            val (red, green, blue, alpha) = when {
                shortCircuited -> QuadColor(255, 55, 85, 85)
                siphoning -> QuadColor(75, 225, 220, 125)
                track.ringIndex == 2 -> QuadColor(150, 70, 255, 130)
                track.ringIndex == 3 -> QuadColor(235, 185, 110, 115)
                else -> QuadColor(185, 105, 255, 110)
            }
            val halfW = track.haloWidth * 0.5
            drawDoubleSidedRingBand(
                beamBuf, m4, m3,
                localCenter, radAxis, effN,
                r - halfW, r + halfW,
                RING_SEGMENTS,
                t * 0.025F * track.spinDir,
                red, green, blue, alpha,
            )

            val rimAlpha = if (shortCircuited) 110 else 195
            val rimR = if (shortCircuited) 255 else 245
            val rimG = if (shortCircuited) 70 else 210
            val rimB = if (shortCircuited) 90 else 135
            drawLightningRing(
                lineBuf, m4,
                localCenter, radAxis, effN,
                r, 0.16,
                RING_SEGMENTS,
                rimR, rimG, rimB, rimAlpha,
            )
        }

        // 2. 二阶段遮天蔽日天幕光环
        if (entity.phase >= 2 && p2 > 0.05F && entity.rings.size > 2) {
            val outer = entity.rings[2]
            val effN = outer.effectiveNormal(fp, df)
            val radAxis = outer.radialAxis(effN)
            val rOuter = outer.effectiveRadius(scale, df, p2)
            if (rOuter > 4.0) {
                val canopyAlpha = (145 * p2).toInt().coerceIn(0, 150)
                drawDoubleSidedRingBand(
                    beamBuf, m4, m3,
                    localCenter, radAxis, effN,
                    rOuter * 0.66, rOuter * 1.07,
                    RING_SEGMENTS,
                    t * 0.012F,
                    48, 16, 84, canopyAlpha,
                )
                drawDoubleSidedRingBand(
                    beamBuf, m4, m3,
                    localCenter, radAxis, effN,
                    rOuter * 0.88, rOuter * 1.03,
                    RING_SEGMENTS,
                    -t * 0.018F,
                    165, 95, 245, (canopyAlpha * 0.65F).toInt(),
                )
            }
        }

        // 3. 核心逆转日冕与虹吸护环
        val coronaR = (1.6 * scale).coerceIn(2.2, 8.0)
        val n1 = Vec3(0.35, 0.9, 0.25).normalize().rotateDA(t * 1.8F, Vec3(0.0, 1.0, 0.0)).normalize()
        val n2 = Vec3(-0.35, 0.9, 0.25).normalize().rotateDA(-t * 2.1F, Vec3(0.0, 1.0, 0.0)).normalize()
        val cRad1 = n1.cross(Vec3(1.0, 0.0, 0.0)).let { if (it.lengthSqr() < 1.0E-4) Vec3(0.0, 0.0, 1.0) else it.normalize() }
        val cRad2 = n2.cross(Vec3(0.0, 0.0, 1.0)).let { if (it.lengthSqr() < 1.0E-4) Vec3(1.0, 0.0, 0.0) else it.normalize() }

        drawLightningRing(lineBuf, m4, localCenter, cRad1, n1, coronaR, 0.14, 24, 255, 215, 115, 210)
        drawLightningRing(lineBuf, m4, localCenter, cRad2, n2, coronaR * 1.25, 0.12, 24, 190, 115, 255, 195)

        if (siphoning) {
            val pulse = 1.0 + 0.12 * sin(t * 0.35)
            drawLightningRing(
                lineBuf, m4,
                localCenter, Vec3(1.0, 0.0, 0.0), Vec3(0.0, 1.0, 0.0),
                coronaR * 1.75 * pulse, 0.24, 32,
                76, 232, 224, 220,
            )
        }

        // 4. 环面切割 3D 旋转光刃圆盘
        if (entity.isPlaneSlicing()) {
            val sliceCenter = Vec3(0.0, entity.slicePlaneLocalY().toDouble(), 0.0)
            val sliceR = entity.ringOuterRadius()
            val sliceN = Vec3(0.0, 1.0, 0.0)
            val sliceRad = Vec3(cos((t * 0.25).toDouble()), 0.0, sin((t * 0.25).toDouble())).normalize()
            drawDoubleSidedRingBand(
                beamBuf, m4, m3,
                sliceCenter, sliceRad, sliceN,
                sliceR * 0.28, sliceR * 1.04,
                RING_SEGMENTS,
                t * 0.09F,
                255, 210, 130, 175,
            )
            drawLightningRing(
                lineBuf, m4,
                sliceCenter, sliceRad, sliceN,
                sliceR * 1.02, 0.35, RING_SEGMENTS,
                255, 245, 200, 235,
            )
        }
    }

    private fun drawDoubleSidedRingBand(
        consumer: VertexConsumer,
        m4: Matrix4f,
        m3: Matrix3f,
        center: Vec3,
        radAxis: Vec3,
        normal: Vec3,
        innerR: Double,
        outerR: Double,
        segments: Int,
        vScroll: Float,
        red: Int,
        green: Int,
        blue: Int,
        alpha: Int,
    ) {
        if (outerR <= innerR || alpha <= 0) return
        val nx = normal.x.toFloat()
        val ny = normal.y.toFloat()
        val nz = normal.z.toFloat()

        for (k in 0 until segments) {
            val a0 = k * 360F / segments
            val a1 = (k + 1) * 360F / segments
            val d0 = radAxis.rotateDA(a0, normal)
            val d1 = radAxis.rotateDA(a1, normal)

            val x0i = (center.x + d0.x * innerR).toFloat()
            val y0i = (center.y + d0.y * innerR).toFloat()
            val z0i = (center.z + d0.z * innerR).toFloat()

            val x0o = (center.x + d0.x * outerR).toFloat()
            val y0o = (center.y + d0.y * outerR).toFloat()
            val z0o = (center.z + d0.z * outerR).toFloat()

            val x1o = (center.x + d1.x * outerR).toFloat()
            val y1o = (center.y + d1.y * outerR).toFloat()
            val z1o = (center.z + d1.z * outerR).toFloat()

            val x1i = (center.x + d1.x * innerR).toFloat()
            val y1i = (center.y + d1.y * innerR).toFloat()
            val z1i = (center.z + d1.z * innerR).toFloat()

            val v0 = k.toFloat() / segments * 4F + vScroll
            val v1 = (k + 1).toFloat() / segments * 4F + vScroll

            // 正面
            consumer.vertex(m4, x0i, y0i, z0i).color(red, green, blue, alpha).uv(0F, v0)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(m3, nx, ny, nz).endVertex()
            consumer.vertex(m4, x0o, y0o, z0o).color(red, green, blue, alpha).uv(1F, v0)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(m3, nx, ny, nz).endVertex()
            consumer.vertex(m4, x1o, y1o, z1o).color(red, green, blue, alpha).uv(1F, v1)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(m3, nx, ny, nz).endVertex()
            consumer.vertex(m4, x1i, y1i, z1i).color(red, green, blue, alpha).uv(0F, v1)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(m3, nx, ny, nz).endVertex()

            // 背面（防仰视剔除）
            consumer.vertex(m4, x1i, y1i, z1i).color(red, green, blue, alpha).uv(0F, v1)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(m3, -nx, -ny, -nz).endVertex()
            consumer.vertex(m4, x1o, y1o, z1o).color(red, green, blue, alpha).uv(1F, v1)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(m3, -nx, -ny, -nz).endVertex()
            consumer.vertex(m4, x0o, y0o, z0o).color(red, green, blue, alpha).uv(1F, v0)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(m3, -nx, -ny, -nz).endVertex()
            consumer.vertex(m4, x0i, y0i, z0i).color(red, green, blue, alpha).uv(0F, v0)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(m3, -nx, -ny, -nz).endVertex()
        }
    }

    private fun drawLightningRing(
        consumer: VertexConsumer,
        m4: Matrix4f,
        center: Vec3,
        radAxis: Vec3,
        normal: Vec3,
        radius: Double,
        halfThickness: Double,
        segments: Int,
        r: Int,
        g: Int,
        b: Int,
        alpha: Int,
    ) {
        val innerR = (radius - halfThickness).coerceAtLeast(0.1)
        val outerR = radius + halfThickness
        for (k in 0 until segments) {
            val a0 = k * 360F / segments
            val a1 = (k + 1) * 360F / segments
            val d0 = radAxis.rotateDA(a0, normal)
            val d1 = radAxis.rotateDA(a1, normal)

            val x0i = (center.x + d0.x * innerR).toFloat()
            val y0i = (center.y + d0.y * innerR).toFloat()
            val z0i = (center.z + d0.z * innerR).toFloat()
            val x0o = (center.x + d0.x * outerR).toFloat()
            val y0o = (center.y + d0.y * outerR).toFloat()
            val z0o = (center.z + d0.z * outerR).toFloat()
            val x1o = (center.x + d1.x * outerR).toFloat()
            val y1o = (center.y + d1.y * outerR).toFloat()
            val z1o = (center.z + d1.z * outerR).toFloat()
            val x1i = (center.x + d1.x * innerR).toFloat()
            val y1i = (center.y + d1.y * innerR).toFloat()
            val z1i = (center.z + d1.z * innerR).toFloat()

            consumer.vertex(m4, x0i, y0i, z0i).color(r, g, b, alpha).endVertex()
            consumer.vertex(m4, x0o, y0o, z0o).color(r, g, b, alpha).endVertex()
            consumer.vertex(m4, x1o, y1o, z1o).color(r, g, b, alpha).endVertex()
            consumer.vertex(m4, x1i, y1i, z1i).color(r, g, b, alpha).endVertex()

            consumer.vertex(m4, x1i, y1i, z1i).color(r, g, b, alpha).endVertex()
            consumer.vertex(m4, x1o, y1o, z1o).color(r, g, b, alpha).endVertex()
            consumer.vertex(m4, x0o, y0o, z0o).color(r, g, b, alpha).endVertex()
            consumer.vertex(m4, x0i, y0i, z0i).color(r, g, b, alpha).endVertex()
        }
    }

    private data class QuadColor(val r: Int, val g: Int, val b: Int, val a: Int)
}
