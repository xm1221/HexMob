package pub.pigeon.yggdyy.hexmob.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.world.BossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pub.pigeon.yggdyy.hexmob.client.UrCircleBossBarRenderer;

/**
 * 拦截大环 Boss 血条渲染，仅接管匹配的 UrCircleEntity 事件并保持其他 Boss 原版渲染。
 */
@Mixin(BossHealthOverlay.class)
public abstract class BossHealthOverlayMixin {
    @Inject(
        method = "drawBar(Lnet/minecraft/client/gui/GuiGraphics;IILnet/minecraft/world/BossEvent;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void hexmob$renderUrCircleCustomBar(GuiGraphics guiGraphics, int x, int y, BossEvent bossEvent, CallbackInfo ci) {
        if (UrCircleBossBarRenderer.tryRenderCustomBar(guiGraphics, x, y, bossEvent)) {
            ci.cancel();
        }
    }
}
