package pub.pigeon.yggdyy.hexmob.content.quench_allay.action

import at.petrak.hexcasting.api.casting.ParticleSpray
import at.petrak.hexcasting.api.casting.RenderedSpell
import at.petrak.hexcasting.api.casting.castables.SpellAction
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.getLivingEntityButNotArmorStand
import at.petrak.hexcasting.api.casting.iota.Iota
import at.petrak.hexcasting.api.casting.mishaps.MishapBadEntity
import at.petrak.hexcasting.api.casting.mishaps.MishapEntityTooFarAway
import at.petrak.hexcasting.api.misc.MediaConstants
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.animal.allay.Allay
import net.minecraft.world.item.ItemStack
import pub.pigeon.yggdyy.hexmob.content.quench_allay.QuenchAllay
import pub.pigeon.yggdyy.hexmob.registry.HexMobEntities

/**
 * 将一只普通悦灵（Allay）淬炼成淬晶悦灵（QuenchAllay）：同位置、同数据转换。
 * 目标必须是 Allay；若已是淬晶悦灵则视为无操作；越界触发 MishapEntityTooFarAway。
 */
object OpQuenchAllayCreate : SpellAction {
    override val argc: Int
        get() = 1

    override fun execute(
        args: List<Iota>,
        env: CastingEnvironment,
    ): SpellAction.Result {
        val entity = args.getLivingEntityButNotArmorStand(0, argc)
        if (entity !is Allay) {
            throw MishapBadEntity.of(entity, "allay")
        }
        if (!env.isEntityInRange(entity)) {
            throw MishapEntityTooFarAway(entity)
        }

        return SpellAction.Result(
            effect = object : RenderedSpell {
                override fun cast(env: CastingEnvironment) {
                    val allay = entity
                    // 已经是淬晶悦灵就不再重复淬炼
                    if (allay is QuenchAllay) return
                    // 服务端转换：保留物品栏与绑定玩家等脑记忆
                    val inv = allay.getInventory()
                    val savedInv = Array(inv.containerSize) { i ->
                        val stack = inv.getItem(i).copy()
                        inv.setItem(i, ItemStack.EMPTY)
                        stack
                    }
                    val likedPlayer = allay.brain.getMemory(MemoryModuleType.LIKED_PLAYER).orElse(null)
                    val likedNote = allay.brain.getMemory(MemoryModuleType.LIKED_NOTEBLOCK_POSITION).orElse(null)
                    val likedNoteCd = allay.brain.getMemory(MemoryModuleType.LIKED_NOTEBLOCK_COOLDOWN_TICKS).orElse(null)

                    val converted = allay.convertTo(HexMobEntities.QUENCH_ALLAY.get(), true) ?: return
                    val newInv = converted.getInventory()
                    for (i in savedInv.indices) {
                        if (i < newInv.containerSize && !savedInv[i].isEmpty) {
                            newInv.setItem(i, savedInv[i])
                        }
                    }
                    if (likedPlayer != null) {
                        converted.brain.setMemory(MemoryModuleType.LIKED_PLAYER, likedPlayer)
                    }
                    if (likedNote != null) {
                        converted.brain.setMemory(MemoryModuleType.LIKED_NOTEBLOCK_POSITION, likedNote)
                    }
                    if (likedNoteCd != null) {
                        converted.brain.setMemory(MemoryModuleType.LIKED_NOTEBLOCK_COOLDOWN_TICKS, likedNoteCd)
                    }
                }
            },
            cost = MediaConstants.QUENCHED_SHARD_UNIT,
            particles = listOf(ParticleSpray.burst(entity.position(), 3.0, 40)),
        )
    }
}
