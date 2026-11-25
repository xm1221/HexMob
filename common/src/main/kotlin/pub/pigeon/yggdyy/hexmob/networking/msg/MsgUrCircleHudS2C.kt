package pub.pigeon.yggdyy.hexmob.networking.msg

import net.minecraft.network.FriendlyByteBuf
import java.util.UUID

/**
 * 单块激活象征石板的精简同步快照，同时驱动客户端自定义 HUD 石板槽与 3D 可篡改光效连线。
 */
data class SlateHudEntry(
    val globalIndex: Int,
    val symbolType: Int,
    val tamperable: Boolean,
    val rebounded: Boolean,
    val progress: Float,
    val hasCustomPos: Boolean = false,
    val posX: Double = 0.0,
    val posY: Double = 0.0,
    val posZ: Double = 0.0,
)

/**
 * 大环自定义 HUD 与象征石板状态同步包（服务端状态变化或定时节流发送）。
 */
data class MsgUrCircleHudS2C(
    val entityId: Int,
    val bossEventUuid: UUID,
    val phase: Int,
    val circleState: Int,
    val stability: Int,
    val shieldPct: Int,
    val slates: List<SlateHudEntry>,
) : HexMobMessageS2C {

    companion object : HexMobMessageCompanion<MsgUrCircleHudS2C> {
        override val type = MsgUrCircleHudS2C::class.java

        override fun decode(buf: FriendlyByteBuf): MsgUrCircleHudS2C {
            val entityId = buf.readVarInt()
            val bossUuid = buf.readUUID()
            val phase = buf.readVarInt()
            val circleState = buf.readVarInt()
            val stability = buf.readVarInt()
            val shieldPct = buf.readVarInt()
            val count = buf.readVarInt().coerceIn(0, 16)
            val list = ArrayList<SlateHudEntry>(count)
            for (i in 0 until count) {
                val globalIndex = buf.readVarInt()
                val symbolType = buf.readByte().toInt()
                val tamperable = buf.readBoolean()
                val rebounded = buf.readBoolean()
                val progress = buf.readFloat()
                val hasCustomPos = buf.readBoolean()
                val posX = if (hasCustomPos) buf.readDouble() else 0.0
                val posY = if (hasCustomPos) buf.readDouble() else 0.0
                val posZ = if (hasCustomPos) buf.readDouble() else 0.0
                list.add(
                    SlateHudEntry(
                        globalIndex = globalIndex,
                        symbolType = symbolType,
                        tamperable = tamperable,
                        rebounded = rebounded,
                        progress = progress,
                        hasCustomPos = hasCustomPos,
                        posX = posX,
                        posY = posY,
                        posZ = posZ,
                    )
                )
            }
            return MsgUrCircleHudS2C(entityId, bossUuid, phase, circleState, stability, shieldPct, list)
        }

        override fun MsgUrCircleHudS2C.encode(buf: FriendlyByteBuf) {
            buf.writeVarInt(entityId)
            buf.writeUUID(bossEventUuid)
            buf.writeVarInt(phase)
            buf.writeVarInt(circleState)
            buf.writeVarInt(stability)
            buf.writeVarInt(shieldPct)
            val clamped = slates.take(16)
            buf.writeVarInt(clamped.size)
            for (entry in clamped) {
                buf.writeVarInt(entry.globalIndex)
                buf.writeByte(entry.symbolType)
                buf.writeBoolean(entry.tamperable)
                buf.writeBoolean(entry.rebounded)
                buf.writeFloat(entry.progress)
                buf.writeBoolean(entry.hasCustomPos)
                if (entry.hasCustomPos) {
                    buf.writeDouble(entry.posX)
                    buf.writeDouble(entry.posY)
                    buf.writeDouble(entry.posZ)
                }
            }
        }
    }
}
