package pub.pigeon.yggdyy.hexmob.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pub.pigeon.yggdyy.hexmob.client.UrCircleSkyTracker;

@Mixin(ClientLevel.class)
public class ClientLevelMixin {
    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
    private void hexmob$tintUrCircleSky(Vec3 pos, float partialTick, CallbackInfoReturnable<Vec3> cir) {
        float intensity = UrCircleSkyTracker.getIntensity();
        if (intensity <= 0.005F) return;
        Vec3 base = cir.getReturnValue();
        float p3 = UrCircleSkyTracker.getPhase3Intensity();
        double targetR = Mth.lerp(p3, 0.075, 0.032);
        double targetG = Mth.lerp(p3, 0.028, 0.012);
        double targetB = Mth.lerp(p3, 0.165, 0.082);
        double r = Mth.lerp(intensity, base.x, targetR);
        double g = Mth.lerp(intensity, base.y, targetG);
        double b = Mth.lerp(intensity, base.z, targetB);
        cir.setReturnValue(new Vec3(r, g, b));
    }

    @Inject(method = "getCloudColor", at = @At("RETURN"), cancellable = true)
    private void hexmob$tintUrCircleClouds(float partialTick, CallbackInfoReturnable<Vec3> cir) {
        float intensity = UrCircleSkyTracker.getIntensity();
        if (intensity <= 0.005F) return;
        Vec3 base = cir.getReturnValue();
        double r = Mth.lerp(intensity, base.x, 0.19);
        double g = Mth.lerp(intensity, base.y, 0.08);
        double b = Mth.lerp(intensity, base.z, 0.34);
        cir.setReturnValue(new Vec3(r, g, b));
    }
}
