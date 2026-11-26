package pub.pigeon.yggdyy.hexmob.content.ur_circle.ur_spell

import at.petrak.hexcasting.api.casting.RenderedSpell
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.iota.Iota
import at.petrak.hexcasting.api.misc.MediaConstants
import at.petrak.hexcasting.xplat.IXplatAbstractions
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks
import pub.pigeon.yggdyy.hexmob.api.casting.actions.UrCircleSpell
import pub.pigeon.yggdyy.hexmob.content.ur_circle.TempAmethystTracker

/**
 * 【紫水晶陷阱】在目标实体脚下铺 3×3×3 临时紫水晶块，困住并使其窒息。
 * 大环技能 Amethyst Trap 的玩家施法版：只埋目标脚下，不带大环的自环柱，定时自动消散。
 */
class AmethystTrapSpell : UrCircleSpell() {
    override val argc: Int get() = 1

    override fun cost(args: List<Iota>, env: CastingEnvironment): Long = MediaConstants.SHARD_UNIT

    override fun makeEffect(args: List<Iota>, env: CastingEnvironment): RenderedSpell {
        val target = livingTarget(args, env)
        env.assertPosInRangeForEditing(target.blockPosition())
        return Spell(target)
    }

    class Spell(private val target: LivingEntity) : RenderedSpell {
        override fun cast(env: CastingEnvironment) {
            val level = env.world
            if (level.isClientSide) return
            val sp = env.castingEntity as? ServerPlayer
            val placeStack = ItemStack(Blocks.AMETHYST_BLOCK)
            val base = target.blockPosition()
            for (dx in -1..1) {
                for (dy in -1..1) {
                    for (dz in -1..1) {
                        val pos = base.offset(dx, dy, dz)
                        if (!env.canEditBlockAt(pos)) continue
                        if (!IXplatAbstractions.INSTANCE.isPlacingAllowed(level, pos, placeStack, sp)) continue
                        TempAmethystTracker.placeTemporary(level, pos, TEMP_LIFETIME_TICKS)
                    }
                }
            }
        }
    }

    companion object {
        const val TEMP_LIFETIME_TICKS = 160
    }
}