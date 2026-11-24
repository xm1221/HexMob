package pub.pigeon.yggdyy.hexmob.content.ur_circle

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import org.joml.Vector3f
import pub.pigeon.yggdyy.hexmob.HexMob
import pub.pigeon.yggdyy.hexmob.content.ur_circle.renderers.CubeRenderer
import pub.pigeon.yggdyy.hexmob.content.ur_circle.renderers.SlateRenderer
import pub.pigeon.yggdyy.hexmob.content.ur_circle.renderers.UrCircleVfxRenderer

class UrCircleRenderer(val context: EntityRendererProvider.Context) : EntityRenderer<UrCircleEntity>(context) {
    override fun getTextureLocation(entity: UrCircleEntity): ResourceLocation = HexMob.id("null")

    override fun render(
        entity: UrCircleEntity,
        entityYaw: Float,
        partialTick: Float,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        packedLight: Int,
    ) {
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight)
        val ix = Mth.lerp(partialTick.toDouble(), entity.xo, entity.x)
        val iy = Mth.lerp(partialTick.toDouble(), entity.yo, entity.y)
        val iz = Mth.lerp(partialTick.toDouble(), entity.zo, entity.z)

        val camPos = Minecraft.getInstance().gameRenderer.mainCamera.position
        val camDist = camPos.distanceTo(Vec3(ix, iy + entity.bbHeight * 0.5, iz))
        val farLod = camDist > LOD_FAR_DISTANCE
        val midLod = camDist > LOD_MID_DISTANCE

        val parts: List<UrCirclePart> = entity.getAllParts()
        for (part in parts) {
            if (!part.shouldRenderAsPart()) continue
            val isWaveHead = entity.isActiveWaveNode(part)
            if (farLod && part !== entity.earth && !isWaveHead && part.globalIndex % 2 != 0) {
                continue
            }

            val p: Vec3 = part.posPrev.lerp(part.posNow, partialTick.toDouble())
            val deltaP: Vec3 = p.subtract(ix, iy, iz)
            val d: Vec3 = part.dirPrev.lerp(part.dirNow, partialTick.toDouble())
            val renderDetails = when {
                farLod -> false
                midLod -> isWaveHead || part.ringIndex == 1
                else -> true
            }

            poseStack.pushPose()
            poseStack.translate(deltaP.x, deltaP.y, deltaP.z)
            poseStack.mulPose(Quaternionf().rotateTo(Vector3f(0F, 0F, 1F), d.toVector3f()))
            for (renderer in renderers) {
                if (renderer.id == part.id) {
                    renderer.render(part, poseStack, buffer, packedLight, partialTick, context, renderDetails)
                    break
                }
            }
            poseStack.popPose()
        }

        UrCircleVfxRenderer.renderCircleVfx(entity, partialTick, poseStack, buffer)
    }

    companion object {
        const val LOD_MID_DISTANCE = 72.0
        const val LOD_FAR_DISTANCE = 140.0

        val renderers: MutableList<PartRenderer> = mutableListOf(
            CubeRenderer(HexMob.id("cube")),
            SlateRenderer(HexMob.id("slate")),
        )
    }
}