package pub.pigeon.yggdyy.hexmob.content.ur_circle.spells

import at.petrak.hexcasting.common.lib.HexSounds
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.BlockParticleOption
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.level.block.Blocks
import pub.pigeon.yggdyy.hexmob.content.ur_circle.TempAmethystTracker
import pub.pigeon.yggdyy.hexmob.content.ur_circle.UrCircleEntity
import pub.pigeon.yggdyy.hexmob.content.ur_circle.UrCircleHexVfx
import pub.pigeon.yggdyy.hexmob.content.ur_circle.canUrCircleGrief
import pub.pigeon.yggdyy.hexmob.util.spawnParticle
import kotlin.math.cos
import kotlin.math.sin

/**
 * 【紫水晶封锁】Amethyst Trap——生成会定时自动消散的临时紫水晶晶牢困住目标；
 * 若禁用地形破坏则退回强力减速 + 紫水晶爆裂伤害。
 */
class AmethystTrapSkill : UrCircleSkill("amethyst_trap", 70) {

    override val weight: Int = 2
    override val waveColor: Int = UrCircleHexVfx.AMETHYST_VIOLET

    override fun channelHurtInterrupts(circle: UrCircleEntity): Boolean = false
    override fun channelMarksTarget(circle: UrCircleEntity): Boolean = true

    override fun canUse(circle: UrCircleEntity): Boolean {
        if (circle.level().isClientSide) return false
        return circle.currentHated().isNotEmpty()
    }

    override fun channelParticlesPerPulse(circle: UrCircleEntity): Int = 16
    override fun channelPulseSound(circle: UrCircleEntity) = SoundEvents.AMETHYST_BLOCK_PLACE
    override fun channelPulseInterval(circle: UrCircleEntity): Int = 10
    override fun releaseSound(circle: UrCircleEntity): SoundEvent = SoundEvents.AMETHYST_BLOCK_BREAK

    override fun onChannelTick(circle: UrCircleEntity) {
        val level = circle.level()
        for (t in circle.currentHated().take(MAX_TARGETS)) {
            for (k in 0 until 2) {
                spawnParticle(
                    level, AMETHYST_BREAK,
                    t.x + (circle.random.nextDouble() - 0.5) * 1.8,
                    t.y + circle.random.nextDouble() * t.bbHeight,
                    t.z + (circle.random.nextDouble() - 0.5) * 1.8,
                    (circle.random.nextDouble() - 0.5) * 0.1, 0.1, (circle.random.nextDouble() - 0.5) * 0.1,
                    UrCircleHexVfx.DEFAULT_RANGE,
                )
                UrCircleHexVfx.spawnConjure(
                    level,
                    UrCircleHexVfx.AMETHYST_VIOLET,
                    t.x + (circle.random.nextDouble() - 0.5) * 2.0,
                    t.y + circle.random.nextDouble() * t.bbHeight,
                    t.z + (circle.random.nextDouble() - 0.5) * 2.0,
                    0.0, 0.08, 0.0,
                )
            }
        }
    }

    override fun cast(circle: UrCircleEntity) {
        val level = circle.level()
        if (level.isClientSide) return
        val serverLevel = level as? ServerLevel ?: return
        val allowBlocks = canUrCircleGrief(level)

        for (t in circle.currentHated().take(MAX_TARGETS)) {
            if (allowBlocks) {
                val base = t.blockPosition()
                for (dx in -1..1) {
                    for (dy in -1..1) {
                        for (dz in -1..1) {
                            TempAmethystTracker.placeTemporary(serverLevel, base.offset(dx, dy, dz), TEMP_LIFETIME_TICKS)
                        }
                    }
                }
            } else {
                t.addEffect(MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 3))
                t.hurt(level.damageSources().indirectMagic(circle, circle), 4.0F)
                UrCircleHexVfx.spawnGroundCircle(level, t.position(), 1.8, UrCircleHexVfx.AMETHYST_VIOLET, points = 16)
            }
        }

        if (allowBlocks) {
            val center = circle.blockPosition().above()
            val radius = (RING_TRAP_RADIUS * circle.totalScale()).coerceAtMost(circle.ringOuterRadius() * 0.9)
            for (k in 0 until RING_TRAP_COUNT) {
                val ang = Math.PI * 2.0 * k / RING_TRAP_COUNT
                val bx = center.x + Math.floor(cos(ang) * radius + 0.5)
                val bz = center.z + Math.floor(sin(ang) * radius + 0.5)
                for (dy in 0 until TRAP_HEIGHT) {
                    TempAmethystTracker.placeTemporary(serverLevel, BlockPos(bx.toInt(), center.y + dy, bz.toInt()), TEMP_LIFETIME_TICKS)
                }
            }
        }
        UrCircleHexVfx.playRingSound(level, circle.beamOrigin(), circle.ringOuterRadius(), HexSounds.CAST_NORMAL, 1.6F, 1.0F)
    }

    companion object {
        const val MAX_TARGETS = 8
        const val RING_TRAP_COUNT = 12
        const val RING_TRAP_RADIUS = 7.0
        const val TRAP_HEIGHT = 3
        const val TEMP_LIFETIME_TICKS = 160
        val AMETHYST_BREAK = BlockParticleOption(ParticleTypes.BLOCK, Blocks.AMETHYST_BLOCK.defaultBlockState())
    }
}
