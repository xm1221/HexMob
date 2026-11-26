package pub.pigeon.yggdyy.hexmob.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.world.BossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pub.pigeon.yggdyy.hexmob.client.UrCircleBossBarRenderer;

/**
 * 拦截大环 Boss 血条渲染，接管匹配的 UrCircleEntity 事件并动态向下推移后续 Boss 血条与标题，避免固定 19px 步长重叠。
 */
@Mixin(BossHealthOverlay.class)
public abstract class BossHealthOverlayMixin {
    @Unique
    private boolean hexmob$inRender = false;
    @Unique
    private int hexmob$pendingExtraY = 0;

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;)V", at = @At("HEAD"))
    private void hexmob$beginBossBarLayout(GuiGraphics guiGraphics, CallbackInfo ci) {
        hexmob$inRender = true;
        hexmob$pendingExtraY = 0;
        guiGraphics.pose().pushPose();
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;)V", at = @At("RETURN"))
    private void hexmob$endBossBarLayout(GuiGraphics guiGraphics, CallbackInfo ci) {
        if (hexmob$inRender) {
            guiGraphics.pose().popPose();
            hexmob$inRender = false;
        }
        hexmob$pendingExtraY = 0;
    }

    @Inject(
        method = "drawBar(Lnet/minecraft/client/gui/GuiGraphics;IILnet/minecraft/world/BossEvent;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void hexmob$renderUrCircleCustomBar(GuiGraphics guiGraphics, int x, int y, BossEvent bossEvent, CallbackInfo ci) {
        if (hexmob$inRender && hexmob$pendingExtraY != 0) {
            guiGraphics.pose().translate(0.0F, (float) hexmob$pendingExtraY, 0.0F);
            hexmob$pendingExtraY = 0;
        }
        if (UrCircleBossBarRenderer.tryRenderCustomBar(guiGraphics, x, y, bossEvent)) {
            if (hexmob$inRender) {
                hexmob$pendingExtraY = UrCircleBossBarRenderer.extraBarHeight(bossEvent);
            }
            ci.cancel();
        }
    }
}
