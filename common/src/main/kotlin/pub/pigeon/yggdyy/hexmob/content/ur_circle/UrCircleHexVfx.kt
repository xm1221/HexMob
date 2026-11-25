package pub.pigeon.yggdyy.hexmob.content.ur_circle

import at.petrak.hexcasting.common.lib.HexSounds
import at.petrak.hexcasting.common.particles.ConjureParticleOptions
import net.minecraft.core.BlockPos
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import pub.pigeon.yggdyy.hexmob.util.spawnParticle
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/** 大环与 Hex 原生视听表现辅助工具（仅服务端粒子广播 + 声音缩放 + 纯参数常量，不依赖客户端类）。 */
object UrCircleHexVfx {
    const val MEDIA_PURPLE = 0xFF_B566FF.toInt()
    const val WAVE_GOLD = 0xFF_FFD166.toInt()
    const val SHORT_CIRCUIT_RED = 0xFF_FF3B5C.toInt()
    const val SIPHON_CYAN = 0xFF_4CE8E0.toInt()
    const val BEAM_LAVENDER = 0xFF_E4C2FF.toInt()
    const val AMETHYST_VIOLET = 0xFF_C77DFF.toInt()

    const val DEFAULT_RANGE = 320.0

    private val PENTATONIC_SEMITONES = intArrayOf(0, 2, 4, 7, 9, 12, 14, 16)

    fun conjure(color: Int): ConjureParticleOptions = ConjureParticleOptions(color)

    fun spawnConjure(
        level: Level,
        color: Int,
        x: Double,
        y: Double,
        z: Double,
        vx: Double = 0.0,
        vy: Double = 0.0,
        vz: Double = 0.0,
        range: Double = DEFAULT_RANGE,
    ) {
        spawnParticle(level, conjure(color), x, y, z, vx, vy, vz, range)
    }

    fun spawnRingArc(
        level: Level,
        from: Vec3,
        to: Vec3,
        color: Int,
        steps: Int = 8,
        range: Double = DEFAULT_RANGE,
    ) {
        if (steps <= 0) return
        val opt = conjure(color)
        val rng = level.random
        val delta = to.subtract(from)
        for (i in 0..steps) {
            val t = i.toDouble() / steps
            val jx = (rng.nextDouble() - 0.5) * 0.25
            val jy = (rng.nextDouble() - 0.5) * 0.25
            val jz = (rng.nextDouble() - 0.5) * 0.25
            val p = from.add(delta.scale(t))
            spawnParticle(level, opt, p.x + jx, p.y + jy, p.z + jz, 0.0, 0.015, 0.0, range)
        }
    }

    fun spawnGroundCircle(
        level: Level,
        center: Vec3,
        radius: Double,
        color: Int,
        points: Int = 20,
        range: Double = DEFAULT_RANGE,
    ) {
        if (points <= 0) return
        val opt = conjure(color)
        for (i in 0 until points) {
            val ang = Math.PI * 2.0 * i / points
            val px = center.x + cos(ang) * radius
            val pz = center.z + sin(ang) * radius
            spawnParticle(level, opt, px, center.y + 0.15, pz, 0.0, 0.03, 0.0, range)
        }
    }

    fun playRingSound(
        level: Level,
        pos: Vec3,
        outerRadius: Double,
        sound: SoundEvent,
        baseVolume: Float = 1.5F,
        pitch: Float = 1.0F,
    ) {
        if (level.isClientSide) return
        val vol = (outerRadius / 10.0).toFloat().coerceIn(baseVolume, 18.0F)
        level.playSound(null, BlockPos.containing(pos), sound, SoundSource.HOSTILE, vol, pitch)
    }

    fun playHexNote(
        level: Level,
        pos: Vec3,
        outerRadius: Double,
        step: Int,
        sound: SoundEvent = HexSounds.SPELL_CIRCLE_FIND_BLOCK,
    ) {
        val semitone = PENTATONIC_SEMITONES[Math.floorMod(step, PENTATONIC_SEMITONES.size)]
        val pitch = (2.0.pow(semitone / 12.0) * 0.8).toFloat().coerceIn(0.75F, 1.6F)
        playRingSound(level, pos, outerRadius, sound, baseVolume = 1.4F, pitch = pitch)
    }
}
