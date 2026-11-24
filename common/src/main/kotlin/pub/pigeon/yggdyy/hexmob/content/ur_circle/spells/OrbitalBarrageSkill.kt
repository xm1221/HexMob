package pub.pigeon.yggdyy.hexmob.content.ur_circle.spells

import at.petrak.hexcasting.common.lib.HexSounds
import net.minecraft.world.phys.Vec3
import pub.pigeon.yggdyy.hexmob.content.ur_circle.SlateProjectile
import pub.pigeon.yggdyy.hexmob.content.ur_circle.UrCircleEntity
import pub.pigeon.yggdyy.hexmob.content.ur_circle.UrCircleHexVfx
import pub.pigeon.yggdyy.hexmob.content.ur_circle.subentities.SlatePart
import pub.pigeon.yggdyy.hexmob.registry.HexMobEntities
import kotlin.math.cos
import kotlin.math.sin

/**
 * 【石板轨道轰炸】几何招式：
 * 吟唱时在目标脚下锁定多处落点并画出紫色/金紫刻度预警圈；
 * 吟唱结束时从外圈石板齐射俯冲石板弹精准轰炸落点。
 */
class OrbitalBarrageSkill : UrCircleSkill("orbital_barrage", CHANNEL_TICKS) {
    override val weight: Int = 4
    override val minPhase: Int = 1
    override val waveColor: Int = UrCircleHexVfx.AMETHYST_VIOLET
    override val channelInterruptible: Boolean = true

    override fun nodeBreakThreshold(circle: UrCircleEntity): Float = 14.0F
    override fun channelHurtInterrupts(circle: UrCircleEntity): Boolean = false
    override fun channelMarksTarget(circle: UrCircleEntity): Boolean = true

    override fun channelPulseSound(circle: UrCircleEntity) = HexSounds.ADD_TO_PATTERN
    override fun releaseSound(circle: UrCircleEntity) = HexSounds.CAST_THOTH

    private val strikeZones = ArrayList<Vec3>()

    override fun canUse(circle: UrCircleEntity): Boolean {
        if (circle.level().isClientSide) return false
        val t = circle.target
        return t != null && t.isAlive
    }

    override fun onChannelTick(circle: UrCircleEntity) {
        val level = circle.level()
        if (circle.stateTicks == 1 || strikeZones.isEmpty()) {
            strikeZones.clear()
            val t = circle.target ?: return
            val count = if (circle.phase >= 2) STRIKE_COUNT_PHASE2 else STRIKE_COUNT_PHASE1
            for (i in 0 until count) {
                val ang = Math.PI * 2.0 * i / count + circle.random.nextDouble() * 0.4
                val r = if (i == 0) 0.5 else 2.5 + circle.random.nextDouble() * 5.0
                strikeZones.add(Vec3(t.x + cos(ang) * r, t.y, t.z + sin(ang) * r))
            }
        }
        if (circle.stateTicks % 4 == 0) {
            for (zone in strikeZones) {
                UrCircleHexVfx.spawnGroundCircle(
                    level,
                    zone,
                    radius = 2.2,
                    color = UrCircleHexVfx.AMETHYST_VIOLET,
                    points = 14,
                )
            }
        }
    }

    override fun cast(circle: UrCircleEntity) {
        val level = circle.level()
        if (level.isClientSide) return
        val sourceRing = if (circle.phase >= 2 && circle.rings.size > 2) circle.rings[2].parts else circle.ecliptic
        val slateParts = sourceRing.filterIsInstance<SlatePart>().filter { it.active && it.posNow != Vec3.ZERO }
        if (slateParts.isEmpty() || strikeZones.isEmpty()) return

        val projScale = if (circle.phase >= 2) 1.5F else 1.0F
        for ((idx, zone) in strikeZones.withIndex()) {
            val part = slateParts[(idx * 3) % slateParts.size]
            val from = part.posNow
            val projectile = SlateProjectile(HexMobEntities.SLATE_PROJECTILE.get(), level)
            projectile.setPos(from.x, from.y, from.z)
            projectile.owner = circle
            projectile.pattern = part.pattern
            projectile.visualScale = projScale
            val aim = zone.add(0.0, 0.3, 0.0).subtract(from)
            projectile.shoot(aim.x, aim.y, aim.z, PROJECTILE_SPEED, 1.2F)
            level.addFreshEntity(projectile)
            UrCircleHexVfx.spawnRingArc(level, from, from.add(aim.normalize().scale(3.0)), UrCircleHexVfx.AMETHYST_VIOLET, steps = 5)
        }
        strikeZones.clear()
    }

    companion object {
        const val CHANNEL_TICKS = 55
        const val STRIKE_COUNT_PHASE1 = 4
        const val STRIKE_COUNT_PHASE2 = 8
        const val PROJECTILE_SPEED = 1.15F
    }
}
