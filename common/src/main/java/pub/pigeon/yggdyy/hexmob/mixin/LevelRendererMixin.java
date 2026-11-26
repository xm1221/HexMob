package pub.pigeon.yggdyy.hexmob.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pub.pigeon.yggdyy.hexmob.client.UrCircleSkyTracker;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
    @Unique
    private boolean hexmob$cloudsSuppressed = false;
    @Unique
    private boolean hexmob$modifiedCloudColor = false;

    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
    private void hexmob$suppressCloudsInUrCircleDomain(
        PoseStack poseStack,
        Matrix4f projectionMatrix,
        float partialTick,
        double camX,
        double camY,
        double camZ,
        CallbackInfo ci
    ) {
        hexmob$modifiedCloudColor = false;
        float intensity = UrCircleSkyTracker.getIntensity();
        if (hexmob$cloudsSuppressed) {
            if (intensity <= 0.42F) {
                hexmob$cloudsSuppressed = false;
            } else {
                ci.cancel();
                return;
            }
        } else if (intensity >= 0.58F) {
            hexmob$cloudsSuppressed = true;
            ci.cancel();
            return;
        }

        if (intensity > 0.28F) {
            float fade = 1.0F - Mth.clamp((intensity - 0.28F) / (0.58F - 0.28F), 0.0F, 1.0F);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, fade);
            hexmob$modifiedCloudColor = true;
        }
    }

    @Inject(method = "renderClouds", at = @At("RETURN"))
    private void hexmob$restoreCloudShaderColor(
        PoseStack poseStack,
        Matrix4f projectionMatrix,
        float partialTick,
        double camX,
        double camY,
        double camZ,
        CallbackInfo ci
    ) {
        if (hexmob$modifiedCloudColor) {
            hexmob$modifiedCloudColor = false;
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }
}
