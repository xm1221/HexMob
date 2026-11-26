package pub.pigeon.yggdyy.hexmob.content.ur_circle

import at.petrak.hexcasting.common.lib.HexSounds
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import pub.pigeon.yggdyy.hexmob.util.rotateDA

/**
 * 接地促动石锚点：
 * 大环吟唱时在战场低空或外环甲板上降下的导通节点，可被玩家近战、弓箭或任意法术直接击破；
 * 击碎锚点即可触发高空大环法术环断路坠落（SHORT_CIRCUIT）。
 */
class UrCircleAnchorEntity(entityType: EntityType<out Entity>, level: Level) : Entity(entityType, level) {
    var owner: UrCircleEntity? = null
    var lifeTicks: Int = MAX_LIFETIME
    var mountedRingIndex: Int = -1
    var mountedAngleOffsetDeg: Float = 0.0F
    var mountedYOffset: Double = 2.8

    init {
        noPhysics = true
        noCulling = true
    }

    override fun defineSynchedData() {
        entityData.define(HP, DEFAULT_HP)
        entityData.define(MAX_HP, DEFAULT_HP)
        entityData.define(TETHER_TARGET, Vector3f(0F, 0F, 0F))
        entityData.define(COLOR, UrCircleHexVfx.WAVE_GOLD)
    }

    var hp: Float
        get() = entityData.get(HP)
        set(value) = entityData.set(HP, value.coerceAtLeast(0.0F))

    var maxHp: Float
        get() = entityData.get(MAX_HP)
        set(value) = entityData.set(MAX_HP, value.coerceAtLeast(1.0F))

    var color: Int
        get() = entityData.get(COLOR)
        set(value) = entityData.set(COLOR, value)

    fun setTetherTarget(pos: Vec3) {
        entityData.set(TETHER_TARGET, Vector3f(pos.x.toFloat(), pos.y.toFloat(), pos.z.toFloat()))
    }

    fun tetherTarget(): Vec3? {
        val v = entityData.get(TETHER_TARGET)
        return if (v.x == 0F && v.y == 0F && v.z == 0F) null
        else Vec3(v.x.toDouble(), v.y.toDouble(), v.z.toDouble())
    }

    override fun isPickable(): Boolean = true

    override fun hurt(source: DamageSource, amount: Float): Boolean {
        if (level().isClientSide || !isAlive) return false
        if (isUrCircleAlly(source.entity) || isUrCircleAlly(source.directEntity)) return false
        if (amount <= 0.0F) return false

        val next = hp - amount
        hp = next
        UrCircleHexVfx.playRingSound(level(), position(), 24.0, SoundEvents.AMETHYST_BLOCK_HIT, 1.4F, 1.2F)
        for (k in 0 until 8) {
            UrCircleHexVfx.spawnConjure(
                level(),
                color,
                x + (random.nextDouble() - 0.5) * 1.2,
                y + 1.0 + (random.nextDouble() - 0.5) * 1.2,
                z + (random.nextDouble() - 0.5) * 1.2,
                (random.nextDouble() - 0.5) * 0.2,
                0.1,
                (random.nextDouble() - 0.5) * 0.2,
            )
        }
        if (next <= 0.0F) {
            for (k in 0 until 20) {
                UrCircleHexVfx.spawnConjure(
                    level(),
                    UrCircleHexVfx.SHORT_CIRCUIT_RED,
                    x, y + 1.0, z,
                    (random.nextDouble() - 0.5) * 0.4,
                    (random.nextDouble() - 0.5) * 0.4,
                    (random.nextDouble() - 0.5) * 0.4,
                )
            }
            UrCircleHexVfx.playRingSound(level(), position(), 36.0, HexSounds.SPELL_CIRCLE_FAIL, 2.0F, 1.1F)
            owner?.onAnchorBroken(this)
            discard()
        }
        return true
    }

    override fun tick() {
        super.tick()
        if (level().isClientSide) return
        val o = owner
        if (o == null || !o.isAlive || o.circleState != CircleState.CHANNELING) {
            discard()
            return
        }
        val origin = o.beamOrigin()
        setTetherTarget(origin)
        if (mountedRingIndex >= 0) {
            val track = o.rings.getOrNull(mountedRingIndex)
            if (track != null && track.isVisible(o.phase, o.phase2Reveal, o.phase3Reveal)) {
                val effN = track.effectiveNormal(o.foldProgress, o.dormantFold)
                val radAxis = track.radialAxis(effN)
                val rLen = track.effectiveRadius(o.totalScale(), o.dormantFold, o.phase2Reveal, o.phase3Reveal)
                val worldPos = origin.add(
                    radAxis.rotateDA(o.ringRotation(mountedRingIndex) + mountedAngleOffsetDeg, effN).scale(rLen)
                ).add(0.0, mountedYOffset, 0.0)
                setPos(worldPos.x, worldPos.y, worldPos.z)
                setDeltaMovement(Vec3.ZERO)
            }
        }
        if (tickCount % 4 == 0) {
            UrCircleHexVfx.spawnConjure(level(), color, x, y + 1.4, z, 0.0, 0.14, 0.0)
        }
        lifeTicks--
        if (lifeTicks <= 0) {
            discard()
        }
    }

    override fun shouldBeSaved(): Boolean = false
    override fun addAdditionalSaveData(compound: CompoundTag) {
        compound.putInt("hexmob:MountedRing", mountedRingIndex)
        compound.putFloat("hexmob:MountedAngleDeg", mountedAngleOffsetDeg)
        compound.putDouble("hexmob:MountedYOffset", mountedYOffset)
    }

    override fun readAdditionalSaveData(compound: CompoundTag) {
        if (compound.contains("hexmob:MountedRing")) {
            mountedRingIndex = compound.getInt("hexmob:MountedRing")
        }
        if (compound.contains("hexmob:MountedAngleDeg")) {
            mountedAngleOffsetDeg = compound.getFloat("hexmob:MountedAngleDeg")
        }
        if (compound.contains("hexmob:MountedYOffset")) {
            mountedYOffset = compound.getDouble("hexmob:MountedYOffset")
        }
    }
    override fun getAddEntityPacket(): Packet<ClientGamePacketListener> = ClientboundAddEntityPacket(this)

    companion object {
        const val DEFAULT_HP = 30.0F
        const val MAX_LIFETIME = 160

        private val HP: EntityDataAccessor<Float> =
            SynchedEntityData.defineId(UrCircleAnchorEntity::class.java, EntityDataSerializers.FLOAT)
        private val MAX_HP: EntityDataAccessor<Float> =
            SynchedEntityData.defineId(UrCircleAnchorEntity::class.java, EntityDataSerializers.FLOAT)
        private val TETHER_TARGET: EntityDataAccessor<Vector3f> =
            SynchedEntityData.defineId(UrCircleAnchorEntity::class.java, EntityDataSerializers.VECTOR3)
        private val COLOR: EntityDataAccessor<Int> =
            SynchedEntityData.defineId(UrCircleAnchorEntity::class.java, EntityDataSerializers.INT)
    }
}
