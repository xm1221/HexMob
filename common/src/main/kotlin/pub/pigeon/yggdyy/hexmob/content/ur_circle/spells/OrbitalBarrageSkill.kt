package pub.pigeon.yggdyy.hexmob.content.ur_circle.spells

import at.petrak.hexcasting.common.lib.HexSounds
import net.minecraft.world.phys.Vec3
import pub.pigeon.yggdyy.hexmob.content.ur_circle.SlateProjectile
import pub.pigeon.yggdyy.hexmob.content.ur_circle.UrCircleEntity
import pub.pigeon.yggdyy.hexmob.content.ur_circle.UrCircleHexVfx
import pub.pigeon.yggdyy.hexmob.content.ur_circle.subentities.SlatePart
import pub.pigeon.yggdyy.hexmob.content.ur_circle.subentities.SlateSymbolType
import pub.pigeon.yggdyy.hexmob.registry.HexMobEntities
import kotlin.math.cos
import kotlin.math.sin

/**
 * 【石板轨道轰炸】M1 参数化版：
 * - 吟唱期派出一枚 Lock 锁敌追踪板环绕目标公转以维持轰炸阵眼；
 * - 若 Lock 板被打偏超过 11 格或被反噬，轰炸落点将失去追踪并散落偏移！
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
        val t = circle.target ?: return
        val lockOrbAng = circle.tickCount * 0.16
        val lockAnchor = Vec3(t.x + cos(lockOrbAng) * 4.5, t.y + 2.8, t.z + sin(lockOrbAng) * 4.5)
        var lockSlate = circle.findSymbolicSlate(SlateSymbolType.LOCK)
        if (circle.stateTicks == 1 || lockSlate == null) {
            lockSlate = circle.deploySymbolicSlate(SlateSymbolType.LOCK, lockAnchor)
        }
        if (lockSlate != null && !lockSlate.detached && !lockSlate.rebounded) {
            lockSlate.deployedPos = lockAnchor
        }

        val trackingBroken = lockSlate != null && (lockSlate.rebounded ||
            (lockSlate.detached && lockSlate.posNow.distanceTo(t.position()) > 11.0))

        if (circle.stateTicks == 1 || strikeZones.isEmpty() || (circle.stateTicks % 15 == 0 && trackingBroken)) {
            strikeZones.clear()
            val count = if (circle.phase >= 2) STRIKE_COUNT_PHASE2 else STRIKE_COUNT_PHASE1
            val centerRef = if (trackingBroken && lockSlate != null) lockSlate.posNow else t.position()
            for (i in 0 until count) {
                val ang = Math.PI * 2.0 * i / count + circle.random.nextDouble() * 0.4
                val r = if (trackingBroken) 10.0 + circle.random.nextDouble() * 12.0 else (if (i == 0) 0.5 else 2.5 + circle.random.nextDouble() * 5.0)
                strikeZones.add(Vec3(centerRef.x + cos(ang) * r, t.y, centerRef.z + sin(ang) * r))
            }
        }
        if (circle.stateTicks % 4 == 0) {
            for (zone in strikeZones) {
                UrCircleHexVfx.spawnGroundCircle(
                    level,
                    zone,
                    radius = 2.2,
                    color = if (trackingBroken) UrCircleHexVfx.SIPHON_CYAN else UrCircleHexVfx.AMETHYST_VIOLET,
                    points = 14,
                )
            }
        }
    }

    override fun cast(circle: UrCircleEntity) {
        try {
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
        } finally {
            strikeZones.clear()
        }
    }

    override fun onChannelCancel(circle: UrCircleEntity) {
        strikeZones.clear()
    }

    companion object {
        const val CHANNEL_TICKS = 55
        const val STRIKE_COUNT_PHASE1 = 4
        const val STRIKE_COUNT_PHASE2 = 8
        const val PROJECTILE_SPEED = 2.1F
    }
}
