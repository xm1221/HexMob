package pub.pigeon.yggdyy.hexmob.content.ur_circle.spells

import net.minecraft.world.damagesource.DamageSource
import pub.pigeon.yggdyy.hexmob.content.ur_circle.UrCircleEntity
import pub.pigeon.yggdyy.hexmob.content.ur_circle.UrCircleHexVfx

class SummonServantSkill : UrCircleSkill("summon_servants", 70) {

    override fun canUse(circle: UrCircleEntity): Boolean {
        if (circle.level().isClientSide) return false
        return circle.health / circle.maxHealth > 0.7F && circle.livingServants() < MAX_SERVANTS
    }

    override val weight: Int = 3
    override val waveColor: Int = UrCircleHexVfx.MEDIA_PURPLE
    override val channelInterruptible: Boolean = true

    override fun channelParticlesPerPulse(circle: UrCircleEntity): Int = 14

    override fun onChannelHurt(circle: UrCircleEntity, source: DamageSource, amount: Float): Boolean {
        if (amount > 0.0F) {
            circle.applyChannelDamage(source, amount)
        }
        if (amount >= INTERRUPT_THRESHOLD) {
            circle.interruptChannel()
        }
        return true
    }

    override fun cast(circle: UrCircleEntity) {
        if (circle.level().isClientSide) return
        val missing = MAX_SERVANTS - circle.livingServants()
        circle.summonPets(missing)
    }

    companion object {
        const val MAX_SERVANTS = 8
        const val INTERRUPT_THRESHOLD = 20.0F
    }
}
