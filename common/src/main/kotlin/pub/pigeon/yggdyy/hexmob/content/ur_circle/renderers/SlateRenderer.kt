package pub.pigeon.yggdyy.hexmob.content.ur_circle.renderers

import at.petrak.hexcasting.api.HexAPI
import at.petrak.hexcasting.api.casting.math.HexPattern
import at.petrak.hexcasting.client.render.PatternColors
import at.petrak.hexcasting.client.render.WorldlyPatternRenderHelpers
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.client.resources.model.BakedModel
import net.minecraft.client.resources.model.ModelResourceLocation
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import pub.pigeon.yggdyy.hexmob.client.UrCircleHudTracker
import pub.pigeon.yggdyy.hexmob.content.ur_circle.CircleState
import pub.pigeon.yggdyy.hexmob.content.ur_circle.PartRenderer
import pub.pigeon.yggdyy.hexmob.content.ur_circle.UrCirclePart
import pub.pigeon.yggdyy.hexmob.content.ur_circle.subentities.SlatePart
import pub.pigeon.yggdyy.hexmob.content.ur_circle.subentities.SlateSymbolType

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
        val hudEntry = UrCircleHudTracker.getSlateEntry(circle.id, slate.globalIndex)
        val symType = hudEntry?.let { SlateSymbolType.byId(it.symbolType) } ?: SlateSymbolType.NONE
        val isSymbolic = symType != SlateSymbolType.NONE
        val drawPattern = if (isSymbolic) symType.hexPattern else slate.pattern
        val lit = !circle.isPartExtinguishedInDeath(slate) && !shortCircuited && (hudEntry?.rebounded != true)
        val isWaveHead = lit && (circle.isActiveWaveNode(slate) || isSymbolic)
        val model: BakedModel = context.modelManager.getModel(if (lit) SLATE_LIT_MODEL else SLATE_UNLIT_MODEL)

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
            var patternToRender: HexPattern? = drawPattern
            var colors: PatternColors? = null
            var patternLight = LightTexture.FULL_BRIGHT

            when {
                shortCircuited || hudEntry?.rebounded == true -> {
                    colors = PatternColors.DIMMED_COLOR
                    patternLight = packedLight
                }
                hudEntry?.tamperable == true -> {
                    val blink = ((circle.tickCount + partialTick) * 0.65F)
                    colors = if (kotlin.math.sin(blink.toDouble()) > 0.0) TAMPER_BRIGHT else TAMPER_DIM
                }
                isSymbolic -> {
                    colors = SYMBOL_COLORS[symType]
                }
                isWaveHead -> {
                    val angles = drawPattern.angles
                    val strokeCount = (circle.channelWaveProgress() * (angles.size + 1)).toInt().coerceIn(0, angles.size)
                    patternToRender = cachedPrefixPattern(drawPattern, strokeCount)
                    colors = WAVE_COLOR_CACHE.getOrPut(circle.waveColor) { PatternColors.glowyStroke(circle.waveColor) }
                }
                lit -> {
                    colors = PatternColors.SLATE_WOBBLY_PURPLE_COLOR
                }
                else -> {
                    patternToRender = null
                }
            }

            if (patternToRender != null && colors != null) {
                poseStack.mulPose(Axis.ZP.rotationDegrees(180F))
                poseStack.translate(0.0, -1.0, 0.0)
                poseStack.mulPose(Axis.YP.rotationDegrees(180F))
                WorldlyPatternRenderHelpers.renderPattern(
                    patternToRender,
                    WorldlyPatternRenderHelpers.WORLDLY_SETTINGS,
                    colors,
                    0.0,
                    poseStack,
                    buffer,
                    WALL_FRONT_NORMAL,
                    SLATE_Z_OFFSET,
                    patternLight,
                    1,
                )
            }
        }
        poseStack.popPose()
    }

    private fun cachedPrefixPattern(pattern: HexPattern, strokeCount: Int): HexPattern {
        val angles = pattern.angles
        if (strokeCount >= angles.size) return pattern
        if (PREFIX_CACHE.size > 256) PREFIX_CACHE.clear()
        val prefixes = PREFIX_CACHE.getOrPut(pattern) {
            Array(angles.size + 1) { count ->
                if (count >= angles.size) pattern
                else HexPattern(pattern.startDir, angles.subList(0, count).toList())
            }
        }
        return prefixes[strokeCount.coerceIn(0, prefixes.lastIndex)]
    }

    companion object {
        private val SLATE_LIT_MODEL = ModelResourceLocation(
            HexAPI.modLoc("slate"),
            "energized=true,face=wall,facing=south,waterlogged=false",
        )
        private val SLATE_UNLIT_MODEL = ModelResourceLocation(
            HexAPI.modLoc("slate"),
            "energized=false,face=wall,facing=south,waterlogged=false",
        )
        private val WALL_FRONT_NORMAL = Vec3(0.0, 0.0, -1.0)
        private const val SLATE_Z_OFFSET = -1F / 16F - 0.005F

        private val TAMPER_BRIGHT = PatternColors.glowyStroke(0x55FFFF)
        private val TAMPER_DIM = PatternColors.glowyStroke(0x1CC8CC)
        private val SYMBOL_COLORS: Map<SlateSymbolType, PatternColors> =
            SlateSymbolType.entries.associateWith { PatternColors.glowyStroke(it.normalColor) }
        private val WAVE_COLOR_CACHE = HashMap<Int, PatternColors>(16)
        private val PREFIX_CACHE = HashMap<HexPattern, Array<HexPattern>>(32)
    }
}