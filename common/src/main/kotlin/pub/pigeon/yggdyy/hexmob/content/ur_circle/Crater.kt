package pub.pigeon.yggdyy.hexmob.content.ur_circle

import at.petrak.hexcasting.common.lib.HexBlocks
import at.petrak.hexcasting.common.lib.HexSounds
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.tags.TagKey
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.GameRules
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import pub.pigeon.yggdyy.hexmob.config.HexMobServerConfig

private val HEX_AMETHYST_BLOCKS = TagKey.create(Registries.BLOCK, ResourceLocation("hexcasting", "amethyst_blocks"))
private val HEX_SLATE_BLOCKS = TagKey.create(Registries.BLOCK, ResourceLocation("hexcasting", "slate_blocks"))

fun canUrCircleGrief(level: Level): Boolean =
    level.gameRules.getBoolean(GameRules.RULE_MOBGRIEFING) && HexMobServerConfig.config.urCircleTerrainDamage

/**
 * 大环/石板弹的"砸地"：在 pos 处破坏 3×3×3 范围（跳过空气、液体与不可破坏方块）。
 * 若当前关闭了地形破坏（mobGriefing 或配置 urCircleTerrainDamage），仅播放 Hex 音效与媒质粒子。
 */
fun craterAround(
    level: Level,
    pos: BlockPos,
    dropper: Entity,
    drop: Boolean = true,
    skipHexStones: Boolean = false,
    convertChance: Float = 0.0F,
) {
    if (!canUrCircleGrief(level)) {
        val rng = level.random
        for (k in 0 until 12) {
            UrCircleHexVfx.spawnConjure(
                level,
                UrCircleHexVfx.AMETHYST_VIOLET,
                pos.x + 0.5 + (rng.nextDouble() - 0.5) * 2.5,
                pos.y + 0.5 + (rng.nextDouble() - 0.5) * 1.5,
                pos.z + 0.5 + (rng.nextDouble() - 0.5) * 2.5,
                (rng.nextDouble() - 0.5) * 0.15,
                rng.nextDouble() * 0.15,
                (rng.nextDouble() - 0.5) * 0.15,
            )
        }
        level.playSound(null, pos, HexSounds.CAST_SPELL, SoundSource.BLOCKS, 2.0F, 1.0F)
        return
    }
    for (dx in -1..1) for (dy in -1..1) for (dz in -1..1) {
        val p = pos.offset(dx, dy, dz)
        val state = level.getBlockState(p)
        if (state.isAir || !state.fluidState.isEmpty) continue
        if (state.getDestroySpeed(level, p) < 0.0F) continue
        if (skipHexStones && (state.`is`(HEX_AMETHYST_BLOCKS) || state.`is`(HEX_SLATE_BLOCKS))) continue
        if (convertChance > 0.0F && level.random.nextFloat() < convertChance) {
            val converted = if (level.random.nextBoolean()) {
                Blocks.AMETHYST_BLOCK.defaultBlockState()
            } else {
                HexBlocks.SLATE_BLOCK.defaultBlockState()
            }
            level.setBlockAndUpdate(p, converted)
        } else {
            level.destroyBlock(p, drop, dropper)
        }
    }
    level.playSound(null, pos, HexSounds.CAST_SPELL, SoundSource.BLOCKS, 2.0F, 1.0F)
}

/** 临时紫水晶方块跟踪器：定时自动消散，避免封锁技能永久污染战场。 */
object TempAmethystTracker {
    private data class Entry(
        val dimension: ResourceKey<Level>,
        val pos: BlockPos,
        val expireGameTime: Long,
    )

    private val entries = ArrayList<Entry>()

    fun placeTemporary(level: ServerLevel, pos: BlockPos, lifetimeTicks: Int = 160) {
        if (!level.getBlockState(pos).isAir) return
        level.setBlockAndUpdate(pos, Blocks.AMETHYST_BLOCK.defaultBlockState())
        entries.add(Entry(level.dimension(), pos.immutable(), level.gameTime + lifetimeTicks))
    }

    fun tickAll(server: MinecraftServer) {
        if (entries.isEmpty()) return
        val it = entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            val level = server.getLevel(e.dimension)
            if (level == null) {
                it.remove()
                continue
            }
            if (level.gameTime < e.expireGameTime) continue
            it.remove()
            if (level.isLoaded(e.pos) && level.getBlockState(e.pos).`is`(Blocks.AMETHYST_BLOCK)) {
                level.setBlockAndUpdate(e.pos, Blocks.AIR.defaultBlockState())
                UrCircleHexVfx.spawnConjure(
                    level,
                    UrCircleHexVfx.AMETHYST_VIOLET,
                    e.pos.x + 0.5,
                    e.pos.y + 0.5,
                    e.pos.z + 0.5,
                    0.0,
                    0.06,
                    0.0,
                )
                level.playSound(null, e.pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.5F, 1.3F)
            }
        }
    }
}
