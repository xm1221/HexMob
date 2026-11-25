package pub.pigeon.yggdyy.hexmob.content.ur_circle.spells

import at.petrak.hexcasting.common.lib.HexSounds
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import pub.pigeon.yggdyy.hexmob.content.ur_circle.UrCircleEntity
import pub.pigeon.yggdyy.hexmob.content.ur_circle.UrCircleHexVfx
import pub.pigeon.yggdyy.hexmob.content.ur_circle.canUrCircleGrief
import pub.pigeon.yggdyy.hexmob.content.ur_circle.subentities.SlateSymbolType

/**
 * 【定向爆破】M1 参数化版：
 * 吟唱时放出 Aim 瞄准板飞向目标点位；最终按 Aim 石板的实际坐标引爆。
 * 若玩家在可篡改窗口将 Aim 板轰回核心近侧，则直接炸在核心自身触发反噬！
 */
class ExplosionSkill : UrCircleSkill("explosion", 45) {

    override val weight: Int = 3
    override val waveColor: Int = UrCircleHexVfx.SHORT_CIRCUIT_RED

    override fun channelHurtInterrupts(circle: UrCircleEntity): Boolean = false

    override fun canUse(circle: UrCircleEntity): Boolean {
        if (circle.level().isClientSide) return false
        return circle.currentHated().any { circle.distanceToSqr(it) < TRIGGER_RANGE * TRIGGER_RANGE }
    }

    override fun channelParticlesPerPulse(circle: UrCircleEntity): Int = 18
    override fun channelPulseSound(circle: UrCircleEntity) = HexSounds.CAST_FAILURE
    override fun channelPulseInterval(circle: UrCircleEntity): Int = 8

    override fun onChannelTick(circle: UrCircleEntity) {
        val targetPos = circle.target?.let { it.position().add(0.0, it.bbHeight * 0.5, 0.0) }
            ?: circle.beamOrigin()
        val aim = circle.findSymbolicSlate(SlateSymbolType.AIM)
            ?: circle.deploySymbolicSlate(SlateSymbolType.AIM, targetPos)
        if (aim != null && !aim.detached && !aim.tamperable && !aim.rebounded && circle.stateTicks < 20) {
            aim.deployedPos = targetPos
        }
        val blastPos = aim?.posNow?.takeIf { it != Vec3.ZERO } ?: targetPos
        if (circle.stateTicks % 4 == 0) {
            UrCircleHexVfx.spawnGroundCircle(circle.level(), blastPos, radius = 3.2, color = UrCircleHexVfx.SHORT_CIRCUIT_RED, points = 14)
        }
    }

    override fun cast(circle: UrCircleEntity) {
        val level = circle.level()
        if (level.isClientSide) return
        val aim = circle.findSymbolicSlate(SlateSymbolType.AIM)
        val pos = aim?.posNow?.takeIf { it != Vec3.ZERO } ?: circle.beamOrigin()

        if (aim != null && (aim.rebounded || pos.distanceTo(circle.beamOrigin()) <= circle.ringInnerRadius() + 2.5)) {
            if (!aim.rebounded) circle.onSlateReboundCore(aim)
            val interaction = Level.ExplosionInteraction.NONE
            level.explode(null, pos.x, pos.y, pos.z, EXPLOSION_POWER, interaction)
            return
        }

        val power = EXPLOSION_POWER * circle.ampDamageMultiplier()
        val interaction = if (canUrCircleGrief(level)) Level.ExplosionInteraction.MOB else Level.ExplosionInteraction.NONE
        level.explode(circle, pos.x, pos.y, pos.z, power, interaction)
        for (k in 0 until 20) {
            UrCircleHexVfx.spawnConjure(
                level,
                UrCircleHexVfx.SHORT_CIRCUIT_RED,
                pos.x, pos.y, pos.z,
                (circle.random.nextDouble() - 0.5) * 0.4,
                (circle.random.nextDouble() - 0.5) * 0.4,
                (circle.random.nextDouble() - 0.5) * 0.4,
            )
        }
    }

    companion object {
        const val TRIGGER_RANGE = 18.0
        const val EXPLOSION_POWER = 6.5F
    }
}
