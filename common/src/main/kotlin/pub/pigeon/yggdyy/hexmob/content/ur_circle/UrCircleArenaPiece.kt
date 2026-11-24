package pub.pigeon.yggdyy.hexmob.content.ur_circle

import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.RandomSource
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.StructureManager
import net.minecraft.world.level.WorldGenLevel
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.chunk.ChunkGenerator
import net.minecraft.world.level.levelgen.structure.BoundingBox
import net.minecraft.world.level.levelgen.structure.StructurePiece
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager
import pub.pigeon.yggdyy.hexmob.registry.HexMobEntities
import pub.pigeon.yggdyy.hexmob.registry.HexMobStructurePieceTypes

/**
 * ur_circle 祭坛结构块：放置祭坛模板方块（忽略模板内实体），并在原点区块生成一只沉睡的大环。
 */
class UrCircleArenaPiece private constructor(box: BoundingBox) :
    StructurePiece(HexMobStructurePieceTypes.UR_CIRCLE_ARENA.get(), 0, box) {

    private var templateId = ResourceLocation("hexmob", "ur_circle")
    private var rotation = Rotation.NONE
    private var pos = BlockPos.ZERO

    constructor(templateManager: StructureTemplateManager, templateId: ResourceLocation, pos: BlockPos, rotation: Rotation) :
        this(makeBox(templateManager, templateId, pos, rotation)) {
        this.templateId = templateId
        this.rotation = rotation
        this.pos = pos
    }

    constructor(context: StructurePieceSerializationContext, tag: CompoundTag) :
        this(makeBox(context.structureTemplateManager(), ResourceLocation(tag.getString("Template")), loadPos(tag), loadRotation(tag))) {
        templateId = ResourceLocation(tag.getString("Template"))
        rotation = loadRotation(tag)
        pos = loadPos(tag)
    }

    override fun addAdditionalSaveData(context: StructurePieceSerializationContext, tag: CompoundTag) {
        tag.putString("Template", templateId.toString())
        tag.putString("Rotation", rotation.name)
        tag.putInt("TPosX", pos.x)
        tag.putInt("TPosY", pos.y)
        tag.putInt("TPosZ", pos.z)
    }

    override fun postProcess(
        level: WorldGenLevel,
        structureManager: StructureManager,
        generator: ChunkGenerator,
        random: RandomSource,
        box: BoundingBox,
        chunkPos: ChunkPos,
        chunkOrigin: BlockPos
    ) {
        val server = (level.level as? net.minecraft.server.level.ServerLevel)?.server ?: return
        val template = server.structureManager.get(templateId).orElse(null) ?: return
        val settings = StructurePlaceSettings()
            .setRotation(rotation)
            .setMirror(Mirror.NONE)
            .setBoundingBox(box)
            .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK)
            .setIgnoreEntities(true) // 模板方块照放，实体由 spawnArenaBoss 控制只放一只
        template.placeInWorld(level, this.pos, this.pos, settings, random, 2)
        // 只在 piece 原点所在 chunk 生成一次 boss：StructurePiece.postProcess 会对每个
        // 与 box 相交的 chunk 各调用一次，祭坛横跨 2×2 chunk，不加门控会重复生成。
        if (chunkPos.x == SectionPos.blockToSectionCoord(this.pos.x) && chunkPos.z == SectionPos.blockToSectionCoord(this.pos.z)) {
            spawnArenaBoss(level)
        }
    }

    /**
     * 祭坛老板：代码生成一只**沉睡**的大环，放在清空区正中。
     * - 模板 nbt 原样保留两只，但 placeInWorld 已 ignoreEntities，这里只放一只；
     * - setDormant(true) 先于 addFreshEntity：实体被 ProtoChunk 序列化暂存时
     *   会把 hexmob:Dormant 写进 NBT，实体化后天生沉睡；
     * - UrCircleEntity 实体化后的首个 tick 还有 checkArenaDormancy 兜底自查。
     */
    private fun spawnArenaBoss(level: WorldGenLevel) {
        val boss = UrCircleEntity(HexMobEntities.UR_CIRCLE.get(), level.level)
        boss.setPos(pos.x + 5.0, pos.y + 3.0, pos.z + 5.0)
        boss.setDormant(true)
        level.addFreshEntityWithPassengers(boss)
    }

    override fun getType(): StructurePieceType = HexMobStructurePieceTypes.UR_CIRCLE_ARENA.get()

    companion object {
        private fun loadRotation(tag: CompoundTag): Rotation = Rotation.valueOf(tag.getString("Rotation"))
        private fun loadPos(tag: CompoundTag): BlockPos = BlockPos(tag.getInt("TPosX"), tag.getInt("TPosY"), tag.getInt("TPosZ"))

        private fun makeBox(templateManager: StructureTemplateManager, templateId: ResourceLocation, pos: BlockPos, rotation: Rotation): BoundingBox {
            val template = templateManager.get(templateId).orElse(null)
            val size = template?.getSize(rotation) ?: return BoundingBox.fromCorners(pos, pos)
            return BoundingBox.fromCorners(pos, pos.offset(size.x - 1, size.y - 1, size.z - 1))
        }
    }
}
