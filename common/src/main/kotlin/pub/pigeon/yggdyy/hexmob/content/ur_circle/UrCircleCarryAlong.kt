package pub.pigeon.yggdyy.hexmob.content.ur_circle

import net.minecraft.client.Minecraft
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.MoverType
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import pub.pigeon.yggdyy.hexmob.content.IHMMultipartEntity
import pub.pigeon.yggdyy.hexmob.content.ur_circle.spells.RingSpinSkill
import kotlin.math.abs

/**
 * M2 实体登环与承重跟随（Carry-Along）：
 * - 在二/三阶段环体展开后，计算玩家/生物脚底是否落在可行走环段顶面容差内，
 *   于大环子实体位置更新瞬间同步牵引本地玩家并贴合至环段顶面 `maxY`、维持 `onGround = true`，
 *   彻底避免客户端 tick 顺序竞态、邻段隐形侧壁卡脚与无法跳跃/冰面打滑问题；
 * - 发动 RingSpinSkill（甩尾狂飙，前摇结束后）或 FOLD_GRIND（合拢翻折）时故意削弱阻尼并甩离玩家，
 *   转化为 Blink / Impulse / 缓降二次登环的立体空战博弈。
 */
object UrCircleCarryAlong {
    private var lastClientCarryTick: Long = Long.MIN_VALUE
    private var lastClientCarryPlayerId: Int = -1

    /**
     * 在客户端 `UrCircleEntity.updatePartsPos()` 更新完所有平台子实体本 tick 位移后立即调用，
     * 保证无论玩家还是大环先 tick，本地玩家都会随平台原子同步移动而不会陷入/脱开地板。
     */
    fun carryClientPlayerOnPartsUpdate(circle: UrCircleEntity) {
        if (!circle.level().isClientSide || !circle.canStandOnRings()) return
        val client = Minecraft.getInstance()
        val player = client.player ?: return
        if (player.level() !== circle.level()) return
        if (lastClientCarryTick == circle.level().gameTime && lastClientCarryPlayerId == player.id) return
        if (carrySingleCircleClientPlayer(circle, player)) {
            lastClientCarryTick = circle.level().gameTime
            lastClientCarryPlayerId = player.id
        }
    }

    fun applyClientCarryAlong(client: Minecraft) {
        val player = client.player ?: return
        val level = client.level ?: return
        if (player.isSpectator || player.abilities.flying) return

        val alreadyCarried = lastClientCarryTick == level.gameTime && lastClientCarryPlayerId == player.id
        val pBox = player.boundingBox

        IHMMultipartEntity.updateInstances()
        for (inst in IHMMultipartEntity.instances) {
            val circle = inst as? UrCircleEntity ?: continue
            if (circle.level() !== level || !circle.canStandOnRings()) continue
            if (!circle.getPartsBoundingBox().inflate(6.0).intersects(pBox)) continue

            if (alreadyCarried) {
                val support = findSupportingPlatform(circle, player, player.y, expanded = true) ?: continue
                val unstable = isUnstable(circle)
                if (!unstable && player.deltaMovement.y <= 0.14 && abs(player.y - support.boundingBox.maxY) <= 0.45) {
                    if (player.deltaMovement.y < 0.0) {
                        player.setDeltaMovement(player.deltaMovement.x, 0.0, player.deltaMovement.z)
                    }
                    player.setOnGround(true)
                    player.fallDistance = 0.0F
                }
                return
            }

            if (carrySingleCircleClientPlayer(circle, player)) {
                lastClientCarryTick = level.gameTime
                lastClientCarryPlayerId = player.id
                return
            }
        }
    }

    private fun carrySingleCircleClientPlayer(circle: UrCircleEntity, player: Player): Boolean {
        if (player.isSpectator || player.abilities.flying) return false
        val pBox = player.boundingBox
        if (!circle.getPartsBoundingBox().inflate(6.0).intersects(pBox)) return false

        val support = findSupportingPlatform(circle, player, player.y, expanded = true) ?: return false
        val delta = support.tickDeltaPos()
        val unstable = isUnstable(circle)

        if (unstable) {
            // 失稳期：仅保留 25% 跟随阻尼，并沿旋转切线与离心方向甩离玩家
            val origin = circle.beamOrigin()
            val radial = player.position().subtract(origin).let { Vec3(it.x, 0.0, it.z) }
            val outward = if (radial.lengthSqr() > 1.0E-4) radial.normalize().scale(0.32) else Vec3.ZERO
            val throwVel = delta.scale(1.45).add(outward).add(0.0, 0.26, 0.0)
            player.move(MoverType.SELF, delta.scale(0.25))
            player.setDeltaMovement(player.deltaMovement.add(throwVel))
            player.hasImpulse = true
            return true
        }

        val jumpingUp = player.deltaMovement.y > 0.14
        if (jumpingUp) {
            val nx = player.x + delta.x
            val ny = player.y + delta.y.coerceAtLeast(0.0)
            val nz = player.z + delta.z
            movePlayerAvoidingSelfParts(player, nx, ny, nz)
            return true
        }

        val nx = player.x + delta.x
        val ny = support.boundingBox.maxY
        val nz = player.z + delta.z
        movePlayerAvoidingSelfParts(player, nx, ny, nz)
        if (player.deltaMovement.y < 0.0) {
            player.setDeltaMovement(player.deltaMovement.x, 0.0, player.deltaMovement.z)
        }
        player.setOnGround(true)
        player.fallDistance = 0.0F
        return true
    }

    private fun movePlayerAvoidingSelfParts(player: Player, nx: Double, ny: Double, nz: Double) {
        val dx = nx - player.x
        val dy = ny - player.y
        val dz = nz - player.z
        if (dx * dx + dy * dy + dz * dz < 1.0E-9) return
        val level = player.level()
        val targetBox = player.boundingBox.move(dx, dy, dz)
        if (!level.getBlockCollisions(player, targetBox).iterator().hasNext()) {
            player.setPos(nx, ny, nz)
        } else {
            player.move(MoverType.SELF, Vec3(dx, dy, dz))
        }
    }

    fun applyServerCarryAlong(circle: UrCircleEntity) {
        if (!circle.canStandOnRings()) return
        val level = circle.level()
        val hull = circle.getPartsBoundingBox()
        val entities = level.getEntitiesOfClass(LivingEntity::class.java, hull) {
            it !== circle && !isUrCircleAlly(it) && it !is Player
        }
        val unstable = isUnstable(circle)
        val origin = circle.beamOrigin()

        for (ent in entities) {
            val part = findSupportingPlatform(circle, ent, ent.y, expanded = true) ?: continue
            val delta = part.tickDeltaPos()
            if (unstable) {
                val radial = ent.position().subtract(origin).let { Vec3(it.x, 0.0, it.z) }
                val outward = if (radial.lengthSqr() > 1.0E-4) radial.normalize().scale(0.30) else Vec3.ZERO
                ent.move(MoverType.SELF, delta.scale(0.25))
                ent.setDeltaMovement(ent.deltaMovement.add(delta.scale(1.35)).add(outward).add(0.0, 0.22, 0.0))
            } else {
                val nx = ent.x + delta.x
                val ny = if (ent.deltaMovement.y > 0.14) ent.y + delta.y.coerceAtLeast(0.0) else part.boundingBox.maxY
                val nz = ent.z + delta.z
                val targetBox = ent.boundingBox.move(nx - ent.x, ny - ent.y, nz - ent.z)
                if (!level.getBlockCollisions(ent, targetBox).iterator().hasNext()) {
                    ent.setPos(nx, ny, nz)
                } else {
                    ent.move(MoverType.SELF, Vec3(nx - ent.x, ny - ent.y, nz - ent.z))
                }
                if (ent.deltaMovement.y <= 0.14) {
                    if (ent.deltaMovement.y < 0.0) {
                        ent.setDeltaMovement(ent.deltaMovement.x, 0.0, ent.deltaMovement.z)
                    }
                    ent.setOnGround(true)
                    ent.fallDistance = 0.0F
                }
            }
        }
    }

    fun isEntityOnRing(circle: UrCircleEntity, entity: LivingEntity): Boolean =
        circle.canStandOnRings() && findSupportingPlatform(circle, entity, entity.boundingBox.minY, expanded = true) != null

    /** 供服务端防飞行踢出逻辑校验：玩家当前正在大环可站立区域或风柱登环区域内时免于滞空计数踢出。 */
    fun isPlayerNearWalkableCircle(player: Player): Boolean {
        if (player.isSpectator) return false
        val level = player.level()
        IHMMultipartEntity.updateInstances()
        for (inst in IHMMultipartEntity.instances) {
            val circle = inst as? UrCircleEntity ?: continue
            if (circle.level() !== level || !circle.canStandOnRings()) continue
            if (isEntityOnRing(circle, player)) return true
            val origin = circle.beamOrigin()
            val maxHoriz = circle.ringOuterRadius() + 18.0
            val dx = player.x - origin.x
            val dz = player.z - origin.z
            if (dx * dx + dz * dz <= maxHoriz * maxHoriz &&
                player.y >= origin.y - 60.0 && player.y <= origin.y + 24.0
            ) {
                return true
            }
        }
        return false
    }

    private fun isUnstable(circle: UrCircleEntity): Boolean =
        (circle.entityData.get(UrCircleEntity.RING_SPINNING) && circle.stateTicks >= RingSpinSkill.WARNING_TICKS) ||
            circle.circleState == CircleState.FOLD_GRIND

    private fun findSupportingPlatform(
        circle: UrCircleEntity,
        entity: LivingEntity,
        footY: Double,
        expanded: Boolean = false,
    ): UrCirclePart? {
        val horizPad = if (expanded) 0.85 else 0.45
        val belowTol = if (expanded) 1.35 else 0.65
        val aboveTol = if (expanded) 0.95 else 0.70
        val horizBox = entity.boundingBox.inflate(horizPad, 0.0, horizPad)
        val cx = entity.x
        val cz = entity.z

        var best: UrCirclePart? = null
        var bestHorizSqr = Double.MAX_VALUE
        for (part in circle.getAllParts()) {
            if (!part.active || !part.walkPlatform || part.posNow == Vec3.ZERO) continue
            val pBox = part.boundingBox
            if (footY < pBox.maxY - belowTol || footY > pBox.maxY + aboveTol) continue

            val insideNow = horizBox.maxX >= pBox.minX && horizBox.minX <= pBox.maxX &&
                horizBox.maxZ >= pBox.minZ && horizBox.minZ <= pBox.maxZ
            val insidePrev = if (!insideNow && expanded && part.posPrev != Vec3.ZERO) {
                val hw = part.bbWidth * 0.5 + horizPad
                abs(cx - part.posPrev.x) <= hw && abs(cz - part.posPrev.z) <= hw
            } else {
                false
            }
            if (!insideNow && !insidePrev) continue

            val dx = cx - part.posNow.x
            val dz = cz - part.posNow.z
            val hSqr = dx * dx + dz * dz
            if (hSqr < bestHorizSqr) {
                bestHorizSqr = hSqr
                best = part
            }
        }
        return best
    }
}
