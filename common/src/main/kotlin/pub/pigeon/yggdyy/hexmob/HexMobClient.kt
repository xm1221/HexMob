package pub.pigeon.yggdyy.hexmob

import dev.architectury.event.events.client.ClientTickEvent
import me.shedaniel.autoconfig.AutoConfig
import net.minecraft.client.gui.screens.Screen
import pub.pigeon.yggdyy.hexmob.client.HexMobGaslightingTracker
import pub.pigeon.yggdyy.hexmob.client.UrCircleHudTracker
import pub.pigeon.yggdyy.hexmob.client.UrCircleSkyTracker
import pub.pigeon.yggdyy.hexmob.config.HexMobClientConfig
import pub.pigeon.yggdyy.hexmob.content.ur_circle.UrCircleCarryAlong
import pub.pigeon.yggdyy.hexmob.registry.HexMobBlockEntityRenderers
import pub.pigeon.yggdyy.hexmob.registry.HexMobItemProperties

object HexMobClient {
    fun init() {
        if(HexMob.LOGGER.isDebugEnabled) HexMob.LOGGER.warn("Client Init")
        HexMobClientConfig.init()
        HexMobItemProperties.init()
        HexMobBlockEntityRenderers.init()
        ClientTickEvent.CLIENT_POST.register { client ->
            UrCircleSkyTracker.tick(client)
            UrCircleHudTracker.tick(client)
            if (!client.isPaused) {
                UrCircleCarryAlong.applyClientCarryAlong(client)
                HexMobGaslightingTracker.postFrameCheckRendered()
            }
        }
    }
    fun getConfigScreen(parent: Screen): Screen {
        return AutoConfig.getConfigScreen(HexMobClientConfig.GlobalConfig::class.java, parent).get()
    }
}
