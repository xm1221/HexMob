package pub.pigeon.yggdyy.hexmob.content.ur_circle.renderers

import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.blaze3d.vertex.VertexFormat
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.client.resources.model.BakedModel
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import org.joml.Matrix3f
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import pub.pigeon.yggdyy.hexmob.HexMob
import pub.pigeon.yggdyy.hexmob.client.UrCircleHudTracker
import pub.pigeon.yggdyy.hexmob.content.ur_circle.CircleState
import pub.pigeon.yggdyy.hexmob.content.ur_circle.UrCircleEntity
import pub.pigeon.yggdyy.hexmob.content.ur_circle.spells.RingSpinSkill
import pub.pigeon.yggdyy.hexmob.content.ur_circle.subentities.SlateSymbolType
import pub.pigeon.yggdyy.hexmob.util.rotateDA
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 大环立体建筑化 + 零闪烁渲染器（五重分离 Pass，严禁共用 BufferBuilder 混用）：
 * 1. 【建筑实体块层】外环深暗板岩/黑石石垣城冠 + 24 根向外突出紫水晶晶簇锯齿 + 倾斜中环石板背骨 + 4 座环上卫星晶柱 + 28 块悬升重力碎石
 * 2. 【立体盒状星盘环带】上下表面与内外环壁物理拉开厚度（绝无零距共面 Z-Fighting 闪烁）
 * 3. 【日蚀暗紫帷幕】内外环之间上下错开分离的半透明暗靛蓝媒质膜（仅写颜色不写深度，柔和遮光且不吞没后续水体/粒子）
 * 4. 【垂天四柱】自外环 4 座卫星节点斜扎入大地水晶峰/地表的粗壮紫金半透明光柱
 * 5. 【纯几何星阵线架】内嵌金线六芒星、倾斜中环紫光尾迹、核心陀螺仪、虹吸晶笼、节点电路弧与斩切光刃
 */
object UrCircleVfxRenderer {
    private val ASTROLABE_RING_TEX = HexMob.id("textures/entity/ur_circle/astrolabe_ring.png")
    private val ECLIPSE_VEIL_TEX = HexMob.id("textures/entity/ur_circle/eclipse_veil.png")
    private val CELESTIAL_FIRMAMENT_TEX = HexMob.id("textures/entity/ur_circle/celestial_firmament.png")
    private val BEACON_TEX = ResourceLocation("textures/entity/beacon_beam.png")
    private const val RING_SEGMENTS = 48
    private const val PILLAR_CACHE_INTERVAL = 4
    private const val DEBRIS_CACHE_INTERVAL = 5

    private const val DOME_PHI_BANDS = 14
    private const val DOME_THETA_SEGS = 36
    private val DOME_MAX_PHI_RAD = Math.toRadians(108.0)
    private val DOME_COS_THETA = DoubleArray(DOME_THETA_SEGS + 1) { idx ->
        cos((idx.toDouble() / DOME_THETA_SEGS) * Math.PI * 2.0)
    }
    private val DOME_SIN_THETA = DoubleArray(DOME_THETA_SEGS + 1) { idx ->
        sin((idx.toDouble() / DOME_THETA_SEGS) * Math.PI * 2.0)
    }
    private val DOME_SIN_PHI = DoubleArray(DOME_PHI_BANDS + 1) { band ->
        sin(DOME_MAX_PHI_RAD * band / DOME_PHI_BANDS)
    }
    private val DOME_COS_PHI = DoubleArray(DOME_PHI_BANDS + 1) { band ->
        cos(DOME_MAX_PHI_RAD * band / DOME_PHI_BANDS)
    }
    private val DOME_BAND_ALPHA = FloatArray(DOME_PHI_BANDS + 1) { band ->
        val phiDeg = 108.0 * band / DOME_PHI_BANDS
        when {
            phiDeg <= 66.0 -> 1.0F
            phiDeg >= 108.0 -> 0.0F
            else -> {
                val u = ((phiDeg - 66.0) / 42.0).coerceIn(0.0, 1.0)
                (1.0 - u * u * (3.0 - 2.0 * u)).toFloat()
            }
        }
    }

    private object UrCircleRenderTypes : RenderType(
        "hexmob_ur_circle_vfx_helper",
        DefaultVertexFormat.NEW_ENTITY,
        VertexFormat.Mode.QUADS,
        256,
        false,
        true,
        Runnable {},
        Runnable {},
    ) {
        fun noFogSkyTranslucent(name: String, texture: ResourceLocation): RenderType {
            val state = CompositeState.builder()
                .setShaderState(POSITION_COLOR_TEX_SHADER)
                .setTextureState(TextureStateShard(texture, false, false))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setCullState(NO_CULL)
                .setWriteMaskState(COLOR_WRITE)
                .createCompositeState(false)
            return create(name, DefaultVertexFormat.POSITION_COLOR_TEX, VertexFormat.Mode.QUADS, 256, false, true, state)
        }
    }

    private val ECLIPSE_VEIL_RENDER_TYPE = UrCircleRenderTypes.noFogSkyTranslucent("hexmob_eclipse_veil", ECLIPSE_VEIL_TEX)
    private val CELESTIAL_FIRMAMENT_RENDER_TYPE = UrCircleRenderTypes.noFogSkyTranslucent("hexmob_celestial_firmament", CELESTIAL_FIRMAMENT_TEX)

    private class EntityVfxCache {
        var levelId: Int = 0
        var lastSampleTick: Int = Int.MIN_VALUE

        val prevRotations = FloatArray(6)
        val currRotations = FloatArray(6)
        val interpRotations = FloatArray(6)
        var prevDf: Float = 0F
        var currDf: Float = 0F
        var prevP2: Float = 0F
        var currP2: Float = 0F
        var prevP3: Float = 0F
        var currP3: Float = 0F
        var prevFp: Float = 0F
        var currFp: Float = 0F
        var prevTotalScale: Float = 1F
        var currTotalScale: Float = 1F

        var lastPillarTick: Int = Int.MIN_VALUE
        val prevPillarWorld = Array(4) { Vec3.ZERO }
        val currPillarWorld = Array(4) { Vec3.ZERO }
        val hasPillar = BooleanArray(4)

        var lastDebrisTick: Int = Int.MIN_VALUE
        var lastDebrisPhase2: Boolean = false
        val prevDebrisY = DoubleArray(40)
        val currDebrisY = DoubleArray(40)
        var debrisInitialized: Boolean = false
    }

    private val entityCaches = HashMap<Int, EntityVfxCache>(8)

    private fun obtainCache(entity: UrCircleEntity, partialTick: Float): EntityVfxCache {
        val curLvlId = System.identityHashCode(entity.level())
        if (entityCaches.size > 16) {
            entityCaches.entries.removeIf { it.value.levelId != curLvlId }
        }
        val cache = entityCaches.getOrPut(entity.id) { EntityVfxCache() }
        val tick = entity.tickCount
        if (cache.levelId != curLvlId || tick < cache.lastSampleTick) {
            cache.levelId = curLvlId
            cache.lastSampleTick = tick
            for (r in 0 until 6) {
                val rot = entity.ringRotation(r)
                cache.prevRotations[r] = rot
                cache.currRotations[r] = rot
                cache.interpRotations[r] = rot
            }
            cache.prevDf = entity.dormantFold; cache.currDf = cache.prevDf
            cache.prevP2 = entity.phase2Reveal; cache.currP2 = cache.prevP2
            cache.prevP3 = entity.phase3Reveal; cache.currP3 = cache.prevP3
            cache.prevFp = entity.foldProgress; cache.currFp = cache.prevFp
            cache.prevTotalScale = entity.totalScale(); cache.currTotalScale = cache.prevTotalScale
            cache.lastPillarTick = Int.MIN_VALUE
            cache.hasPillar.fill(false)
            cache.lastDebrisTick = Int.MIN_VALUE
            cache.debrisInitialized = false
        } else if (tick != cache.lastSampleTick) {
            val continuous = (tick - cache.lastSampleTick) <= 2
            for (r in 0 until 6) {
                val nowRot = entity.ringRotation(r)
                cache.prevRotations[r] = if (continuous) cache.currRotations[r] else nowRot
                cache.currRotations[r] = nowRot
            }
            val nowDf = entity.dormantFold
            val nowP2 = entity.phase2Reveal
            val nowP3 = entity.phase3Reveal
            val nowFp = entity.foldProgress
            val nowScale = entity.totalScale()
            if (continuous) {
                cache.prevDf = cache.currDf
                cache.prevP2 = cache.currP2
                cache.prevP3 = cache.currP3
                cache.prevFp = cache.currFp
                cache.prevTotalScale = cache.currTotalScale
            } else {
                cache.prevDf = nowDf
                cache.prevP2 = nowP2
                cache.prevP3 = nowP3
                cache.prevFp = nowFp
                cache.prevTotalScale = nowScale
            }
            cache.currDf = nowDf
            cache.currP2 = nowP2
            cache.currP3 = nowP3
            cache.currFp = nowFp
            cache.currTotalScale = nowScale
            cache.lastSampleTick = tick
        }
        val pt = partialTick.coerceIn(0.0F, 1.0F)
        for (r in 0 until 6) {
            cache.interpRotations[r] = Mth.lerp(pt, cache.prevRotations[r], cache.currRotations[r])
        }
        return cache
    }

    fun renderCircleVfx(
        entity: UrCircleEntity,
        partialTick: Float,
        ix: Double,
        iy: Double,
        iz: Double,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        packedLight: Int,
    ) {
        val dying = entity.circleState == CircleState.DYING
        if (dying && entity.stateTicks >= UrCircleEntity.DEATH_END_TICK) return
        val coreBroken = dying && entity.stateTicks >= UrCircleEntity.CORE_BREAK_TICK

        val cache = obtainCache(entity, partialTick)
        val pt = partialTick.coerceIn(0.0F, 1.0F)
        val t = entity.tickCount + partialTick
        val localCenter = Vec3(0.0, entity.bbHeight * 0.5, 0.0)
        val scale = Mth.lerp(pt, cache.prevTotalScale, cache.currTotalScale)
        val unit = (scale * 0.25F).coerceIn(0.35F, 4.0F)
        val df = Mth.lerp(pt, cache.prevDf, cache.currDf)
        val p2 = Mth.lerp(pt, cache.prevP2, cache.currP2)
        val p3 = Mth.lerp(pt, cache.prevP3, cache.currP3)
        val fp = Mth.lerp(pt, cache.prevFp, cache.currFp)
        val effScale = scale * (1.0F - 0.65F * df)
        val hasPhase2 = entity.phase >= 2 && p2 > 0.03F
        val hasPhase3 = entity.phase >= 3 && p3 > 0.03F
        val shortCircuited = entity.circleState == CircleState.SHORT_CIRCUIT
        val siphoning = entity.isSiphoning()

        val outerTrack = entity.rings.getOrNull(2)
        val outerN = outerTrack?.effectiveNormal(fp, df) ?: Vec3(0.0, 1.0, 0.0)
        val outerRad = outerTrack?.radialAxis(outerN) ?: Vec3(1.0, 0.0, 0.0)
        val outerR = outerTrack?.effectiveRadius(scale, df, p2, p3) ?: 0.0
        val outerRot = cache.interpRotations[2]
        val firmamentR = if (hasPhase3 && outerR > 8.0) maxOf(outerR * 2.75, 340.0) * (0.25 + 0.75 * p3) else 0.0

        // 预先算好外环 4 座卫星节点的局部坐标及其节流缓存后的地表落点，供方块层、光柱与冲击光环共享
        val satPositions = if (hasPhase2 && outerR > 8.0 && !coreBroken) {
            Array(4) { i ->
                val dir = outerRad.rotateDA(outerRot + i * 90F, outerN)
                localCenter.add(dir.scale(outerR)) to dir
            }
        } else {
            emptyArray()
        }
        val pillarTargets = if (satPositions.isNotEmpty()) {
            val needRefresh = cache.lastPillarTick < 0 ||
                (entity.tickCount - cache.lastPillarTick) >= PILLAR_CACHE_INTERVAL ||
                !cache.hasPillar[0]
            if (needRefresh) {
                for (idx in satPositions.indices) {
                    val (satPos, dir) = satPositions[idx]
                    val worldSpot = findGroundSpotWorld(entity, ix, iy, iz, satPos, dir, unit)
                    if (cache.hasPillar[idx]) {
                        cache.prevPillarWorld[idx] = cache.currPillarWorld[idx]
                    } else {
                        cache.prevPillarWorld[idx] = worldSpot
                        cache.hasPillar[idx] = true
                    }
                    cache.currPillarWorld[idx] = worldSpot
                }
                cache.lastPillarTick = entity.tickCount
            }
            val pAlpha = ((entity.tickCount - cache.lastPillarTick + pt) / PILLAR_CACHE_INTERVAL.toFloat())
                .coerceIn(0.0F, 1.0F).toDouble()
            Array(satPositions.size) { idx ->
                cache.prevPillarWorld[idx].lerp(cache.currPillarWorld[idx], pAlpha).subtract(ix, iy, iz)
            }
        } else {
            emptyArray()
        }

        if (!coreBroken) {
            // =========================================================================
            // PASS 1: 真实建筑体素冠环、锯齿晶簇、中环石骨、光柱落点晶簇与重力浮空碎石（Cutout 批处理）
            // =========================================================================
            renderArchitecturalMasonryPass(
                entity, cache, partialTick, t, ix, iy, iz, localCenter, scale, unit, df, p2, p3, fp,
                hasPhase2, hasPhase3, outerN, outerRad, outerR, outerRot, satPositions, pillarTargets,
                poseStack, buffer, packedLight,
            )

            val pose = poseStack.last()
            val m4 = pose.pose()
            val m3 = pose.normal()

            // =========================================================================
            // PASS 2: 二/三阶段 3D 立体空心星盘环梁（含三阶段“覆压周天”天球子午星盘巨拱，零 Z-Fight）
            // =========================================================================
            if (hasPhase2) {
                val ringTexBuf = buffer.getBuffer(RenderType.entityTranslucent(ASTROLABE_RING_TEX))
                renderExtrudedAstrolabeGirdersPass(
                    entity, cache, partialTick, t, localCenter, scale, unit, df, p2, p3, fp,
                    shortCircuited, siphoning, hasPhase2, hasPhase3, firmamentR, satPositions,
                    ringTexBuf, m4, m3,
                )
            }

            // =========================================================================
            // PASS 3: 内外环间半透明日蚀暗紫遮光膜（仅写颜色不写深度，避免吞没水体/粒子）
            // =========================================================================
            if (hasPhase2 && outerR > 8.0) {
                val veilBuf = buffer.getBuffer(ECLIPSE_VEIL_RENDER_TYPE)
                renderEclipseVeilPass(entity, partialTick, localCenter, outerN, outerRad, outerR, outerRot, unit, p2, p3, shortCircuited, veilBuf, m4)
            }

            // =========================================================================
            // PASS 3.5: 三阶段“覆压周天”贴图天穹星幕（340~400 格无雾穹顶，平滑边界消隐）
            // =========================================================================
            if (hasPhase3 && firmamentR > 24.0) {
                val skyBuf = buffer.getBuffer(CELESTIAL_FIRMAMENT_RENDER_TYPE)
                renderPhase3CelestialFirmamentPass(entity, partialTick, ix, iy, iz, localCenter, firmamentR, outerRot, p3, shortCircuited, skyBuf, m4)
            }

            // =========================================================================
            // PASS 4: 四道“垂天光柱”自卫星节点直扎实际地表（RenderType.beaconBeam）
            // =========================================================================
            val pillarFade = if (dying) (1.0F - ((entity.stateTicks + partialTick) / 36.0F)).coerceIn(0F, 1F) else 1.0F
            if (satPositions.isNotEmpty() && !shortCircuited && pillarFade > 0.04F) {
                renderSkyToEarthPillarsPass(t, satPositions, pillarTargets, unit, p2 * pillarFade, poseStack, buffer)
            }
        }

        // =========================================================================
        // PASS 5: 纯几何六芒星桁架、中环紫色尾迹、核心陀螺仪、死亡终幕光束与爆炸余晖
        // =========================================================================
        val poseAfter = poseStack.last()
        val lineBuf = buffer.getBuffer(RenderType.lightning())
        if (coreBroken) {
            renderDeathAftermathPass(entity, partialTick, localCenter, unit, effScale, lineBuf, poseAfter.pose())
        } else {
            renderStructuralGeometricPass(
                entity, partialTick, t, ix, iy, iz, localCenter, scale, unit, effScale, df, p2, p3, fp,
                shortCircuited, siphoning, hasPhase2, hasPhase3, outerN, outerRad, outerR, outerRot, satPositions, pillarTargets,
                lineBuf, poseAfter.pose(),
            )
        }
    }

    private fun renderArchitecturalMasonryPass(
        entity: UrCircleEntity,
        cache: EntityVfxCache,
        partialTick: Float,
        t: Float,
        ix: Double,
        iy: Double,
        iz: Double,
        localCenter: Vec3,
        scale: Float,
        unit: Float,
        df: Float,
        p2: Float,
        p3: Float,
        fp: Float,
        hasPhase2: Boolean,
        hasPhase3: Boolean,
        outerN: Vec3,
        outerRad: Vec3,
        outerR: Double,
        outerRot: Float,
        satPositions: Array<Pair<Vec3, Vec3>>,
        pillarTargets: Array<Vec3>,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        packedLight: Int,
    ) {
        val blockRenderer = Minecraft.getInstance().blockRenderer
        val deepslateModel = blockRenderer.getBlockModel(Blocks.DEEPSLATE_BRICKS.defaultBlockState())
        val blackstoneModel = blockRenderer.getBlockModel(Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState())
        val obsidianModel = blockRenderer.getBlockModel(Blocks.CRYING_OBSIDIAN.defaultBlockState())
        val amethystModel = blockRenderer.getBlockModel(Blocks.AMETHYST_BLOCK.defaultBlockState())
        val buddingModel = blockRenderer.getBlockModel(Blocks.BUDDING_AMETHYST.defaultBlockState())
        val clusterModel = blockRenderer.getBlockModel(Blocks.AMETHYST_CLUSTER.defaultBlockState())
        val cutoutBuf = buffer.getBuffer(RenderType.cutout())
        val tmpFwd = Vector3f()
        val tmpUp = Vector3f()
        val tmpQuat = Quaternionf()

        // 1. 二阶段专属：中环倾斜陀螺背骨（ECLIPTIC_SLATE，38°倾角厚实板岩山脊，含紫晶肋节）；一阶段保持轻简双环
        val midTrack = entity.rings.getOrNull(1)
        if (hasPhase2 && midTrack != null && midTrack.isVisible(entity.phase, p2, p3)) {
            val midN = midTrack.effectiveNormal(fp, df)
            val midRad = midTrack.radialAxis(midN)
            val midR = midTrack.effectiveRadius(scale, df, p2, p3)
            val midRot = cache.interpRotations[1]
            val count = 32
            for (i in 0 until count) {
                val surv = dyingRingSurvival(entity, 1, i, partialTick)
                if (surv <= 0.03F) continue
                val radial = midRad.rotateDA(midRot + i * 360F / count, midN)
                val crumble = 1.0F - surv
                val pos = localCenter.add(radial.scale(midR + crumble * 12.0 * unit)).add(0.0, -crumble * crumble * 20.0 * unit, 0.0)
                val tangent = midN.cross(radial).let { if (it.lengthSqr() > 1.0E-5) it.normalize() else Vec3(0.0, 0.0, 1.0) }
                val isCrystalRib = i % 4 == 0
                val model = if (isCrystalRib) buddingModel else deepslateModel
                val light = if (isCrystalRib) LightTexture.FULL_BRIGHT else packedLight
                renderOrientedModel(
                    blockRenderer, cutoutBuf, poseStack, model,
                    pos, forward = tangent, up = midN,
                    sx = 4.6F * unit * surv, sy = 3.2F * unit * surv, sz = 7.2F * unit * surv,
                    light = light, tmpFwd, tmpUp, tmpQuat,
                )
            }
        }

        // 2. 二阶段“山岳级”超巨型外环：72 段凹凸崎岖环形山脉 + 双层岩崖 + 24 座巨大紫水晶山峰锯齿
        if (hasPhase2 && outerR > 8.0) {
            val crownSegments = 72
            for (i in 0 until crownSegments) {
                val surv = dyingRingSurvival(entity, 2, i, partialTick)
                if (surv <= 0.03F) continue
                val crumble = 1.0F - surv
                val ang = outerRot + i * 360F / crownSegments
                val radial = outerRad.rotateDA(ang, outerN)
                val tangent = outerN.cross(radial).let { if (it.lengthSqr() > 1.0E-5) it.normalize() else Vec3(0.0, 0.0, 1.0) }
                val cragLevel = (i * 11) % 5
                val yJitter = (cragLevel - 3.2) * 1.1 * unit
                val basePos = localCenter.add(radial.scale(outerR + crumble * 16.0 * unit))
                    .add(outerN.scale(yJitter))
                    .add(0.0, -crumble * crumble * 28.0 * unit, 0.0)

                val wallModel = when (i % 3) {
                    0 -> deepslateModel
                    1 -> blackstoneModel
                    else -> obsidianModel
                }
                val wallLight = if (i % 3 == 2) LightTexture.FULL_BRIGHT else packedLight
                val wallH = (6.8F + cragLevel * 1.4F) * unit * surv
                renderOrientedModel(
                    blockRenderer, cutoutBuf, poseStack, wallModel,
                    basePos, forward = tangent, up = outerN,
                    sx = 10.5F * unit * surv, sy = wallH, sz = 12.2F * unit * surv,
                    light = wallLight, tmpFwd, tmpUp, tmpQuat,
                )
                // 偶数段下方悬垂岩石根基，拉出山体下轮廓厚度
                if (i % 2 == 1) {
                    renderOrientedModel(
                        blockRenderer, cutoutBuf, poseStack, blackstoneModel,
                        basePos.add(outerN.scale(-4.8 * unit * surv)), forward = tangent, up = outerN,
                        sx = 7.4F * unit * surv, sy = 6.2F * unit * surv, sz = 9.0F * unit * surv,
                        light = packedLight, tmpFwd, tmpUp, tmpQuat,
                    )
                }

                // 24 座巨型紫水晶山峰向外拔地刺出
                if ((i * 24) / crownSegments != ((i - 1) * 24) / crownSegments || i == 0) {
                    val isCardinal = i % 18 == 0
                    val spikePos = basePos.add(radial.scale(7.2 * unit * surv))
                    renderOrientedModel(
                        blockRenderer, cutoutBuf, poseStack, amethystModel,
                        spikePos, forward = radial, up = outerN,
                        sx = (if (isCardinal) 4.4F else 3.2F) * unit * surv,
                        sy = (if (isCardinal) 4.4F else 3.2F) * unit * surv,
                        sz = (if (isCardinal) 15.5F else 11.0F) * unit * surv,
                        light = LightTexture.FULL_BRIGHT, tmpFwd, tmpUp, tmpQuat,
                    )
                    val tipPos = spikePos.add(radial.scale((if (isCardinal) 9.8 else 7.2) * unit * surv))
                    renderOrientedModel(
                        blockRenderer, cutoutBuf, poseStack, clusterModel,
                        tipPos, forward = outerN, up = radial,
                        sx = 3.4F * unit * surv, sy = 4.6F * unit * surv, sz = 3.4F * unit * surv,
                        light = LightTexture.FULL_BRIGHT, tmpFwd, tmpUp, tmpQuat,
                    )
                }
            }

            // 2.5 第 5 层天顶陡斜陀螺环背骨（三阶段“覆压周天”展开，72°倾角 28 段深暗石肋与紫晶节点）
            val zenithTrack = entity.rings.getOrNull(4)
            if (hasPhase3 && zenithTrack != null && zenithTrack.isVisible(entity.phase, p2, p3)) {
                val zenN = zenithTrack.effectiveNormal(fp, df)
                val zenRad = zenithTrack.radialAxis(zenN)
                val zenR = zenithTrack.effectiveRadius(scale, df, p2, p3)
                val zenRot = cache.interpRotations[4]
                val zenCount = 28
                for (i in 0 until zenCount) {
                    val surv = dyingRingSurvival(entity, 4, i, partialTick)
                    if (surv <= 0.03F) continue
                    val crumble = 1.0F - surv
                    val radial = zenRad.rotateDA(zenRot + i * 360F / zenCount, zenN)
                    val pos = localCenter.add(radial.scale(zenR + crumble * 12.0 * unit)).add(0.0, -crumble * crumble * 20.0 * unit, 0.0)
                    val tangent = zenN.cross(radial).let { if (it.lengthSqr() > 1.0E-5) it.normalize() else Vec3(0.0, 0.0, 1.0) }
                    val isCrystal = i % 3 == 0
                    renderOrientedModel(
                        blockRenderer, cutoutBuf, poseStack,
                        if (isCrystal) buddingModel else obsidianModel,
                        pos, forward = tangent, up = zenN,
                        sx = 4.2F * unit * surv, sy = 2.8F * unit * surv, sz = 6.8F * unit * surv,
                        light = if (isCrystal) LightTexture.FULL_BRIGHT else packedLight,
                        tmpFwd, tmpUp, tmpQuat,
                    )
                }
            }

            // 3. 外环四向卫星节点上的巨型棱晶晶柱核心
            for ((idx, pair) in satPositions.withIndex()) {
                val satSurv = dyingRingSurvival(entity, 2, idx * 18, partialTick)
                if (satSurv <= 0.03F) continue
                val (satPos, _) = pair
                poseStack.pushPose()
                poseStack.translate(satPos.x, satPos.y, satPos.z)
                poseStack.mulPose(Axis.YP.rotationDegrees(t * 2.8F + idx * 90F))
                poseStack.mulPose(Axis.XP.rotationDegrees(45F))
                poseStack.mulPose(Axis.ZP.rotationDegrees(35F))
                val cs = 5.4F * unit * satSurv
                poseStack.scale(cs, cs, cs)
                poseStack.translate(-0.5, -0.5, -0.5)
                blockRenderer.modelRenderer.renderModel(
                    poseStack.last(), cutoutBuf, Blocks.AIR.defaultBlockState(),
                    amethystModel, 1F, 1F, 1F, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                )
                poseStack.popPose()
            }

            // 3.5 光柱直击地表的紫水晶共鸣晶簇底座
            val pillarSurv = if (entity.circleState == CircleState.DYING) {
                (1.0F - ((entity.stateTicks + partialTick) / 36.0F)).coerceIn(0F, 1F)
            } else {
                1.0F
            }
            if (pillarSurv > 0.04F) {
                for (groundLocal in pillarTargets) {
                    poseStack.pushPose()
                    poseStack.translate(groundLocal.x, groundLocal.y + 0.9 * unit, groundLocal.z)
                    poseStack.mulPose(Axis.YP.rotationDegrees(t * 1.5F))
                    val gs = 2.6F * unit.coerceAtLeast(1.0F) * pillarSurv
                    poseStack.scale(gs, gs * 1.8F, gs)
                    poseStack.translate(-0.5, -0.5, -0.5)
                    blockRenderer.modelRenderer.renderModel(
                        poseStack.last(), cutoutBuf, Blocks.AIR.defaultBlockState(),
                        clusterModel, 1F, 1F, 1F, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                    )
                    poseStack.popPose()
                }
            }
        }

        // 4. 大环正下方大地引力浮空山岩碎块（40 块大尺寸碎岩自地面缓慢升向山岳外环；高度图每 5 tick 节流缓存并插值）
        if (hasPhase2 || entity.circleState == CircleState.CHANNELING) {
            val debrisCount = if (hasPhase2) 40 else 12
            val defaultLiftSpan = if (hasPhase2) 72.0 else 28.0
            val baseR = if (hasPhase2) 42.0 * unit else 18.0 * unit
            val lvl = entity.level()
            val needDebrisRefresh = !cache.debrisInitialized ||
                cache.lastDebrisTick < 0 ||
                cache.lastDebrisPhase2 != hasPhase2 ||
                (entity.tickCount - cache.lastDebrisTick) >= DEBRIS_CACHE_INTERVAL
            if (needDebrisRefresh) {
                val sampleT = entity.tickCount.toFloat()
                for (i in 0 until 40) {
                    val cycle = ((sampleT * 0.006F + i * 0.137F) % 1.0F)
                    val angle = i * 2.39996 + sampleT * 0.012 * (if (i % 2 == 0) 1.0 else -1.0)
                    val radius = baseR * (0.45 + 0.65 * ((i * 7 % 11) / 10.0)) * (1.0 - 0.18 * cycle)
                    val dx = cos(angle) * radius
                    val dz = sin(angle) * radius
                    val surfaceY = lvl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(ix + dx), Mth.floor(iz + dz))
                    val worldY = if (surfaceY > lvl.minBuildHeight) surfaceY.toDouble() else Double.NaN
                    if (cache.debrisInitialized && cache.lastDebrisPhase2 == hasPhase2) {
                        cache.prevDebrisY[i] = cache.currDebrisY[i]
                    } else {
                        cache.prevDebrisY[i] = worldY
                    }
                    cache.currDebrisY[i] = worldY
                }
                cache.lastDebrisPhase2 = hasPhase2
                cache.debrisInitialized = true
                cache.lastDebrisTick = entity.tickCount
            }
            val pt = partialTick.coerceIn(0.0F, 1.0F)
            val debrisAlpha = ((entity.tickCount - cache.lastDebrisTick + pt) / DEBRIS_CACHE_INTERVAL.toFloat())
                .coerceIn(0.0F, 1.0F).toDouble()
            for (i in 0 until debrisCount) {
                val cycle = ((t * 0.006F + i * 0.137F) % 1.0F)
                val angle = i * 2.39996 + t * 0.012 * (if (i % 2 == 0) 1.0 else -1.0)
                val radius = baseR * (0.45 + 0.65 * ((i * 7 % 11) / 10.0)) * (1.0 - 0.18 * cycle)
                val dx = cos(angle) * radius
                val dz = sin(angle) * radius
                val pY = cache.prevDebrisY[i]
                val cY = cache.currDebrisY[i]
                val startLocalY = when {
                    !pY.isNaN() && !cY.isNaN() -> Mth.lerp(debrisAlpha, pY, cY) - iy + 0.6
                    !cY.isNaN() -> cY - iy + 0.6
                    else -> localCenter.y - defaultLiftSpan
                }
                val endLocalY = localCenter.y + 6.0
                val curY = Mth.lerp(cycle.toDouble(), startLocalY, endLocalY.coerceAtLeast(startLocalY + 10.0))
                val dModel = when (i % 4) {
                    0 -> amethystModel
                    1 -> blackstoneModel
                    else -> deepslateModel
                }
                val dLight = if (i % 4 == 0) LightTexture.FULL_BRIGHT else packedLight
                val ds = (1.35F + (i % 3) * 0.45F) * unit.coerceAtLeast(0.9F)
                poseStack.pushPose()
                poseStack.translate(localCenter.x + dx, curY, localCenter.z + dz)
                poseStack.mulPose(Axis.YP.rotationDegrees(t * 1.4F + i * 47F))
                poseStack.mulPose(Axis.XP.rotationDegrees(t * 0.9F + i * 29F))
                poseStack.scale(ds, ds, ds)
                poseStack.translate(-0.5, -0.5, -0.5)
                blockRenderer.modelRenderer.renderModel(
                    poseStack.last(), cutoutBuf, Blocks.AIR.defaultBlockState(),
                    dModel, 1F, 1F, 1F, dLight, OverlayTexture.NO_OVERLAY,
                )
                poseStack.popPose()
            }
        }
    }

    private fun renderOrientedModel(
        blockRenderer: net.minecraft.client.renderer.block.BlockRenderDispatcher,
        consumer: VertexConsumer,
        poseStack: PoseStack,
        model: BakedModel,
        pos: Vec3,
        forward: Vec3,
        up: Vec3,
        sx: Float,
        sy: Float,
        sz: Float,
        light: Int,
        tmpFwd: Vector3f,
        tmpUp: Vector3f,
        tmpQuat: Quaternionf,
    ) {
        poseStack.pushPose()
        poseStack.translate(pos.x, pos.y, pos.z)
        if (forward.lengthSqr() > 1.0E-8 && up.lengthSqr() > 1.0E-8) {
            tmpFwd.set(forward.x.toFloat(), forward.y.toFloat(), forward.z.toFloat()).normalize()
            tmpUp.set(up.x.toFloat(), up.y.toFloat(), up.z.toFloat()).normalize()
            if (abs(tmpFwd.dot(tmpUp)) > 0.9995F) {
                if (abs(tmpFwd.y) < 0.9F) tmpUp.set(0F, 1F, 0F) else tmpUp.set(1F, 0F, 0F)
            }
            tmpQuat.identity().lookAlong(tmpFwd, tmpUp).conjugate()
            poseStack.mulPose(tmpQuat)
        }
        poseStack.scale(sx, sy, sz)
        poseStack.translate(-0.5, -0.5, -0.5)
        blockRenderer.modelRenderer.renderModel(
            poseStack.last(), consumer, Blocks.AIR.defaultBlockState(),
            model, 1F, 1F, 1F, light, OverlayTexture.NO_OVERLAY,
        )
        poseStack.popPose()
    }

    private fun renderExtrudedAstrolabeGirdersPass(
        entity: UrCircleEntity,
        cache: EntityVfxCache,
        partialTick: Float,
        t: Float,
        localCenter: Vec3,
        scale: Float,
        unit: Float,
        df: Float,
        p2: Float,
        p3: Float,
        fp: Float,
        shortCircuited: Boolean,
        siphoning: Boolean,
        hasPhase2: Boolean,
        hasPhase3: Boolean,
        firmamentR: Double,
        satPositions: Array<Pair<Vec3, Vec3>>,
        ringTexBuf: VertexConsumer,
        m4: Matrix4f,
        m3: Matrix3f,
    ) {
        val dying = entity.circleState == CircleState.DYING
        val stateTime = entity.stateTicks + partialTick
        val baseAlpha = if (shortCircuited) 130 else 225
        val rTint = if (shortCircuited || dying) 235 else if (siphoning) 130 else 255
        val gTint = if (shortCircuited) 60 else if (dying) 125 else if (siphoning) 235 else 255
        val bTint = if (shortCircuited) 85 else if (dying) 150 else if (siphoning) 230 else 255

        for (track in entity.rings) {
            if (!track.isVisible(entity.phase, p2, p3)) continue
            val ringFade = if (!dying) {
                1.0F
            } else {
                ((48F + entity.deathWaveOf(track.ringIndex) * 20F - stateTime) / 22F).coerceIn(0F, 1F)
            }
            if (ringFade <= 0.03F) continue
            val alpha = (baseAlpha * ringFade).toInt().coerceIn(0, 255)
            val effN = track.effectiveNormal(fp, df)
            val radAxis = track.radialAxis(effN)
            val r = track.effectiveRadius(scale, df, p2, p3)
            if (r < 1.2) continue
            val rotDeg = cache.interpRotations[track.ringIndex.coerceIn(0, 5)]
            val rotatedRad = radAxis.rotateDA(rotDeg, effN)

            if (track.ringIndex == 2 && hasPhase2) {
                // 外环采用内外两道错开于山体岩冠上下缘的立体环梁，随外环同步旋转
                val outerRimShift = localCenter.add(effN.scale(5.8 * unit))
                drawExtruded3DRingGirder(
                    ringTexBuf, m4, m3,
                    outerRimShift, rotatedRad, effN,
                    innerR = r - 6.0 * unit,
                    outerR = r + 6.4 * unit,
                    halfH = 0.85 * unit,
                    segments = RING_SEGMENTS,
                    vOffset = 0F,
                    vTiles = 18F,
                    rTint, gTint, bTint, alpha,
                )
                drawExtruded3DRingGirder(
                    ringTexBuf, m4, m3,
                    localCenter.subtract(effN.scale(5.2 * unit)), rotatedRad, effN,
                    innerR = r * 0.78,
                    outerR = r * 0.86,
                    halfH = 0.55 * unit,
                    segments = RING_SEGMENTS,
                    vOffset = 0F,
                    vTiles = 16F,
                    245, 220, 255, (alpha * 0.85F).toInt(),
                )
            } else {
                // 各二阶段内/中/天顶环：星盘环梁锁死各环 rotDeg，与石头完全同步旋转
                val halfW = track.haloWidth * 0.55
                val halfH = (0.42 * unit).coerceAtLeast(0.28)
                drawExtruded3DRingGirder(
                    ringTexBuf, m4, m3,
                    localCenter, rotatedRad, effN,
                    innerR = r - halfW,
                    outerR = r + halfW,
                    halfH = halfH,
                    segments = 36,
                    vOffset = 0F,
                    vTiles = 10F,
                    rTint, gTint, bTint, (alpha * 0.9F).toInt(),
                )
            }
        }

        // 卫星节点小星盘光环（随自身旋转，死亡早期淡出）
        val satFade = if (dying) (1.0F - stateTime / 48F).coerceIn(0F, 1F) else 1.0F
        if (satFade > 0.04F) {
            for (i in satPositions.indices) {
                val (satPos, _) = satPositions[i]
                val nSat = Vec3(0.0, 1.0, 0.0)
                val satRot = t * 2.2F * (if (i % 2 == 0) 1F else -1F)
                val radSat = Vec3(1.0, 0.0, 0.0).rotateDA(satRot, nSat)
                drawExtruded3DRingGirder(
                    ringTexBuf, m4, m3,
                    satPos, radSat, nSat,
                    innerR = 6.2 * unit,
                    outerR = 8.8 * unit,
                    halfH = 0.42 * unit,
                    segments = 24,
                    vOffset = 0F,
                    vTiles = 4F,
                    255, 240, 255, (225 * satFade).toInt(),
                )
            }
        }

        // 三阶段“覆压周天”天穹子午星盘巨拱（贴图驱动 3 条横贯天穹的大尺度立体环梁，200+ 格视距清晰可见）
        if (hasPhase3 && firmamentR > 40.0) {
            val archFade = if (dying) (1.0F - stateTime / 64F).coerceIn(0F, 1F) else 1.0F
            val archAlpha = ((if (shortCircuited) 75 else 210) * p3 * archFade).toInt().coerceIn(0, 230)
            if (archAlpha > 4) {
                val horizonN = Vec3(0.0, 1.0, 0.0)
                val horizonRad = Vec3(1.0, 0.0, 0.0).rotateDA(cache.interpRotations[2] * 0.25F, horizonN)
                val archH = (1.15 * unit).coerceIn(1.2, 3.2)
                val archW = (9.5 * unit).coerceIn(10.0, 22.0)

                val horizonR = firmamentR * 0.88
                drawExtruded3DRingGirder(
                    ringTexBuf, m4, m3,
                    localCenter.add(0.0, 14.0 * unit, 0.0), horizonRad, horizonN,
                    innerR = horizonR - archW,
                    outerR = horizonR + archW,
                    halfH = archH,
                    segments = RING_SEGMENTS,
                    vOffset = 0F,
                    vTiles = 32F,
                    255, 232, 185, archAlpha,
                )

                val meridianAxisA = Vec3(cos(Math.toRadians(32.0)), 0.0, sin(Math.toRadians(32.0)))
                val mN1 = horizonN.rotateDA(54F, meridianAxisA).normalize()
                val mRad1 = meridianAxisA.rotateDA(cache.interpRotations[4] * 0.35F, mN1)
                val mR1 = firmamentR * 0.76
                drawExtruded3DRingGirder(
                    ringTexBuf, m4, m3,
                    localCenter.add(0.0, 10.0 * unit, 0.0), mRad1, mN1,
                    innerR = mR1 - archW * 0.85,
                    outerR = mR1 + archW * 0.85,
                    halfH = archH * 0.9,
                    segments = RING_SEGMENTS,
                    vOffset = 0F,
                    vTiles = 28F,
                    215, 165, 255, (archAlpha * 0.92F).toInt(),
                )

                val meridianAxisB = Vec3(cos(Math.toRadians(-58.0)), 0.0, sin(Math.toRadians(-58.0)))
                val mN2 = horizonN.rotateDA(-54F, meridianAxisB).normalize()
                val mRad2 = meridianAxisB.rotateDA(-cache.interpRotations[5] * 0.45F, mN2)
                drawExtruded3DRingGirder(
                    ringTexBuf, m4, m3,
                    localCenter.add(0.0, 10.0 * unit, 0.0), mRad2, mN2,
                    innerR = mR1 - archW * 0.85,
                    outerR = mR1 + archW * 0.85,
                    halfH = archH * 0.9,
                    segments = RING_SEGMENTS,
                    vOffset = 0F,
                    vTiles = 28F,
                    255, 224, 155, (archAlpha * 0.90F).toInt(),
                )
            }
        }
    }

    private fun renderEclipseVeilPass(
        entity: UrCircleEntity,
        partialTick: Float,
        localCenter: Vec3,
        outerN: Vec3,
        outerRad: Vec3,
        outerR: Double,
        outerRot: Float,
        unit: Float,
        p2: Float,
        p3: Float,
        shortCircuited: Boolean,
        veilBuf: VertexConsumer,
        m4: Matrix4f,
    ) {
        val dyingMul = if (entity.circleState == CircleState.DYING) {
            (1.0F - (entity.stateTicks + partialTick) / 75.0F).coerceIn(0.0F, 1.0F)
        } else {
            1.0F
        }
        val phaseMul = if (entity.phase >= 3) 0.72F + 0.28F * p3 else 0.72F
        val alpha = ((if (shortCircuited) 85 else 195) * p2 * phaseMul * dyingMul).toInt().coerceIn(0, 210)
        if (alpha <= 4) return

        // 天幕日蚀星印置于环面上侧（+1.65*unit 与 +0.85*unit），既不与下方几何六芒星遮挡又能让 24 根日冕尖刺探出山冠外沿
        val radU = outerRad.rotateDA(outerRot, outerN)
        val radV = outerN.cross(radU).normalize()
        val veilR = outerR * (1.115 + 0.22 * p3)

        drawSingleSideTexturedQuad(
            veilBuf, m4,
            localCenter.add(outerN.scale(1.65 * unit)),
            radU.scale(veilR), radV.scale(veilR),
            255, 255, 255, alpha,
            flipWinding = false,
        )
        drawSingleSideTexturedQuad(
            veilBuf, m4,
            localCenter.add(outerN.scale(0.85 * unit)),
            radU.scale(veilR), radV.scale(veilR),
            255, 255, 255, alpha,
            flipWinding = true,
        )
    }

    /**
     * 三阶段“覆压周天”贴图天穹穹顶（单层 NO_CULL 无雾穹顶 + 纬圈渐隐 + 相机越界平滑过渡）：
     * 消除暗紫天空雾吞没贴图及跨越 340~400 格边界时的硬切换感。
     */
    private fun renderPhase3CelestialFirmamentPass(
        entity: UrCircleEntity,
        partialTick: Float,
        ix: Double,
        iy: Double,
        iz: Double,
        localCenter: Vec3,
        firmamentR: Double,
        outerRot: Float,
        p3: Float,
        shortCircuited: Boolean,
        skyBuf: VertexConsumer,
        m4: Matrix4f,
    ) {
        val dyingMul = if (entity.circleState == CircleState.DYING) {
            (1.0F - (entity.stateTicks + partialTick) / 75.0F).coerceIn(0.0F, 1.0F)
        } else {
            1.0F
        }
        val camPos = Minecraft.getInstance().gameRenderer.mainCamera.position
        val cx = localCenter.x
        val cy = localCenter.y + 12.0
        val cz = localCenter.z
        val camDist = camPos.distanceTo(Vec3(ix + cx, iy + cy, iz + cz))
        val ratio = camDist / firmamentR.coerceAtLeast(1.0)
        val camFade = when {
            ratio <= 0.82 -> 1.0F
            ratio < 1.15 -> {
                val u = ((ratio - 0.82) / 0.33).coerceIn(0.0, 1.0)
                val s = u * u * (3.0 - 2.0 * u)
                (1.0 + (0.38 - 1.0) * s).toFloat()
            }
            ratio < 1.55 -> {
                val u = ((ratio - 1.15) / 0.40).coerceIn(0.0, 1.0)
                val s = u * u * (3.0 - 2.0 * u)
                (0.38 * (1.0 - s)).toFloat()
            }
            else -> 0.0F
        }
        val baseAlpha = if (shortCircuited) 95F else 238F
        val alpha = (baseAlpha * p3 * dyingMul * camFade).toInt().coerceIn(0, 245)
        if (alpha <= 4) return

        val rotRad = Math.toRadians((outerRot * 0.25F).toDouble())

        // 单层 NO_CULL 穹顶面（无双层壳体跨界突变，内外观测皆流畅平滑）
        drawTexturedCelestialDome(
            skyBuf, m4, cx, cy, cz, firmamentR,
            rotRad, 255, 255, 255, alpha,
            inwardFacing = true,
        )
    }

    private fun drawTexturedCelestialDome(
        consumer: VertexConsumer,
        m4: Matrix4f,
        cx: Double,
        cy: Double,
        cz: Double,
        radius: Double,
        rotRad: Double,
        red: Int,
        green: Int,
        blue: Int,
        alpha: Int,
        inwardFacing: Boolean,
    ) {
        val cosRot = cos(rotRad)
        val sinRot = sin(rotRad)

        for (band in 0 until DOME_PHI_BANDS) {
            val a0 = (alpha * DOME_BAND_ALPHA[band]).toInt().coerceIn(0, 255)
            val a1 = (alpha * DOME_BAND_ALPHA[band + 1]).toInt().coerceIn(0, 255)
            if (a0 <= 2 && a1 <= 2) continue

            val sinPhi0 = DOME_SIN_PHI[band]
            val cosPhi0 = DOME_COS_PHI[band]
            val sinPhi1 = DOME_SIN_PHI[band + 1]
            val cosPhi1 = DOME_COS_PHI[band + 1]
            val rBand0 = sinPhi0 * radius
            val rBand1 = sinPhi1 * radius
            val y0 = (cy + cosPhi0 * radius).toFloat()
            val y1 = (cy + cosPhi1 * radius).toFloat()
            val rUv0 = (0.495 * band / DOME_PHI_BANDS).toFloat()
            val rUv1 = (0.495 * (band + 1) / DOME_PHI_BANDS).toFloat()

            for (seg in 0 until DOME_THETA_SEGS) {
                val ct0 = DOME_COS_THETA[seg]
                val st0 = DOME_SIN_THETA[seg]
                val ct1 = DOME_COS_THETA[seg + 1]
                val st1 = DOME_SIN_THETA[seg + 1]

                val p00x = (cx + ct0 * rBand0).toFloat()
                val p00z = (cz + st0 * rBand0).toFloat()
                val p10x = (cx + ct1 * rBand0).toFloat()
                val p10z = (cz + st1 * rBand0).toFloat()
                val p11x = (cx + ct1 * rBand1).toFloat()
                val p11z = (cz + st1 * rBand1).toFloat()
                val p01x = (cx + ct0 * rBand1).toFloat()
                val p01z = (cz + st0 * rBand1).toFloat()

                val cosUv0 = (ct0 * cosRot + st0 * sinRot).toFloat()
                val sinUv0 = (st0 * cosRot - ct0 * sinRot).toFloat()
                val cosUv1 = (ct1 * cosRot + st1 * sinRot).toFloat()
                val sinUv1 = (st1 * cosRot - ct1 * sinRot).toFloat()

                val u00 = 0.5F + rUv0 * cosUv0
                val v00 = 0.5F + rUv0 * sinUv0
                val u10 = 0.5F + rUv0 * cosUv1
                val v10 = 0.5F + rUv0 * sinUv1
                val u11 = 0.5F + rUv1 * cosUv1
                val v11 = 0.5F + rUv1 * sinUv1
                val u01 = 0.5F + rUv1 * cosUv0
                val v01 = 0.5F + rUv1 * sinUv0

                if (!inwardFacing) {
                    consumer.vertex(m4, p00x, y0, p00z).color(red, green, blue, a0).uv(u00, v00).endVertex()
                    consumer.vertex(m4, p10x, y0, p10z).color(red, green, blue, a0).uv(u10, v10).endVertex()
                    consumer.vertex(m4, p11x, y1, p11z).color(red, green, blue, a1).uv(u11, v11).endVertex()
                    consumer.vertex(m4, p01x, y1, p01z).color(red, green, blue, a1).uv(u01, v01).endVertex()
                } else {
                    consumer.vertex(m4, p01x, y1, p01z).color(red, green, blue, a1).uv(u01, v01).endVertex()
                    consumer.vertex(m4, p11x, y1, p11z).color(red, green, blue, a1).uv(u11, v11).endVertex()
                    consumer.vertex(m4, p10x, y0, p10z).color(red, green, blue, a0).uv(u10, v10).endVertex()
                    consumer.vertex(m4, p00x, y0, p00z).color(red, green, blue, a0).uv(u00, v00).endVertex()
                }
            }
        }
    }

    private fun renderSkyToEarthPillarsPass(
        t: Float,
        satPositions: Array<Pair<Vec3, Vec3>>,
        pillarTargets: Array<Vec3>,
        unit: Float,
        p2: Float,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
    ) {
        val beamBuf = buffer.getBuffer(RenderType.beaconBeam(BEACON_TEX, true))
        val innerR = (1.35F * unit).coerceIn(0.65F, 3.0F)
        val outerR = (2.6F * unit).coerceIn(1.3F, 5.2F)
        val aInner = (195 * p2).toInt().coerceIn(0, 210)
        val aOuter = (110 * p2).toInt().coerceIn(0, 125)

        for (i in satPositions.indices) {
            val (satPos, _) = satPositions[i]
            val groundSpot = pillarTargets.getOrNull(i) ?: continue
            val rel = groundSpot.subtract(satPos)
            val horiz = Mth.sqrt((rel.x * rel.x + rel.z * rel.z).toFloat())
            val length = rel.length().toFloat()
            if (length < 1.0F) continue

            poseStack.pushPose()
            poseStack.translate(satPos.x, satPos.y, satPos.z)
            poseStack.mulPose(Axis.YP.rotation(-Math.atan2(rel.z, rel.x).toFloat() - 1.5707964F))
            poseStack.mulPose(Axis.XP.rotation(-Math.atan2(horiz.toDouble(), rel.y).toFloat() - 1.5707964F))

            val p = poseStack.last()
            val pm4 = p.pose()
            val pm3 = p.normal()
            drawBeamTube(beamBuf, pm4, pm3, innerR, length, -(t * 0.08F), length * 0.25F - t * 0.08F, 225, 165, 255, aInner)
            drawBeamTube(beamBuf, pm4, pm3, outerR, length, t * 0.04F, length * 0.18F + t * 0.04F, 255, 210, 115, aOuter)
            poseStack.popPose()
        }
    }

    private fun renderStructuralGeometricPass(
        entity: UrCircleEntity,
        partialTick: Float,
        t: Float,
        ix: Double,
        iy: Double,
        iz: Double,
        localCenter: Vec3,
        scale: Float,
        unit: Float,
        effScale: Float,
        df: Float,
        p2: Float,
        p3: Float,
        fp: Float,
        shortCircuited: Boolean,
        siphoning: Boolean,
        hasPhase2: Boolean,
        hasPhase3: Boolean,
        outerN: Vec3,
        outerRad: Vec3,
        outerR: Double,
        outerRot: Float,
        satPositions: Array<Pair<Vec3, Vec3>>,
        pillarTargets: Array<Vec3>,
        lineBuf: VertexConsumer,
        m4: Matrix4f,
    ) {
        // 1. 外环内嵌双层金紫六芒星弦架 + 最外圈悬浮金辉精密星盘外环 + 径向辐条 + 落柱真实地表冲击光环
        if (hasPhase2 && outerR > 8.0 && !shortCircuited) {
            val hexR = outerR * 0.77
            drawChordPolygon(lineBuf, m4, localCenter.subtract(outerN.scale(0.25)), outerRad, outerN, hexR, 3, outerRot, 0.28 * unit, 255, 222, 130, 210)
            drawChordPolygon(lineBuf, m4, localCenter.subtract(outerN.scale(0.65)), outerRad, outerN, hexR, 3, outerRot + 60F, 0.28 * unit, 205, 135, 255, 195)
            // 内层反旋小六芒星弦阵
            val innerHexR = outerR * 0.54
            drawChordPolygon(lineBuf, m4, localCenter.add(outerN.scale(0.20)), outerRad, outerN, innerHexR, 3, -outerRot * 1.3F, 0.20 * unit, 255, 232, 165, 165)
            drawChordPolygon(lineBuf, m4, localCenter.subtract(outerN.scale(0.15)), outerRad, outerN, innerHexR, 3, -outerRot * 1.3F + 60F, 0.20 * unit, 195, 125, 250, 155)

            // 最外侧包绕 24 根日冕星芒尖刺的悬浮金紫天文星环（对应截图最外圈亮金环框）
            val rimRotRad = outerRad.rotateDA(-outerRot * 0.45F, outerN)
            drawStrutRing(lineBuf, m4, localCenter.add(outerN.scale(0.95 * unit)), rimRotRad, outerN, outerR * 1.125, 64, 0.22 * unit, 255, 226, 150, 195)
            drawStrutRing(lineBuf, m4, localCenter.subtract(outerN.scale(0.55 * unit)), rimRotRad, outerN, outerR * 1.155, 64, 0.14 * unit, 195, 135, 255, 140)
            // 12 方向日冕刻度外环锚接线
            for (k in 0 until 12) {
                val rDir = outerRad.rotateDA(outerRot + k * 30F, outerN)
                val pIn = localCenter.add(rDir.scale(outerR * 0.96)).add(outerN.scale(0.95 * unit))
                val pOut = localCenter.add(rDir.scale(outerR * 1.125)).add(outerN.scale(0.95 * unit))
                drawBeamStrut(lineBuf, m4, pIn, pOut, 0.14 * unit, 255, 224, 145, 145)
            }

            for (i in satPositions.indices) {
                val (satPos, _) = satPositions[i]
                // 卫星向中心核心连出亮紫金结构辐条
                drawBeamStrut(lineBuf, m4, satPos, localCenter, 0.22 * unit, 225, 160, 255, 115)
                // 垂天光柱贴合实际地表的冲击能量环
                val groundSpot = pillarTargets.getOrNull(i)?.add(0.0, 0.15, 0.0) ?: continue
                drawStrutRing(lineBuf, m4, groundSpot, Vec3(1.0, 0.0, 0.0), Vec3(0.0, 1.0, 0.0), 3.6 * unit, 16, 0.18 * unit, 245, 200, 115, 165)
            }
        }

        // 2. 二阶段倾斜中环的淡淡紫色媒质尾迹（前后错开法线高度，呈现翻滚拖影）
        val midTrack = entity.rings.getOrNull(1)
        if (hasPhase2 && midTrack != null && midTrack.isVisible(entity.phase, p2, p3) && !shortCircuited) {
            val midN = midTrack.effectiveNormal(fp, df)
            val midRad = midTrack.radialAxis(midN)
            val midR = midTrack.effectiveRadius(scale, df, p2, p3)
            drawStrutRing(lineBuf, m4, localCenter.add(midN.scale(0.65 * unit)), midRad, midN, midR * 1.04, 32, 0.16 * unit, 185, 105, 245, 105)
            drawStrutRing(lineBuf, m4, localCenter.subtract(midN.scale(0.65 * unit)), midRad, midN, midR * 0.96, 32, 0.14 * unit, 245, 200, 115, 95)
        }

        // 2.2 环刃风暴（RingSpinSkill）前摇 1 秒：外环与黄道环高频闪烁橙红警示环圈
        if (entity.entityData.get(UrCircleEntity.RING_SPINNING) && entity.stateTicks < RingSpinSkill.WARNING_TICKS && hasPhase2) {
            val pulse = ((0.5 + 0.5 * sin((t * 1.1F).toDouble())) * 105.0 + 130.0).toInt().coerceIn(0, 245)
            if (outerR > 8.0) {
                drawStrutRing(lineBuf, m4, localCenter.add(outerN.scale(0.8 * unit)), outerRad, outerN, outerR * 1.08, 48, 0.34 * unit, 255, 95, 45, pulse)
            }
            if (midTrack != null && midTrack.isVisible(entity.phase, p2, p3)) {
                val midN = midTrack.effectiveNormal(fp, df)
                val midRad = midTrack.radialAxis(midN)
                val midR = midTrack.effectiveRadius(scale, df, p2, p3)
                drawStrutRing(lineBuf, m4, localCenter.add(midN.scale(0.5 * unit)), midRad, midN, midR * 1.06, 36, 0.28 * unit, 255, 95, 45, pulse)
            }
        }

        // 2.5 M1 象征石板核心连线与“可篡改态”青色闪烁虚线 + 石板青光边框
        val syncedSlates = UrCircleHudTracker.getByEntityId(entity.id)?.packet?.slates
        if (!syncedSlates.isNullOrEmpty() && !shortCircuited) {
            val parts = entity.getAllParts()
            for (entry in syncedSlates) {
                val part = parts.getOrNull(entry.globalIndex) ?: continue
                if (!part.active || part.posNow == Vec3.ZERO) continue
                val slateLocal = part.posPrev.lerp(part.posNow, partialTick.toDouble()).subtract(ix, iy, iz)
                if (entry.rebounded) continue

                val symType = SlateSymbolType.byId(entry.symbolType)
                if (entry.tamperable) {
                    val blinkOn = sin((t * 0.85F).toDouble()) > -0.25
                    val dashAlpha = if (blinkOn) 235 else 85
                    drawDashedBeamStrut(lineBuf, m4, localCenter, slateLocal, 0.19 * unit, 55, 245, 235, dashAlpha, t * 0.14F)
                    val borderR = (part.bbWidth * 0.95).coerceAtLeast(1.4)
                    val bNormal = localCenter.subtract(slateLocal).let { if (it.lengthSqr() > 1.0E-4) it.normalize() else Vec3(0.0, 1.0, 0.0) }
                    drawChordPolygon(lineBuf, m4, slateLocal, orthoRadial(bNormal), bNormal, borderR, 4, t * 5.0F, 0.14, 65, 250, 240, dashAlpha)
                } else {
                    val col = symType.normalColor
                    val cr = col shr 16 and 0xFF
                    val cg = col shr 8 and 0xFF
                    val cb = col and 0xFF
                    drawBeamStrut(lineBuf, m4, localCenter, slateLocal, 0.15 * unit, cr, cg, cb, 185)
                    val bNormal = localCenter.subtract(slateLocal).let { if (it.lengthSqr() > 1.0E-4) it.normalize() else Vec3(0.0, 1.0, 0.0) }
                    drawChordPolygon(lineBuf, m4, slateLocal, orthoRadial(bNormal), bNormal, (part.bbWidth * 0.8).coerceAtLeast(1.2), 4, 45F, 0.10, cr, cg, cb, 155)
                }
            }
        }

        // 3. 吟唱期：环上当前活跃波节点之间的高亮传导弧线
        if (entity.circleState == CircleState.CHANNELING && !shortCircuited) {
            val wc = entity.waveColor
            val wr = wc shr 16 and 0xFF
            val wg = wc shr 8 and 0xFF
            val wb = wc and 0xFF
            for (track in entity.rings) {
                if (!track.isVisible(entity.phase, p2, p3)) continue
                val parts = track.parts
                for (i in parts.indices) {
                    val pA = parts[i]
                    val pB = parts[(i + 1) % parts.size]
                    if (!pA.active || !pB.active) continue
                    if (entity.isActiveWaveNode(pA) || entity.isActiveWaveNode(pB)) {
                        val posA = pA.posPrev.lerp(pA.posNow, partialTick.toDouble()).subtract(ix, iy, iz)
                        val posB = pB.posPrev.lerp(pB.posNow, partialTick.toDouble()).subtract(ix, iy, iz)
                        drawBeamStrut(lineBuf, m4, posA, posB, 0.24 * unit, 255, 215, 115, 200)
                        drawBeamStrut(lineBuf, m4, posA, localCenter, 0.14 * unit, wr, wg, wb, 145)
                    }
                }
            }
        }

        // 4. 核心陀螺仪与虹吸八面体结晶护笼（一阶段仅保留 1 道轻巧金环，二阶段展开完整三轴陀螺仪）
        val coronaR = (2.4 * effScale).coerceIn(3.2, 13.0)
        val n1 = Vec3(0.35, 0.9, 0.25).normalize().rotateDA(t * 2.2F, Vec3(0.0, 1.0, 0.0)).normalize()
        val aMul = if (shortCircuited) 0.35F else 1.0F

        drawStrutRing(lineBuf, m4, localCenter, orthoRadial(n1), n1, coronaR, 24, 0.12, 245, 200, 100, (145 * aMul).toInt())
        if (hasPhase2) {
            val n2 = Vec3(-0.35, 0.9, 0.25).normalize().rotateDA(-t * 2.6F, Vec3(0.0, 1.0, 0.0)).normalize()
            drawStrutRing(lineBuf, m4, localCenter, orthoRadial(n2), n2, coronaR * 1.28, 24, 0.11, 180, 100, 245, (135 * aMul).toInt())
        }
        if (hasPhase3) {
            val n3 = Vec3(0.85, 0.3, -0.4).normalize().rotateDA(t * 3.0F, Vec3(0.0, 0.0, 1.0)).normalize()
            drawStrutRing(lineBuf, m4, localCenter, orthoRadial(n3), n3, coronaR * 1.54, 24, 0.09, 220, 155, 250, (135 * aMul).toInt())
        }

        if (siphoning) {
            val pulse = 1.0 + 0.08 * sin(t * 0.35)
            val cageR = coronaR * 1.85 * pulse
            drawOctahedronWireframe(lineBuf, m4, localCenter, cageR, t * 2.4F, 0.20, 65, 225, 220, 175)
            drawOctahedronWireframe(lineBuf, m4, localCenter, cageR * 0.78, -t * 3.1F, 0.15, 235, 90, 180, 145)
        }

        if (entity.circleState == CircleState.BEAM && !entity.isPlaneSlicing()) {
            drawChordPolygon(lineBuf, m4, localCenter, Vec3(1.0, 0.0, 0.0), Vec3(0.0, 1.0, 0.0), coronaR * 1.6, 6, t * 6.0F, 0.24, 250, 210, 120, 170)
        }

        // 5. 死亡临界爆发射线（类末影龙全向破壳圣光）与阶段转场震荡波
        if (entity.circleState == CircleState.DYING && entity.stateTicks >= 72) {
            renderDyingVolumetricRays(entity, partialTick, t, localCenter, effScale, lineBuf, m4)
        }
        if (entity.circleState == CircleState.PHASE_TRANSITION && entity.stateTicks >= UrCircleEntity.PHASE_BURST_TICK) {
            val elapsed = (entity.stateTicks - UrCircleEntity.PHASE_BURST_TICK + partialTick).coerceAtLeast(0F)
            val total = (UrCircleEntity.PHASE_TRANSITION_TICKS - UrCircleEntity.PHASE_BURST_TICK).toFloat().coerceAtLeast(1F)
            val progress = (elapsed / total).coerceIn(0F, 1F)
            val waveR = 4.0 + progress * 34.0 * effScale
            val alpha = ((1.0F - progress) * 195F).toInt().coerceIn(0, 195)
            if (alpha > 4) {
                drawStrutRing(lineBuf, m4, localCenter, Vec3(1.0, 0.0, 0.0), Vec3(0.0, 1.0, 0.0), waveR, 40, 0.5, 255, 215, 120, alpha)
                drawStrutRing(lineBuf, m4, localCenter, Vec3(0.0, 1.0, 0.0), Vec3(1.0, 0.0, 0.0), waveR * 0.85, 28, 0.32, 185, 105, 245, (alpha * 0.75F).toInt())
            }
        }

        // 6. 环面切割 3D 旋转倾斜斩切盘线框（同步服务端 slicePlaneLocalOrigin 与 slicePlaneNormal）
        if (entity.isPlaneSlicing()) {
            val sliceCenter = entity.slicePlaneLocalOrigin()
            val sliceN = entity.slicePlaneNormal()
            val baseU = orthoRadial(sliceN)
            val baseV = sliceN.cross(baseU).let { if (it.lengthSqr() > 1.0E-6) it.normalize() else Vec3(0.0, 0.0, 1.0) }
            val spinAng = (t * 0.28).toDouble()
            val ca = cos(spinAng)
            val sa = sin(spinAng)
            val sliceRad = baseU.scale(ca).add(baseV.scale(sa))
            val orthRad = sliceN.cross(sliceRad).let { if (it.lengthSqr() > 1.0E-6) it.normalize() else baseV }
            val sliceR = maxOf(entity.ringOuterRadius(), sliceCenter.distanceTo(localCenter) + 12.0, 24.0)
            drawStrutRing(lineBuf, m4, sliceCenter, sliceRad, sliceN, sliceR, 48, 0.34, 255, 220, 125, 190)
            drawStrutRing(lineBuf, m4, sliceCenter, sliceRad, sliceN, sliceR * 0.65, 36, 0.20, 210, 145, 255, 135)
            drawBeamStrut(lineBuf, m4, sliceCenter.subtract(sliceRad.scale(sliceR)), sliceCenter.add(sliceRad.scale(sliceR)), 0.42, 255, 225, 135, 185)
            drawBeamStrut(lineBuf, m4, sliceCenter.subtract(orthRad.scale(sliceR)), sliceCenter.add(orthRad.scale(sliceR)), 0.34, 195, 110, 255, 165)
        }
    }

    /**
     * 3D 立体空心环梁：上下表面与内外圆壁各自外法线单独绕序，且上下隔开 2*halfH 物理厚度，
     * 完全规避零距正反双面重叠产生的深度竞争闪烁（Z-Fighting）；热路径全原始数值展开以消除 Vec3 分配。
     */
    private fun drawExtruded3DRingGirder(
        consumer: VertexConsumer,
        m4: Matrix4f,
        m3: Matrix3f,
        center: Vec3,
        radAxis: Vec3,
        normal: Vec3,
        innerR: Double,
        outerR: Double,
        halfH: Double,
        segments: Int,
        vOffset: Float,
        vTiles: Float,
        red: Int,
        green: Int,
        blue: Int,
        alpha: Int,
    ) {
        if (outerR <= innerR || alpha <= 2 || halfH <= 0.05 || segments <= 0) return
        val nLenSq = normal.lengthSqr()
        if (nLenSq <= 1.0E-8) return
        val nInv = 1.0 / sqrt(nLenSq)
        val nxD = normal.x * nInv
        val nyD = normal.y * nInv
        val nzD = normal.z * nInv
        val nx = nxD.toFloat()
        val ny = nyD.toFloat()
        val nz = nzD.toFloat()
        val hx = nxD * halfH
        val hy = nyD * halfH
        val hz = nzD * halfH

        // 将 radAxis 正交归一化为基向量 u，v = n × u
        val dot = radAxis.x * nxD + radAxis.y * nyD + radAxis.z * nzD
        var ux = radAxis.x - nxD * dot
        var uy = radAxis.y - nyD * dot
        var uz = radAxis.z - nzD * dot
        val uLenSq = ux * ux + uy * uy + uz * uz
        if (uLenSq <= 1.0E-8) {
            val ortho = orthoRadial(Vec3(nxD, nyD, nzD))
            ux = ortho.x; uy = ortho.y; uz = ortho.z
        } else {
            val uInv = 1.0 / sqrt(uLenSq)
            ux *= uInv; uy *= uInv; uz *= uInv
        }
        val vx = nyD * uz - nzD * uy
        val vy = nzD * ux - nxD * uz
        val vz = nxD * uy - nyD * ux

        val cx = center.x
        val cy = center.y
        val cz = center.z
        val stepRad = Math.PI * 2.0 / segments

        var dx0 = ux
        var dy0 = uy
        var dz0 = uz

        for (k in 0 until segments) {
            val ang1 = (k + 1) * stepRad
            val c1 = cos(ang1)
            val s1 = sin(ang1)
            val dx1 = ux * c1 + vx * s1
            val dy1 = uy * c1 + vy * s1
            val dz1 = uz * c1 + vz * s1

            val b0ix = cx + dx0 * innerR; val b0iy = cy + dy0 * innerR; val b0iz = cz + dz0 * innerR
            val b0ox = cx + dx0 * outerR; val b0oy = cy + dy0 * outerR; val b0oz = cz + dz0 * outerR
            val b1ox = cx + dx1 * outerR; val b1oy = cy + dy1 * outerR; val b1oz = cz + dz1 * outerR
            val b1ix = cx + dx1 * innerR; val b1iy = cy + dy1 * innerR; val b1iz = cz + dz1 * innerR

            val t0ix = (b0ix + hx).toFloat(); val t0iy = (b0iy + hy).toFloat(); val t0iz = (b0iz + hz).toFloat()
            val d0ix = (b0ix - hx).toFloat(); val d0iy = (b0iy - hy).toFloat(); val d0iz = (b0iz - hz).toFloat()
            val t0ox = (b0ox + hx).toFloat(); val t0oy = (b0oy + hy).toFloat(); val t0oz = (b0oz + hz).toFloat()
            val d0ox = (b0ox - hx).toFloat(); val d0oy = (b0oy - hy).toFloat(); val d0oz = (b0oz - hz).toFloat()
            val t1ox = (b1ox + hx).toFloat(); val t1oy = (b1oy + hy).toFloat(); val t1oz = (b1oz + hz).toFloat()
            val d1ox = (b1ox - hx).toFloat(); val d1oy = (b1oy - hy).toFloat(); val d1oz = (b1oz - hz).toFloat()
            val t1ix = (b1ix + hx).toFloat(); val t1iy = (b1iy + hy).toFloat(); val t1iz = (b1iz + hz).toFloat()
            val d1ix = (b1ix - hx).toFloat(); val d1iy = (b1iy - hy).toFloat(); val d1iz = (b1iz - hz).toFloat()

            val v0 = k.toFloat() / segments * vTiles + vOffset
            val v1 = (k + 1).toFloat() / segments * vTiles + vOffset

            // 顶面（法线 +normal）
            emitLitQuadCoords(consumer, m4, m3, t0ix, t0iy, t0iz, t0ox, t0oy, t0oz, t1ox, t1oy, t1oz, t1ix, t1iy, t1iz, 0F, v0, 1F, v1, red, green, blue, alpha, nx, ny, nz)
            // 底面（与顶面相距 2*halfH，法线 -normal）
            emitLitQuadCoords(consumer, m4, m3, d1ix, d1iy, d1iz, d1ox, d1oy, d1oz, d0ox, d0oy, d0oz, d0ix, d0iy, d0iz, 0F, v1, 1F, v0, red, green, blue, alpha, -nx, -ny, -nz)

            // 外侧立壁（沿径向朝外）与内侧立壁（沿径向朝内）
            val mx = dx0 + dx1
            val my = dy0 + dy1
            val mz = dz0 + dz1
            val mLenSq = mx * mx + my * my + mz * mz
            val mInv = if (mLenSq > 1.0E-8) (1.0 / sqrt(mLenSq)).toFloat() else 1.0F
            val omx = mx.toFloat() * mInv
            val omy = my.toFloat() * mInv
            val omz = mz.toFloat() * mInv
            emitLitQuadCoords(consumer, m4, m3, d0ox, d0oy, d0oz, t0ox, t0oy, t0oz, t1ox, t1oy, t1oz, d1ox, d1oy, d1oz, 0F, v0, 0.25F, v1, red, green, blue, alpha, omx, omy, omz)
            emitLitQuadCoords(consumer, m4, m3, t0ix, t0iy, t0iz, d0ix, d0iy, d0iz, d1ix, d1iy, d1iz, t1ix, t1iy, t1iz, 0.75F, v0, 1F, v1, red, green, blue, alpha, -omx, -omy, -omz)

            dx0 = dx1
            dy0 = dy1
            dz0 = dz1
        }
    }

    private fun emitLitQuadCoords(
        consumer: VertexConsumer,
        m4: Matrix4f,
        m3: Matrix3f,
        x0: Float, y0: Float, z0: Float,
        x1: Float, y1: Float, z1: Float,
        x2: Float, y2: Float, z2: Float,
        x3: Float, y3: Float, z3: Float,
        u0: Float, v0: Float,
        u1: Float, v1: Float,
        r: Int, g: Int, b: Int, a: Int,
        nx: Float, ny: Float, nz: Float,
    ) {
        consumer.vertex(m4, x0, y0, z0).color(r, g, b, a).uv(u0, v0)
            .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(m3, nx, ny, nz).endVertex()
        consumer.vertex(m4, x1, y1, z1).color(r, g, b, a).uv(u1, v0)
            .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(m3, nx, ny, nz).endVertex()
        consumer.vertex(m4, x2, y2, z2).color(r, g, b, a).uv(u1, v1)
            .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(m3, nx, ny, nz).endVertex()
        consumer.vertex(m4, x3, y3, z3).color(r, g, b, a).uv(u0, v1)
            .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(m3, nx, ny, nz).endVertex()
    }

    private fun drawSingleSideTexturedQuad(
        consumer: VertexConsumer,
        m4: Matrix4f,
        center: Vec3,
        uAxis: Vec3,
        vAxis: Vec3,
        r: Int,
        g: Int,
        b: Int,
        alpha: Int,
        flipWinding: Boolean,
    ) {
        val p00 = center.subtract(uAxis).subtract(vAxis)
        val p10 = center.add(uAxis).subtract(vAxis)
        val p11 = center.add(uAxis).add(vAxis)
        val p01 = center.subtract(uAxis).add(vAxis)

        if (!flipWinding) {
            consumer.vertex(m4, p00.x.toFloat(), p00.y.toFloat(), p00.z.toFloat()).color(r, g, b, alpha).uv(0F, 0F).endVertex()
            consumer.vertex(m4, p10.x.toFloat(), p10.y.toFloat(), p10.z.toFloat()).color(r, g, b, alpha).uv(1F, 0F).endVertex()
            consumer.vertex(m4, p11.x.toFloat(), p11.y.toFloat(), p11.z.toFloat()).color(r, g, b, alpha).uv(1F, 1F).endVertex()
            consumer.vertex(m4, p01.x.toFloat(), p01.y.toFloat(), p01.z.toFloat()).color(r, g, b, alpha).uv(0F, 1F).endVertex()
        } else {
            consumer.vertex(m4, p01.x.toFloat(), p01.y.toFloat(), p01.z.toFloat()).color(r, g, b, alpha).uv(0F, 1F).endVertex()
            consumer.vertex(m4, p11.x.toFloat(), p11.y.toFloat(), p11.z.toFloat()).color(r, g, b, alpha).uv(1F, 1F).endVertex()
            consumer.vertex(m4, p10.x.toFloat(), p10.y.toFloat(), p10.z.toFloat()).color(r, g, b, alpha).uv(1F, 0F).endVertex()
            consumer.vertex(m4, p00.x.toFloat(), p00.y.toFloat(), p00.z.toFloat()).color(r, g, b, alpha).uv(0F, 0F).endVertex()
        }
    }

    private fun drawBeamTube(
        consumer: VertexConsumer,
        m4: Matrix4f,
        m3: Matrix3f,
        r: Float,
        length: Float,
        vStart: Float,
        vEnd: Float,
        red: Int,
        green: Int,
        blue: Int,
        alpha: Int,
    ) {
        var prevX = 0.0F
        var prevY = r
        var prevU = 0.0F
        for (i in 1..8) {
            val rad = i * 6.2831855F / 8.0F
            val nx = Mth.sin(rad) * r
            val ny = Mth.cos(rad) * r
            val u = i / 8.0F
            consumer.vertex(m4, prevX, prevY, 0F).color(red, green, blue, alpha).uv(prevU, vStart)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(m3, 0F, 1F, 0F).endVertex()
            consumer.vertex(m4, prevX, prevY, length).color(red, green, blue, alpha).uv(prevU, vEnd)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(m3, 0F, 1F, 0F).endVertex()
            consumer.vertex(m4, nx, ny, length).color(red, green, blue, alpha).uv(u, vEnd)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(m3, 0F, 1F, 0F).endVertex()
            consumer.vertex(m4, nx, ny, 0F).color(red, green, blue, alpha).uv(u, vStart)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(m3, 0F, 1F, 0F).endVertex()
            prevX = nx; prevY = ny; prevU = u
        }
    }

    private fun drawStrutRing(
        consumer: VertexConsumer,
        m4: Matrix4f,
        center: Vec3,
        radAxis: Vec3,
        normal: Vec3,
        radius: Double,
        segments: Int,
        halfThick: Double,
        r: Int,
        g: Int,
        b: Int,
        alpha: Int,
    ) {
        if (alpha <= 2 || radius <= 0.1 || segments <= 0) return
        val nLenSq = normal.lengthSqr()
        if (nLenSq <= 1.0E-8) return
        val nInv = 1.0 / sqrt(nLenSq)
        val nx = normal.x * nInv
        val ny = normal.y * nInv
        val nz = normal.z * nInv
        val dot = radAxis.x * nx + radAxis.y * ny + radAxis.z * nz
        var ux = radAxis.x - nx * dot
        var uy = radAxis.y - ny * dot
        var uz = radAxis.z - nz * dot
        val uLenSq = ux * ux + uy * uy + uz * uz
        if (uLenSq <= 1.0E-8) {
            val ortho = orthoRadial(Vec3(nx, ny, nz))
            ux = ortho.x; uy = ortho.y; uz = ortho.z
        } else {
            val uInv = 1.0 / sqrt(uLenSq)
            ux *= uInv; uy *= uInv; uz *= uInv
        }
        val vx = ny * uz - nz * uy
        val vy = nz * ux - nx * uz
        val vz = nx * uy - ny * ux

        val cx = center.x; val cy = center.y; val cz = center.z
        var px = cx + ux * radius
        var py = cy + uy * radius
        var pz = cz + uz * radius
        val stepRad = Math.PI * 2.0 / segments
        for (k in 1..segments) {
            val a = k * stepRad
            val c = cos(a)
            val s = sin(a)
            val qx = cx + (ux * c + vx * s) * radius
            val qy = cy + (uy * c + vy * s) * radius
            val qz = cz + (uz * c + vz * s) * radius
            drawBeamStrutCoords(consumer, m4, px, py, pz, qx, qy, qz, halfThick, r, g, b, alpha)
            px = qx; py = qy; pz = qz
        }
    }

    private fun drawChordPolygon(
        consumer: VertexConsumer,
        m4: Matrix4f,
        center: Vec3,
        radAxis: Vec3,
        normal: Vec3,
        radius: Double,
        sides: Int,
        rotDeg: Float,
        halfThick: Double,
        r: Int,
        g: Int,
        b: Int,
        alpha: Int,
    ) {
        if (alpha <= 2 || sides < 3) return
        val nLenSq = normal.lengthSqr()
        if (nLenSq <= 1.0E-8) return
        val nInv = 1.0 / sqrt(nLenSq)
        val nx = normal.x * nInv
        val ny = normal.y * nInv
        val nz = normal.z * nInv
        val dot = radAxis.x * nx + radAxis.y * ny + radAxis.z * nz
        var ux = radAxis.x - nx * dot
        var uy = radAxis.y - ny * dot
        var uz = radAxis.z - nz * dot
        val uLenSq = ux * ux + uy * uy + uz * uz
        if (uLenSq <= 1.0E-8) {
            val ortho = orthoRadial(Vec3(nx, ny, nz))
            ux = ortho.x; uy = ortho.y; uz = ortho.z
        } else {
            val uInv = 1.0 / sqrt(uLenSq)
            ux *= uInv; uy *= uInv; uz *= uInv
        }
        val vx = ny * uz - nz * uy
        val vy = nz * ux - nx * uz
        val vz = nx * uy - ny * ux

        val cx = center.x; val cy = center.y; val cz = center.z
        val rotRad = Math.toRadians(rotDeg.toDouble())
        val stepRad = Math.PI * 2.0 / sides
        val startC = cos(rotRad)
        val startS = sin(rotRad)
        val firstX = cx + (ux * startC + vx * startS) * radius
        val firstY = cy + (uy * startC + vy * startS) * radius
        val firstZ = cz + (uz * startC + vz * startS) * radius
        var px = firstX; var py = firstY; var pz = firstZ
        for (i in 1 until sides) {
            val a = rotRad + i * stepRad
            val c = cos(a); val s = sin(a)
            val qx = cx + (ux * c + vx * s) * radius
            val qy = cy + (uy * c + vy * s) * radius
            val qz = cz + (uz * c + vz * s) * radius
            drawBeamStrutCoords(consumer, m4, px, py, pz, qx, qy, qz, halfThick, r, g, b, alpha)
            px = qx; py = qy; pz = qz
        }
        drawBeamStrutCoords(consumer, m4, px, py, pz, firstX, firstY, firstZ, halfThick, r, g, b, alpha)
    }

    private fun drawOctahedronWireframe(
        consumer: VertexConsumer,
        m4: Matrix4f,
        center: Vec3,
        radius: Double,
        rotDeg: Float,
        halfThick: Double,
        r: Int,
        g: Int,
        b: Int,
        alpha: Int,
    ) {
        val cx = center.x; val cy = center.y; val cz = center.z
        val topY = cy + radius
        val botY = cy - radius
        val rotRad = Math.toRadians(rotDeg.toDouble())
        val c0 = cos(rotRad) * radius
        val s0 = sin(rotRad) * radius
        // 4 个赤道点绕 Y 轴间隔 90°：(c0, s0), (-s0, c0), (-c0, -s0), (s0, -c0)
        val x0 = cx + c0; val z0 = cz + s0
        val x1 = cx - s0; val z1 = cz + c0
        val x2 = cx - c0; val z2 = cz - s0
        val x3 = cx + s0; val z3 = cz - c0

        drawBeamStrutCoords(consumer, m4, x0, cy, z0, cx, topY, cz, halfThick, r, g, b, alpha)
        drawBeamStrutCoords(consumer, m4, x0, cy, z0, cx, botY, cz, halfThick, r, g, b, alpha)
        drawBeamStrutCoords(consumer, m4, x0, cy, z0, x1, cy, z1, halfThick, r, g, b, alpha)

        drawBeamStrutCoords(consumer, m4, x1, cy, z1, cx, topY, cz, halfThick, r, g, b, alpha)
        drawBeamStrutCoords(consumer, m4, x1, cy, z1, cx, botY, cz, halfThick, r, g, b, alpha)
        drawBeamStrutCoords(consumer, m4, x1, cy, z1, x2, cy, z2, halfThick, r, g, b, alpha)

        drawBeamStrutCoords(consumer, m4, x2, cy, z2, cx, topY, cz, halfThick, r, g, b, alpha)
        drawBeamStrutCoords(consumer, m4, x2, cy, z2, cx, botY, cz, halfThick, r, g, b, alpha)
        drawBeamStrutCoords(consumer, m4, x2, cy, z2, x3, cy, z3, halfThick, r, g, b, alpha)

        drawBeamStrutCoords(consumer, m4, x3, cy, z3, cx, topY, cz, halfThick, r, g, b, alpha)
        drawBeamStrutCoords(consumer, m4, x3, cy, z3, cx, botY, cz, halfThick, r, g, b, alpha)
        drawBeamStrutCoords(consumer, m4, x3, cy, z3, x0, cy, z0, halfThick, r, g, b, alpha)
    }

    private fun drawDashedBeamStrut(
        consumer: VertexConsumer,
        m4: Matrix4f,
        a: Vec3,
        b: Vec3,
        halfThick: Double,
        r: Int,
        g: Int,
        blue: Int,
        alpha: Int,
        phaseScroll: Float,
    ) {
        val dx = b.x - a.x; val dy = b.y - a.y; val dz = b.z - a.z
        val totalLen = sqrt(dx * dx + dy * dy + dz * dz)
        if (totalLen < 0.5 || alpha <= 2) return
        val dashCount = (totalLen / 1.8).toInt().coerceIn(4, 28)
        for (i in 0 until dashCount) {
            val f0 = ((i.toFloat() / dashCount) + phaseScroll) % 1.0F
            val f1 = (f0 + 0.55F / dashCount).coerceAtMost(1.0F)
            if (f1 <= f0) continue
            val d0 = f0.toDouble(); val d1 = f1.toDouble()
            drawBeamStrutCoords(
                consumer, m4,
                a.x + dx * d0, a.y + dy * d0, a.z + dz * d0,
                a.x + dx * d1, a.y + dy * d1, a.z + dz * d1,
                halfThick, r, g, blue, alpha,
            )
        }
    }

    private fun drawBeamStrut(
        consumer: VertexConsumer,
        m4: Matrix4f,
        a: Vec3,
        b: Vec3,
        halfThick: Double,
        r: Int,
        g: Int,
        blue: Int,
        alpha: Int,
    ) {
        drawBeamStrutCoords(consumer, m4, a.x, a.y, a.z, b.x, b.y, b.z, halfThick, r, g, blue, alpha)
    }

    private fun drawBeamStrutCoords(
        consumer: VertexConsumer,
        m4: Matrix4f,
        ax: Double, ay: Double, az: Double,
        bx: Double, by: Double, bz: Double,
        halfThick: Double,
        r: Int,
        g: Int,
        blue: Int,
        alpha: Int,
    ) {
        if (alpha <= 1) return
        val dx = bx - ax; val dy = by - ay; val dz = bz - az
        val lenSq = dx * dx + dy * dy + dz * dz
        if (lenSq < 1.0E-5) return
        val invLen = 1.0 / sqrt(lenSq)
        val ndx = dx * invLen; val ndy = dy * invLen; val ndz = dz * invLen

        // u = normalize(nd × ref) * halfThick
        var ux: Double; var uy: Double; var uz: Double
        if (abs(ndy) < 0.9) {
            // nd × (0, 1, 0) = (-ndz, 0, ndx)
            ux = -ndz; uy = 0.0; uz = ndx
        } else {
            // nd × (1, 0, 0) = (0, ndz, -ndy)
            ux = 0.0; uy = ndz; uz = -ndy
        }
        val uInv = halfThick / sqrt(ux * ux + uy * uy + uz * uz)
        ux *= uInv; uy *= uInv; uz *= uInv

        // v = normalize(nd × u) * halfThick
        var vx = ndy * uz - ndz * uy
        var vy = ndz * ux - ndx * uz
        var vz = ndx * uy - ndy * ux
        val vInv = halfThick / sqrt(vx * vx + vy * vy + vz * vz)
        vx *= vInv; vy *= vInv; vz *= vInv

        val axF = ax.toFloat(); val ayF = ay.toFloat(); val azF = az.toFloat()
        val bxF = bx.toFloat(); val byF = by.toFloat(); val bzF = bz.toFloat()
        val uxF = ux.toFloat(); val uyF = uy.toFloat(); val uzF = uz.toFloat()
        val vxF = vx.toFloat(); val vyF = vy.toFloat(); val vzF = vz.toFloat()

        emitSingleQuadCoords(
            consumer, m4,
            axF - uxF, ayF - uyF, azF - uzF,
            axF + uxF, ayF + uyF, azF + uzF,
            bxF + uxF, byF + uyF, bzF + uzF,
            bxF - uxF, byF - uyF, bzF - uzF,
            r, g, blue, alpha,
        )
        emitSingleQuadCoords(
            consumer, m4,
            axF - vxF, ayF - vyF, azF - vzF,
            axF + vxF, ayF + vyF, azF + vzF,
            bxF + vxF, byF + vyF, bzF + vzF,
            bxF - vxF, byF - vyF, bzF - vzF,
            r, g, blue, alpha,
        )
    }

    private fun emitSingleQuadCoords(
        consumer: VertexConsumer,
        m4: Matrix4f,
        x0: Float, y0: Float, z0: Float,
        x1: Float, y1: Float, z1: Float,
        x2: Float, y2: Float, z2: Float,
        x3: Float, y3: Float, z3: Float,
        r: Int, g: Int, b: Int, alpha: Int,
    ) {
        consumer.vertex(m4, x0, y0, z0).color(r, g, b, alpha).endVertex()
        consumer.vertex(m4, x1, y1, z1).color(r, g, b, alpha).endVertex()
        consumer.vertex(m4, x2, y2, z2).color(r, g, b, alpha).endVertex()
        consumer.vertex(m4, x3, y3, z3).color(r, g, b, alpha).endVertex()
    }

    /**
     * 自卫星节点沿略微外扩的斜线向地面做射线检测（由渲染主入口每 4 tick 节流调用并缓存世界坐标）；
     * 若命中地形方块则精确贴地，否则退化到高度图或保底落差。
     */
    private fun findGroundSpotWorld(
        entity: UrCircleEntity,
        ix: Double,
        iy: Double,
        iz: Double,
        satLocal: Vec3,
        dir: Vec3,
        unit: Float,
    ): Vec3 {
        val lvl = entity.level()
        val satWorld = Vec3(ix + satLocal.x, iy + satLocal.y, iz + satLocal.z)
        val probeEnd = satWorld.add(dir.x * 28.0 * unit, -240.0, dir.z * 28.0 * unit)
        val hit = lvl.clip(ClipContext(satWorld, probeEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, entity))
        return if (hit.type == HitResult.Type.BLOCK) {
            hit.location
        } else {
            val fx = satWorld.x + dir.x * 9.0 * unit
            val fz = satWorld.z + dir.z * 9.0 * unit
            val hmY = lvl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(fx), Mth.floor(fz))
            if (hmY > lvl.minBuildHeight) {
                Vec3(fx, hmY.toDouble(), fz)
            } else {
                satWorld.add(dir.x * 9.0 * unit, -52.0 * unit.coerceAtLeast(0.8F), dir.z * 9.0 * unit)
            }
        }
    }

    private fun dyingRingSurvival(entity: UrCircleEntity, ringIndex: Int, segIndex: Int, partialTick: Float): Float {
        if (entity.circleState != CircleState.DYING) return 1.0F
        val vanishTick = 32.0F + entity.deathWaveOf(ringIndex) * 20.0F + (segIndex % 6) * 2.5F
        val dt = (entity.stateTicks + partialTick) - vanishTick
        return when {
            dt <= 0.0F -> 1.0F
            dt >= 16.0F -> 0.0F
            else -> 1.0F - dt / 16.0F
        }
    }

    private fun renderDyingVolumetricRays(
        entity: UrCircleEntity,
        partialTick: Float,
        t: Float,
        localCenter: Vec3,
        effScale: Float,
        lineBuf: VertexConsumer,
        m4: Matrix4f,
    ) {
        val start = 72.0F
        val p = ((entity.stateTicks + partialTick - start) / (UrCircleEntity.CORE_BREAK_TICK - start)).coerceIn(0.0F, 1.0F)
        if (p <= 0.01F) return

        val rayCount = 18
        val rayLen = (3.5 + p * p * 11.5) * effScale
        val alpha = (90 + p * 145).toInt().coerceIn(0, 235)
        for (i in 0 until rayCount) {
            val phi = i * 2.39996 + t * (0.04 + 0.08 * p) * (if (i % 2 == 0) 1.0 else -1.0)
            val cosTheta = 1.0 - 2.0 * ((i + 0.5) / rayCount)
            val sinTheta = kotlin.math.sqrt((1.0 - cosTheta * cosTheta).coerceAtLeast(0.0))
            val dir = Vec3(sinTheta * cos(phi), cosTheta, sinTheta * sin(phi)).normalize()
            val end = localCenter.add(dir.scale(rayLen))
            val hw = (0.18 + 0.42 * p) * effScale.coerceIn(1.0F, 5.0F) * 0.25
            val rCol = if (i % 3 == 0) 255 else 210
            val gCol = if (i % 3 == 0) 220 else 125
            val bCol = if (i % 3 == 0) 140 else 255
            drawBeamStrut(lineBuf, m4, localCenter, end, hw, rCol, gCol, bCol, alpha)
        }
    }

    private fun renderDeathAftermathPass(
        entity: UrCircleEntity,
        partialTick: Float,
        localCenter: Vec3,
        unit: Float,
        effScale: Float,
        lineBuf: VertexConsumer,
        m4: Matrix4f,
    ) {
        val elapsed = (entity.stateTicks - UrCircleEntity.CORE_BREAK_TICK + partialTick).coerceAtLeast(0.0F)
        val total = (UrCircleEntity.DEATH_END_TICK - UrCircleEntity.CORE_BREAK_TICK).toFloat().coerceAtLeast(1.0F)
        val postT = (elapsed / total).coerceIn(0.0F, 1.0F)
        val fade = 1.0F - postT
        if (fade <= 0.02F) return

        // 1. 三重扩散球形震荡冲击波（最远扩至 ~88 格）
        val waveR = 6.0 + postT * 22.0 * effScale
        val waveAlpha = (fade * fade * 230.0F).toInt().coerceIn(0, 230)
        drawStrutRing(lineBuf, m4, localCenter, Vec3(1.0, 0.0, 0.0), Vec3(0.0, 1.0, 0.0), waveR, 40, 0.75 * unit, 255, 225, 130, waveAlpha)
        drawStrutRing(lineBuf, m4, localCenter, Vec3(0.0, 1.0, 0.0), Vec3(1.0, 0.0, 0.0), waveR * 0.88, 32, 0.55 * unit, 200, 120, 255, (waveAlpha * 0.85F).toInt())
        drawStrutRing(lineBuf, m4, localCenter, Vec3(0.0, 1.0, 0.0), Vec3(0.0, 0.0, 1.0), waveR * 0.88, 32, 0.55 * unit, 200, 120, 255, (waveAlpha * 0.85F).toInt())

        // 2. 直冲净空天际的飞升余晖光柱
        val spireH = 40.0 + postT * 90.0
        val spireR = (2.8 * unit * fade).coerceAtLeast(0.08)
        val spireAlpha = (fade * 215.0F).toInt().coerceIn(0, 215)
        drawBeamStrut(lineBuf, m4, localCenter.add(0.0, -12.0, 0.0), localCenter.add(0.0, spireH, 0.0), spireR, 255, 235, 165, spireAlpha)
    }

    private fun orthoRadial(n: Vec3): Vec3 =
        n.cross(Vec3(1.0, 0.0, 0.0)).let {
            if (it.lengthSqr() < 1.0E-4) Vec3(0.0, 0.0, 1.0) else it.normalize()
        }
}
