package pub.pigeon.yggdyy.hexmob.forge

import dev.architectury.platform.Platform
import dev.architectury.platform.forge.EventBuses
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.server.ServerStartingEvent
import pub.pigeon.yggdyy.hexmob.HexMob
import pub.pigeon.yggdyy.hexmob.registry.HexMobActions
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent
import net.minecraftforge.fml.event.lifecycle.FMLDedicatedServerSetupEvent
import pub.pigeon.yggdyy.hexmob.registry.HexMobEntityRenderers
import pub.pigeon.yggdyy.hexmob.worldgen.CrystalSpikesHolder
import thedarkcolour.kotlinforforge.forge.MOD_BUS

@Mod(HexMob.MODID)
class ForgeHexMob {
    init {
        MOD_BUS.apply {
            EventBuses.registerModEventBus(HexMob.MODID, this)
            addListener{event: FMLClientSetupEvent -> ForgeHexMobClient.init()}
            addListener{event: FMLDedicatedServerSetupEvent -> ForgeHexMobServer.init()}
        }
        MinecraftForge.EVENT_BUS.addListener { event: ServerStartingEvent ->
            val server = event.server
            val opt = server.registryAccess()
                .registry(Registries.BIOME)
                .flatMap { it.getHolder(ResourceKey.create(Registries.BIOME, ResourceLocation("hexmob", "crystal_spikes"))) }
            if (opt.isPresent) {
                CrystalSpikesHolder.set(opt.get())
                HexMob.LOGGER.info("[CrystalSpikes] holder cached from registry at server start")
            } else {
                HexMob.LOGGER.warn(
                    "[CrystalSpikes] holder NOT found at server start (biome registry present: {})",
                    server.registryAccess().registry(Registries.BIOME).isPresent
                )
            }
        }
        HexMob.init()
        initRegistry(HexMobActions)
        HexMobCapabilities.init()
        if(Platform.getEnv().isClient) {
            HexMobEntityRenderers.init()
        }
    }
}
