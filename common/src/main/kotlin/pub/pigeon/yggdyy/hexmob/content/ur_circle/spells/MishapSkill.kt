package pub.pigeon.yggdyy.hexmob.content.ur_circle.spells

import at.petrak.hexcasting.common.lib.HexSounds
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.effect.MobEffectInstance
import pub.pigeon.yggdyy.hexmob.content.ur_circle.UrCircleEntity
import pub.pigeon.yggdyy.hexmob.content.ur_circle.UrCircleHexVfx

class MishapSkill : UrCircleSkill("mishap", 80) {

    override val weight: Int = 3
    override val waveColor: Int = UrCircleHexVfx.SHORT_CIRCUIT_RED

    override fun channelHurtInterrupts(circle: UrCircleEntity): Boolean = false
    override fun channelMarksTarget(circle: UrCircleEntity): Boolean = true

    override fun canUse(circle: UrCircleEntity): Boolean {
        if (circle.level().isClientSide) return false
        val t = circle.target
        return t != null && t.isAlive && circle.health / circle.maxHealth <= 0.75F
    }

    override fun channelParticlesPerPulse(circle: UrCircleEntity): Int = 18
    override fun channelPulseSound(circle: UrCircleEntity) = HexSounds.CAST_FAILURE
    override fun channelPulseInterval(circle: UrCircleEntity): Int = 12

    override fun onChannelTick(circle: UrCircleEntity) {
        val t = circle.target
        if (t == null || !t.isAlive) return
        for (k in 0 until 3) {
            UrCircleHexVfx.spawnConjure(
                circle.level(),
                UrCircleHexVfx.SHORT_CIRCUIT_RED,
                t.x + (circle.random.nextDouble() - 0.5) * 2.0,
                t.y + circle.random.nextDouble() * t.bbHeight,
                t.z + (circle.random.nextDouble() - 0.5) * 2.0,
                (circle.random.nextDouble() - 0.5) * 0.15,
                0.1,
                (circle.random.nextDouble() - 0.5) * 0.15,
            )
        }
    }

    override fun cast(circle: UrCircleEntity) {
        val level = circle.level()
        if (level.isClientSide) return
        val t = circle.target ?: return
        if (!t.isAlive) return
        val mishap = UrCircleStatusTable.randomMishap(circle, level.random)
        if (t is ServerPlayer) {
            UrCircleMishap.throwAt(t, mishap)
        } else {
            val debuff = UrCircleStatusTable.randomDebuff(level.random)
            t.addEffect(MobEffectInstance(debuff.effect, debuff.duration, debuff.amplifier))
        }
    }
}
