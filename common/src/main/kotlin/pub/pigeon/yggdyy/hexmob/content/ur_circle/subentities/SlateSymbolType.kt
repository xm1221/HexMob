package pub.pigeon.yggdyy.hexmob.content.ur_circle.subentities

import at.petrak.hexcasting.api.casting.math.HexDir
import at.petrak.hexcasting.api.casting.math.HexPattern
import pub.pigeon.yggdyy.hexmob.content.ur_circle.UrCircleHexVfx

/**
 * 象征石板类型（高辨识度收敛设计）：
 * - NONE: 普通轨道石板，仅传导媒质波
 * - AIM: 坐标瞄准板（射线/向量图案，金色指向线），决定光束/爆破/环面斩切落点
 * - LOCK: 锁敌追踪板（侦察/实体图案，紫罗兰追踪线），维持弹幕与灵蛇追踪
 * - AMP: 媒质增幅板（数值/媒质图案，紧贴核心旋绕），放大核心护盾与技能伤害倍率
 * - TRIGGER: 周天触发板（三阶段高悬天穹，赤金脉冲倒计时开关），驱动多石板协同大法术
 */
enum class SlateSymbolType(
    val id: Int,
    val normalColor: Int,
    val hexPattern: HexPattern,
) {
    NONE(0, UrCircleHexVfx.MEDIA_PURPLE, HexPattern.fromAnglesUnchecked("e", HexDir.EAST)),
    AIM(1, UrCircleHexVfx.WAVE_GOLD, HexPattern.fromAnglesUnchecked("wawwwqqaw", HexDir.EAST)),
    LOCK(2, UrCircleHexVfx.BEAM_LAVENDER, HexPattern.fromAnglesUnchecked("qqaeqwaeswqwq", HexDir.NORTH_WEST)),
    AMP(3, 0x7A4CFF, HexPattern.fromAnglesUnchecked("aqaaq", HexDir.EAST)),
    TRIGGER(4, 0xFF7A48, HexPattern.fromAnglesUnchecked("eqawwwwqqaw", HexDir.SOUTH_EAST));

    companion object {
        fun byId(id: Int): SlateSymbolType = entries.getOrElse(id.coerceIn(0, entries.lastIndex)) { NONE }
    }
}
