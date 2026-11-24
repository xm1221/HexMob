package pub.pigeon.yggdyy.hexmob.content.ur_circle.ur_spell

import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import pub.pigeon.yggdyy.hexmob.content.ur_circle.UrCircleHexVfx

class UrCoreBeamEntity(entityType: EntityType<out Entity>, level: Level) : Entity(entityType, level) {
    init {
        noPhysics = true
        noCulling = true
    }

    override fun defineSynchedData() {
        entityData.define(BEAM_TARGET, Vector3f(0F, 0F, 0F))
        entityData.define(BEAM_RADIUS, 0.85F)
        entityData.define(BEAM_COLOR, UrCircleHexVfx.BEAM_LAVENDER)
    }

    var beamRadius: Float
        get() = entityData.get(BEAM_RADIUS)
        set(value) = entityData.set(BEAM_RADIUS, value.coerceIn(0.08F, 4.0F))

    var beamColor: Int
        get() = entityData.get(BEAM_COLOR)
        set(value) = entityData.set(BEAM_COLOR, value)

    fun setBeamTarget(pos: Vec3) {
        entityData.set(BEAM_TARGET, Vector3f(pos.x.toFloat(), pos.y.toFloat(), pos.z.toFloat()))
    }

    fun beamTarget(): Vec3? {
        val v = entityData.get(BEAM_TARGET)
        return if (v.x == 0F && v.y == 0F && v.z == 0F) null
        else Vec3(v.x.toDouble(), v.y.toDouble(), v.z.toDouble())
    }

    override fun shouldBeSaved(): Boolean = false
    override fun addAdditionalSaveData(compound: CompoundTag) {}
    override fun readAdditionalSaveData(compound: CompoundTag) {}
    override fun getAddEntityPacket(): Packet<ClientGamePacketListener> = ClientboundAddEntityPacket(this)

    companion object {
        private val BEAM_TARGET: EntityDataAccessor<Vector3f> =
            SynchedEntityData.defineId(UrCoreBeamEntity::class.java, EntityDataSerializers.VECTOR3)
        private val BEAM_RADIUS: EntityDataAccessor<Float> =
            SynchedEntityData.defineId(UrCoreBeamEntity::class.java, EntityDataSerializers.FLOAT)
        private val BEAM_COLOR: EntityDataAccessor<Int> =
            SynchedEntityData.defineId(UrCoreBeamEntity::class.java, EntityDataSerializers.INT)
    }
}
