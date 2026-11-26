package pub.pigeon.yggdyy.hexmob.content.ur_circle.subentities

import pub.pigeon.yggdyy.hexmob.HexMob
import pub.pigeon.yggdyy.hexmob.content.ur_circle.UrCircleEntity
import pub.pigeon.yggdyy.hexmob.content.ur_circle.UrCirclePart

/**
 * 大型旋转环体上的隐形可行走弧段子实体（M2 登环机制）：
 * - 在二/三阶段环体展开后与岩冠/石脊同半径同步公转；
 * - 不单独渲染模型（模型由 UrCircleVfxRenderer 与 SlateRenderer 负责），
 *   专职提供致密无死角的硬碰撞顶面与玩家承重跟随（Carry-Along）。
 */
class RingPlatformPart(
    parentMob: UrCircleEntity,
    baseWidth: Float = 1.65F,
    baseHeight: Float = 0.85F,
) : UrCirclePart(parentMob, HexMob.id("platform"), baseWidth, baseHeight) {
    init {
        walkPlatform = true
    }

    override fun isPickable(): Boolean = false
    override fun shouldRenderAsPart(): Boolean = false
}
