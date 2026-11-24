package pub.pigeon.yggdyy.hexmob.content.ur_circle.spells

import at.petrak.hexcasting.common.lib.HexSounds
import pub.pigeon.yggdyy.hexmob.content.ur_circle.UrCircleEntity
import pub.pigeon.yggdyy.hexmob.content.ur_circle.UrCircleHexVfx
import pub.pigeon.yggdyy.hexmob.content.ur_circle.serpent.UrCircleSerpent
import pub.pigeon.yggdyy.hexmob.registry.HexMobEntities

class EruptSerpentSkill : UrCircleSkill("erupt_serpent", 60) {

    override val weight: Int = 3
    override val waveColor: Int = UrCircleHexVfx.AMETHYST_VIOLET

    override fun channelHurtInterrupts(circle: UrCircleEntity): Boolean = false
    override fun channelMarksTarget(circle: UrCircleEntity): Boolean = true

    override fun canUse(circle: UrCircleEntity): Boolean {
        if (circle.level().isClientSide) return false
        return circle.currentHated().any { it.isAlive && circle.health / circle.maxHealth > 0.4F }
    }

    override fun channelParticlesPerPulse(circle: UrCircleEntity): Int = 14
    override fun channelPulseSound(circle: UrCircleEntity) = HexSounds.ADD_TO_PATTERN
    override fun channelPulseInterval(circle: UrCircleEntity): Int = 10

    override fun onChannelTick(circle: UrCircleEntity) {
        val level = circle.level()
        if (level.isClientSide) return
        for (t in circle.currentHated().take(MAX_TARGETS)) {
            if (!t.isAlive) continue
            for (k in 0 until 3) {
                UrCircleHexVfx.spawnConjure(
                    level,
                    UrCircleHexVfx.AMETHYST_VIOLET,
                    t.x + (circle.random.nextDouble() - 0.5) * 1.4,
                    t.y - 0.2,
                    t.z + (circle.random.nextDouble() - 0.5) * 1.4,
                    (circle.random.nextDouble() - 0.5) * 0.1,
                    0.28,
                    (circle.random.nextDouble() - 0.5) * 0.1,
                )
            }
        }
    }

    override fun cast(circle: UrCircleEntity) {
        val level = circle.level()
        if (level.isClientSide) return
        val serpentScale = if (circle.phase >= 2) 1.5F else 1.0F
        for (t in circle.currentHated().take(MAX_TARGETS)) {
            if (!t.isAlive) continue
            for (i in 0 until ERUPT_PER_TARGET) {
                val serpent = UrCircleSerpent(HexMobEntities.UR_CIRCLE_SERPENT.get(), level)
                serpent.owner = circle
                serpent.visualScale = serpentScale
                val ox = (circle.random.nextDouble() - 0.5) * 1.6
                val oz = (circle.random.nextDouble() - 0.5) * 1.6
                serpent.setPos(t.x + ox, t.y - 1.0, t.z + oz)
                serpent.setAim(0.0, 1.0, 0.0)
                level.addFreshEntity(serpent)
            }
        }
    }

    companion object {
        const val MAX_TARGETS = 6
        const val ERUPT_PER_TARGET = 3
    }
}
