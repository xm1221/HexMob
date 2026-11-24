package pub.pigeon.yggdyy.hexmob.content.amethyst_silverfish

import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.MobRenderer
import net.minecraft.resources.ResourceLocation
import pub.pigeon.yggdyy.hexmob.HexMob

class AmethystSilverfishRenderer(context: EntityRendererProvider.Context) :
    MobRenderer<AmethystSilverfishEntity, AmethystSilverfishModel>(
        context,
        AmethystSilverfishModel(context.bakeLayer(AmethystSilverfishModel.LAYER_LOCATION)),
        0.25f,
    ) {
    override fun getTextureLocation(entity: AmethystSilverfishEntity): ResourceLocation = TEXTURE

    companion object {
        private val TEXTURE: ResourceLocation = HexMob.id("textures/entity/amethyst_silverfish.png")
    }
}