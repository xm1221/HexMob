package pub.pigeon.yggdyy.hexmob.content.ur_circle

import at.petrak.hexcasting.api.casting.math.HexDir
import at.petrak.hexcasting.api.casting.math.HexPattern
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile
import net.minecraft.world.level.Level
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.EntityHitResult
import pub.pigeon.yggdyy.hexmob.HexMob

/**
 * 石板弹：大环从黄道石板发射的弹体，携带对应石板图案（供渲染绘制）。
 * 无重力、匀速直线飞行；命中实体造成伤害（含击退），命中方块即消散。
 */
class SlateProjectile(type: EntityType<SlateProjectile>, level: Level) : AbstractHurtingProjectile(type, level) {
    init {
        isNoGravity = true
        noCulling = true
    }

    override fun defineSynchedData() {
        super.defineSynchedData()
        entityData.define(PATTERN, HexPattern.fromAngles("", HexDir.EAST).serializeToNBT())
        entityData.define(SCALE, 1.0F)
    }

    var pattern: HexPattern
        get() = HexPattern.fromNBT(entityData.get(PATTERN))
        set(value) = entityData.set(PATTERN, value.serializeToNBT())

    var visualScale: Float
        get() = entityData.get(SCALE)
        set(value) = entityData.set(SCALE, value.coerceIn(0.5F, 4.0F))

    override fun tick() {
        super.tick()
        if (!level().isClientSide && tickCount % 2 == 0) {
            UrCircleHexVfx.spawnConjure(level(), UrCircleHexVfx.MEDIA_PURPLE, x, y, z, 0.0, 0.02, 0.0)
        }
    }

    override fun addAdditionalSaveData(compound: CompoundTag) {
        super.addAdditionalSaveData(compound)
        compound.put(PATTERN_KEY, entityData.get(PATTERN))
        compound.putFloat(SCALE_KEY, visualScale)
    }

    override fun readAdditionalSaveData(compound: CompoundTag) {
        super.readAdditionalSaveData(compound)
        if (compound.contains(PATTERN_KEY)) {
            entityData.set(PATTERN, compound.getCompound(PATTERN_KEY))
        }
        if (compound.contains(SCALE_KEY)) {
            visualScale = compound.getFloat(SCALE_KEY)
        }
    }

    /** 匀速直线，不做惯性衰减（恶魂火球是 0.95，这里保持 1.0）。 */
    override fun getInertia(): Float = 1.0F

    /** 石板不燃烧（父类默认 true 会让弹体每 tick 被点燃，产生火焰外观）。 */
    override fun shouldBurn(): Boolean = false

    override fun canHitEntity(target: Entity): Boolean =
        super.canHitEntity(target) && !(owner is UrCircleEntity && isUrCircleAlly(target))

    override fun onHitEntity(result: EntityHitResult) {
        super.onHitEntity(result)
        if (level().isClientSide) return
        val victim = result.entity
        if (owner is UrCircleEntity && isUrCircleAlly(victim)) return
        val source = if (owner is LivingEntity) level().damageSources().mobAttack(owner as LivingEntity) else level().damageSources().magic()
        if (victim.hurt(source, DAMAGE)) {
            (victim as? LivingEntity)?.knockback(0.5, deltaMovement.x, deltaMovement.z)
        }
        discard()
    }

    override fun onHitBlock(result: BlockHitResult) {
        super.onHitBlock(result)
        if (!level().isClientSide) {
            // 命中处炸出 3×3×3 的坑 + 爆炸音效（不可破坏方块除外）：
            // 不破坏 HexMod 紫水晶/板岩类方块、破坏不掉落、命中把一部分方块转化为紫水晶/板岩
            craterAround(level(), result.blockPos, this, drop = false, skipHexStones = true, convertChance = CONVERT_CHANCE)
            discard()
        }
    }

    companion object {
        const val DAMAGE = 8.0F
        /** 命中方块时把一部分方块转化为紫水晶/板岩的概率（0~1）。 */
        const val CONVERT_CHANCE = 0.4F
        val PATTERN: EntityDataAccessor<CompoundTag> = SynchedEntityData.defineId(SlateProjectile::class.java, EntityDataSerializers.COMPOUND_TAG)
        val SCALE: EntityDataAccessor<Float> = SynchedEntityData.defineId(SlateProjectile::class.java, EntityDataSerializers.FLOAT)
        val PATTERN_KEY: String = HexMob.id("pattern").toString()
        val SCALE_KEY: String = HexMob.id("scale").toString()
    }
}
