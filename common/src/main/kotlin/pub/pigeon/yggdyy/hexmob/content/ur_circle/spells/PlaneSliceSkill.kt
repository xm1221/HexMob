package pub.pigeon.yggdyy.hexmob.content.ur_circle.spells

import at.petrak.hexcasting.common.lib.HexSounds
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.monster.Enemy
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import pub.pigeon.yggdyy.hexmob.content.ur_circle.UrCircleEntity
import pub.pigeon.yggdyy.hexmob.content.ur_circle.UrCircleHexVfx
import pub.pigeon.yggdyy.hexmob.content.ur_circle.isUrCircleAlly
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * 【环面切割】二阶段几何大招：
 * 沿巨型环的几何平面发射 360° 旋转高能媒质光刃带。
 * 吟唱期间沿切割平面放出预警光环，释放时仅命中"垂直距离该平面 ≤2.2 格且在环半径范围内"的生物，
 * 鼓励玩家利用跳跃/下潜或高低地形穿出切割平面。
 */
class PlaneSliceSkill : UrCircleSkill("plane_slice", CHANNEL_TICKS) {
    override val weight: Int = 4
    override val minPhase: Int = 2
    override val waveColor: Int = UrCircleHexVfx.BEAM_LAVENDER
    override val channelInterruptible: Boolean = true

    override fun nodeBreakThreshold(circle: UrCircleEntity): Float = 15.0F
    override fun channelHurtInterrupts(circle: UrCircleEntity): Boolean = false
    override fun channelMarksTarget(circle: UrCircleEntity): Boolean = false

    override fun releaseSound(circle: UrCircleEntity) = HexSounds.CAST_THOTH

    private var planeOrigin: Vec3 = Vec3.ZERO
    private var planeNormal: Vec3 = Vec3(0.0, 1.0, 0.0)
    private var planeAxisU: Vec3 = Vec3(1.0, 0.0, 0.0)
    private var planeAxisV: Vec3 = Vec3(0.0, 0.0, 1.0)
    private var planeRadius: Double = 36.0

    override fun canUse(circle: UrCircleEntity): Boolean {
        if (circle.level().isClientSide) return false
        val t = circle.target
        return circle.phase >= 2 && t != null && t.isAlive
    }

    override fun onChannelTick(circle: UrCircleEntity) {
        val t = circle.target
        if (circle.stateTicks == 1 || t != null && t.isAlive) {
            val center = circle.beamOrigin()
            val targetCenter = t?.position()?.add(0.0, t.bbHeight / 2.0, 0.0) ?: center.add(1.0, 0.0, 0.0)
            // 使平面穿过目标附近且保持微倾斜，留出明确可躲避的上下空间
            val toTarget = targetCenter.subtract(center)
            val horiz = Vec3(toTarget.x, 0.0, toTarget.z)
            val right = if (horiz.lengthSqr() > 1.0E-4) horiz.cross(Vec3(0.0, 1.0, 0.0)).normalize() else Vec3(1.0, 0.0, 0.0)
            val dirToTarget = if (toTarget.lengthSqr() > 1.0E-4) toTarget.normalize() else Vec3(0.0, 0.0, 1.0)
            planeOrigin = Vec3(center.x, targetCenter.y + 0.4, center.z)
            planeNormal = dirToTarget.cross(right).normalize()
            planeAxisU = right
            planeAxisV = planeNormal.cross(planeAxisU).normalize()
            planeRadius = circle.ringOuterRadius().coerceAtLeast(24.0)
        }
        if (circle.stateTicks % 4 == 0) {
            emitPlaneRing(circle, UrCircleHexVfx.BEAM_LAVENDER, points = 32)
        }
    }

    override fun onChannelHurt(circle: UrCircleEntity, source: DamageSource, amount: Float): Boolean {
        val dmg = amount * 0.6F
        if (dmg > 0.0F) circle.applyChannelDamage(source, dmg)
        return true
    }

    override fun beamTicks(circle: UrCircleEntity): Int = SLICE_DURATION

    override fun cast(circle: UrCircleEntity) {
        circle.setPlaneSlicing(true, (planeOrigin.y - circle.y).toFloat())
        circle.beginBeam(this)
    }

    override fun onBeamEnd(circle: UrCircleEntity) {
        circle.setPlaneSlicing(false, 0.0F)
    }

    override fun onBeamTick(circle: UrCircleEntity) {
        val level = circle.level()
        if (level.isClientSide) return
        val progress = (circle.stateTicks.toFloat() / SLICE_DURATION).coerceIn(0F, 1F)
        val sweepAngle = progress * Math.PI * 2.0

        // 绘制两条对向扫过的光刃射线与环缘光带
        val bladeDir = planeAxisU.scale(cos(sweepAngle)).add(planeAxisV.scale(sin(sweepAngle))).normalize()
        UrCircleHexVfx.spawnRingArc(
            level,
            planeOrigin.subtract(bladeDir.scale(planeRadius)),
            planeOrigin.add(bladeDir.scale(planeRadius)),
            UrCircleHexVfx.WAVE_GOLD,
            steps = 20,
        )
        if (circle.stateTicks % 3 == 0) {
            emitPlaneRing(circle, UrCircleHexVfx.WAVE_GOLD, points = 36)
        }

        if (circle.stateTicks % DAMAGE_INTERVAL == 0) {
            val box = AABB(planeOrigin, planeOrigin).inflate(planeRadius + 2.0)
            for (victim in level.getEntitiesOfClass(LivingEntity::class.java, box)) {
                if (victim === circle || victim is Enemy || isUrCircleAlly(victim)) continue
                val vc = victim.position().add(0.0, victim.bbHeight / 2.0, 0.0)
                val rel = vc.subtract(planeOrigin)
                val perpDist = abs(rel.dot(planeNormal))
                val inPlaneSq = rel.lengthSqr() - perpDist * perpDist
                if (perpDist <= SLICE_HALF_THICKNESS && inPlaneSq <= (planeRadius + 1.5) * (planeRadius + 1.5)) {
                    victim.hurt(level.damageSources().mobAttack(circle), SLICE_DAMAGE)
                }
            }
        }
    }

    private fun emitPlaneRing(circle: UrCircleEntity, color: Int, points: Int) {
        val level = circle.level()
        for (i in 0 until points) {
            val a = Math.PI * 2.0 * i / points
            val p = planeOrigin
                .add(planeAxisU.scale(cos(a) * planeRadius))
                .add(planeAxisV.scale(sin(a) * planeRadius))
            UrCircleHexVfx.spawnConjure(level, color, p.x, p.y, p.z, 0.0, 0.02, 0.0)
        }
    }

    companion object {
        const val CHANNEL_TICKS = 60
        const val SLICE_DURATION = 45
        const val DAMAGE_INTERVAL = 5
        const val SLICE_HALF_THICKNESS = 2.2
        const val SLICE_DAMAGE = 9.0F
    }
}
