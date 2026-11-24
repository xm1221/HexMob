package pub.pigeon.yggdyy.hexmob.content.ur_circle.spells

import at.petrak.hexcasting.common.lib.HexSounds
import net.minecraft.world.level.Level
import pub.pigeon.yggdyy.hexmob.content.ur_circle.UrCircleEntity
import pub.pigeon.yggdyy.hexmob.content.ur_circle.UrCircleHexVfx
import pub.pigeon.yggdyy.hexmob.content.ur_circle.canUrCircleGrief

class ExplosionSkill : UrCircleSkill("explosion", 40) {

    override val weight: Int = 2
    override val waveColor: Int = UrCircleHexVfx.SHORT_CIRCUIT_RED

    override fun channelHurtInterrupts(circle: UrCircleEntity): Boolean = false

    override fun canUse(circle: UrCircleEntity): Boolean {
        if (circle.level().isClientSide) return false
        return circle.currentHated().any { circle.distanceToSqr(it) < CLOSE_RANGE * CLOSE_RANGE }
    }

    override fun channelParticlesPerPulse(circle: UrCircleEntity): Int = 18
    override fun channelPulseSound(circle: UrCircleEntity) = HexSounds.CAST_FAILURE
    override fun channelPulseInterval(circle: UrCircleEntity): Int = 8

    override fun cast(circle: UrCircleEntity) {
        val level = circle.level()
        if (level.isClientSide) return
        val pos = circle.beamOrigin()
        val interaction = if (canUrCircleGrief(level)) Level.ExplosionInteraction.MOB else Level.ExplosionInteraction.NONE
        level.explode(circle, pos.x, pos.y, pos.z, EXPLOSION_POWER, interaction)
        for (k in 0 until 20) {
            UrCircleHexVfx.spawnConjure(
                level,
                UrCircleHexVfx.SHORT_CIRCUIT_RED,
                pos.x, pos.y, pos.z,
                (circle.random.nextDouble() - 0.5) * 0.4,
                (circle.random.nextDouble() - 0.5) * 0.4,
                (circle.random.nextDouble() - 0.5) * 0.4,
            )
        }
    }

    companion object {
        const val CLOSE_RANGE = 8.0
        const val EXPLOSION_POWER = 7.5F
    }
}
