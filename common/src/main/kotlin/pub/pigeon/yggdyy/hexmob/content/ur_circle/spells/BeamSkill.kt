package pub.pigeon.yggdyy.hexmob.content.ur_circle.spells

import at.petrak.hexcasting.common.lib.HexSounds
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.sounds.SoundEvent
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.monster.Enemy
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import pub.pigeon.yggdyy.hexmob.content.ur_circle.UrCircleEntity
import pub.pigeon.yggdyy.hexmob.content.ur_circle.UrCircleHexVfx
import pub.pigeon.yggdyy.hexmob.content.ur_circle.isUrCircleAlly
import pub.pigeon.yggdyy.hexmob.util.spawnParticle

/**
 * 【恒速扫射光炮】核心光线重做版：
 * - 吟唱阶段先放出红色细预警射线，明确标出光路；
 * - 释放后转为高能粗光束，但落点仅以恒定最大角/线速度追踪目标，玩家靠疾跑、掩体或位移法术均可躲避。
 */
class BeamSkill : UrCircleSkill("core_beam", CHANNEL_TICKS) {
    override val weight: Int = 4
    override val waveColor: Int = UrCircleHexVfx.BEAM_LAVENDER
    override val channelInterruptible: Boolean = true
    override fun channelMarksTarget(circle: UrCircleEntity): Boolean = true

    override fun canUse(circle: UrCircleEntity): Boolean {
        val t = circle.target
        return t != null && t.isAlive && circle.health / circle.maxHealth <= 0.85F
    }

    override fun releaseSound(circle: UrCircleEntity): SoundEvent = HexSounds.CAST_HERMES
    override fun channelParticlesPerPulse(circle: UrCircleEntity): Int = 20

    private var sweepPoint: Vec3 = Vec3.ZERO

    override fun onChannelTick(circle: UrCircleEntity) {
        circle.addEffect(MobEffectInstance(MobEffects.GLOWING, 12, 0, false, false))
        val origin = circle.beamOrigin()
        val t = circle.target
        val desired = if (t != null && t.isAlive) {
            t.position().add(0.0, t.bbHeight * 0.45, 0.0)
        } else {
            origin.add(circle.lookAngle.scale(16.0))
        }
        if (circle.stateTicks <= 1 || sweepPoint == Vec3.ZERO) {
            sweepPoint = desired
        } else {
            val delta = desired.subtract(sweepPoint)
            val dist = delta.length()
            if (dist > 0.01) {
                sweepPoint = sweepPoint.add(delta.scale(minOf(dist, 0.55) / dist))
            }
        }
        circle.syncCircleBeam(sweepPoint, WARN_RADIUS, UrCircleHexVfx.SHORT_CIRCUIT_RED)
    }

    override fun onChannelHurt(circle: UrCircleEntity, source: DamageSource, amount: Float): Boolean {
        val dmg = amount * BEAM_DAMAGE_MULTIPLIER
        if (dmg > 0.0F) {
            circle.applyChannelDamage(source, dmg)
        }
        if (amount >= INTERRUPT_THRESHOLD) {
            circle.interruptChannel()
        }
        return true
    }

    override fun beamTicks(circle: UrCircleEntity): Int = BEAM_DURATION

    override fun cast(circle: UrCircleEntity) {
        circle.beginBeam(this)
    }

    override fun onBeamTick(circle: UrCircleEntity) {
        val level = circle.level()
        val origin = circle.beamOrigin()
        val t = circle.target
        val desired = if (t != null && t.isAlive) {
            t.position().add(0.0, t.bbHeight * 0.45, 0.0)
        } else {
            sweepPoint
        }
        if (sweepPoint == Vec3.ZERO) sweepPoint = desired
        val delta = desired.subtract(sweepPoint)
        val dist = delta.length()
        if (dist > 0.01) {
            val step = minOf(dist, SWEEP_MAX_STEP)
            sweepPoint = sweepPoint.add(delta.scale(step / dist))
        }

        val ray = sweepPoint.subtract(origin)
        val rayLen = ray.length()
        val end = if (rayLen > 0.05) sweepPoint.add(ray.normalize().scale(BEAM_EXTEND)) else sweepPoint
        circle.syncCircleBeam(end, ACTIVE_RADIUS, UrCircleHexVfx.BEAM_LAVENDER)

        if (circle.stateTicks % 2 == 0) {
            UrCircleHexVfx.spawnConjure(level, UrCircleHexVfx.WAVE_GOLD, sweepPoint.x, sweepPoint.y, sweepPoint.z, 0.0, 0.12, 0.0)
            spawnParticle(level, ParticleTypes.ELECTRIC_SPARK, sweepPoint.x, sweepPoint.y, sweepPoint.z, 0.0, 0.16, 0.0, UrCircleHexVfx.DEFAULT_RANGE)
        }

        if (circle.stateTicks % BEAM_DAMAGE_INTERVAL == 0) {
            val minX = minOf(origin.x, end.x) - BEAM_AOE_RADIUS
            val minY = minOf(origin.y, end.y) - BEAM_AOE_RADIUS
            val minZ = minOf(origin.z, end.z) - BEAM_AOE_RADIUS
            val maxX = maxOf(origin.x, end.x) + BEAM_AOE_RADIUS
            val maxY = maxOf(origin.y, end.y) + BEAM_AOE_RADIUS
            val maxZ = maxOf(origin.z, end.z) + BEAM_AOE_RADIUS
            val queryBox = AABB(minX, minY, minZ, maxX, maxY, maxZ)

            for (victim in level.getEntitiesOfClass(LivingEntity::class.java, queryBox)) {
                if (victim === circle || victim is Enemy || isUrCircleAlly(victim)) continue
                val vc = victim.position().add(0.0, victim.bbHeight * 0.5, 0.0)
                val nearImpact = vc.distanceToSqr(sweepPoint) <= BEAM_AOE_RADIUS * BEAM_AOE_RADIUS
                val nearBeam = distSqPointToSegment(vc, origin, end) <= BEAM_TUBE_HIT_RADIUS * BEAM_TUBE_HIT_RADIUS
                if (nearImpact || nearBeam) {
                    victim.hurt(level.damageSources().mobAttack(circle), beamDamageFor(victim))
                }
            }
        }
    }

    override fun onBeamEnd(circle: UrCircleEntity) {
        sweepPoint = Vec3.ZERO
    }

    private fun distSqPointToSegment(p: Vec3, a: Vec3, b: Vec3): Double {
        val ab = b.subtract(a)
        val lenSq = ab.lengthSqr()
        if (lenSq < 1.0E-6) return p.distanceToSqr(a)
        val t = (p.subtract(a).dot(ab) / lenSq).coerceIn(0.0, 1.0)
        return p.distanceToSqr(a.add(ab.scale(t)))
    }

    private fun beamDamageFor(victim: LivingEntity): Float =
        if (victim.maxHealth > PERCENT_HEALTH_THRESHOLD) victim.maxHealth * BEAM_PERCENT_DAMAGE else BEAM_DAMAGE

    companion object {
        const val CHANNEL_TICKS = 50
        const val BEAM_DURATION = 65
        const val SWEEP_MAX_STEP = 0.42
        const val WARN_RADIUS = 0.16F
        const val ACTIVE_RADIUS = 1.25F
        const val BEAM_DAMAGE = 7.0F
        const val PERCENT_HEALTH_THRESHOLD = 100.0F
        const val BEAM_PERCENT_DAMAGE = 0.16F
        const val BEAM_DAMAGE_INTERVAL = 3
        const val BEAM_AOE_RADIUS = 2.4
        const val BEAM_TUBE_HIT_RADIUS = 1.4
        const val BEAM_EXTEND = 3.0
        const val BEAM_DAMAGE_MULTIPLIER = 0.5F
        const val INTERRUPT_THRESHOLD = 20.0F
    }
}
