package pub.pigeon.yggdyy.hexmob.content.quench_allay

import at.petrak.hexcasting.api.utils.downcast
import at.petrak.hexcasting.api.utils.serializeToNBT
import at.petrak.hexcasting.api.utils.vecFromNBT
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.LongArrayTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.animal.allay.Allay
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import pub.pigeon.yggdyy.hexmob.HexMob
import pub.pigeon.yggdyy.hexmob.api.entity.CastingEntity
import pub.pigeon.yggdyy.hexmob.api.entity.IotaEntity
import pub.pigeon.yggdyy.hexmob.api.entity.defineIotaAccessor
import pub.pigeon.yggdyy.hexmob.api.entity.emptyIotaTag
import pub.pigeon.yggdyy.hexmob.api.entity.extractMediaFromHands

/**
 * A quenched allay (淬晶悦灵): an allay that doubles as an iota carrier and
 * caster, and can be given a movement target as a stored vector.
 *
 * Note: this is an ALLAY, not a sheep — it does not use the sheep's behaviour
 * registry, wool colouring, or spawn rules.
 */
class QuenchAllay(entityType: EntityType<out QuenchAllay>, level: Level) : Allay(entityType, level), CastingEntity,
    IotaEntity {

    companion object {
        private val IOTA = defineIotaAccessor(QuenchAllay::class.java)
        private val TARGET = defineIotaAccessor(QuenchAllay::class.java)
        private val IOTA_KEY: String = HexMob.id("iota").toString()
        private val TARGET_KEY: String = HexMob.id("target").toString()
    }

    override fun consumeMedia(cost: Long, simulate: Boolean): Long =
        this.extractMediaFromHands(cost, simulate)

    override fun getCastingRange(): Double = 32.0

    // 数据放 entityData：自动同步到客户端
    override fun defineSynchedData() {
        super.defineSynchedData()
        this.entityData.define(IOTA, emptyIotaTag())
        this.entityData.define(TARGET, emptyIotaTag())
    }

    // 存盘/读盘：iota 槽与 target 槽分开持久化
    override fun readAdditionalSaveData(compound: CompoundTag) {
        super.readAdditionalSaveData(compound)
        if (compound.contains(IOTA_KEY)) {
            setIotaNbt(compound.getCompound(IOTA_KEY))
        }
        if (compound.contains(TARGET_KEY)) {
            setAllayTarget(compound.getCompound(TARGET_KEY))
        }
    }

    override fun addAdditionalSaveData(compound: CompoundTag) {
        super.addAdditionalSaveData(compound)
        compound.put(IOTA_KEY, getIotaNbt())
        compound.put(TARGET_KEY, getAllayTargetTag())
    }

    override fun getIotaNbt(): CompoundTag = this.entityData.get(IOTA)

    override fun setIotaNbt(nbt: CompoundTag) {
        this.entityData.set(IOTA, nbt)
    }

    override fun getServerLevel(): ServerLevel? = level() as? ServerLevel

    fun getAllayTargetTag(): CompoundTag = this.entityData.get(TARGET)

    fun setAllayTarget(nbt: CompoundTag) {
        this.entityData.set(TARGET, nbt)
    }

    fun setAllayTarget(vec: Vec3) {
        this.setAllayTarget(vec.serializeToNBT())
    }

    fun getAllayTarget(): Vec3 {
        val tag = this.getAllayTargetTag()
        return if (tag.type == LongArrayTag.TYPE) {
            val lat = tag.downcast(LongArrayTag.TYPE)
            vecFromNBT(lat.asLongArray)
        } else {
            vecFromNBT(tag)
        }
    }

    /** 清空移动目标（备用方法）。 */
    fun clearAllayTarget() {
        this.entityData.set(TARGET, emptyIotaTag())
    }

    /** 默认为空 target（emptyIotaTag）；只有确实设置了向量目标时才与之不同。 */
    private fun hasTarget(): Boolean = this.getAllayTargetTag() != emptyIotaTag()

    override fun tick() {
        super.tick()
        if (this.tickCount % 7 == 0 && hasTarget()) {
            val vec = this.getAllayTarget()
            this.navigation.moveTo(vec.x, vec.y, vec.z, 1.0)
        }
    }
}
