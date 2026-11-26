package pub.pigeon.yggdyy.hexmob.content.akashic_library

import at.petrak.hexcasting.api.HexAPI
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.enchantment.EnchantmentHelper
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.level.GameRules
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import pub.pigeon.yggdyy.hexmob.HexMob
import pub.pigeon.yggdyy.hexmob.content.amethyst_silverfish.AmethystSilverfishEntity
import pub.pigeon.yggdyy.hexmob.registry.HexMobEntities

class AmethystInfestedBlock(properties: Properties) : Block(properties) {
    override fun playerDestroy(
        level: Level,
        player: Player,
        pos: BlockPos,
        state: BlockState,
        blockEntity: BlockEntity?,
        tool: ItemStack,
    ) {
        if (player.isCreative) return
        super.playerDestroy(level, player, pos, state, blockEntity, tool)
        if (level is ServerLevel &&
            level.gameRules.getBoolean(GameRules.RULE_DOBLOCKDROPS) &&
            EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SILK_TOUCH, tool) > 0
        ) {
            val selfId = BuiltInRegistries.BLOCK.getKey(this)
            val hostId = infestMap.entries.firstOrNull { it.value == selfId }?.key
            val dropBlock = if (hostId != null) BuiltInRegistries.BLOCK.get(hostId) else this
            popResource(level, pos, ItemStack(dropBlock))
        }
    }

    override fun spawnAfterBreak(
        state: BlockState,
        level: ServerLevel,
        pos: BlockPos,
        stack: ItemStack,
        dropExperience: Boolean
    ) {
        super.spawnAfterBreak(state, level, pos, stack, dropExperience)
        if (level.gameRules.getBoolean(GameRules.RULE_DOBLOCKDROPS) &&
            EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SILK_TOUCH, stack) == 0
        ) {
            val entity = AmethystSilverfishEntity(HexMobEntities.AMETHYST_SILVERFISH.get(), level)
            entity.setPos(pos.center)
            level.addFreshEntity(entity)
            level.playSound(null, pos, SoundEvents.AMETHYST_CLUSTER_PLACE, SoundSource.HOSTILE)
        }
    }
    companion object {
        val infestMap: MutableMap<ResourceLocation, ResourceLocation> = mutableMapOf(
            Pair(HexAPI.modLoc("edified_planks"), HexMob.id("infested_edified_planks")),
            Pair(HexAPI.modLoc("edified_panel"), HexMob.id("infested_edified_panel")),
            Pair(HexAPI.modLoc("edified_tile"), HexMob.id("infested_edified_tile"))
        )
    }
}