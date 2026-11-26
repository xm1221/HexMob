package pub.pigeon.yggdyy.hexmob.content.ur_circle.spells

import at.petrak.hexcasting.common.lib.HexSounds
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.monster.Enemy
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import pub.pigeon.yggdyy.hexmob.content.ur_circle.UrCircleEntity
import pub.pigeon.yggdyy.hexmob.content.ur_circle.UrCircleHexVfx
import pub.pigeon.yggdyy.hexmob.content.ur_circle.canUrCircleGrief
import pub.pigeon.yggdyy.hexmob.content.ur_circle.isUrCircleAlly
import pub.pigeon.yggdyy.hexmob.content.ur_circle.subentities.SlateSymbolType

/**
 * 【覆压周天 · 协同星阵】（三阶段终局大法术）：
 * 同时在天穹各环部署 Aim（目标定位）、Amp（媒质增幅）与 Trigger（天顶触发脉冲板），
 * 带着明确倒计时同步落雷轰炸；任意打断 Trigger 板或反噬 2 块协同石板即可反劫持整个周天大阵！
 */
class CelestialConjunctionSkill : UrCircleSkill("celestial_conjunction", CHANNEL_TICKS) {
    override val weight: Int = 6
    override val perSkillCooldownTicks: Int = 260
    override val minPhase: Int = 3
    override val waveColor: Int = UrCircleHexVfx.WAVE_GOLD
    override val channelInterruptible: Boolean = false

    override fun channelHurtInterrupts(circle: UrCircleEntity): Boolean = false
    override fun channelMarksTarget(circle: UrCircleEntity): Boolean = true
    override fun releaseSound(circle: UrCircleEntity) = HexSounds.CAST_THOTH

    override fun canUse(circle: UrCircleEntity): Boolean {
        if (circle.level().isClientSide) return false
        val t = circle.target
        return circle.phase >= 3 && t != null && t.isAlive
    }

    override fun onChannelTick(circle: UrCircleEntity) {
        val level = circle.level()
        val origin = circle.beamOrigin()
        val t = circle.target
        val aimAnchor = t?.position()?.add(0.0, t.bbHeight * 0.4, 0.0) ?: origin.add(0.0, -8.0, 0.0)
        val triggerAnchor = origin.add(0.0, 14.0 * circle.totalScale().coerceAtLeast(1.0F), 0.0)

        var aim = circle.findSymbolicSlate(SlateSymbolType.AIM)
        var trigger = circle.findSymbolicSlate(SlateSymbolType.TRIGGER)
        var amp = circle.findSymbolicSlate(SlateSymbolType.AMP)

        if (circle.stateTicks <= 1 || aim == null) {
            aim = circle.deploySymbolicSlate(SlateSymbolType.AIM, aimAnchor, preferredRing = 2)
            amp = circle.deploySymbolicSlate(SlateSymbolType.AMP, null, preferredRing = 0)
            trigger = circle.deploySymbolicSlate(SlateSymbolType.TRIGGER, triggerAnchor, preferredRing = 4)
        } else {
            if (!aim.detached && !aim.rebounded) aim.deployedPos = aimAnchor
            if (trigger != null && !trigger.detached && !trigger.rebounded) trigger.deployedPos = triggerAnchor
        }

        // 若 Trigger 板已被反噬，或 2 块以上协同板进入失效，大阵反劫持引爆核心
        val disruptedCount = listOfNotNull(aim, amp, trigger).count { it.rebounded }
        if ((trigger?.rebounded == true) || disruptedCount >= 2) {
            circle.triggerShortCircuit()
            return
        }

        if (circle.stateTicks % 4 == 0) {
            val blastPos = aim?.posNow?.takeIf { it != Vec3.ZERO } ?: aimAnchor
            val pulseRadius = 3.0 + (circle.stateTicks / CHANNEL_TICKS.toDouble()) * 5.0
            UrCircleHexVfx.spawnGroundCircle(level, blastPos, radius = pulseRadius, color = UrCircleHexVfx.WAVE_GOLD, points = 24)
            trigger?.posNow?.takeIf { it != Vec3.ZERO }?.let { tp ->
                UrCircleHexVfx.spawnRingArc(level, tp, blastPos, UrCircleHexVfx.WAVE_GOLD, steps = 12)
                UrCircleHexVfx.spawnRingArc(level, origin, tp, UrCircleHexVfx.BEAM_LAVENDER, steps = 10)
            }
        }
    }

    override fun cast(circle: UrCircleEntity) {
        val level = circle.level()
        if (level.isClientSide) return
        val origin = circle.beamOrigin()
        val aim = circle.findSymbolicSlate(SlateSymbolType.AIM)
        val strikePos = aim?.posNow?.takeIf { it != Vec3.ZERO } ?: origin
        val mul = circle.ampDamageMultiplier()

        val radius = 11.0 * mul
        val box = AABB(strikePos, strikePos).inflate(radius)
        for (victim in level.getEntitiesOfClass(LivingEntity::class.java, box)) {
            if (victim === circle || victim is Enemy || isUrCircleAlly(victim)) continue
            if (victim.position().distanceToSqr(strikePos) <= radius * radius) {
                victim.hurt(level.damageSources().magic(), CONJUNCTION_DAMAGE * mul)
            }
        }

        val interaction = if (canUrCircleGrief(level)) Level.ExplosionInteraction.MOB else Level.ExplosionInteraction.NONE
        level.explode(circle, strikePos.x, strikePos.y, strikePos.z, 7.5F * mul, interaction)
        UrCircleHexVfx.spawnGroundCircle(level, strikePos, radius = radius, color = UrCircleHexVfx.WAVE_GOLD, points = 32)
    }

    companion object {
        const val CHANNEL_TICKS = 105
        const val CONJUNCTION_DAMAGE = 28.0F
    }
}
