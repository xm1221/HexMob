package pub.pigeon.yggdyy.hexmob.content.ur_circle

import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityDimensions
import net.minecraft.world.entity.Pose
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import pub.pigeon.yggdyy.hexmob.content.HMEntityPart
import kotlin.math.abs

abstract class UrCirclePart(
    val parentMob: UrCircleEntity,
    val id: ResourceLocation,
    val baseWidth: Float,
    val baseHeight: Float,
) : HMEntityPart(parentMob.type, parentMob.level()) {
    var size: EntityDimensions = EntityDimensions.scalable(baseWidth, baseHeight)
    var appliedScale: Float = 1.0F
    var active: Boolean = true
    var globalIndex: Int = -1
    var ringIndex: Int = -1

    var posNow: Vec3 = Vec3.ZERO
    var posPrev: Vec3 = Vec3.ZERO
    var dirNow = Vec3(0.0, 0.0, 1.0)
    var dirPrev = Vec3(0.0, 0.0, 1.0)

    init {
        this.refreshDimensions()
    }

    fun applyScale(scale: Float) {
        val s = scale.coerceIn(0.25F, 12.0F)
        if (abs(s - appliedScale) >= 0.02F) {
            appliedScale = s
            size = EntityDimensions.scalable(baseWidth * s, baseHeight * s)
            this.refreshDimensions()
        }
    }

    override fun defineSynchedData() {}
    override fun readAdditionalSaveData(compound: CompoundTag) {}
    override fun addAdditionalSaveData(compound: CompoundTag) {}
    override fun isPickable(): Boolean = active
    override fun getPickResult(): ItemStack? = parentMob.pickResult
    override fun hurt(source: DamageSource, amount: Float): Boolean {
        if (!active || isInvulnerableTo(source)) return false
        return parentMob.hurtPart(this, source, amount)
    }
    override fun `is`(entity: Entity): Boolean = this === entity || parentMob === entity
    override fun getDimensions(pose: Pose): EntityDimensions = size
    override fun shouldBeSaved(): Boolean = false
    override fun getAddEntityPacket(): Packet<ClientGamePacketListener> {
        throw UnsupportedOperationException()
    }
    override fun shouldInteract(): Boolean = active
    override fun shouldRenderAsPart(): Boolean = active

    fun changeState(pos: Vec3, dir: Vec3) {
        xOld = x
        yOld = y
        zOld = z
        setPos(pos.add(0.0, -bbHeight / 2.0, 0.0))
        posPrev = posNow
        posNow = pos
        dirPrev = dirNow
        dirNow = dir
    }
}