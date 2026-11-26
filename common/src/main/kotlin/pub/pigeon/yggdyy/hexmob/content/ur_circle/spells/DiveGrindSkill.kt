package pub.pigeon.yggdyy.hexmob.content.ur_circle.spells

import at.petrak.hexcasting.common.lib.HexSounds
import net.minecraft.core.BlockPos
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.monster.Enemy
import net.minecraft.world.phys.Vec3
import pub.pigeon.yggdyy.hexmob.content.ur_circle.UrCircleEntity
import pub.pigeon.yggdyy.hexmob.content.ur_circle.UrCircleHexVfx
import pub.pigeon.yggdyy.hexmob.content.ur_circle.canUrCircleGrief
import pub.pigeon.yggdyy.hexmob.content.ur_circle.craterAround
import pub.pigeon.yggdyy.hexmob.content.ur_circle.isUrCircleAlly

/**
 * 【合拢俯冲 · 天球碾压】二阶段几何大招：
 * 吟唱时四环法线平滑归零合拢为水平巨碟并画出地面预警圈；
 * 释放后整枚巨碟俯冲至目标头顶近战高度高速横向碾压战场，随后拉升重展开。
 */
class DiveGrindSkill : UrCircleSkill("dive_grind", CHANNEL_TICKS) {
    override val weight: Int = 4
    override val minPhase: Int = 2
    override val waveColor: Int = UrCircleHexVfx.WAVE_GOLD
    override val channelInterruptible: Boolean = true

    override fun nodeBreakThreshold(circle: UrCircleEntity): Float = 16.0F
    override fun channelHurtInterrupts(circle: UrCircleEntity): Boolean = false
    override fun channelMarksTarget(circle: UrCircleEntity): Boolean = true

    override fun channelStartSound(circle: UrCircleEntity) = HexSounds.START_PATTERN
    override fun channelPulseSound(circle: UrCircleEntity) = HexSounds.SPELL_CIRCLE_FIND_BLOCK
    override fun releaseSound(circle: UrCircleEntity) = HexSounds.CAST_HERMES

    private var lockedTargetPos: Vec3 = Vec3.ZERO
    private var grindOrigin: Vec3 = Vec3.ZERO
    private var grindDir: Vec3 = Vec3(1.0, 0.0, 0.0)
    private var pullBackHomeY: Double = 0.0

    override fun canUse(circle: UrCircleEntity): Boolean {
        if (circle.level().isClientSide) return false
        val t = circle.target
        return circle.phase >= 2 && t != null && t.isAlive
    }

    override fun onChannelTick(circle: UrCircleEntity) {
        circle.targetFoldProgress = 1.0F
        val t = circle.target
        if (t != null && t.isAlive) {
            lockedTargetPos = t.position()
            if (circle.stateTicks % 5 == 0) {
                UrCircleHexVfx.spawnGroundCircle(
                    circle.level(),
                    lockedTargetPos,
                    radius = 8.0,
                    color = UrCircleHexVfx.WAVE_GOLD,
                    points = 24,
                )
            }
        }
    }

    override fun onChannelHurt(circle: UrCircleEntity, source: DamageSource, amount: Float): Boolean {
        val dmg = amount * 0.7F
        if (dmg > 0.0F) {
            circle.applyChannelDamage(source, dmg)
        }
        return true
    }

    override fun grindTicks(circle: UrCircleEntity): Int = GRIND_DURATION

    override fun cast(circle: UrCircleEntity) {
        val level = circle.level()
        if (level.isClientSide) return
        val t = circle.target
        val dest = if (t != null && t.isAlive) t.position() else lockedTargetPos
        pullBackHomeY = circle.y
        grindOrigin = circle.position()
        val horiz = Vec3(dest.x - grindOrigin.x, 0.0, dest.z - grindOrigin.z)
        grindDir = if (horiz.lengthSqr() > 1.0E-4) horiz.normalize() else Vec3(1.0, 0.0, 0.0)
        lockedTargetPos = dest
        circle.targetFoldProgress = 1.0F
        circle.beginGrind(this)
    }

    override fun onGrindTick(circle: UrCircleEntity) {
        val level = circle.level()
        val tick = circle.stateTicks
        circle.targetFoldProgress = 1.0F

        val targetY = lockedTargetPos.y + 4.0
        when {
            tick < DIVE_TICKS -> {
                // 俯冲切入贴地高度
                val dy = ((targetY - circle.y) * 0.25).coerceIn(-1.2, 0.6)
                circle.setDeltaMovement(grindDir.x * 0.45, dy, grindDir.z * 0.45)
            }
            tick < DIVE_TICKS + SWEEP_TICKS -> {
                // 高速贴地横扫碾压：玩家近战可击中环身，同时对沿途实体造成冲击
                val dy = ((targetY - circle.y) * 0.2).coerceIn(-0.4, 0.4)
                circle.setDeltaMovement(grindDir.x * SWEEP_SPEED, dy, grindDir.z * SWEEP_SPEED)
                if (tick % 4 == 0) {
                    val hull = circle.getPartsBoundingBox()
                    val center = circle.position().add(0.0, circle.bbHeight / 2.0, 0.0)
                    for (victim in level.getEntitiesOfClass(LivingEntity::class.java, hull)) {
                        if (victim === circle || victim is Enemy || isUrCircleAlly(victim)) continue
                        val nearestSq = circle.getAllParts()
                            .filter { it.active && it.posNow != Vec3.ZERO }
                            .minOfOrNull { it.posNow.distanceToSqr(victim.position().add(0.0, victim.bbHeight / 2.0, 0.0)) }
                            ?: continue
                        if (nearestSq <= 12.0) {
                            victim.hurt(level.damageSources().mobAttack(circle), SWEEP_DAMAGE)
                            val kb = victim.position().subtract(center)
                            victim.knockback(1.6, kb.x, kb.z)
                        }
                    }
                    if (canUrCircleGrief(level) && tick % 12 == 0) {
                        val ground = BlockPos.containing(circle.x, targetY - 4.5, circle.z)
                        if (!level.getBlockState(ground).isAir) {
                            craterAround(level, ground, circle, drop = false, skipHexStones = true, convertChance = 0.25F)
                        }
                    }
                }
            }
            else -> {
                // 结束拉升重展开
                val riseTarget = maxOf(pullBackHomeY, lockedTargetPos.y + 18.0)
                val dy = ((riseTarget - circle.y) * 0.18).coerceIn(0.15, 0.85)
                circle.setDeltaMovement(grindDir.x * 0.2, dy, grindDir.z * 0.2)
                circle.targetFoldProgress = 0.0F
            }
        }
    }

    override fun onGrindEnd(circle: UrCircleEntity) {
        circle.targetFoldProgress = 0.0F
        UrCircleHexVfx.playRingSound(circle.level(), circle.position(), circle.ringOuterRadius(), HexSounds.CAST_NORMAL, 1.8F, 1.1F)
    }

    override fun onChannelCancel(circle: UrCircleEntity) {
        circle.targetFoldProgress = 0.0F
    }

    companion object {
        const val CHANNEL_TICKS = 50
        const val DIVE_TICKS = 14
        const val SWEEP_TICKS = 28
        const val GRIND_DURATION = 55
        const val SWEEP_SPEED = 0.85
        const val SWEEP_DAMAGE = 12.0F
    }
}
