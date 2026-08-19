package pub.pigeon.yggdyy.hexmob.content.ur_circle

/**
 * 大环的战斗状态机。
 * CRUISE=悬浮巡航（默认）；WINDUP/CHARGING/STAGGER（冲撞前摇/冲刺/僵直）与 BEAM（核心光线）
 * 由后续招式（第 3、4 步）驱动；通过 SynchedEntityData 同步到客户端供渲染/粒子使用。
 */
enum class CircleState {
    CRUISE, WINDUP, CHARGING, STAGGER, BEAM;

    companion object {
        fun byId(id: Int): CircleState = values()[id.coerceIn(values().indices)]
    }
}
