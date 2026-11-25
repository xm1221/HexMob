package pub.pigeon.yggdyy.hexmob.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pub.pigeon.yggdyy.hexmob.client.UrCircleSkyTracker;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
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
        if (UrCircleSkyTracker.getIntensity() >= 0.38F) {
            ci.cancel();
        }
    }
}
