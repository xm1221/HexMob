package pub.pigeon.yggdyy.hexmob.content.ur_circle

import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.goal.target.TargetGoal
import pub.pigeon.yggdyy.hexmob.registry.HexMobTags

/**
 * 大环的索敌：周期扫描附近 32 格内"有智慧"的生物（hexmob:wise tag——
 * 村民、流浪商人、猪灵、玩家、灾厄村民），锁定最近的一个。
 *
 * 大环是 Mob 而非 PathfinderMob，用不了 vanilla 的
 * NearestAttackableTargetGoal，所以自写一个（TargetGoal 只需 Mob）。
 */
class UrCircleTargetGoal(private val circle: UrCircleEntity) : TargetGoal(circle, true) {

    override fun canUse(): Boolean {
        // 每 10 tick 重新扫描；中间只维持现有目标
        if (circle.tickCount % 10 != 0) {
            val current = circle.target
            return current != null && current.isAlive
        }
        val target = circle.level()
            .getEntitiesOfClass(LivingEntity::class.java, circle.boundingBox.inflate(32.0))
            .filter { it.isAlive && it.type.`is`(HexMobTags.EntityTypeTags.WISE) }
            .minByOrNull { it.distanceToSqr(circle) }
        if (target != null) {
            circle.target = target
        }
        return target != null
    }

    override fun canContinueToUse(): Boolean {
        val t = circle.target ?: return false
        return t.isAlive && circle.distanceToSqr(t) <= 40.0 * 40.0
    }
}
