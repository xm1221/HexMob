package pub.pigeon.yggdyy.hexmob.content.ur_circle.renderers

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import org.joml.Matrix3f
import org.joml.Matrix4f
import pub.pigeon.yggdyy.hexmob.content.ur_circle.UrCircleAnchorEntity
import kotlin.math.cos
import kotlin.math.sin

/**
 * 接地促动石锚点渲染器：
 * 旋转悬浮紫水晶块 + 地面发光刻度阵 + 直连天穹大环的粗壮媒质光柱。
 */
class UrCircleAnchorRenderer(context: EntityRendererProvider.Context) : EntityRenderer<UrCircleAnchorEntity>(context) {
    override fun getTextureLocation(entity: UrCircleAnchorEntity): ResourceLocation = BEACON_TEX

    override fun render(
        entity: UrCircleAnchorEntity,
        entityYaw: Float,
        partialTick: Float,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        packedLight: Int,
    ) {
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight)
        val t = entity.tickCount + partialTick
        val hpRatio = (entity.hp / entity.maxHp).coerceIn(0.2F, 1.0F)
        val color = entity.color
        val cr = color shr 16 and 0xFF
        val cg = color shr 8 and 0xFF
        val cb = color and 0xFF

        // 1. 悬浮旋转晶体方块
        poseStack.pushPose()
        val bob = sin(t * 0.15) * 0.2
        poseStack.translate(0.0, 1.05 + bob, 0.0)
        poseStack.mulPose(Axis.YP.rotationDegrees(t * 4.5F))
        poseStack.mulPose(Axis.XP.rotationDegrees(25.0F * sin(t * 0.08).toFloat()))
        val s = 0.75F + 0.35F * hpRatio
        poseStack.scale(s, s * 1.3F, s)
        poseStack.translate(-0.5, -0.5, -0.5)
        Minecraft.getInstance().blockRenderer.renderSingleBlock(
            Blocks.AMETHYST_BLOCK.defaultBlockState(),
            poseStack,
            buffer,
            LightTexture.FULL_BRIGHT,
            OverlayTexture.NO_OVERLAY,
        )
        poseStack.popPose()

        // 2. 地面旋转光阵与晶体能量环
        val m4 = poseStack.last().pose()
        val lineBuf = buffer.getBuffer(RenderType.lightning())
        drawGroundRing(lineBuf, m4, 0.05F, 1.8F, 0.14F, t * 0.08F, cr, cg, cb, 220)
        drawGroundRing(lineBuf, m4, (1.05 + bob).toFloat(), 1.35F, 0.1F, -t * 0.14F, 255, 220, 130, 220)

        // 3. 直连天穹大环的粗壮发光导通光柱
        val target = entity.tetherTarget() ?: return
        val ox = Mth.lerp(partialTick.toDouble(), entity.xo, entity.x)
        val oy = Mth.lerp(partialTick.toDouble(), entity.yo, entity.y) + 1.05 + bob
        val oz = Mth.lerp(partialTick.toDouble(), entity.zo, entity.z)
        val rel = target.subtract(Vec3(ox, oy, oz))

        poseStack.pushPose()
        poseStack.translate(0.0, 1.05 + bob, 0.0)
        renderTetherBeam(
            rel.x.toFloat(), rel.y.toFloat(), rel.z.toFloat(),
            t, cr, cg, cb, poseStack, buffer,
        )
        poseStack.popPose()
    }

    private fun drawGroundRing(
        consumer: VertexConsumer,
        m4: Matrix4f,
        y: Float,
        radius: Float,
        halfW: Float,
        rotOffset: Float,
        r: Int,
        g: Int,
        b: Int,
        alpha: Int,
    ) {
        val segs = 24
        val inR = (radius - halfW).coerceAtLeast(0.1F)
        val outR = radius + halfW
        for (k in 0 until segs) {
            val a0 = rotOffset + k * 6.2831855F / segs
            val a1 = rotOffset + (k + 1) * 6.2831855F / segs
            val c0 = cos(a0.toDouble()).toFloat()
            val s0 = sin(a0.toDouble()).toFloat()
            val c1 = cos(a1.toDouble()).toFloat()
            val s1 = sin(a1.toDouble()).toFloat()

            consumer.vertex(m4, c0 * inR, y, s0 * inR).color(r, g, b, alpha).endVertex()
            consumer.vertex(m4, c0 * outR, y, s0 * outR).color(r, g, b, alpha).endVertex()
            consumer.vertex(m4, c1 * outR, y, s1 * outR).color(r, g, b, alpha).endVertex()
            consumer.vertex(m4, c1 * inR, y, s1 * inR).color(r, g, b, alpha).endVertex()

            consumer.vertex(m4, c1 * inR, y, s1 * inR).color(r, g, b, alpha).endVertex()
            consumer.vertex(m4, c1 * outR, y, s1 * outR).color(r, g, b, alpha).endVertex()
            consumer.vertex(m4, c0 * outR, y, s0 * outR).color(r, g, b, alpha).endVertex()
            consumer.vertex(m4, c0 * inR, y, s0 * inR).color(r, g, b, alpha).endVertex()
        }
    }

    private fun renderTetherBeam(
        x: Float,
        y: Float,
        z: Float,
        t: Float,
        cr: Int,
        cg: Int,
        cb: Int,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
    ) {
        val horiz = Mth.sqrt(x * x + z * z)
        val length = Mth.sqrt(x * x + y * y + z * z)
        if (length < 0.2F) return

        poseStack.pushPose()
        poseStack.mulPose(Axis.YP.rotation(-Math.atan2(z.toDouble(), x.toDouble()).toFloat() - 1.5707964F))
        poseStack.mulPose(Axis.XP.rotation(-Math.atan2(horiz.toDouble(), y.toDouble()).toFloat() - 1.5707964F))

        val pose = poseStack.last()
        val m4: Matrix4f = pose.pose()
        val m3: Matrix3f = pose.normal()

        drawBeamTube(buffer.getBuffer(RenderType.beaconBeam(BEACON_TEX, false)), m4, m3, 0.16F, length, -(t * 0.12F), length * 0.3F - t * 0.12F, 255, 240, 255, 255)
        drawBeamTube(buffer.getBuffer(RenderType.beaconBeam(BEACON_TEX, true)), m4, m3, 0.42F, length, t * 0.06F, length * 0.2F + t * 0.06F, cr, cg, cb, 145)

        poseStack.popPose()
    }

    private fun drawBeamTube(
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
        for (i in 1..6) {
            val rad = i * 6.2831855F / 6.0F
            val nx = Mth.sin(rad) * r
            val ny = Mth.cos(rad) * r
            val u = i / 6.0F
            consumer.vertex(m4, prevX, prevY, 0F).color(red, green, blue, alpha).uv(prevU, vStart)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(m3, 0F, 1F, 0F).endVertex()
            consumer.vertex(m4, prevX, prevY, length).color(red, green, blue, alpha).uv(prevU, vEnd)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(m3, 0F, 1F, 0F).endVertex()
            consumer.vertex(m4, nx, ny, length).color(red, green, blue, alpha).uv(u, vEnd)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(m3, 0F, 1F, 0F).endVertex()
            consumer.vertex(m4, nx, ny, 0F).color(red, green, blue, alpha).uv(u, vStart)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(m3, 0F, 1F, 0F).endVertex()
            prevX = nx
            prevY = ny
            prevU = u
        }
    }

    companion object {
        private val BEACON_TEX = ResourceLocation("textures/entity/beacon_beam.png")
    }
}
