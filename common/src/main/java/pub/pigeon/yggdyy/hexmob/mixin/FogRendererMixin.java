package pub.pigeon.yggdyy.hexmob.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pub.pigeon.yggdyy.hexmob.client.UrCircleSkyTracker;

@Mixin(FogRenderer.class)
public class FogRendererMixin {
    @Shadow
    private static float fogRed;
    @Shadow
    private static float fogGreen;
    @Shadow
    private static float fogBlue;

    @Inject(method = "setupColor", at = @At("RETURN"))
    private static void hexmob$tintUrCircleFog(
        Camera activeRenderInfo,
        float partialTicks,
        ClientLevel level,
        int renderDistanceChunks,
        float bossColorModifier,
        CallbackInfo ci
    ) {
        float intensity = UrCircleSkyTracker.getIntensity();
        if (intensity <= 0.005F) return;
        float p3 = UrCircleSkyTracker.getPhase3Intensity();
        float targetR = Mth.lerp(p3, 0.105F, 0.048F);
        float targetG = Mth.lerp(p3, 0.042F, 0.020F);
        float targetB = Mth.lerp(p3, 0.215F, 0.115F);
        fogRed = Mth.lerp(intensity, fogRed, targetR);
        fogGreen = Mth.lerp(intensity, fogGreen, targetG);
        fogBlue = Mth.lerp(intensity, fogBlue, targetB);
        RenderSystem.clearColor(fogRed, fogGreen, fogBlue, 0.0F);
    }
}
