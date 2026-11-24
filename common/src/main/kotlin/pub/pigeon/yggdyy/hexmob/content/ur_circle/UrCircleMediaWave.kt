package pub.pigeon.yggdyy.hexmob.content.ur_circle

import at.petrak.hexcasting.common.lib.HexSounds
import net.minecraft.world.phys.Vec3
import pub.pigeon.yggdyy.hexmob.content.ur_circle.spells.UrCircleSkill
import kotlin.math.abs

/**
 * 活体法术环媒质波调度器：
 * 吟唱时驱动媒质波沿石板环逐块传递、点亮当前节点并在相邻促动石间拉弧；
 * 若玩家精准击中正在导通的高亮节点并累计超过阈值，则打断施法触发断路僵直。
 */
class UrCircleMediaWave {
    private var stepCursor = 0
    private var nodeBreakDamage = 0.0F
    private var primaryRingIdx = 1
    private var secondaryRingIdx = -1

    fun start(circle: UrCircleEntity, skill: UrCircleSkill) {
        stepCursor = 0
        nodeBreakDamage = 0.0F
        circle.waveColor = skill.waveColor
        if (!skill.usesMediaWave) {
            primaryRingIdx = -1
            secondaryRingIdx = -1
            circle.activeNodeA = -1
            circle.activeNodeB = -1
            return
        }
        val phase2 = circle.phase >= 2 && circle.rings.size > 2
        primaryRingIdx = if (phase2) 2 else 1
        secondaryRingIdx = if (phase2) 1 else -1

        val primary = circle.rings.getOrNull(primaryRingIdx)
        val secondary = circle.rings.getOrNull(secondaryRingIdx)
        circle.activeNodeA = primary?.parts?.firstOrNull()?.globalIndex ?: -1
        circle.activeNodeB = secondary?.parts?.firstOrNull()?.globalIndex ?: -1
    }

    fun tick(circle: UrCircleEntity, skill: UrCircleSkill) {
        if (!skill.usesMediaWave) return
        val primary = circle.rings.getOrNull(primaryRingIdx) ?: return
        if (primary.parts.isEmpty()) return
        val secondary = circle.rings.getOrNull(secondaryRingIdx)
        val level = circle.level()

        if (circle.stateTicks > 0 && circle.stateTicks % STEP_INTERVAL == 0) {
            val prevIdxA = Math.floorMod(stepCursor, primary.parts.size)
            stepCursor++
            val curIdxA = Math.floorMod(stepCursor, primary.parts.size)
            val prevPartA = primary.parts[prevIdxA]
            val curPartA = primary.parts[curIdxA]
            circle.activeNodeA = curPartA.globalIndex

            if (prevPartA.posNow != Vec3.ZERO && curPartA.posNow != Vec3.ZERO) {
                UrCircleHexVfx.spawnRingArc(level, prevPartA.posNow, curPartA.posNow, skill.waveColor, steps = 8)
                UrCircleHexVfx.playHexNote(level, curPartA.posNow, circle.ringOuterRadius(), stepCursor)
            }

            if (secondary != null && secondary.parts.isNotEmpty()) {
                val prevIdxB = Math.floorMod(-(stepCursor - 1), secondary.parts.size)
                val curIdxB = Math.floorMod(-stepCursor, secondary.parts.size)
                val prevPartB = secondary.parts[prevIdxB]
                val curPartB = secondary.parts[curIdxB]
                circle.activeNodeB = curPartB.globalIndex
                if (prevPartB.posNow != Vec3.ZERO && curPartB.posNow != Vec3.ZERO) {
                    UrCircleHexVfx.spawnRingArc(level, prevPartB.posNow, curPartB.posNow, skill.waveColor, steps = 6)
                }
                // 二阶段时，在主波节点与最近促动石间拉一道媒质弧线
                if (curPartA.posNow != Vec3.ZERO) {
                    val polarOrInner = circle.rings.getOrNull(3)?.takeIf { it.isVisible(circle.phase, circle.phase2Reveal) }
                        ?: circle.rings.getOrNull(0)
                    val impetus = polarOrInner?.parts
                        ?.filter { it.active && it.posNow != Vec3.ZERO }
                        ?.minByOrNull { it.posNow.distanceToSqr(curPartA.posNow) }
                    if (impetus != null) {
                        UrCircleHexVfx.spawnRingArc(level, curPartA.posNow, impetus.posNow, skill.waveColor, steps = 5)
                    }
                }
            } else {
                circle.activeNodeB = -1
            }
        }

        // 每 2 tick 在当前导通石板上冒出高亮提示粒子，确保战斗中一眼可读
        if (circle.stateTicks % 2 == 0) {
            emitNodeSpark(circle, circle.activeNodeA, skill.waveColor)
            if (circle.activeNodeB >= 0) emitNodeSpark(circle, circle.activeNodeB, skill.waveColor)
        }
    }

    private fun emitNodeSpark(circle: UrCircleEntity, globalIdx: Int, color: Int) {
        val part = circle.getAllParts().getOrNull(globalIdx) ?: return
        if (!part.active || part.posNow == Vec3.ZERO) return
        val rng = circle.random
        for (k in 0 until 2) {
            UrCircleHexVfx.spawnConjure(
                circle.level(),
                color,
                part.posNow.x + (rng.nextDouble() - 0.5) * part.bbWidth * 0.6,
                part.posNow.y + (rng.nextDouble() - 0.5) * part.bbHeight * 0.6,
                part.posNow.z + (rng.nextDouble() - 0.5) * part.bbWidth * 0.6,
                0.0,
                0.08,
                0.0,
            )
        }
    }

    /**
     * 若命中了当前导通节点（或同环相邻 1 格），累加打断伤害；
     * 达到技能打断阈值返回 true 触发法术环断路。
     */
    fun onPartHurt(circle: UrCircleEntity, skill: UrCircleSkill, part: UrCirclePart, amount: Float): Boolean {
        if (!skill.usesMediaWave || amount <= 0.0F) return false
        val hitA = isNearNode(circle, primaryRingIdx, circle.activeNodeA, part)
        val hitB = isNearNode(circle, secondaryRingIdx, circle.activeNodeB, part)
        if (!hitA && !hitB) return false

        nodeBreakDamage += amount
        if (part.posNow != Vec3.ZERO) {
            val rng = circle.random
            for (k in 0 until 10) {
                UrCircleHexVfx.spawnConjure(
                    circle.level(),
                    UrCircleHexVfx.SHORT_CIRCUIT_RED,
                    part.posNow.x,
                    part.posNow.y,
                    part.posNow.z,
                    (rng.nextDouble() - 0.5) * 0.25,
                    (rng.nextDouble() - 0.5) * 0.25,
                    (rng.nextDouble() - 0.5) * 0.25,
                )
            }
            UrCircleHexVfx.playRingSound(
                circle.level(),
                part.posNow,
                circle.ringOuterRadius(),
                HexSounds.IMPETUS_REDSTONE_DING,
                baseVolume = 1.6F,
                pitch = 1.35F,
            )
        }
        return nodeBreakDamage >= skill.nodeBreakThreshold(circle)
    }

    private fun isNearNode(circle: UrCircleEntity, ringIdx: Int, nodeGlobalIdx: Int, part: UrCirclePart): Boolean {
        if (ringIdx < 0 || nodeGlobalIdx < 0 || part.ringIndex != ringIdx) return false
        val ring = circle.rings.getOrNull(ringIdx) ?: return false
        val size = ring.parts.size
        if (size == 0) return false
        val nodeIdx = ring.parts.indexOfFirst { it.globalIndex == nodeGlobalIdx }
        val hitIdx = ring.parts.indexOf(part)
        if (nodeIdx < 0 || hitIdx < 0) return false
        val diff = abs(nodeIdx - hitIdx)
        return diff <= 1 || diff >= size - 1
    }

    fun clear(circle: UrCircleEntity) {
        nodeBreakDamage = 0.0F
        circle.activeNodeA = -1
        circle.activeNodeB = -1
    }

    companion object {
        const val STEP_INTERVAL = 5
    }
}
