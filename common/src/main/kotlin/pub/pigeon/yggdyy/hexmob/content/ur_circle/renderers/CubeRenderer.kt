package pub.pigeon.yggdyy.hexmob.content.ur_circle.renderers

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.client.resources.model.BakedModel
import net.minecraft.client.resources.model.ModelResourceLocation
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.level.block.Blocks
import pub.pigeon.yggdyy.hexmob.content.ur_circle.CircleState
import pub.pigeon.yggdyy.hexmob.content.ur_circle.PartRenderer
import pub.pigeon.yggdyy.hexmob.content.ur_circle.UrCirclePart
import pub.pigeon.yggdyy.hexmob.content.ur_circle.subentities.CubePart

class CubeRenderer(id: ResourceLocation) : PartRenderer(id) {
    override fun render(
        part: UrCirclePart,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        packedLight: Int,
        partialTick: Float,
        context: EntityRendererProvider.Context,
    ) {
        val cube: CubePart = part as? CubePart ?: return
        val circle = cube.parentMob
        if (circle.isPartVanishedInDeath(cube)) return

        val model: BakedModel = context.modelManager.getModel(ModelResourceLocation(cube.modelId, effectiveVariant(cube)))
        poseStack.pushPose()
        val s = circle.partVisualScale(cube)
        val w = cube.baseWidth * s
        val h = cube.baseHeight * s
        poseStack.translate(-w / 2.0, -h / 2.0, -w / 2.0)
        poseStack.scale(w, h, w)
        Minecraft.getInstance().blockRenderer.modelRenderer.renderModel(
            poseStack.last(),
            buffer.getBuffer(RenderType.cutout()),
            Blocks.AIR.defaultBlockState(),
            model,
            1F, 1F, 1F,
            packedLight,
            OverlayTexture.NO_OVERLAY,
        )
        poseStack.popPose()
    }

    private fun effectiveVariant(cube: CubePart): String {
        val circle = cube.parentMob
        if (cube === circle.earth) return cube.modelVariant
        if (circle.isPartExtinguishedInDeath(cube) || circle.circleState == CircleState.SHORT_CIRCUIT) {
            return cube.modelVariant.replace(ENERGIZED_REGEX, "energized=false")
        }
        if (circle.hasEffect(MobEffects.GLOWING) || circle.isSiphoning() || circle.circleState == CircleState.PHASE_TRANSITION) {
            return cube.modelVariant.replace(ENERGIZED_REGEX, "energized=true")
        }
        if (circle.circleState == CircleState.CHANNELING) {
            val idx = cube.globalIndex.coerceAtLeast(0)
            val on = (idx + circle.stateTicks / 2) % 2 == 0
            return cube.modelVariant.replace(ENERGIZED_REGEX, if (on) "energized=true" else "energized=false")
        }
        return cube.modelVariant
    }

    companion object {
        private val ENERGIZED_REGEX = Regex("energized=(true|false)")
    }
}