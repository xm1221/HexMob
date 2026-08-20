package pub.pigeon.yggdyy.hexmob.content.ur_circle.spells

import at.petrak.hexcasting.common.lib.HexSounds
import net.minecraft.core.particles.BlockParticleOption
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.level.block.Blocks
import pub.pigeon.yggdyy.hexmob.content.ur_circle.UrCircleEntity
import pub.pigeon.yggdyy.hexmob.util.spawnParticle

/**
 * 【紫水晶窒息】Amethyst Trap——在所有仇恨目标的位置铺 3×3×2 紫水晶块，
 * 困住并使其窒息（头部埋进实心方块）。目标是大环"攻击对象里的生物"，可多目标同时埋。
 */
class AmethystTrapSkill : UrCircleSkill("amethyst_trap", 70) {

    override val weight: Int = 2

    /** 不可打断：吟唱不中断（受击只掉血）。 */
    override fun channelHurtInterrupts(circle: UrCircleEntity): Boolean = false

    /** 吟唱标记目标发光。 */
    override fun channelMarksTarget(circle: UrCircleEntity): Boolean = true

    override fun canUse(circle: UrCircleEntity): Boolean {
        if (circle.level().isClientSide) return false
        return circle.currentHated().isNotEmpty()
    }

    override fun channelParticleType(circle: UrCircleEntity) = ParticleTypes.END_ROD
    override fun channelParticlesPerPulse(circle: UrCircleEntity): Int = 14
    override fun channelPulseSound(circle: UrCircleEntity) = SoundEvents.AMETHYST_BLOCK_PLACE
    override fun channelPulseInterval(circle: UrCircleEntity): Int = 10
    override fun releaseSound(circle: UrCircleEntity): SoundEvent = SoundEvents.AMETHYST_BLOCK_BREAK

    // 吟唱期间：在目标周围生成"紫水晶方块被破坏"的粒子（预兆：要被埋了）
    override fun onChannelTick(circle: UrCircleEntity) {
        val level = circle.level()
        for (t in circle.currentHated().take(MAX_TARGETS)) {
            for (k in 0 until 3) {
                spawnParticle(
                    level, AMETHYST_BREAK,
                    t.x + (circle.random.nextDouble() - 0.5) * 1.8,
                    t.y + circle.random.nextDouble() * t.bbHeight,
                    t.z + (circle.random.nextDouble() - 0.5) * 1.8,
                    (circle.random.nextDouble() - 0.5) * 0.1, 0.1, (circle.random.nextDouble() - 0.5) * 0.1
                )
            }
        }
    }

    override fun cast(circle: UrCircleEntity) {
        val level = circle.level()
        if (level.isClientSide) return
        for (t in circle.currentHated().take(MAX_TARGETS)) {
            val base = t.blockPosition()
            for (dx in -1..1) {
                for (dy in -1..1) {
                    for (dz in -1..1) {
                        val pos = base.offset(dx, dy, dz)
                        if (level.getBlockState(pos).isAir) {
                            level.setBlockAndUpdate(pos, Blocks.AMETHYST_BLOCK.defaultBlockState())
                        }
                    }
                }
            }
        }
        level.playSound(null, circle.blockPosition(), HexSounds.CAST_NORMAL, SoundSource.HOSTILE, 1.5F, 1.0F)
    }

    companion object {
        /** 最多同时埋几个仇恨目标。 */
        const val MAX_TARGETS = 6
        /** 紫水晶方块被破坏的粒子。 */
        val AMETHYST_BREAK = BlockParticleOption(ParticleTypes.BLOCK, Blocks.AMETHYST_BLOCK.defaultBlockState())
    }
}
