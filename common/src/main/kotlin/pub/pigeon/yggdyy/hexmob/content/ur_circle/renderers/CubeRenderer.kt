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

        val model: BakedModel = context.modelManager.getModel(effectiveModelLocation(cube))
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

    private fun effectiveModelLocation(cube: CubePart): ModelResourceLocation {
        val cached = MODEL_CACHE.getOrPut(ModelKey(cube.modelId, cube.modelVariant)) {
            val baseLoc = ModelResourceLocation(cube.modelId, cube.modelVariant)
            val trueVar = cube.modelVariant.replace(ENERGIZED_REGEX, "energized=true")
            val falseVar = cube.modelVariant.replace(ENERGIZED_REGEX, "energized=false")
            val trueLoc = if (trueVar == cube.modelVariant) baseLoc else ModelResourceLocation(cube.modelId, trueVar)
            val falseLoc = if (falseVar == cube.modelVariant) baseLoc else ModelResourceLocation(cube.modelId, falseVar)
            CachedCubeModels(baseLoc, trueLoc, falseLoc)
        }
        val circle = cube.parentMob
        if (cube === circle.earth) return cached.normal
        if (circle.isPartExtinguishedInDeath(cube) || circle.circleState == CircleState.SHORT_CIRCUIT) {
            return cached.energizedFalse
        }
        if (circle.hasEffect(MobEffects.GLOWING) || circle.isSiphoning() || circle.circleState == CircleState.PHASE_TRANSITION) {
            return cached.energizedTrue
        }
        if (circle.circleState == CircleState.CHANNELING) {
            val idx = cube.globalIndex.coerceAtLeast(0)
            val on = (idx + circle.stateTicks / 2) % 2 == 0
            return if (on) cached.energizedTrue else cached.energizedFalse
        }
        return cached.normal
    }

    private data class ModelKey(val modelId: ResourceLocation, val variant: String)
    private class CachedCubeModels(
        val normal: ModelResourceLocation,
        val energizedTrue: ModelResourceLocation,
        val energizedFalse: ModelResourceLocation,
    )

    companion object {
        private val ENERGIZED_REGEX = Regex("energized=(true|false)")
        private val MODEL_CACHE = HashMap<ModelKey, CachedCubeModels>(16)
    }
}