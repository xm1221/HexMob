package pub.pigeon.yggdyy.hexmob.util

import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import kotlin.math.*

fun Vec3.rotateDA(deg: Float, axis: Vec3): Vec3 {
    val axisLenSqr = axis.lengthSqr()
    if (!axisLenSqr.isFinite() || axisLenSqr <= 1.0E-8) return this
    val invLen = 1.0 / sqrt(axisLenSqr)
    val y: Vec3 = axis.cross(this).scale(invLen)
    val o: Vec3 = axis.scale(axis.dot(this) / axisLenSqr)
    val x: Vec3 = y.cross(axis).scale(invLen)
    val rad: Double = deg / 180.0 * PI
    return o.add(y.scale(sin(rad))).add(x.scale(cos(rad)))
}

/**
 * 对两帧方向做安全球面/线性插值：先归一化，并在接近 180° 反向导致线性插值归零时沿正交轴旋转过渡。
 */
fun safeLerpDir(prev: Vec3, now: Vec3, partialTick: Double): Vec3 {
    val prevLenSqr = prev.lengthSqr()
    val nowLenSqr = now.lengthSqr()
    if (!nowLenSqr.isFinite() || nowLenSqr <= 1.0E-8) {
        return if (prevLenSqr.isFinite() && prevLenSqr > 1.0E-8) prev.normalize() else Vec3(0.0, 0.0, 1.0)
    }
    val nNow = now.normalize()
    if (!prevLenSqr.isFinite() || prevLenSqr <= 1.0E-8) {
        return nNow
    }
    val nPrev = prev.normalize()
    val lerped = nPrev.lerp(nNow, partialTick)
    val lerpedLenSqr = lerped.lengthSqr()
    if (lerpedLenSqr.isFinite() && lerpedLenSqr > 1.0E-8) {
        return lerped.normalize()
    }
    val ortho = if (abs(nPrev.y) < 0.9) Vec3(0.0, 1.0, 0.0) else Vec3(1.0, 0.0, 0.0)
    val rotAxis = nPrev.cross(ortho)
    return nPrev.rotateDA((partialTick * 180.0).toFloat(), rotAxis).normalize()
}

/**
 * 构建将 +Z (0, 0, 1) 旋转至 [dir] 的四元数，带零向量、非有限值及 180° 反向保护。
 */
fun safeRotateZTo(dir: Vec3, out: Quaternionf = Quaternionf()): Quaternionf {
    val lenSqr = dir.lengthSqr()
    if (!lenSqr.isFinite() || lenSqr <= 1.0E-8) {
        return out.identity()
    }
    val invLen = 1.0 / sqrt(lenSqr)
    val dx = (dir.x * invLen).toFloat()
    val dy = (dir.y * invLen).toFloat()
    val dz = (dir.z * invLen).toFloat()
    if (dz <= -0.9999F) {
        return out.rotationY(PI.toFloat())
    }
    return out.identity().rotateTo(0F, 0F, 1F, dx, dy, dz)
}