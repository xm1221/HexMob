package pub.pigeon.yggdyy.hexmob.content.ur_circle

import at.petrak.hexcasting.common.lib.HexSounds
import net.minecraft.core.BlockPos
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level

/**
 * 大环/石板弹的"砸地"：在 pos 处破坏 3×3×3 范围（跳过空气、液体与不可破坏方块
 * ——getDestroySpeed < 0，如基岩/屏障/命令方块），并播放法术释放音效（声音偏大）。
 */
fun craterAround(level: Level, pos: BlockPos, dropper: Entity) {
    for (dx in -1..1) for (dy in -1..1) for (dz in -1..1) {
        val p = pos.offset(dx, dy, dz)
        val state = level.getBlockState(p)
        if (state.isAir || !state.fluidState.isEmpty) continue
        if (state.getDestroySpeed(level, p) < 0.0F) continue
        level.destroyBlock(p, true, dropper)
    }
    level.playSound(null, pos, HexSounds.CAST_SPELL, SoundSource.BLOCKS, 2.0F, 1.0F)
}
