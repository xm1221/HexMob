package pub.pigeon.yggdyy.hexmob.api.entity

import at.petrak.hexcasting.api.casting.iota.Iota
import at.petrak.hexcasting.api.casting.iota.IotaType
import at.petrak.hexcasting.api.casting.iota.NullIota
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level
import pub.pigeon.yggdyy.hexmob.HexMob

class IotaEntity(entityType: EntityType<*>, level: Level) : Entity(entityType, level) {

    companion object {
        private val IOTA: EntityDataAccessor<CompoundTag> =
            SynchedEntityData.defineId(IotaEntity::class.java, EntityDataSerializers.COMPOUND_TAG)
        private val IOTA_KEY: String = HexMob.id("iota").toString()
    }

    // 数据放 entityData：自动同步到客户端，客户端据此显示
    override fun defineSynchedData() {
        this.entityData.define(IOTA, IotaType.serialize(NullIota()))
    }

    // 存盘/读盘：真正持久化到实体 NBT
    override fun readAdditionalSaveData(compound: CompoundTag) {
        readAdditionalSaveData(compound)
        if (compound.contains(IOTA_KEY)) {
            setIotaNbt(compound.getCompound(IOTA_KEY))
        }
    }

    override fun addAdditionalSaveData(compound: CompoundTag) {
        addAdditionalSaveData(compound)
        compound.put(IOTA_KEY, getIotaNbt())
    }

    fun writeIota(iota: Iota) {
        setIotaNbt(IotaType.serialize(iota))
    }

    /** 读当前存储的 iota；需服务端，客户端返回 NullIota 兜底 */
    fun readIota(): Iota {
        val world = level()
        return if (world is ServerLevel) {
            IotaType.deserialize(getIotaNbt(), world)
        } else {
            NullIota()
        }
    }

    private fun getIotaNbt(): CompoundTag = this.entityData.get(IOTA)
    private fun setIotaNbt(nbt: CompoundTag) {
        this.entityData.set(IOTA, nbt)
    }
}
