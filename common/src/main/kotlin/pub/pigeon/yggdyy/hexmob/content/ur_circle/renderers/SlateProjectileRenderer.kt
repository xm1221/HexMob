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
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.client.resources.model.ModelResourceLocation
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import pub.pigeon.yggdyy.hexmob.HexMob
import pub.pigeon.yggdyy.hexmob.content.ur_circle.SlateProjectile
import pub.pigeon.yggdyy.hexmob.util.safeRotateZTo

/** 石板弹渲染：小号石板 + 随携带图案绘制，朝向飞行方向。 */
class SlateProjectileRenderer(val context: EntityRendererProvider.Context) : EntityRenderer<SlateProjectile>(context) {
    private val rotQuat = Quaternionf()
    private val patternCache = HashMap<CompoundTag, HexPattern>(32)

    override fun getTextureLocation(entity: SlateProjectile): ResourceLocation = HexMob.id("null")

    override fun render(entity: SlateProjectile, entityYaw: Float, partialTick: Float, poseStack: PoseStack, buffer: MultiBufferSource, packedLight: Int) {
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight)
        val model = context.modelManager.getModel(SLATE_LIT_MODEL)
        poseStack.pushPose()
        val sc = 0.6F * entity.visualScale
        poseStack.scale(sc, sc, sc)
        // 让石板正对飞行方向，并对零向量/180°反向做安全保护
        val moveDir = entity.deltaMovement.let { v ->
            if (v.lengthSqr() > 1.0E-7) v
            else Vec3(entity.x - entity.xo, entity.y - entity.yo, entity.z - entity.zo)
        }
        if (moveDir.lengthSqr() > 1.0E-7) {
            poseStack.mulPose(safeRotateZTo(moveDir, rotQuat))
        }
        poseStack.translate(-0.5, -0.5, 0.0)
        Minecraft.getInstance().blockRenderer.modelRenderer.renderModel(
            poseStack.last(),
            buffer.getBuffer(RenderType.cutout()),
            Blocks.AIR.defaultBlockState(),
            model,
            1F, 1F, 1F,
            packedLight,
            OverlayTexture.NO_OVERLAY,
        )
        val tag = entity.entityData.get(SlateProjectile.PATTERN)
        if (patternCache.size > 128) patternCache.clear()
        val pattern = patternCache.getOrPut(tag) { HexPattern.fromNBT(tag) }

        poseStack.mulPose(Axis.ZP.rotationDegrees(180F))
        poseStack.translate(0.0, -1.0, 0.0)
        poseStack.mulPose(Axis.YP.rotationDegrees(180F))
        WorldlyPatternRenderHelpers.renderPattern(
            pattern,
            WorldlyPatternRenderHelpers.WORLDLY_SETTINGS,
            PatternColors.SLATE_WOBBLY_PURPLE_COLOR,
            0.0,
            poseStack,
            buffer,
            WALL_FRONT_NORMAL,
            SLATE_Z_OFFSET,
            LightTexture.FULL_BRIGHT,
            1,
        )
        poseStack.popPose()
    }

    companion object {
        private val SLATE_LIT_MODEL = ModelResourceLocation(
            HexAPI.modLoc("slate"),
            "energized=true,face=wall,facing=south,waterlogged=false",
        )
        private val WALL_FRONT_NORMAL = Vec3(0.0, 0.0, -1.0)
        private const val SLATE_Z_OFFSET = -1F / 16F - 0.005F
    }
}
