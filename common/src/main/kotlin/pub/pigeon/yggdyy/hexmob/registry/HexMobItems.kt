package pub.pigeon.yggdyy.hexmob.registry

import dev.architectury.registry.registries.DeferredRegister
import dev.architectury.registry.registries.DeferredSupplier
import net.minecraft.core.registries.Registries
import net.minecraft.world.item.Item
import net.minecraft.world.item.SpawnEggItem
import pub.pigeon.yggdyy.hexmob.HexMob
import pub.pigeon.yggdyy.hexmob.content.ur_circle.UrCircleCoreItem

object HexMobItems {
    fun init() {
        ITEMS.register()
    }

    private const val WHITE: Int = 0xFF_FFFFFF.toInt()
    private const val AMETHYST_PURPLE: Int = 0xFF_7B2FBE.toInt()
    private const val ALLAY_CYAN: Int = 0xFF_7AC4E8.toInt()

    private val ITEMS: DeferredRegister<Item> = DeferredRegister.create(HexMob.MODID, Registries.ITEM)

    // Note: HexMobEntities.init() must run BEFORE HexMobItems.init() so these
    // factories can resolve the EntityTypes when they are built.
    val IOTA_SHEEP_SPAWN_EGG: DeferredSupplier<SpawnEggItem> = ITEMS.register("iota_sheep_spawn_egg") {
        SpawnEggItem(
            HexMobEntities.IOTA_SHEEP.get(),
            WHITE,            // wool base
            AMETHYST_PURPLE,  // hex/amethyst spots
            Item.Properties(),
        )
    }
    val QUENCH_ALLAY_SPAWN_EGG: DeferredSupplier<SpawnEggItem> = ITEMS.register("quench_allay_spawn_egg") {
        SpawnEggItem(
            HexMobEntities.QUENCH_ALLAY.get(),
            ALLAY_CYAN,       // allay cyan base
            WHITE,            // bright spots
            Item.Properties(),
        )
    }
    /** 大环核心：Boss 战利品（备用物品类，见 UrCircleCoreItem）。 */
    val UR_CIRCLE_CORE: DeferredSupplier<UrCircleCoreItem> = ITEMS.register("ur_circle_core") {
        UrCircleCoreItem(Item.Properties())
    }
}
