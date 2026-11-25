package pub.pigeon.yggdyy.hexmob.networking.handler

import dev.architectury.networking.NetworkManager.PacketContext
import pub.pigeon.yggdyy.hexmob.client.UrCircleHudTracker
import pub.pigeon.yggdyy.hexmob.config.HexMobServerConfig
import pub.pigeon.yggdyy.hexmob.networking.msg.*

fun HexMobMessageS2C.applyOnClient(ctx: PacketContext) = ctx.queue {
    when (this) {
        is MsgSyncConfigS2C -> {
            HexMobServerConfig.onSyncConfig(serverConfig)
        }
        is MsgUrCircleHudS2C -> {
            UrCircleHudTracker.onPacket(this)
        }
    }
}
