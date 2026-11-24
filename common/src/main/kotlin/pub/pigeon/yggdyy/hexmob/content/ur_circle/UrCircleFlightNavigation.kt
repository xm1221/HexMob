package pub.pigeon.yggdyy.hexmob.content.ur_circle

import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.sin

/**
 * 大环壳体避障器：用 8 方位 × 3 高度层探测与结果缓存取代全量体素扫描，
 * 控制单 tick 碰撞查询开销的同时确保多环部件不穿墙。
 */
class UrCircleFlightNavigation(private val circle: UrCircleEntity) {
    private var hullClearCache: Boolean? = null
    private var hullCacheTick = -100
    private var hullCachePos = Vec3.ZERO

    private var cachedSafeDir: Vec3? = null
    private var cachedDesired = Vec3.ZERO
    private var safeDirCacheTick = -100

    fun isHullClear(): Boolean {
        val now = circle.tickCount
        val origin = circle.position().add(0.0, circle.bbHeight / 2.0, 0.0)
        if (hullClearCache != null && now - hullCacheTick < CACHE_TICKS && origin.distanceToSqr(hullCachePos) < 1.5) {
            return hullClearCache!!
        }
        hullCacheTick = now
        hullCachePos = origin
        val rOuter = circle.ringOuterRadius() + CLEAR_MARGIN
        val clear = isHullClearAt(circle.level(), origin, rOuter)
        hullClearCache = clear
        return clear
    }

    fun safeFlightDir(desired: Vec3, lookAhead: Double): Vec3? {
        if (desired.lengthSqr() < 1.0E-6) return null
        val desiredNorm = desired.normalize()
        val now = circle.tickCount
        if (now - safeDirCacheTick < CACHE_TICKS && desiredNorm.dot(cachedDesired) > 0.75) {
            return cachedSafeDir
        }
        safeDirCacheTick = now
        cachedDesired = desiredNorm

        val origin = circle.position().add(0.0, circle.bbHeight / 2.0, 0.0)
        val rOuter = circle.ringOuterRadius() + CLEAR_MARGIN
        val level = circle.level()
        val currentlyClear = isHullClear()

        val sideAngle = if ((now / 40) % 2 == 0) 0.75F else -0.75F
        val candidates = ArrayList<Vec3>(4)
        if (!currentlyClear) {
            candidates.add(Vec3(0.0, 1.0, 0.0))
        }
        candidates.add(desiredNorm)
        candidates.add(Vec3(desiredNorm.x, desiredNorm.y + 0.5, desiredNorm.z).normalize())
        candidates.add(desiredNorm.yRot(sideAngle))

        var chosen: Vec3? = null
        for (c in candidates) {
            val end = origin.add(c.scale(lookAhead))
            val endClear = isHullClearAt(level, end, rOuter)
            if (!endClear) continue
            if (lookAhead <= 3.0 || isHullClearAt(level, origin.add(c.scale(lookAhead * 0.5)), rOuter)) {
                chosen = c
                break
            }
        }
        cachedSafeDir = chosen
        return chosen
    }

    private fun isHullClearAt(level: Level, pos: Vec3, rOuter: Double): Boolean {
        val dySpan = rOuter * 0.35
        val ys = doubleArrayOf(-dySpan, 0.0, dySpan)
        for (k in 0 until AZIMUTH_SAMPLES) {
            val a = Math.PI * 2.0 * k / AZIMUTH_SAMPLES
            val sx = pos.x + cos(a) * rOuter
            val sz = pos.z + sin(a) * rOuter
            for (dy in ys) {
                val py = pos.y + dy
                if (!level.noCollision(AABB(sx - 0.1, py - 0.1, sz - 0.1, sx + 0.1, py + 0.1, sz + 0.1))) {
                    return false
                }
            }
        }
        return level.noCollision(AABB(pos.x - 0.8, pos.y - 0.8, pos.z - 0.8, pos.x + 0.8, pos.y + 0.8, pos.z + 0.8))
    }

    companion object {
        private const val CACHE_TICKS = 4
        private const val AZIMUTH_SAMPLES = 8
        private const val CLEAR_MARGIN = 1.2
    }
}
