package pub.pigeon.yggdyy.hexmob.client

import net.minecraft.client.Minecraft
import net.minecraft.world.phys.AABB
import pub.pigeon.yggdyy.hexmob.content.ur_circle.CircleState
import pub.pigeon.yggdyy.hexmob.content.ur_circle.UrCircleEntity
import kotlin.math.sqrt

/**
 * 追踪距离玩家最近且清醒的大环，驱动二/三阶段天穹暗蚀、云层遮蔽与远景雾效渐变。
 */
object UrCircleSkyTracker {
    private const val INNER_RANGE = 220.0
    private const val OUTER_RANGE = 520.0

    @Volatile
    private var domainIntensity: Float = 0.0F

    @Volatile
    private var phase3Intensity: Float = 0.0F

    @JvmStatic
    fun getIntensity(): Float = domainIntensity

    @JvmStatic
    fun getPhase3Intensity(): Float = phase3Intensity

    fun tick(client: Minecraft) {
        val level = client.level
        val camPos = client.gameRenderer.mainCamera.position
        if (level == null) {
            domainIntensity = 0.0F
            phase3Intensity = 0.0F
            return
        }

        val box = AABB(camPos, camPos).inflate(OUTER_RANGE)
        var bestTarget = 0.0F
        var bestPhase3 = 0.0F
        for (circle in level.getEntitiesOfClass(UrCircleEntity::class.java, box)) {
            if (!circle.isAlive || circle.isDormantState()) continue
            val dx = circle.x - camPos.x
            val dy = (circle.y + circle.bbHeight * 0.5) - camPos.y
            val dz = circle.z - camPos.z
            val dist = sqrt(dx * dx + dy * dy + dz * dz)
            if (dist >= OUTER_RANGE) continue

            val distFade = if (dist <= INNER_RANGE) {
                1.0F
            } else {
                val raw = ((OUTER_RANGE - dist) / (OUTER_RANGE - INNER_RANGE)).coerceIn(0.0, 1.0)
                (raw * raw * (3.0 - 2.0 * raw)).toFloat()
            }

            val basePresence = when {
                circle.circleState == CircleState.DYING -> {
                    val dyingMul = if (circle.stateTicks < UrCircleEntity.CORE_BREAK_TICK) {
                        1.0F
                    } else {
                        val after = (circle.stateTicks - UrCircleEntity.CORE_BREAK_TICK) /
                            (UrCircleEntity.DEATH_END_TICK - UrCircleEntity.CORE_BREAK_TICK).toFloat()
                        (1.0F - after).coerceIn(0.0F, 1.0F)
                    }
                    (if (circle.phase >= 2) 0.78F else 0.25F) * dyingMul
                }
                circle.circleState == CircleState.SHORT_CIRCUIT -> 0.16F
                circle.phase >= 3 -> 0.65F + 0.35F * circle.phase3Reveal
                circle.phase >= 2 -> 0.16F + 0.36F * circle.phase2Reveal
                circle.circleState == CircleState.CHANNELING || circle.circleState == CircleState.BEAM -> 0.22F
                else -> 0.12F
            }

            val contribution = basePresence * distFade
            if (contribution > bestTarget) {
                bestTarget = contribution
            }
            if (circle.phase >= 3 && circle.circleState != CircleState.SHORT_CIRCUIT && circle.circleState != CircleState.DYING) {
                val p3Contribution = circle.phase3Reveal.coerceIn(0.0F, 1.0F) * distFade
                if (p3Contribution > bestPhase3) {
                    bestPhase3 = p3Contribution
                }
            }
        }

        domainIntensity = when {
            domainIntensity < bestTarget -> (domainIntensity + 0.025F).coerceAtMost(bestTarget)
            domainIntensity > bestTarget -> (domainIntensity - 0.025F).coerceAtLeast(bestTarget)
            else -> domainIntensity
        }
        phase3Intensity = when {
            phase3Intensity < bestPhase3 -> (phase3Intensity + 0.025F).coerceAtMost(bestPhase3)
            phase3Intensity > bestPhase3 -> (phase3Intensity - 0.025F).coerceAtLeast(bestPhase3)
            else -> phase3Intensity
        }
    }
}
