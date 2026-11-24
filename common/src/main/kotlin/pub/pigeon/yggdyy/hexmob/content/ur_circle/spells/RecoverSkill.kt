package pub.pigeon.yggdyy.hexmob.content.ur_circle.spells

import at.petrak.hexcasting.common.lib.HexSounds
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import pub.pigeon.yggdyy.hexmob.content.ur_circle.UrCircleEntity
import pub.pigeon.yggdyy.hexmob.content.ur_circle.UrCircleHexVfx

class RecoverSkill : UrCircleSkill("recover", 60) {
    override fun canUse(circle: UrCircleEntity): Boolean =
        !circle.level().isClientSide && circle.health / circle.maxHealth < 0.5F

    override val weight: Int = 3
    override val waveColor: Int = UrCircleHexVfx.SIPHON_CYAN
    override val channelInterruptible: Boolean = true
    override fun nodeBreakThreshold(circle: UrCircleEntity): Float = 14.0F

    override fun channelParticlesPerPulse(circle: UrCircleEntity): Int = 18
    override fun channelPulseSound(circle: UrCircleEntity) = HexSounds.IMPETUS_REDSTONE_DING
    override fun channelPulseInterval(circle: UrCircleEntity): Int = 10

    override fun onChannelTick(circle: UrCircleEntity) {
        circle.addEffect(MobEffectInstance(MobEffects.GLOWING, 12, 0, false, false))
    }

    override fun onChannelHurt(circle: UrCircleEntity, source: DamageSource, amount: Float): Boolean {
        val dmg = amount * RECOVER_DAMAGE_MULTIPLIER
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
        circle.healSelf(HEAL_AMOUNT)
        val origin = circle.beamOrigin()
        for (k in 0 until 28) {
            val dx = (circle.random.nextDouble() - 0.5) * 8.0
            val dy = (circle.random.nextDouble() - 0.5) * 8.0
            val dz = (circle.random.nextDouble() - 0.5) * 8.0
            UrCircleHexVfx.spawnConjure(level, UrCircleHexVfx.SIPHON_CYAN, origin.x + dx, origin.y + dy, origin.z + dz, 0.0, 0.25, 0.0)
        }
        UrCircleHexVfx.playRingSound(level, origin, circle.ringOuterRadius(), HexSounds.FLIGHT_FINISH, 1.8F, 1.25F)
    }

    companion object {
        const val HEAL_AMOUNT = 200.0F
        const val INTERRUPT_THRESHOLD = 30.0F
        const val RECOVER_DAMAGE_MULTIPLIER = 0.5F
    }
}
