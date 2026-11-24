package pub.pigeon.yggdyy.hexmob.content.ur_circle.ur_spell

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.math.Axis
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import org.joml.Matrix3f
import org.joml.Matrix4f
import kotlin.math.cos
import kotlin.math.sin

/**
 * 四层全亮程序化光束渲染器：
 * 1. 全亮白紫高能内核（beaconBeam 不透明）
 * 2. 半透明外晕脉冲层（beaconBeam 半透明，按 beamColor 着色）
 * 3. 双螺旋加色能量丝带（RenderType.lightning）
 * 4. 发射口与落点十字星芒光核
 */
class UrCoreBeamRenderer(context: EntityRendererProvider.Context) : EntityRenderer<UrCoreBeamEntity>(context) {
    override fun getTextureLocation(entity: UrCoreBeamEntity): ResourceLocation = BEACON_TEX

    override fun render(
        entity: UrCoreBeamEntity,
        entityYaw: Float,
        partialTick: Float,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        packedLight: Int,
    ) {
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight)
        val target = entity.beamTarget() ?: return
        val ox = Mth.lerp(partialTick.toDouble(), entity.xo, entity.x)
        val oy = Mth.lerp(partialTick.toDouble(), entity.yo, entity.y)
        val oz = Mth.lerp(partialTick.toDouble(), entity.zo, entity.z)
        val rel = target.subtract(Vec3(ox, oy, oz))

        renderHexBeam(
            rel.x.toFloat(),
            rel.y.toFloat(),
            rel.z.toFloat(),
            entity.beamRadius,
            entity.beamColor,
            partialTick,
            entity.tickCount,
            poseStack,
            buffer,
        )
    }

    private fun renderHexBeam(
        x: Float,
        y: Float,
        z: Float,
        radius: Float,
        colorArgb: Int,
        partialTick: Float,
        tickCount: Int,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
    ) {
        val horiz = Mth.sqrt(x * x + z * z)
        val length = Mth.sqrt(x * x + y * y + z * z)
        if (length < 0.05F) return

        val cr = colorArgb shr 16 and 0xFF
        val cg = colorArgb shr 8 and 0xFF
        val cb = colorArgb and 0xFF
        val t = tickCount + partialTick

        poseStack.pushPose()
        poseStack.mulPose(Axis.YP.rotation(-Math.atan2(z.toDouble(), x.toDouble()).toFloat() - 1.5707964F))
        poseStack.mulPose(Axis.XP.rotation(-Math.atan2(horiz.toDouble(), y.toDouble()).toFloat() - 1.5707964F))

        val pose = poseStack.last()
        val m4: Matrix4f = pose.pose()
        val m3: Matrix3f = pose.normal()

        // 1. 高能内核
        val coreR = radius * 0.45F
        val coreV0 = -(t * 0.14F)
        val coreV1 = length * 0.4F - t * 0.14F
        drawCylinder(
            buffer.getBuffer(RenderType.beaconBeam(BEACON_TEX, false)),
            m4, m3,
            coreR, length,
            coreV0, coreV1,
            255, 240, 255, 255,
        )

        // 2. 半透明外晕
        val haloR = radius * (1.15F + 0.12F * Mth.sin(t * 0.35F))
        val haloV0 = t * 0.08F
        val haloV1 = length * 0.25F + t * 0.08F
        drawCylinder(
            buffer.getBuffer(RenderType.beaconBeam(BEACON_TEX, true)),
            m4, m3,
            haloR, length,
            haloV0, haloV1,
            cr, cg, cb, 140,
        )

        // 3. 双螺旋加色能量丝带（仅足够粗的光束绘制）
        val lightning = buffer.getBuffer(RenderType.lightning())
        if (radius >= 0.25F) {
            drawHelixRibbons(lightning, m4, radius * 1.35F, radius * 0.16F, length, t, cr, cg, cb)
        }

        // 4. 起点与落点星芒光核
        drawStarburst(lightning, m4, 0.05F, radius * 1.9F, t * 0.2F, 255, 235, 255, 200)
        drawStarburst(lightning, m4, length - 0.05F, radius * 2.4F, -t * 0.25F, cr, cg, cb, 220)

        poseStack.popPose()
    }

    private fun drawCylinder(
        consumer: VertexConsumer,
        m4: Matrix4f,
        m3: Matrix3f,
        r: Float,
        length: Float,
        vStart: Float,
        vEnd: Float,
        red: Int,
        green: Int,
        blue: Int,
        alpha: Int,
    ) {
        var prevX = 0.0F
        var prevY = r
        var prevU = 0.0F
        for (i in 1..8) {
            val rad = i * 6.2831855F / 8.0F
            val nx = Mth.sin(rad) * r
            val ny = Mth.cos(rad) * r
            val u = i / 8.0F

            consumer.vertex(m4, prevX, prevY, 0.0F)
                .color(red, green, blue, alpha).uv(prevU, vStart)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                .normal(m3, 0.0F, 1.0F, 0.0F).endVertex()
            consumer.vertex(m4, prevX, prevY, length)
                .color(red, green, blue, alpha).uv(prevU, vEnd)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                .normal(m3, 0.0F, 1.0F, 0.0F).endVertex()
            consumer.vertex(m4, nx, ny, length)
                .color(red, green, blue, alpha).uv(u, vEnd)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                .normal(m3, 0.0F, 1.0F, 0.0F).endVertex()
            consumer.vertex(m4, nx, ny, 0.0F)
                .color(red, green, blue, alpha).uv(u, vStart)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                .normal(m3, 0.0F, 1.0F, 0.0F).endVertex()

            prevX = nx
            prevY = ny
            prevU = u
        }
    }

    private fun drawHelixRibbons(
        consumer: VertexConsumer,
        m4: Matrix4f,
        helixR: Float,
        ribbonHalfW: Float,
        length: Float,
        t: Float,
        cr: Int,
        cg: Int,
        cb: Int,
    ) {
        val segs = (length * 1.4F).toInt().coerceIn(8, 44)
        for (strand in 0 until 2) {
            val phaseOffset = strand * Math.PI
            val rCol = if (strand == 0) 255 else cr
            val gCol = if (strand == 0) 215 else cg
            val bCol = if (strand == 0) 115 else cb
            for (k in 0 until segs) {
                val f0 = k.toFloat() / segs
                val f1 = (k + 1).toFloat() / segs
                val z0 = f0 * length
                val z1 = f1 * length
                val a0 = phaseOffset + z0 * 0.55 - t * 0.35
                val a1 = phaseOffset + z1 * 0.55 - t * 0.35

                val x0 = (cos(a0) * helixR).toFloat()
                val y0 = (sin(a0) * helixR).toFloat()
                val x1 = (cos(a1) * helixR).toFloat()
                val y1 = (sin(a1) * helixR).toFloat()

                val tx0 = (-sin(a0) * ribbonHalfW).toFloat()
                val ty0 = (cos(a0) * ribbonHalfW).toFloat()
                val tx1 = (-sin(a1) * ribbonHalfW).toFloat()
                val ty1 = (cos(a1) * ribbonHalfW).toFloat()

                consumer.vertex(m4, x0 - tx0, y0 - ty0, z0).color(rCol, gCol, bCol, 180).endVertex()
                consumer.vertex(m4, x0 + tx0, y0 + ty0, z0).color(rCol, gCol, bCol, 180).endVertex()
                consumer.vertex(m4, x1 + tx1, y1 + ty1, z1).color(rCol, gCol, bCol, 180).endVertex()
                consumer.vertex(m4, x1 - tx1, y1 - ty1, z1).color(rCol, gCol, bCol, 180).endVertex()
            }
        }
    }

    private fun drawStarburst(
        consumer: VertexConsumer,
        m4: Matrix4f,
        zPos: Float,
        size: Float,
        rotRad: Float,
        r: Int,
        g: Int,
        b: Int,
        alpha: Int,
    ) {
        val c = cos(rotRad.toDouble()).toFloat()
        val s = sin(rotRad.toDouble()).toFloat()
        val thin = size * 0.22F

        fun emitBlade(ux: Float, uy: Float, vx: Float, vy: Float) {
            consumer.vertex(m4, -ux * size - vx * thin, -uy * size - vy * thin, zPos).color(r, g, b, 0).endVertex()
            consumer.vertex(m4, -ux * size + vx * thin, -uy * size + vy * thin, zPos).color(r, g, b, alpha).endVertex()
            consumer.vertex(m4, ux * size + vx * thin, uy * size + vy * thin, zPos).color(r, g, b, alpha).endVertex()
            consumer.vertex(m4, ux * size - vx * thin, uy * size - vy * thin, zPos).color(r, g, b, 0).endVertex()
        }
        emitBlade(c, s, -s, c)
        emitBlade(-s, c, c, s)
    }

    companion object {
        private val BEACON_TEX = ResourceLocation("textures/entity/beacon_beam.png")
    }
}
