package pub.pigeon.yggdyy.hexmob.content.ur_circle

import net.minecraft.client.Minecraft
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.MoverType
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import pub.pigeon.yggdyy.hexmob.content.IHMMultipartEntity

/**
 * M2 实体登环与承重跟随（Carry-Along）：
 * - 在二/三阶段环体展开后，计算玩家/生物脚底 AABB 是否落在可行走环段顶面容差内，
 *   按环段帧位移 [UrCirclePart.tickDeltaPos] 平滑牵引，消除踩环滑落；
 * - 客户端对本地玩家（LocalPlayer）同步执行，服务端对非玩家实体执行，彻底避免 MC 客户端移动回弹；
 * - 发动 RingSpinSkill（甩尾狂飙）或 FOLD_GRIND（合拢翻折）时故意削弱阻尼并将玩家甩离环面，
 *   转化为 Blink / Impulse / 缓降二次登环的立体空战博弈。
 */
object UrCircleCarryAlong {
    fun applyClientCarryAlong(client: Minecraft) {
        val player = client.player ?: return
        val level = client.level ?: return
        if (player.isSpectator || player.abilities.flying) return

        val pBox = player.boundingBox
        val footY = pBox.minY

        IHMMultipartEntity.updateInstances()
        for (inst in IHMMultipartEntity.instances) {
            val circle = inst as? UrCircleEntity ?: continue
            if (circle.level() !== level || !circle.canStandOnRings()) continue
            if (!circle.getPartsBoundingBox().inflate(4.0).intersects(pBox)) continue

            val candidate = findSupportingPlatform(circle, player, footY) ?: continue
            val delta = candidate.tickDeltaPos()
            if (delta.lengthSqr() < 1.0E-6) continue

            val unstable = circle.entityData.get(UrCircleEntity.RING_SPINNING) ||
                circle.circleState == CircleState.FOLD_GRIND
            if (unstable) {
                // 失稳期：仅保留 25% 跟随阻尼，并沿旋转切线与离心方向甩离玩家
                val origin = circle.position().add(0.0, circle.bbHeight * 0.5, 0.0)
                val radial = player.position().subtract(origin).let { Vec3(it.x, 0.0, it.z) }
                val outward = if (radial.lengthSqr() > 1.0E-4) radial.normalize().scale(0.32) else Vec3.ZERO
                val throwVel = delta.scale(1.45).add(outward).add(0.0, 0.24, 0.0)
                player.move(MoverType.SELF, delta.scale(0.25))
                player.setDeltaMovement(player.deltaMovement.add(throwVel))
                player.hasImpulse = true
            } else {
                player.move(MoverType.SELF, delta)
                player.fallDistance = 0.0F
            }
            return
        }
    }

    fun applyServerCarryAlong(circle: UrCircleEntity) {
        if (!circle.canStandOnRings()) return
        val level = circle.level()
        val hull = circle.getPartsBoundingBox()
        val entities = level.getEntitiesOfClass(LivingEntity::class.java, hull) {
            it !== circle && !isUrCircleAlly(it) && it !is Player
        }
        val unstable = circle.entityData.get(UrCircleEntity.RING_SPINNING) ||
            circle.circleState == CircleState.FOLD_GRIND
        val origin = circle.position().add(0.0, circle.bbHeight * 0.5, 0.0)

        for (ent in entities) {
            val part = findSupportingPlatform(circle, ent, ent.boundingBox.minY) ?: continue
            val delta = part.tickDeltaPos()
            if (delta.lengthSqr() < 1.0E-6) continue
            if (unstable) {
                val radial = ent.position().subtract(origin).let { Vec3(it.x, 0.0, it.z) }
                val outward = if (radial.lengthSqr() > 1.0E-4) radial.normalize().scale(0.30) else Vec3.ZERO
                ent.move(MoverType.SELF, delta.scale(0.25))
                ent.setDeltaMovement(ent.deltaMovement.add(delta.scale(1.35)).add(outward).add(0.0, 0.22, 0.0))
            } else {
                ent.move(MoverType.SELF, delta)
                ent.fallDistance = 0.0F
            }
        }
    }

    private fun findSupportingPlatform(circle: UrCircleEntity, entity: LivingEntity, footY: Double): UrCirclePart? {
        val horizBox = entity.boundingBox.inflate(0.35, 0.0, 0.35)
        var best: UrCirclePart? = null
        var bestDist = Double.MAX_VALUE
        for (part in circle.getAllParts()) {
            if (!part.active || !part.walkPlatform) continue
            val pBox = part.boundingBox
            if (footY < pBox.maxY - 0.55 || footY > pBox.maxY + 0.75) continue
            if (horizBox.maxX < pBox.minX || horizBox.minX > pBox.maxX) continue
            if (horizBox.maxZ < pBox.minZ || horizBox.minZ > pBox.maxZ) continue
            val d = entity.distanceToSqr(part)
            if (d < bestDist) {
                bestDist = d
                best = part
            }
        }
        return best
    }
}
