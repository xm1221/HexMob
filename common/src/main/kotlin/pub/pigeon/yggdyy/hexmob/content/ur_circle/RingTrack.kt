package pub.pigeon.yggdyy.hexmob.content.ur_circle

import at.petrak.hexcasting.api.HexAPI
import at.petrak.hexcasting.api.casting.math.HexDir
import at.petrak.hexcasting.api.casting.math.HexPattern
import net.minecraft.world.phys.Vec3
import pub.pigeon.yggdyy.hexmob.HexMob
import pub.pigeon.yggdyy.hexmob.content.ur_circle.subentities.CubePart
import pub.pigeon.yggdyy.hexmob.content.ur_circle.subentities.SlatePart
import pub.pigeon.yggdyy.hexmob.util.rotateDA
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

enum class RingKind {
    INNER_IMPETUS,
    ECLIPTIC_SLATE,
    OUTER_SLATE,
    POLAR_IMPETUS,
    ZENITH_SLATE,
    APEX_IMPETUS,
}

/**
 * 大环的单条旋转环轨：保存静态轨道参数并计算随阶段/合拢/沉睡插值的法线与半径。
 */
class RingTrack(
    val kind: RingKind,
    val ringIndex: Int,
    val baseRadius: Double,
    val baseTiltDeg: Float,
    val tiltAzimuthDeg: Float,
    val spinDir: Float,
    val spinSpeedMul: Float,
    val partScale: Float,
    val unlockPhase: Int,
    val parts: List<UrCirclePart>,
    val faceTangent: Boolean,
    val haloWidth: Float,
) {
    fun isVisible(phase: Int, phase2Reveal: Float): Boolean =
        unlockPhase == 1 || (phase >= 2 && phase2Reveal > 0.01F)

    fun effectiveNormal(foldProgress: Float, dormantFold: Float): Vec3 {
        val dormantTargetTilt = if (ringIndex % 2 == 0) 48F else -48F
        val baseOrDormantTilt = baseTiltDeg + (dormantTargetTilt - baseTiltDeg) * dormantFold.coerceIn(0F, 1F)
        val finalTilt = baseOrDormantTilt * (1F - foldProgress.coerceIn(0F, 1F))
        if (abs(finalTilt) < 0.01F) return Vec3(0.0, 1.0, 0.0)
        val azRad = Math.toRadians(tiltAzimuthDeg.toDouble())
        val axis = Vec3(cos(azRad), 0.0, sin(azRad))
        return Vec3(0.0, 1.0, 0.0).rotateDA(finalTilt, axis).normalize()
    }

    fun radialAxis(normal: Vec3): Vec3 {
        val ref = if (abs(normal.x) < 0.9) Vec3(1.0, 0.0, 0.0) else Vec3(0.0, 0.0, 1.0)
        return normal.cross(ref).normalize()
    }

    fun effectiveRadius(totalScale: Float, dormantFold: Float, phase2Reveal: Float): Double {
        val dormantShrink = 1.0 - 0.72 * dormantFold.coerceIn(0F, 1F)
        if (unlockPhase == 1) {
            return baseRadius * totalScale * dormantShrink
        }
        if (phase2Reveal <= 0.01F) return 0.0
        val reveal = 0.2 + 0.8 * phase2Reveal.coerceIn(0F, 1F)
        return baseRadius * totalScale * reveal * dormantShrink
    }
}

object RingTrackFactory {
    private val CLASSIC_PATTERNS: List<HexPattern> = listOf(
        HexPattern.fromAnglesUnchecked("eqawwwwqqaw", HexDir.SOUTH_EAST),
        HexPattern.fromAnglesUnchecked("e", HexDir.EAST),
        HexPattern.fromAnglesUnchecked("waqw", HexDir.SOUTH_EAST),
        HexPattern.fromAnglesUnchecked("wedw", HexDir.NORTH_WEST),
        HexPattern.fromAnglesUnchecked("qsq", HexDir.NORTH_EAST),
        HexPattern.fromAnglesUnchecked("aawqqeee", HexDir.NORTH_WEST),
        HexPattern.fromAnglesUnchecked("qqaeqwaeswqwq", HexDir.NORTH_WEST),
        HexPattern.fromAnglesUnchecked("qaq", HexDir.NORTH_EAST),
        HexPattern.fromAnglesUnchecked("wawwawwewwqsq", HexDir.WEST),
        HexPattern.fromAnglesUnchecked("qaqwqaaswa", HexDir.NORTH_EAST),
        HexPattern.fromAnglesUnchecked("wqadaqw", HexDir.NORTH_EAST),
        HexPattern.fromAnglesUnchecked("eqqwqwqeda", HexDir.SOUTH_EAST),
    )

    private val IMPETUS_VARIANTS: List<Pair<String, String>> = listOf(
        "impetus/empty" to "energized=false,facing=south",
        "impetus/look" to "energized=false,facing=south",
        "impetus/redstone" to "energized=false,facing=south,powered=false",
        "impetus/rightclick" to "energized=false,facing=south",
        "impetus/empty" to "energized=true,facing=south",
        "impetus/look" to "energized=true,facing=south",
        "impetus/redstone" to "energized=true,facing=south,powered=false",
        "impetus/rightclick" to "energized=true,facing=south",
        "directrix/empty" to "energized=false,facing=south",
        "directrix/boolean" to "energized=false,facing=south,state=false",
        "directrix/redstone" to "energized=false,facing=south,powered=false",
        "directrix/empty" to "energized=true,facing=south",
        "directrix/boolean" to "energized=true,facing=south,state=false",
        "directrix/redstone" to "energized=true,facing=south,powered=false",
    )

    data class BuiltCircleGeometry(
        val rings: List<RingTrack>,
        val earth: CubePart,
        val allParts: List<UrCirclePart>,
    )

    fun buildAll(circle: UrCircleEntity): BuiltCircleGeometry {
        var nextGlobal = 0

        val innerParts = IMPETUS_VARIANTS.take(12).map { (path, variant) ->
            CubePart(circle, HexAPI.modLoc(path), variant).also {
                it.ringIndex = 0
                it.globalIndex = nextGlobal++
            }
        }
        val innerTrack = RingTrack(
            kind = RingKind.INNER_IMPETUS,
            ringIndex = 0,
            baseRadius = 7.5,
            baseTiltDeg = -20F,
            tiltAzimuthDeg = 0F,
            spinDir = -1F,
            spinSpeedMul = 1.7F,
            partScale = 1.8F,
            unlockPhase = 1,
            parts = innerParts,
            faceTangent = true,
            haloWidth = 2.2F,
        )

        val eclipticParts = (0 until 14).map { idx ->
            val pat = CLASSIC_PATTERNS[idx % CLASSIC_PATTERNS.size]
            SlatePart(circle, pat).also {
                it.ringIndex = 1
                it.globalIndex = nextGlobal++
            }
        }
        val eclipticTrack = RingTrack(
            kind = RingKind.ECLIPTIC_SLATE,
            ringIndex = 1,
            baseRadius = 14.0,
            baseTiltDeg = 38F,
            tiltAzimuthDeg = 0F,
            spinDir = 1F,
            spinSpeedMul = 0.65F,
            partScale = 2.6F,
            unlockPhase = 1,
            parts = eclipticParts,
            faceTangent = false,
            haloWidth = 3.8F,
        )

        val outerParts = (0 until 20).map { idx ->
            val pat = CLASSIC_PATTERNS[idx % CLASSIC_PATTERNS.size]
            SlatePart(circle, pat).also {
                it.ringIndex = 2
                it.globalIndex = nextGlobal++
            }
        }
        val outerTrack = RingTrack(
            kind = RingKind.OUTER_SLATE,
            ringIndex = 2,
            baseRadius = 29.0,
            baseTiltDeg = 4F,
            tiltAzimuthDeg = 20F,
            spinDir = 1F,
            spinSpeedMul = 0.14F,
            partScale = 3.6F,
            unlockPhase = 2,
            parts = outerParts,
            faceTangent = false,
            haloWidth = 7.5F,
        )

        val polarParts = (0 until 16).map { idx ->
            val (path, variant) = IMPETUS_VARIANTS[(idx * 3) % IMPETUS_VARIANTS.size]
            CubePart(circle, HexAPI.modLoc(path), variant).also {
                it.ringIndex = 3
                it.globalIndex = nextGlobal++
            }
        }
        val polarTrack = RingTrack(
            kind = RingKind.POLAR_IMPETUS,
            ringIndex = 3,
            baseRadius = 10.8,
            baseTiltDeg = -36F,
            tiltAzimuthDeg = -35F,
            spinDir = -1F,
            spinSpeedMul = 1.0F,
            partScale = 2.1F,
            unlockPhase = 2,
            parts = polarParts,
            faceTangent = true,
            haloWidth = 3.0F,
        )

        val zenithParts = (0 until 14).map { idx ->
            val pat = CLASSIC_PATTERNS[(idx * 5) % CLASSIC_PATTERNS.size]
            SlatePart(circle, pat).also {
                it.ringIndex = 4
                it.globalIndex = nextGlobal++
            }
        }
        val zenithTrack = RingTrack(
            kind = RingKind.ZENITH_SLATE,
            ringIndex = 4,
            baseRadius = 21.0,
            baseTiltDeg = 72F,
            tiltAzimuthDeg = -55F,
            spinDir = -1F,
            spinSpeedMul = 0.42F,
            partScale = 3.0F,
            unlockPhase = 2,
            parts = zenithParts,
            faceTangent = false,
            haloWidth = 4.5F,
        )

        val apexParts = (0 until 16).map { idx ->
            val (path, variant) = IMPETUS_VARIANTS[(idx * 5) % IMPETUS_VARIANTS.size]
            CubePart(circle, HexAPI.modLoc(path), variant).also {
                it.ringIndex = 5
                it.globalIndex = nextGlobal++
            }
        }
        val apexTrack = RingTrack(
            kind = RingKind.APEX_IMPETUS,
            ringIndex = 5,
            baseRadius = 25.0,
            baseTiltDeg = -18F,
            tiltAzimuthDeg = 75F,
            spinDir = -1F,
            spinSpeedMul = 0.28F,
            partScale = 2.5F,
            unlockPhase = 2,
            parts = apexParts,
            faceTangent = true,
            haloWidth = 3.8F,
        )

        val earth = CubePart(circle, HexMob.id("cube"), 2F, 2F, HexMob.id("ur_circle_core"), "").also {
            it.ringIndex = -1
            it.globalIndex = nextGlobal++
        }

        val rings = listOf(innerTrack, eclipticTrack, outerTrack, polarTrack, zenithTrack, apexTrack)
        val allParts = buildList {
            for (ring in rings) addAll(ring.parts)
            add(earth)
        }
        return BuiltCircleGeometry(rings, earth, allParts)
    }
}
