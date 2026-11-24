package pub.pigeon.yggdyy.hexmob.content.ur_circle

import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.goal.Goal
import net.minecraft.world.phys.Vec3
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 悬浮巡航（大环默认行为）：
 * - 一阶段保持中距环绕并悬在目标斜上方；
 * - 二阶段展开巨环后自动抬升空域，避免外环插地并俯瞰战场。
 */
class UrCircleCruiseGoal(private val circle: UrCircleEntity) : Goal() {

    override fun canUse(): Boolean =
        circle.isAwake() && circle.circleState == CircleState.CRUISE

    override fun canContinueToUse(): Boolean = canUse()

    override fun tick() {
        val origin = circle.position()
        val target = circle.target
        val wanted = if (target != null && target.isAlive) {
            cruiseAroundTarget(target, origin)
        } else {
            hoverAtHome(origin)
        }
        val mc = circle.moveControl
        if (mc is UrCircleMoveControl) {
            mc.setWantedPosition(wanted.x, clampHeight(wanted.y), wanted.z, 1.0)
        }
    }

    private fun clampHeight(wantedY: Double): Double {
        val home = circle.homePos ?: return wantedY
        val ceiling = if (circle.phase >= 2) FLY_CEILING_PHASE2 else FLY_CEILING_PHASE1
        val cap = home.y + ceiling
        return if (wantedY > cap) cap else wantedY
    }

    private fun cruiseAroundTarget(target: LivingEntity, origin: Vec3): Vec3 {
        val t = circle.tickCount * 0.02
        val tc = target.position().add(0.0, target.bbHeight / 2.0, 0.0)
        var dx = origin.x - tc.x
        var dz = origin.z - tc.z
        val h = sqrt(dx * dx + dz * dz)
        if (h < 0.001) {
            dx = 1.0; dz = 0.0
        } else {
            dx /= h; dz /= h
        }
        val isPhase2 = circle.phase >= 2
        val preferred = if (isPhase2) PREFERRED_RANGE_PHASE2 else PREFERRED_RANGE_PHASE1
        val approach = if (isPhase2) APPROACH_RANGE_PHASE2 else APPROACH_RANGE_PHASE1
        val backOff = if (isPhase2) BACK_OFF_RANGE_PHASE2 else BACK_OFF_RANGE_PHASE1
        val hoverHeight = if (isPhase2) HOVER_HEIGHT_PHASE2 else HOVER_HEIGHT_PHASE1
        val bobAmp = if (isPhase2) 2.5 else 1.5

        val range = when {
            h > approach -> preferred + 3.0
            h < backOff -> preferred + 6.0
            else -> preferred + sin(t) * 2.5
        }
        val orbit = sin(t) * 4.0
        val px = tc.x + dx * range - dz * orbit
        val pz = tc.z + dz * range + dx * orbit
        val py = tc.y + hoverHeight + sin(t * 1.3) * bobAmp
        return Vec3(px, py, pz)
    }

    private fun hoverAtHome(origin: Vec3): Vec3 {
        val home = circle.homePos ?: origin
        val t = circle.tickCount * 0.02
        val offset = if (circle.phase >= 2) 10.0 else 2.0
        return Vec3(home.x, home.y + offset + sin(t) * 1.5, home.z)
    }

    companion object {
        const val PREFERRED_RANGE_PHASE1 = 14.0
        const val APPROACH_RANGE_PHASE1 = 18.0
        const val BACK_OFF_RANGE_PHASE1 = 7.0
        const val HOVER_HEIGHT_PHASE1 = 10.0
        const val FLY_CEILING_PHASE1 = 24.0

        const val PREFERRED_RANGE_PHASE2 = 22.0
        const val APPROACH_RANGE_PHASE2 = 28.0
        const val BACK_OFF_RANGE_PHASE2 = 12.0
        const val HOVER_HEIGHT_PHASE2 = 22.0
        const val FLY_CEILING_PHASE2 = 42.0
    }
}
