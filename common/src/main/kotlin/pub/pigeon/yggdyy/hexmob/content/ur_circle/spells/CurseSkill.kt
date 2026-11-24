package pub.pigeon.yggdyy.hexmob.content.ur_circle.spells

import at.petrak.hexcasting.common.lib.HexSounds
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.monster.Enemy
import net.minecraft.world.phys.AABB
import pub.pigeon.yggdyy.hexmob.content.ur_circle.UrCircleEntity
import pub.pigeon.yggdyy.hexmob.content.ur_circle.UrCircleHexVfx
import pub.pigeon.yggdyy.hexmob.content.ur_circle.isUrCircleAlly

class CurseSkill : UrCircleSkill("curse", 60) {

    override val weight: Int = 3
    override val waveColor: Int = UrCircleHexVfx.MEDIA_PURPLE

    override fun channelHurtInterrupts(circle: UrCircleEntity): Boolean = false
    override fun channelMarksTarget(circle: UrCircleEntity): Boolean = true

    override fun canUse(circle: UrCircleEntity): Boolean {
        if (circle.level().isClientSide) return false
        val t = circle.target
        return t != null && t.isAlive && circle.health / circle.maxHealth > 0.4F
    }

    override fun channelParticlesPerPulse(circle: UrCircleEntity): Int = 16
    override fun channelPulseSound(circle: UrCircleEntity) = HexSounds.ADD_TO_PATTERN
    override fun channelPulseInterval(circle: UrCircleEntity): Int = 10

    override fun onChannelTick(circle: UrCircleEntity) {
        val t = circle.target
        if (t == null || !t.isAlive) return
        for (k in 0 until 3) {
            UrCircleHexVfx.spawnConjure(
                circle.level(),
                UrCircleHexVfx.MEDIA_PURPLE,
                t.x + (circle.random.nextDouble() - 0.5) * 1.6,
                t.y + circle.random.nextDouble() * t.bbHeight,
                t.z + (circle.random.nextDouble() - 0.5) * 1.6,
                (circle.random.nextDouble() - 0.5) * 0.1,
                0.12,
                (circle.random.nextDouble() - 0.5) * 0.1,
            )
        }
    }

    override fun cast(circle: UrCircleEntity) {
        val level = circle.level()
        if (level.isClientSide) return
        val t = circle.target ?: return
        if (!t.isAlive) return
        val debuff = UrCircleStatusTable.randomDebuff(level.random)
        for (victim in level.getEntitiesOfClass(LivingEntity::class.java, AABB(t.blockPosition()).inflate(3.0))) {
            if (victim is Enemy || isUrCircleAlly(victim)) continue
            victim.addEffect(MobEffectInstance(debuff.effect, debuff.duration, debuff.amplifier))
        }
        val top = t.position().add(0.0, t.bbHeight.toDouble(), 0.0)
        for (k in 0 until 24) {
            UrCircleHexVfx.spawnConjure(
                level,
                UrCircleHexVfx.MEDIA_PURPLE,
                top.x, top.y, top.z,
                (circle.random.nextDouble() - 0.5) * 0.2,
                circle.random.nextDouble() * 0.22,
                (circle.random.nextDouble() - 0.5) * 0.2,
            )
        }
        UrCircleHexVfx.playRingSound(level, top, circle.ringOuterRadius(), HexSounds.CAST_FAILURE, 1.5F, 1.15F)
    }
}
