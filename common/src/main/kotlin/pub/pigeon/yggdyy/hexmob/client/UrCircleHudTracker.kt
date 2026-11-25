package pub.pigeon.yggdyy.hexmob.client

import net.minecraft.client.Minecraft
import pub.pigeon.yggdyy.hexmob.content.ur_circle.CircleState
import pub.pigeon.yggdyy.hexmob.content.ur_circle.subentities.SlateSymbolType
import pub.pigeon.yggdyy.hexmob.networking.msg.MsgUrCircleHudS2C
import pub.pigeon.yggdyy.hexmob.networking.msg.SlateHudEntry
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 客户端大环 HUD 与象征石板缓存，供 BossHealthOverlayMixin、SlateRenderer 和 UrCircleVfxRenderer 共享。
 */
object UrCircleHudTracker {
    data class HudSnapshot(
        val packet: MsgUrCircleHudS2C,
        val lastGameTime: Long,
        val slateMap: Map<Int, SlateHudEntry>,
        var delayedHpRatio: Float = 1.0F,
        var lastRealHpRatio: Float = 1.0F,
    )

    private val byBossUuid = ConcurrentHashMap<UUID, HudSnapshot>()
    private val byEntityId = ConcurrentHashMap<Int, HudSnapshot>()

    fun onPacket(packet: MsgUrCircleHudS2C) {
        val level = Minecraft.getInstance().level
        val now = level?.gameTime ?: 0L
        val existing = byEntityId[packet.entityId]
        val slateMap = HashMap<Int, SlateHudEntry>(packet.slates.size)
        for (entry in packet.slates) {
            slateMap[entry.globalIndex] = entry
        }
        val snap = HudSnapshot(
            packet = packet,
            lastGameTime = now,
            slateMap = slateMap,
            delayedHpRatio = existing?.delayedHpRatio ?: 1.0F,
            lastRealHpRatio = existing?.lastRealHpRatio ?: 1.0F,
        )
        byBossUuid[packet.bossEventUuid] = snap
        byEntityId[packet.entityId] = snap
    }

    fun getByBossEvent(uuid: UUID): HudSnapshot? = byBossUuid[uuid]

    fun getByEntityId(entityId: Int): HudSnapshot? = byEntityId[entityId]

    fun getSlateEntry(entityId: Int, globalIndex: Int): SlateHudEntry? =
        byEntityId[entityId]?.slateMap?.get(globalIndex)

    fun isShortCircuited(entityId: Int): Boolean =
        byEntityId[entityId]?.packet?.circleState == CircleState.SHORT_CIRCUIT.ordinal

    fun tick(client: Minecraft) {
        val level = client.level
        if (level == null) {
            byBossUuid.clear()
            byEntityId.clear()
            return
        }
        val now = level.gameTime
        val it = byEntityId.entries.iterator()
        while (it.hasNext()) {
            val (id, snap) = it.next()
            val entityMissing = level.getEntity(id) == null
            if (now - snap.lastGameTime > 80L && entityMissing) {
                byBossUuid.remove(snap.packet.bossEventUuid)
                it.remove()
            } else {
                // 平滑追赶延迟掉血残影条
                if (snap.delayedHpRatio > snap.lastRealHpRatio) {
                    snap.delayedHpRatio = (snap.delayedHpRatio - 0.008F).coerceAtLeast(snap.lastRealHpRatio)
                } else {
                    snap.delayedHpRatio = snap.lastRealHpRatio
                }
            }
        }
    }

    fun symbolLabelKey(type: SlateSymbolType): String = when (type) {
        SlateSymbolType.AIM -> "hud.hexmob.ur_circle.slate.aim"
        SlateSymbolType.LOCK -> "hud.hexmob.ur_circle.slate.lock"
        SlateSymbolType.AMP -> "hud.hexmob.ur_circle.slate.amp"
        SlateSymbolType.TRIGGER -> "hud.hexmob.ur_circle.slate.trigger"
        SlateSymbolType.NONE -> "hud.hexmob.ur_circle.slate.none"
    }
}
