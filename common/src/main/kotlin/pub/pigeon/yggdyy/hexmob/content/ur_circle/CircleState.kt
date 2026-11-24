package pub.pigeon.yggdyy.hexmob.content.ur_circle

/**
 * 大环的战斗状态机，通过 SynchedEntityData 同步到客户端驱动渲染与粒子演出。
 */
enum class CircleState {
    CRUISE,
    WINDUP,
    CHARGING,
    STAGGER,
    BEAM,
    CHANNELING,
    PHASE_TRANSITION,
    SHORT_CIRCUIT,
    FOLD_GRIND,
    DYING;

    companion object {
        fun byId(id: Int): CircleState = values()[id.coerceIn(values().indices)]
    }
}
