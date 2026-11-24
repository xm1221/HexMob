package pub.pigeon.yggdyy.hexmob.content.ur_circle.renderers

import at.petrak.hexcasting.api.HexAPI
import at.petrak.hexcasting.api.casting.math.HexPattern
import at.petrak.hexcasting.client.render.PatternColors
import at.petrak.hexcasting.client.render.PatternRenderer
import at.petrak.hexcasting.client.render.WorldlyPatternRenderHelpers
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.client.resources.model.BakedModel
import net.minecraft.client.resources.model.ModelResourceLocation
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.Blocks
import pub.pigeon.yggdyy.hexmob.content.ur_circle.CircleState
import pub.pigeon.yggdyy.hexmob.content.ur_circle.PartRenderer
import pub.pigeon.yggdyy.hexmob.content.ur_circle.UrCirclePart
import pub.pigeon.yggdyy.hexmob.content.ur_circle.subentities.SlatePart

class SlateRenderer(id: ResourceLocation) : PartRenderer(id) {
    override fun render(
        part: UrCirclePart,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        packedLight: Int,
        partialTick: Float,
        context: EntityRendererProvider.Context,
    ) {
        render(part, poseStack, buffer, packedLight, partialTick, context, renderDetails = true)
    }

    override fun render(
        part: UrCirclePart,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        packedLight: Int,
        partialTick: Float,
        context: EntityRendererProvider.Context,
        renderDetails: Boolean,
    ) {
        val slate: SlatePart = part as? SlatePart ?: return
        val circle = slate.parentMob
        if (circle.isPartVanishedInDeath(slate)) return

        val shortCircuited = circle.circleState == CircleState.SHORT_CIRCUIT
        val lit = !circle.isPartExtinguishedInDeath(slate) && !shortCircuited
        val isWaveHead = lit && circle.isActiveWaveNode(slate)
        val energized = if (lit) "energized=true" else "energized=false"
        val model: BakedModel = context.modelManager.getModel(
            ModelResourceLocation(HexAPI.modLoc("slate"), "$energized,face=wall,facing=south,waterlogged=false")
        )

        poseStack.pushPose()
        val s = circle.partVisualScale(slate)
        val w = slate.baseWidth * s
        val h = slate.baseHeight * s
        poseStack.translate(-w / 2.0, -h / 2.0, 0.0)
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

        if (renderDetails || isWaveHead) {
            poseStack.translate(0.0, 0.0, 0.063)
            when {
                shortCircuited -> {
                    PatternRenderer.renderPattern(
                        slate.pattern,
                        poseStack,
                        WorldlyPatternRenderHelpers.WORLDLY_SETTINGS,
                        PatternColors.DIMMED_COLOR,
                        0.0,
                        512,
                    )
                }
                isWaveHead -> {
                    val angles = slate.pattern.angles
                    val strokeCount = (circle.channelWaveProgress() * (angles.size + 1)).toInt().coerceIn(0, angles.size)
                    val patToDraw = if (strokeCount < angles.size) {
                        HexPattern(slate.pattern.startDir, angles.subList(0, strokeCount))
                    } else {
                        slate.pattern
                    }
                    PatternRenderer.renderPattern(
                        patToDraw,
                        poseStack,
                        WorldlyPatternRenderHelpers.WORLDLY_SETTINGS,
                        PatternColors.glowyStroke(circle.waveColor),
                        0.0,
                        512,
                    )
                }
                lit -> {
                    PatternRenderer.renderPattern(
                        slate.pattern,
                        poseStack,
                        WorldlyPatternRenderHelpers.WORLDLY_SETTINGS,
                        PatternColors.SLATE_WOBBLY_PURPLE_COLOR,
                        0.0,
                        512,
                    )
                }
            }
        }
        poseStack.popPose()
    }
}