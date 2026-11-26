package pub.pigeon.yggdyy.hexmob.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pub.pigeon.yggdyy.hexmob.content.HMEntityPart;
import pub.pigeon.yggdyy.hexmob.content.IHMMultipartEntity;

import java.util.List;
import java.util.function.Predicate;

@Mixin(Level.class)
public abstract class LevelMixin {
    @Inject(method = "getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;", at = @At("RETURN"), cancellable = true)
    private void addHexMobMultipartEntities1(Entity entity, AABB area, Predicate<? super Entity> predicate, CallbackInfoReturnable<List<Entity>> cir) {
        Level self = (Level) ((Object) this);
        List<Entity> res = cir.getReturnValue();
        IHMMultipartEntity.Companion.updateInstances();
        for (IHMMultipartEntity<?> multipart : IHMMultipartEntity.Companion.getInstances()) {
            if (!(multipart instanceof Entity parent) || parent == entity || parent.level() != self) {
                continue;
            }
            AABB partsBox = multipart.getPartsBoundingBox();
            if (partsBox != null && !partsBox.intersects(area)) {
                continue;
            }
            for (HMEntityPart part : multipart.getAllParts()) {
                if (part != null && part.shouldInteract() && part.getBoundingBox().intersects(area) && predicate.test(part)) {
                    res.add(part);
                }
            }
        }
        cir.setReturnValue(res);
    }
    @Inject(method = "getEntities(Lnet/minecraft/world/level/entity/EntityTypeTest;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;Ljava/util/List;I)V", at = @At("RETURN"))
    private void addHexMobMultipartEntities2(EntityTypeTest<Entity, Entity> entityTypeTest, AABB bounds, Predicate<Entity> predicate, List<Entity> output, int maxResults, CallbackInfo ci) {
        // HMEntityPart 构造传入 parentMob.type，按 EntityType 筛选时必须跳过，防止误强转为父实体类型抛出 ClassCastException
        if (entityTypeTest instanceof EntityType<?> || output.size() >= maxResults) {
            return;
        }
        Level self = (Level) ((Object) this);
        IHMMultipartEntity.Companion.updateInstances();
        Class<?> baseClass = entityTypeTest.getBaseClass();
        for (IHMMultipartEntity<?> multipart : IHMMultipartEntity.Companion.getInstances()) {
            if (!(multipart instanceof Entity parent) || parent.level() != self) {
                continue;
            }
            AABB partsBox = multipart.getPartsBoundingBox();
            if (partsBox != null && !partsBox.intersects(bounds)) {
                continue;
            }
            for (HMEntityPart part : multipart.getAllParts()) {
                if (part == null || !part.shouldInteract()) {
                    continue;
                }
                Entity e1 = entityTypeTest.tryCast(part);
                if (e1 != null && baseClass.isInstance(e1) && e1.getBoundingBox().intersects(bounds) && predicate.test(e1)) {
                    output.add(e1);
                    if (output.size() >= maxResults) {
                        return;
                    }
                }
            }
        }
    }
}
