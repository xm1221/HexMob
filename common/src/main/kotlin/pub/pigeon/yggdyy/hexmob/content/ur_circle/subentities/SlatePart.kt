package pub.pigeon.yggdyy.hexmob.content.ur_circle.subentities

import at.petrak.hexcasting.api.casting.iota.Iota
import at.petrak.hexcasting.api.casting.iota.IotaType
import at.petrak.hexcasting.api.casting.iota.PatternIota
import at.petrak.hexcasting.api.casting.math.HexPattern
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3
import pub.pigeon.yggdyy.hexmob.HexMob
import pub.pigeon.yggdyy.hexmob.api.entity.IotaEntity
import pub.pigeon.yggdyy.hexmob.content.ur_circle.UrCircleEntity
import pub.pigeon.yggdyy.hexmob.content.ur_circle.UrCirclePart

/**
 * 大环石板子实体：
 * - 实现 [IotaEntity]，配合编年史家之策略（OpTheCoolerRead / OpTheCoolerWrite）
 *   支持读取当前图案，或在可篡改态下被施法者覆写诱发大环自身 Mishap；
 * - 携带象征节点角色 [symbolType]、物理脱轨速度/位移态与可篡改标志，直接作为技能参数来源。
 */
open class SlatePart(
    parentMob: UrCircleEntity,
    id: net.minecraft.resources.ResourceLocation,
    val basePattern: HexPattern,
) : UrCirclePart(parentMob, id, 1F, 1F), IotaEntity {

    constructor(parentMob: UrCircleEntity, pattern: HexPattern) : this(parentMob, HexMob.id("slate"), pattern)

    var pattern: HexPattern = basePattern
    var symbolType: SlateSymbolType = SlateSymbolType.NONE

    /** 当前技能部署出的目标世界坐标（非空时石板脱离环轨悬停/公转该位置附近）。 */
    var deployedPos: Vec3? = null
    var deployTicks: Int = 0
    var skillProgress: Float = 0.0F

    /** 是否因玩家冲量/减速或主动脱离核心而进入“可篡改态”。 */
    var tamperable: Boolean = false

    /** 是否已被轰回核心触发反噬（本轮技能内冻结，不再增幅或锁定）。 */
    var rebounded: Boolean = false

    /** 物理脱轨标志与积分位置/速度。 */
    var detached: Boolean = false
    var detachedPos: Vec3 = Vec3.ZERO
    var detachedVel: Vec3 = Vec3.ZERO

    /** 上一 tick 轨道基准位置与瞬时线速度。 */
    var lastNominalPos: Vec3 = Vec3.ZERO
    var orbitSpeed: Double = 0.0

    /** 被负面减速手段命中的剩余 tick。 */
    var slowdownTicks: Int = 0

    /** 上次被玩家冲量冲击到的服务器 tick，防止同 tick 重复触发。 */
    var lastImpulseTick: Int = -1

    private var customIotaTag: CompoundTag? = null

    fun assignSymbol(type: SlateSymbolType, targetPos: Vec3? = null) {
        symbolType = type
        pattern = if (type == SlateSymbolType.NONE) basePattern else type.hexPattern
        deployedPos = targetPos
        deployTicks = 0
        skillProgress = 0.0F
        tamperable = false
        rebounded = false
        detached = false
        detachedPos = posNow
        detachedVel = Vec3.ZERO
        slowdownTicks = 0
        customIotaTag = null
    }

    fun clearSymbol() {
        symbolType = SlateSymbolType.NONE
        pattern = basePattern
        deployedPos = null
        deployTicks = 0
        skillProgress = 0.0F
        tamperable = false
        rebounded = false
        detached = false
        detachedVel = Vec3.ZERO
        slowdownTicks = 0
        customIotaTag = null
    }

    // ---- IotaEntity / ADIotaHolder ----

    override fun getIotaNbt(): CompoundTag {
        val custom = customIotaTag
        if (custom != null) return custom
        return IotaType.serialize(PatternIota(pattern))
    }

    override fun setIotaNbt(nbt: CompoundTag) {
        customIotaTag = nbt.copy()
    }

    override fun getServerLevel(): ServerLevel? = parentMob.level() as? ServerLevel

    override fun writeable(): Boolean =
        active && !rebounded && symbolType != SlateSymbolType.NONE && tamperable

    override fun writeIota(iota: Iota?, simulate: Boolean): Boolean {
        if (!writeable()) {
            if (!simulate) {
                parentMob.onProtectedSlateWriteAttempt(this)
            }
            return false
        }
        if (iota == null) return true
        if (!simulate) {
            setIotaNbt(IotaType.serialize(iota))
            if (iota is PatternIota) {
                pattern = iota.pattern
            }
            parentMob.onSlateHexOverwrite(this, iota)
        }
        return true
    }
}
