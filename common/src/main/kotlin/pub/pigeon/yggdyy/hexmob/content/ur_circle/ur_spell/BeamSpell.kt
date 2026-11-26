package pub.pigeon.yggdyy.hexmob.content.ur_circle.ur_spell

import at.petrak.hexcasting.api.casting.RenderedSpell
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.getPositiveDoubleUnderInclusive
import at.petrak.hexcasting.api.casting.getVec3
import at.petrak.hexcasting.api.casting.iota.Iota
import at.petrak.hexcasting.api.misc.MediaConstants
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import pub.pigeon.yggdyy.hexmob.api.casting.actions.UrCircleSpell

/**
 * 【核心光线】在给定位置（[args[0]] vec）朝目标实体（[args[1]] entity）打出一条持续光束：
 * 持续时间由第 2 个参数（[args[2]] 数字 iota，秒）决定（上限 [MAX_DURATION_SECONDS] 秒以防溢出），
 * **媒质消耗按持续时间累计**。
 * 施法范围检查：起点位置超出范围抛 [at.petrak.hexcasting.api.casting.mishaps.MishapBadLocation]，
 * 目标实体超出范围抛 [at.petrak.hexcasting.api.casting.mishaps.MishapEntityTooFarAway]。
 * 光束每 [BeamTicker.DAMAGE_INTERVAL] tick 对目标直击（必中）并对其周围 AoE 半伤
 * （照大环核心光线：高血量实体按 20% 最大生命扣血，设有单次上限）。
 */
class BeamSpell : UrCircleSpell() {
    override val argc: Int get() = 3

    private fun durationSeconds(args: List<Iota>): Double =
        args.getPositiveDoubleUnderInclusive(2, MAX_DURATION_SECONDS, argc)

    override fun cost(args: List<Iota>, env: CastingEnvironment): Long =
        COST_BASE + (durationSeconds(args) * COST_PER_SECOND).toLong()

    override fun makeEffect(args: List<Iota>, env: CastingEnvironment): RenderedSpell {
        val origin = args.getVec3(0, argc)
        env.assertVecInRange(origin) // 起点位置施法范围检查
        val target = livingTarget(args, env, 1) // 目标实体施法范围检查
        return Spell(origin, target, durationSeconds(args))
    }

    class Spell(
        private val origin: Vec3,
        private val target: LivingEntity,
        private val durationSeconds: Double,
    ) : RenderedSpell {
        override fun cast(env: CastingEnvironment) {
            val caster = env.castingEntity ?: return
            val level = env.world as? ServerLevel ?: return
            if (level.isClientSide || target.level() !== level) return
            val ticks = (durationSeconds * 20.0).toInt().coerceIn(1, MAX_DURATION_TICKS)
            BeamTicker.launch(level, origin, caster, target, ticks)
        }
    }

    companion object {
        /** 最大持续秒数，防止超长参数引发 Long/Int 溢出。 */
        const val MAX_DURATION_SECONDS = 300.0
        const val MAX_DURATION_TICKS = 6000
        /** 基础消耗（媒质）。 */
        const val COST_BASE = 10L
        /** 每持续 1 秒的消耗（媒质，照原每 tick 1 shard，20tick=1秒）。 */
        const val COST_PER_SECOND = MediaConstants.SHARD_UNIT * 20L
    }
}
