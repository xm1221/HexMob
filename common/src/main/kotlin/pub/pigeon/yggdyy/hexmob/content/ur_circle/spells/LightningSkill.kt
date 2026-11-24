package pub.pigeon.yggdyy.hexmob.content.ur_circle.spells

import net.minecraft.core.particles.ParticleTypes
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level
import pub.pigeon.yggdyy.hexmob.content.ur_circle.UrCircleEntity
import pub.pigeon.yggdyy.hexmob.content.ur_circle.UrCircleHexVfx
import pub.pigeon.yggdyy.hexmob.util.spawnParticle
import kotlin.math.cos
import kotlin.math.sin

class LightningSkill : UrCircleSkill("lightning", 60) {

    override val weight: Int = 3
    override val waveColor: Int = UrCircleHexVfx.WAVE_GOLD
    override val channelInterruptible: Boolean = true
    override fun channelMarksTarget(circle: UrCircleEntity): Boolean = true

    override fun canUse(circle: UrCircleEntity): Boolean {
        if (circle.level().isClientSide) return false
        return circle.currentHated().isNotEmpty() && circle.health / circle.maxHealth <= 0.5
    }

    override fun channelParticleType(circle: UrCircleEntity) = ParticleTypes.ELECTRIC_SPARK
    override fun channelParticlesPerPulse(circle: UrCircleEntity): Int = 16
    override fun channelPulseSound(circle: UrCircleEntity) = SoundEvents.LIGHTNING_BOLT_THUNDER
    override fun channelPulseInterval(circle: UrCircleEntity): Int = 8

    override fun onChannelTick(circle: UrCircleEntity) {
        val level = circle.level()
        for (t in circle.currentHated().take(MAX_TARGETS)) {
            for (k in 0 until 2) {
                spawnParticle(
                    level, ParticleTypes.ELECTRIC_SPARK,
                    t.x + (circle.random.nextDouble() - 0.5) * 1.6,
                    t.y + circle.random.nextDouble() * t.bbHeight,
                    t.z + (circle.random.nextDouble() - 0.5) * 1.6,
                    (circle.random.nextDouble() - 0.5) * 0.2, 0.15, (circle.random.nextDouble() - 0.5) * 0.2,
                    UrCircleHexVfx.DEFAULT_RANGE,
                )
                UrCircleHexVfx.spawnConjure(
                    level,
                    UrCircleHexVfx.WAVE_GOLD,
                    t.x + (circle.random.nextDouble() - 0.5) * 1.6,
                    t.y + circle.random.nextDouble() * t.bbHeight,
                    t.z + (circle.random.nextDouble() - 0.5) * 1.6,
                    0.0, 0.1, 0.0,
                )
            }
        }
    }

    override fun onChannelHurt(circle: UrCircleEntity, source: DamageSource, amount: Float): Boolean {
        val dmg = amount * LIGHTNING_DAMAGE_MULTIPLIER
        if (dmg > 0.0F) {
            circle.applyChannelDamage(source, dmg)
        }
        if (amount >= INTERRUPT_THRESHOLD) {
            circle.interruptChannel()
        }
        return true
    }

    override fun cast(circle: UrCircleEntity) {
        val level = circle.level()
        if (level.isClientSide) return
        for (t in circle.currentHated().take(MAX_TARGETS)) {
            spawnBolt(level, t.x, t.y, t.z)
        }
        val center = circle.position().add(0.0, 1.0, 0.0)
        val radius = (RING_BOLT_RADIUS * circle.totalScale()).coerceAtMost(circle.ringOuterRadius() * 0.95)
        for (k in 0 until RING_BOLTS) {
            val ang = Math.PI * 2.0 * k / RING_BOLTS
            spawnBolt(level, center.x + cos(ang) * radius, center.y, center.z + sin(ang) * radius)
        }
    }

    private fun spawnBolt(level: Level, x: Double, y: Double, z: Double) {
        val bolt = EntityType.LIGHTNING_BOLT.create(level) ?: return
        bolt.moveTo(x, y, z, 0.0F, 0.0F)
        level.addFreshEntity(bolt)
    }

    companion object {
        const val MAX_TARGETS = 12
        const val RING_BOLTS = 10
        const val RING_BOLT_RADIUS = 8.0
        const val INTERRUPT_THRESHOLD = 30.0F
        const val LIGHTNING_DAMAGE_MULTIPLIER = 0.8F
    }
}
