package pub.pigeon.yggdyy.hexmob.content.ur_circle.spells

import at.petrak.hexcasting.common.lib.HexBlocks
import at.petrak.hexcasting.common.lib.HexSounds
import net.minecraft.core.particles.BlockParticleOption
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.monster.Enemy
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import pub.pigeon.yggdyy.hexmob.content.ur_circle.UrCircleEntity
import pub.pigeon.yggdyy.hexmob.content.ur_circle.UrCircleHexVfx
import pub.pigeon.yggdyy.hexmob.content.ur_circle.isUrCircleAlly
import pub.pigeon.yggdyy.hexmob.util.spawnParticle
import java.util.UUID

/**
 * 【环刃风暴】Ring Spin——部件膨胀、环半径扩展、转速飙升，
 * 吸引周边实体并以单次包围球查询 + 最近部件距离结算绞杀伤害。
 */
class RingSpinSkill : UrCircleSkill("ring_spin", CHANNEL_TICKS) {

    override val weight: Int = 4
    override val waveColor: Int = UrCircleHexVfx.WAVE_GOLD

    override fun channelHurtInterrupts(circle: UrCircleEntity): Boolean = false
    override fun channelPulseVolume(circle: UrCircleEntity): Float = 2.2F

    override fun canUse(circle: UrCircleEntity): Boolean {
        if (circle.level().isClientSide) return false
        val t = circle.target
        return t != null && t.isAlive && circle.health / circle.maxHealth > 0.5
    }

    override fun channelParticlesPerPulse(circle: UrCircleEntity): Int = 20
    override fun channelPulseSound(circle: UrCircleEntity) = HexSounds.CAST_NORMAL
    override fun channelPulseInterval(circle: UrCircleEntity): Int = 8

    private val hitCooldowns: MutableMap<UUID, Int> = HashMap()

    override fun onChannelTick(circle: UrCircleEntity) {
        val level = circle.level()
        if (level.isClientSide) return

        val it = hitCooldowns.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            val next = e.value - 1
            if (next <= 0) it.remove() else e.setValue(next)
        }

        val origin = circle.beamOrigin()
        val hull = circle.getPartsBoundingBox()
        val parts = circle.getAllParts()
        for (victim in level.getEntitiesOfClass(LivingEntity::class.java, hull)) {
            if (victim === circle || victim is Enemy || isUrCircleAlly(victim) || hitCooldowns.containsKey(victim.uuid)) continue
            val vc = victim.position().add(0.0, victim.bbHeight / 2.0, 0.0)
            var hit = false
            for (part in parts) {
                if (!part.active || part.walkPlatform || part.posNow == Vec3.ZERO) continue
                val r = part.bbWidth * 0.65 + victim.bbWidth * 0.5 + 0.5
                if (part.posNow.distanceToSqr(vc) <= r * r) {
                    hit = true
                    break
                }
            }
            if (hit && victim.hurt(level.damageSources().mobAttack(circle), SPIN_DAMAGE)) {
                val kb = victim.position().subtract(origin)
                victim.knockback(KNOCKBACK.toDouble(), kb.x, kb.z)
                hitCooldowns[victim.uuid] = HIT_COOLDOWN
            }
        }

        val pullR = (circle.ringOuterRadius() * 1.15).coerceAtMost(MAX_PULL_RADIUS)
        for (victim in level.getEntitiesOfClass(LivingEntity::class.java, AABB(origin, origin).inflate(pullR))) {
            if (victim === circle || victim is Enemy || isUrCircleAlly(victim)) continue
            val toCenter = origin.subtract(victim.position())
            val dist = toCenter.length()
            if (dist < 1.5) continue
            val dir = toCenter.normalize()
            val strength = PULL_ACCEL * (1.5 - dist / pullR)
            victim.setDeltaMovement(
                victim.deltaMovement.scale(PULL_DAMP)
                    .add(dir.x * strength, dir.y * strength * 0.35, dir.z * strength)
            )
        }

        if (circle.tickCount % 3 == 0) {
            val activeParts = parts.filter { it.active && it.posNow != Vec3.ZERO }
            if (activeParts.isNotEmpty()) {
                val part = activeParts[circle.random.nextInt(activeParts.size)]
                val p = part.posNow
                spawnParticle(level, SLATE_PART, p.x, p.y, p.z, 0.0, 0.15, 0.0, UrCircleHexVfx.DEFAULT_RANGE)
                UrCircleHexVfx.spawnConjure(level, UrCircleHexVfx.WAVE_GOLD, p.x, p.y, p.z, 0.0, 0.12, 0.0)
            }
        }
    }

    override fun cast(circle: UrCircleEntity) {}

    override fun onChannelCancel(circle: UrCircleEntity) {
        hitCooldowns.clear()
    }

    override fun channelDamageMultiplier(circle: UrCircleEntity): Float = 0.9F

    companion object {
        const val CHANNEL_TICKS = 80
        const val SPIN_DAMAGE = 14.0F
        const val HIT_COOLDOWN = 2
        const val KNOCKBACK = 5
        const val MAX_PULL_RADIUS = 48.0
        const val PULL_ACCEL = 0.08
        const val PULL_DAMP = 0.95
        val SLATE_PART = BlockParticleOption(ParticleTypes.BLOCK, HexBlocks.SLATE.defaultBlockState())
    }
}
