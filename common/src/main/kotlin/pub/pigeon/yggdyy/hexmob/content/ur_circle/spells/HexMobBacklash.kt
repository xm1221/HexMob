package pub.pigeon.yggdyy.hexmob.content.ur_circle.spells

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.eval.env.CircleCastEnv
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.AABB
import pub.pigeon.yggdyy.hexmob.api.casting.env.MobCastingEnv
import pub.pigeon.yggdyy.hexmob.content.ur_circle.UrCircleEntity

/**
 * 反向过度施法（媒质虹吸反噬）：
 * 只有当附近大环处于可预判的"媒质虹吸"姿态时，玩家在附近直接施法才会触发反噬；
 * 法术环（[CircleCastEnv]）与淬晶悦灵等生物施法（[MobCastingEnv]）等间接施法不受此反噬。
 */
object HexMobBacklash {
    private val suppressLocal: ThreadLocal<Boolean> = ThreadLocal.withInitial { false }

    var suppress: Boolean
        get() = suppressLocal.get()
        set(value) {
            if (value) suppressLocal.set(true) else suppressLocal.remove()
        }

    fun onCast(env: CastingEnvironment) {
        if (suppress) return
        if (env is CircleCastEnv || env is MobCastingEnv) return
        val player: ServerPlayer = env.caster ?: return
        if (!player.isAlive) return
        val level = player.level()
        if (level.isClientSide) return
        val maxDistSq = UrCircleEntity.BACKLASH_RANGE * UrCircleEntity.BACKLASH_RANGE
        val circle = level.getEntitiesOfClass(
            UrCircleEntity::class.java,
            AABB(player.blockPosition()).inflate(UrCircleEntity.BACKLASH_RANGE)
        ) { it.isAlive && it.isSiphoning() && it.distanceToSqr(player) <= maxDistSq }
            .minByOrNull { it.distanceToSqr(player) } ?: return
        circle.accumulateBacklash(player)
    }
}
