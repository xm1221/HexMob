package pub.pigeon.yggdyy.hexmob.fabric.mixin;

import at.petrak.hexcasting.api.addldata.ADIotaHolder;
import at.petrak.hexcasting.fabric.xplat.FabricXplatImpl;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pub.pigeon.yggdyy.hexmob.content.ur_circle.subentities.SlatePart;

@Mixin(value = FabricXplatImpl.class, remap = false)
public abstract class FabricXplatImplMixin {
    @Inject(method = "findDataHolder(Lnet/minecraft/world/entity/Entity;)Lat/petrak/hexcasting/api/addldata/ADIotaHolder;", at = @At("HEAD"), cancellable = true)
    private void hexmob$slatePartIotaHolder(Entity entity, CallbackInfoReturnable<ADIotaHolder> cir) {
        if (entity instanceof SlatePart slate && slate.getActive()) {
            cir.setReturnValue(slate);
        }
    }
}
