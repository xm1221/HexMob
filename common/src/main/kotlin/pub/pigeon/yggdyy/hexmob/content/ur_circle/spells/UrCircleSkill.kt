package pub.pigeon.yggdyy.hexmob.content.ur_circle.spells

import at.petrak.hexcasting.common.lib.HexSounds
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.sounds.SoundEvent
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.phys.Vec3
import pub.pigeon.yggdyy.hexmob.content.ur_circle.UrCircleEntity
import pub.pigeon.yggdyy.hexmob.content.ur_circle.UrCircleHexVfx

/**
 * 大环的蓄力技能抽象。
 * 吟唱期间由活体媒质波沿石板环传导并逐块点亮图案，击中导通节点可触发断路打断。
 */
abstract class UrCircleSkill(val name: String, val channelTicks: Int) {
    abstract fun canUse(circle: UrCircleEntity): Boolean
    abstract fun cast(circle: UrCircleEntity)

    open val weight: Int = 1

    /** 同技能最小复放间隔（tick）。 */
    open val perSkillCooldownTicks: Int = 140

    /** 最低解锁阶段（1=一阶段可用，2=二阶段巨环展开后解锁）。 */
    open val minPhase: Int = 1

    /** 吟唱期间代表媒质波颜色（用于石板发光与粒子）。 */
    open val waveColor: Int = UrCircleHexVfx.MEDIA_PURPLE

    /** 是否由活体媒质波沿环传导（可被击中导通节点打断）。 */
    open val usesMediaWave: Boolean = true

    /** 击穿正在导通的高亮节点所需的累计伤害阈值。 */
    open fun nodeBreakThreshold(circle: UrCircleEntity): Float = 14.0F

    open val channelInterruptible: Boolean = false

    open fun channelMarksTarget(circle: UrCircleEntity): Boolean = false

    // ---- Hex 原生粒子 ----

    open fun channelParticleType(circle: UrCircleEntity): ParticleOptions = UrCircleHexVfx.conjure(waveColor)

    open fun channelParticlesPerPulse(circle: UrCircleEntity): Int = 20

    open fun channelParticlePos(circle: UrCircleEntity): Vec3 {
        val origin = circle.position().add(0.0, circle.bbHeight / 2.0, 0.0)
        val base = (circle.ringOuterRadius() * 0.65).coerceIn(4.0, 28.0)
        val r = base * (0.6 + circle.random.nextDouble() * 0.6)
        val theta = circle.random.nextDouble() * Math.PI * 2.0
        val phi = Math.acos(2.0 * circle.random.nextDouble() - 1.0)
        return origin.add(
            r * Math.sin(phi) * Math.cos(theta),
            r * Math.sin(phi) * Math.sin(theta),
            r * Math.cos(phi)
        )
    }

    open fun channelParticleVelocity(circle: UrCircleEntity, pos: Vec3): Vec3 {
        val origin = circle.position().add(0.0, circle.bbHeight / 2.0, 0.0)
        val dir = origin.subtract(pos)
        if (dir.lengthSqr() < 1.0E-4) return Vec3.ZERO
        return dir.normalize().scale(0.16 + circle.random.nextDouble() * 0.12)
    }

    // ---- Hex 原生音效 ----

    open fun channelStartSound(circle: UrCircleEntity): SoundEvent = HexSounds.START_PATTERN
    open fun channelStartVolume(circle: UrCircleEntity): Float = 1.6F

    open fun channelPulseSound(circle: UrCircleEntity): SoundEvent = HexSounds.SPELL_CIRCLE_FIND_BLOCK
    open fun channelPulseVolume(circle: UrCircleEntity): Float = 1.3F

    open fun channelPulseInterval(circle: UrCircleEntity): Int = 12

    open fun releaseSound(circle: UrCircleEntity): SoundEvent = HexSounds.CAST_THOTH
    open fun releaseVolume(circle: UrCircleEntity): Float = 2.2F

    // ---- 吟唱受伤控制 ----

    open fun onChannelHurt(circle: UrCircleEntity, source: DamageSource, amount: Float): Boolean {
        val dmg = amount * channelDamageMultiplier(circle)
        var handled = true
        if (dmg > 0.0F) {
            handled = circle.applyChannelDamage(source, dmg)
        }
        if (handled && channelHurtInterrupts(circle)) circle.interruptChannel()
        return handled
    }

    open fun channelDamageMultiplier(circle: UrCircleEntity): Float = 1.0F

    open fun channelHurtInterrupts(circle: UrCircleEntity): Boolean = true

    open fun onChannelTick(circle: UrCircleEntity) {}

    /** 技能吟唱或持续阶段被强制打断/转阶段/死亡取消时的统一状态清理钩子。 */
    open fun onChannelCancel(circle: UrCircleEntity) {}

    // ---- 光束 / 持续环面状态（cast 后进入 BEAM） ----

    open fun beamTicks(circle: UrCircleEntity): Int = 0
    open fun onBeamTick(circle: UrCircleEntity) {}
    open fun onBeamEnd(circle: UrCircleEntity) {}

    // ---- 合拢俯冲碾压状态（cast 后进入 FOLD_GRIND） ----

    open fun grindTicks(circle: UrCircleEntity): Int = 0
    open fun onGrindTick(circle: UrCircleEntity) {}
    open fun onGrindEnd(circle: UrCircleEntity) {}
}
