package pub.pigeon.yggdyy.hexmob.content.ur_circle

import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.goal.target.TargetGoal
import pub.pigeon.yggdyy.hexmob.registry.HexMobTags

/**
 * 大环的索敌：周期扫描附近 32 格内"有智慧"的生物（hexmob:wise tag——
 * 村民、流浪商人、猪灵、玩家、灾厄村民），锁定最近的一个。
 *
 * 关键点：
 * - 每 10 tick 重扫一次（canUse/canContinueToUse/tick 都会触发），
 *   解决"锁死第一个目标永不换"的问题；
 * - 已有存活目标时不抢换，保证"被打还击"（hurt() 设的目标）能一直钉在玩家身上。
 *
 * 大环是 Mob 而非 PathfinderMob，用不了 vanilla 的 NearestAttackableTargetGoal，
 * 所以自写一个（TargetGoal 只需 Mob）。
 */
class UrCircleTargetGoal(private val circle: UrCircleEntity) : TargetGoal(circle, true) {

    override fun canUse(): Boolean {
        rescanIfDue()
        val t = circle.target ?: return false
        return t.isAlive
    }

    override fun canContinueToUse(): Boolean {
        rescanIfDue()
        val t = circle.target ?: return false
        return t.isAlive && circle.distanceToSqr(t) <= 40.0 * 40.0
    }

    override fun tick() {
        rescanIfDue()
    }

    /** 每 10 tick 重扫：仅当没有存活目标时才重新锁定最近的智慧生物。 */
    private fun rescanIfDue() {
        if (circle.tickCount % 10 != 0) return
        val cur = circle.target
        if (cur != null && cur.isAlive) return
        val target = circle.level()
            .getEntitiesOfClass(LivingEntity::class.java, circle.boundingBox.inflate(32.0))
            .filter { it.isAlive && it !== circle && it.type.`is`(HexMobTags.EntityTypeTags.WISE) }
            .minByOrNull { it.distanceToSqr(circle) }
        if (target != null) {
            circle.target = target
        }
    }
}
