package pub.pigeon.yggdyy.hexmob.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pub.pigeon.yggdyy.hexmob.content.ur_circle.UrCircleCarryAlong;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
    @Shadow
    public ServerPlayer player;

    @Shadow
    private boolean clientIsFloating;

    @Shadow
    private int aboveGroundTickCount;

    @Inject(method = "tick", at = @At("HEAD"))
    private void resetFloatingOnUrCircleRing(CallbackInfo ci) {
        if ((clientIsFloating || aboveGroundTickCount > 0) &&
            player != null &&
            UrCircleCarryAlong.INSTANCE.isPlayerNearWalkableCircle(player)
        ) {
            clientIsFloating = false;
            aboveGroundTickCount = 0;
        }
    }
}
