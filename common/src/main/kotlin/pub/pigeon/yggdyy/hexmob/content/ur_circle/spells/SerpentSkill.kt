package pub.pigeon.yggdyy.hexmob.content.ur_circle.spells

import at.petrak.hexcasting.common.lib.HexBlocks
import net.minecraft.core.particles.BlockParticleOption
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.phys.Vec3
import pub.pigeon.yggdyy.hexmob.content.ur_circle.UrCircleEntity
import pub.pigeon.yggdyy.hexmob.content.ur_circle.UrCircleHexVfx
import pub.pigeon.yggdyy.hexmob.content.ur_circle.serpent.UrCircleSerpent
import pub.pigeon.yggdyy.hexmob.content.ur_circle.subentities.SlateSymbolType
import pub.pigeon.yggdyy.hexmob.registry.HexMobEntities
import kotlin.math.cos
import kotlin.math.sin

class SerpentSkill : UrCircleSkill("serpent", 80) {
    override val weight: Int = 3
    override val waveColor: Int = UrCircleHexVfx.MEDIA_PURPLE
    override fun channelMarksTarget(circle: UrCircleEntity): Boolean = true

    override fun canUse(circle: UrCircleEntity): Boolean {
        val t = circle.target
        return t != null && t.isAlive
    }

    override fun channelParticleType(circle: UrCircleEntity) =
        BlockParticleOption(ParticleTypes.BLOCK, HexBlocks.SLATE.defaultBlockState())
    override fun channelParticlesPerPulse(circle: UrCircleEntity): Int = 16

    override fun onChannelTick(circle: UrCircleEntity) {
        val t = circle.target ?: return
        val ang = circle.tickCount * 0.14
        val anchor = Vec3(t.x + cos(ang) * 4.8, t.y + 2.4, t.z + sin(ang) * 4.8)
        val lock = circle.findSymbolicSlate(SlateSymbolType.LOCK)
            ?: circle.deploySymbolicSlate(SlateSymbolType.LOCK, anchor)
        if (lock != null && !lock.detached && !lock.rebounded) {
            lock.deployedPos = anchor
        }
    }

    override fun onChannelHurt(circle: UrCircleEntity, source: DamageSource, amount: Float): Boolean {
        if (amount > 0.0F) {
            circle.applyChannelDamage(source, amount)
        }
        return true
    }

    override fun cast(circle: UrCircleEntity) {
        val level = circle.level()
        if (level.isClientSide) return
        val origin = circle.beamOrigin()
        val t = circle.target
        val lock = circle.findSymbolicSlate(SlateSymbolType.LOCK)
        val focusPoint = when {
            lock != null && (lock.detached || lock.rebounded) && lock.posNow != Vec3.ZERO -> lock.posNow
            t != null && t.isAlive -> t.position().add(0.0, t.bbHeight / 2.0, 0.0)
            else -> origin.add(0.0, 0.0, 8.0)
        }
        val base = focusPoint.subtract(origin).let { if (it.lengthSqr() > 1.0E-4) it.normalize() else Vec3(0.0, 0.0, 1.0) }
        val serpentScale = if (circle.phase >= 2) 1.5F else 1.0F
        for (i in 0 until SERPENT_COUNT) {
            val serpent = UrCircleSerpent(HexMobEntities.UR_CIRCLE_SERPENT.get(), level)
            serpent.owner = circle
            serpent.visualScale = serpentScale
            val ang = circle.random.nextDouble() * Math.PI * 2.0
            serpent.setPos(origin.x + cos(ang) * 0.6, origin.y, origin.z + sin(ang) * 0.6)
            val spread = (i - (SERPENT_COUNT - 1) / 2.0) * FAN_ANGLE
            val aim = rotateY(base, spread)
            val jitter = 0.05
            serpent.setAim(
                aim.x + (circle.random.nextDouble() - 0.5) * jitter,
                aim.y + (circle.random.nextDouble() - 0.5) * jitter,
                aim.z + (circle.random.nextDouble() - 0.5) * jitter
            )
            level.addFreshEntity(serpent)
            UrCircleHexVfx.spawnRingArc(level, origin, origin.add(aim.scale(4.0)), UrCircleHexVfx.MEDIA_PURPLE, steps = 6)
        }
    }

    private fun rotateY(v: Vec3, angle: Double): Vec3 {
        val c = cos(angle)
        val s = sin(angle)
        return Vec3(v.x * c + v.z * s, v.y, -v.x * s + v.z * c)
    }

    companion object {
        const val SERPENT_COUNT = 3
        const val FAN_ANGLE = 0.18
    }
}
