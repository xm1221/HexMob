package pub.pigeon.yggdyy.hexmob.content.ur_circle

import at.petrak.hexcasting.api.HexAPI
import at.petrak.hexcasting.api.casting.math.HexDir
import at.petrak.hexcasting.api.casting.math.HexPattern
import at.petrak.hexcasting.api.casting.mishaps.Mishap
import at.petrak.hexcasting.common.lib.HexSounds
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.level.ServerBossEvent
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundSource
import net.minecraft.world.BossEvent
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.monster.Enemy
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import pub.pigeon.yggdyy.hexmob.HexMob
import pub.pigeon.yggdyy.hexmob.api.entity.FlickeringEntity
import pub.pigeon.yggdyy.hexmob.content.IHMMultipartEntity
import pub.pigeon.yggdyy.hexmob.content.ur_circle.subentities.CubePart
import pub.pigeon.yggdyy.hexmob.content.ur_circle.subentities.SlatePart
import pub.pigeon.yggdyy.hexmob.registry.HexMobEntities
import pub.pigeon.yggdyy.hexmob.util.rotateDA
import java.util.UUID

class UrCircleEntity(entityType: EntityType<out Mob>, level: Level) : Mob(entityType, level), Enemy,
    IHMMultipartEntity<UrCirclePart>, FlickeringEntity {
    val equator: MutableList<UrCirclePart> = mutableListOf(
        CubePart(this, HexAPI.modLoc("impetus/empty"), "energized=false,facing=south"),
        CubePart(this, HexAPI.modLoc("impetus/look"), "energized=false,facing=south"),
        CubePart(this, HexAPI.modLoc("impetus/redstone"), "energized=false,facing=south,powered=false"),
        CubePart(this, HexAPI.modLoc("impetus/rightclick"), "energized=false,facing=south"),
        CubePart(this, HexAPI.modLoc("impetus/empty"), "energized=true,facing=south"),
        CubePart(this, HexAPI.modLoc("impetus/look"), "energized=true,facing=south"),
        CubePart(this, HexAPI.modLoc("impetus/redstone"), "energized=true,facing=south,powered=false"),
        CubePart(this, HexAPI.modLoc("impetus/rightclick"), "energized=true,facing=south"),
        CubePart(this, HexAPI.modLoc("directrix/empty"), "energized=false,facing=south"),
        CubePart(this, HexAPI.modLoc("directrix/boolean"), "energized=false,facing=south,state=false"),
        CubePart(this, HexAPI.modLoc("directrix/redstone"), "energized=false,facing=south,powered=false"),
        CubePart(this, HexAPI.modLoc("directrix/empty"), "energized=true,facing=south"),
        CubePart(this, HexAPI.modLoc("directrix/boolean"), "energized=true,facing=south,state=false"),
        CubePart(this, HexAPI.modLoc("directrix/redstone"), "energized=true,facing=south,powered=false"),
    )
    val ecliptic: MutableList<UrCirclePart> = mutableListOf(
        SlatePart(this, HexPattern.fromAnglesUnchecked("eqawwwwqqaw", HexDir.SOUTH_EAST)),
        SlatePart(this, HexPattern.fromAnglesUnchecked("e", HexDir.EAST)),
        SlatePart(this, HexPattern.fromAnglesUnchecked("waqw", HexDir.SOUTH_EAST)),
        SlatePart(this, HexPattern.fromAnglesUnchecked("wedw", HexDir.NORTH_WEST)),
        SlatePart(this, HexPattern.fromAnglesUnchecked("qsq", HexDir.NORTH_EAST)),
        SlatePart(this, HexPattern.fromAnglesUnchecked("aawqqeee", HexDir.NORTH_WEST)),
        SlatePart(this, HexPattern.fromAnglesUnchecked("qqaeqwaeswqwq", HexDir.NORTH_WEST)),
        SlatePart(this, HexPattern.fromAnglesUnchecked("qaq", HexDir.NORTH_EAST)),
        SlatePart(this, HexPattern.fromAnglesUnchecked("wawwawwewwqsq", HexDir.WEST)),
        SlatePart(this, HexPattern.fromAnglesUnchecked("qaqwqaaswa", HexDir.NORTH_EAST)),
        SlatePart(this, HexPattern.fromAnglesUnchecked("wqadaqw", HexDir.NORTH_EAST)),
        SlatePart(this, HexPattern.fromAnglesUnchecked("eqqwqwqeda", HexDir.SOUTH_EAST)),
    )
    val earth: CubePart = CubePart(this, HexMob.id("cube"), 2F, 2F, HexAPI.modLoc("quenched_allay_bricks"), "")
    var equatorRadius: Vec3
        get() = Vec3(entityData.get(EQUATOR_RADIUS))
        set(value) = entityData.set(EQUATOR_RADIUS, value.toVector3f())
    var equatorNormal: Vec3
        get() = Vec3(entityData.get(EQUATOR_NORMAL))
        set(value) = entityData.set(EQUATOR_NORMAL, value.toVector3f())
    var equatorRotation: Float
        get() = entityData.get(EQUATOR_ROTATION)
        set(value) = entityData.set(EQUATOR_ROTATION, value)
    var eclipticRadius: Vec3
        get() = Vec3(entityData.get(ECLIPTIC_RADIUS))
        set(value) = entityData.set(ECLIPTIC_RADIUS, value.toVector3f())
    var eclipticNormal: Vec3
        get() = Vec3(entityData.get(ECLIPTIC_NORMAL))
        set(value) = entityData.set(ECLIPTIC_NORMAL, value.toVector3f())
    var eclipticRotation: Float
        get() = entityData.get(ECLIPTIC_ROTATION)
        set(value) = entityData.set(ECLIPTIC_ROTATION, value)
    var earthRadius: Vec3
        get() = Vec3(entityData.get(EARTH_RADIUS))
        set(value) = entityData.set(EARTH_RADIUS, value.toVector3f())
    var earthNormal: Vec3
        get() = Vec3(entityData.get(EARTH_NORMAL))
        set(value) = entityData.set(EARTH_NORMAL, value.toVector3f())
    var earthRotation: Float
        get() = entityData.get(EARTH_ROTATION)
        set(value) = entityData.set(EARTH_ROTATION, value)
    // 战斗状态机（默认巡航；后续招式状态由冲撞/光束驱动），同步到客户端供渲染使用
    var circleState: CircleState
        get() = CircleState.byId(entityData.get(STATE))
        set(value) = entityData.set(STATE, value.ordinal)
    var stateTicks: Int
        get() = entityData.get(STATE_TICKS)
        set(value) = entityData.set(STATE_TICKS, value)
    /** 出生点：无目标时巡航回这里。仅服务器端。 */
    var homePos: Vec3? = null
    /** 碰撞攻击冷却表：UUID -> 剩余 tick（同一受害者被轮盘碾过后的喘息时间）。 */
    private val contactCooldowns: MutableMap<UUID, Int> = HashMap()
    /** 石板弹发射冷却与轮换指针。 */
    private var fireCooldown = 0
    private var slateIndex = 0
    /** 冲撞：冷却计时与锁定的冲刺方向。 */
    private var chargeCooldown = 0
    private var chargeDir = Vec3.ZERO
    /** 贴地破坏冷却：防止轮盘每 tick 刷坑刷音效。 */
    private var groundCraterCooldown = 0
    /** Boss 血条（仅服务器端）：凋灵式，随距离加入/移除玩家。 */
    private var bossEvent: ServerBossEvent? = null
    init {
        noPhysics = true
        noCulling = true
        IHMMultipartEntity.instances.add(this)
    }

    // 索敌：周期锁定附近"有智慧"的生物（hexmob:wise tag），见 UrCircleTargetGoal。
    // 移动：自定义飞行控制器（恶魂式悬浮）+ 巡航 Goal，行为走 Mob 标准 AI 管线。
    override fun registerGoals() {
        moveControl = UrCircleMoveControl(this)
        targetSelector.addGoal(1, UrCircleTargetGoal(this))
        goalSelector.addGoal(1, UrCircleCruiseGoal(this))
    }
    override fun defineSynchedData() {
        super.defineSynchedData()
        entityData.define(EQUATOR_RADIUS, Vector3f(4F, 0F, 0F))
        entityData.define(EQUATOR_NORMAL, Vector3f(0F, 0.917F, -0.399F))
        entityData.define(EQUATOR_ROTATION, 0F)
        entityData.define(ECLIPTIC_RADIUS, Vector3f(6F, 0F, 0F))
        entityData.define(ECLIPTIC_NORMAL, Vector3f(0F, 0.917F, 0.399F))
        entityData.define(ECLIPTIC_ROTATION, 0F)
        entityData.define(EARTH_RADIUS, Vector3f(0.1F, 0F, 0F))
        entityData.define(EARTH_NORMAL, Vector3f(0F, 1F, 0F))
        entityData.define(EARTH_ROTATION, 0F)
        entityData.define(STATE, CircleState.CRUISE.ordinal)
        entityData.define(STATE_TICKS, 0)
    }
    override fun aiStep() {
        super.aiStep()
        // 先更新部件位置，让战斗逻辑（碰撞/发射）使用当 tick 的石板位置
        updatePartsPos()
        if (!level().isClientSide) {
            if (homePos == null) homePos = position()
            stateTicks += 1
            combatBrain()
            hurtContacts()
            tryFireSlate()
            groundContact()
            // 常态环境声：法术环吟唱（约每 5 秒一次）
            if (tickCount % AMBIENT_SOUND_INTERVAL == 0) {
                level().playSound(null, blockPosition(), HexSounds.CASTING_AMBIANCE, SoundSource.HOSTILE, 1.2F, 1.0F)
            }
            updateBossBar()
        }
        updateShape()
    }
    /**
     * 怒气/怒意因子（基础，未阻尼）：
     * - 索敌时 ×2.5；
     * - 生命越低越大：满血 1.0 → 半血 1.75 → 1/4 血 2.125。
     * 最高约 5.3，仅作为各行为因子的"原始怒气"。
     */
    fun currentAnger(): Float {
        val t = target
        val tf = if (t != null && t.isAlive) 2.5F else 1.0F
        val hpRatio = (health / maxHealth).coerceIn(0.0F, 1.0F)
        return tf * (1.0F + (1.0F - hpRatio) * 1.5F)
    }
    /** 旋转速度因子：随怒气增强最猛——索敌/低血时转得明显更快。 */
    fun rotationSpeedFactor(): Float = 1.0F + (currentAnger() - 1.0F) * 1.5F
    /** 移动速度因子：强烈阻尼，移速基本保持稳定。 */
    fun moveSpeedFactor(): Float = 1.0F + (currentAnger() - 1.0F) * 0.3F
    /** 射速因子：中等阻尼，射速可以稍快但不夸张。 */
    fun fireRateFactor(): Float = 1.0F + (currentAnger() - 1.0F) * 0.6F
    fun updateShape() {
        if(!level().isClientSide) {
            val rot = rotationSpeedFactor()
            equatorRotation += -1 * rot
            eclipticRotation += 1 * rot
        }
    }
    fun updatePartsPos() {
        val origin: Vec3 = position().add(0.0, bbHeight / 2.0, 0.0)
        earth.changeState(origin, if(target != null) target!!.position().subtract(origin).normalize() else Vec3(0.0, 0.0, 1.0))
        for(i in 0..<equator.size) {
            val deg: Float = equatorRotation + (i / equator.size.toFloat() * 360F)
            val delta: Vec3 = equatorRadius.rotateDA(deg, equatorNormal)
            equator[i].changeState(origin.add(delta), equatorNormal.cross(delta).normalize())
        }
        for(i in 0..<ecliptic.size) {
            val deg: Float = eclipticRotation + (i / ecliptic.size.toFloat() * 360F)
            val delta: Vec3 = eclipticRadius.rotateDA(deg, eclipticNormal)
            ecliptic[i].changeState(origin.add(delta), delta.normalize())
        }
    }
    /** 碰撞攻击：部件命中范围内的非敌对生物（玩家/村民/猪灵等）受到伤害+击退，每受害者带冷却。 */
    private fun hurtContacts() {
        if (level().isClientSide) return
        // 冷却衰减
        val it = contactCooldowns.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            val next = e.value - 1
            if (next <= 0) it.remove() else e.setValue(next)
        }
        val origin = position().add(0.0, bbHeight / 2.0, 0.0)
        for (part in getAllParts()) {
            val p = part.posNow
            if (p == Vec3.ZERO) continue // 首 tick 部件还没就位
            val box = AABB(p, p).inflate(1.1)
            for (victim in level().getEntitiesOfClass(LivingEntity::class.java, box)) {
                if (victim === this || victim is Enemy) continue
                if (contactCooldowns.containsKey(victim.uuid)) continue
                if (victim.hurt(level().damageSources().mobAttack(this), if (circleState == CircleState.CHARGING) CONTACT_DAMAGE * 2 else CONTACT_DAMAGE)) {
                    val kb = victim.position().subtract(origin)
                    victim.knockback(0.8, kb.x, kb.z)
                    contactCooldowns[victim.uuid] = CONTACT_COOLDOWN
                }
            }
        }
    }
    /** 石板弹：周期从黄道石板（连续多块齐射）朝目标发射，弹体携带各自石板图案。仅巡航状态发射。 */
    private fun tryFireSlate() {
        if (circleState != CircleState.CRUISE) return
        val t = target
        if (t == null || !t.isAlive) return
        if (fireCooldown > 0) {
            fireCooldown--
            return
        }
        // 射速：怒意增强但阻尼（射速可以快一点，但别太夸张）
        fireCooldown = (FIRE_INTERVAL / fireRateFactor()).toInt().coerceAtLeast(MIN_FIRE_INTERVAL)
        val volley = (1 + ((currentAnger() - 1.0F) * 1.5F).toInt()).coerceIn(1, MAX_VOLLEY)
        // 齐射音效：法术弹射声（偏大）
        level().playSound(null, blockPosition(), HexSounds.CAST_NORMAL, SoundSource.HOSTILE, 1.8F, 1.0F)
        for (n in 0 until volley) {
            val part = ecliptic[(slateIndex + n) % ecliptic.size] as? SlatePart ?: continue
            val from = part.posNow
            // 发射口紫色魔法粒子
            for (k in 0 until 4) {
                level().addParticle(
                    ParticleTypes.AMBIENT_ENTITY_EFFECT,
                    from.x, from.y, from.z,
                    (random.nextDouble() - 0.5) * 0.1,
                    random.nextDouble() * 0.05,
                    (random.nextDouble() - 0.5) * 0.1
                )
            }
            val projectile = SlateProjectile(HexMobEntities.SLATE_PROJECTILE.get(), level())
            projectile.setPos(from.x, from.y, from.z)
            projectile.owner = this
            projectile.pattern = part.pattern
            val aim = t.position().add(0.0, t.bbHeight / 2.0, 0.0).subtract(from)
            projectile.shoot(aim.x, aim.y, aim.z, SLATE_SPEED, 1.0F)
            level().addFreshEntity(projectile)
        }
        slateIndex += volley
    }
    /** 大环贴地：noPhysics 不会自然落地，手动检测脚底 1 格是否为实心地面；
     *  接触时在接触点炸出 3×3×3 的坑 + 爆炸音效（带冷却防刷屏）。 */
    private fun groundContact() {
        if (groundCraterCooldown > 0) {
            groundCraterCooldown--
            return
        }
        val feet = BlockPos.containing(position().x, y - 1.0, position().z)
        val state = level().getBlockState(feet)
        if (state.isAir || !state.fluidState.isEmpty) return
        if (state.getDestroySpeed(level(), feet) < 0.0F) return // 不炸基岩
        craterAround(level(), feet, this)
        groundCraterCooldown = GROUND_CRATER_COOLDOWN
    }
    /** Boss 血条（凋灵式）：进入 128 格的玩家看到紫色血条；活着时屏幕天色变暗 + Boss 音乐。 */
    private fun updateBossBar() {
        val serverLevel = level() as? ServerLevel ?: return
        if (bossEvent == null) {
            bossEvent = ServerBossEvent(type.description, BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10).apply {
                setDarkenScreen(true)
                setPlayBossMusic(true)
            }
        }
        val be = bossEvent ?: return
        be.name = type.description
        be.progress = health / maxHealth
        if (!isAlive) {
            be.removeAllPlayers()
            return
        }
        for (player in serverLevel.players()) {
            if (player.distanceToSqr(this) <= BOSS_BAR_RANGE * BOSS_BAR_RANGE) {
                be.addPlayer(player)
            } else {
                be.removePlayer(player)
            }
        }
    }
    override fun remove(reason: Entity.RemovalReason) {
        bossEvent?.removeAllPlayers()
        super.remove(reason)
    }
    override fun die(source: DamageSource) {
        bossEvent?.removeAllPlayers()
        super.die(source)
    }
    /**
     * 冲撞状态机（第 3 步）：
     * CRUISE(概率触发) → WINDUP(前摇：停住+粒子示警) → CHARGING(直线冲刺，锁定方向) → STAGGER(僵直恢复) → CRUISE。
     * 大脑在 moveControl 之后运行，冲刺速度可覆盖其残留巡航速度。
     */
    private fun combatBrain() {
        when (circleState) {
            CircleState.CRUISE -> {
                if (chargeCooldown > 0) chargeCooldown--
                val t = target
                if (t != null && t.isAlive && chargeCooldown <= 0 && stateTicks > 40 && random.nextFloat() < 0.01F) {
                    circleState = CircleState.WINDUP
                    stateTicks = 0
                }
            }
            CircleState.WINDUP -> {
                // 前摇：停住 + 紫色粒子示警
                setDeltaMovement(deltaMovement.scale(0.8))
                for (k in 0 until 3) {
                    val part = equator[random.nextInt(equator.size)]
                    level().addParticle(ParticleTypes.END_ROD, part.posNow.x, part.posNow.y, part.posNow.z, 0.0, 0.08, 0.0)
                }
                if (stateTicks >= WINDUP_TICKS) {
                    val origin = position().add(0.0, bbHeight / 2.0, 0.0)
                    val t = target
                    chargeDir = if (t != null) t.position().add(0.0, t.bbHeight / 2.0, 0.0).subtract(origin).normalize() else Vec3(0.0, 0.0, 1.0)
                    circleState = CircleState.CHARGING
                    stateTicks = 0
                }
            }
            CircleState.CHARGING -> {
                // 直线高速冲刺（覆盖 moveControl 残留速度）
                setDeltaMovement(chargeDir.scale(CHARGE_SPEED))
                if (stateTicks >= CHARGE_TICKS) {
                    circleState = CircleState.STAGGER
                    stateTicks = 0
                }
            }
            CircleState.STAGGER -> {
                // 僵直：减速停住，恢复期结束后回巡航并进入冷却
                setDeltaMovement(deltaMovement.scale(0.8))
                if (stateTicks >= STAGGER_TICKS) {
                    circleState = CircleState.CRUISE
                    stateTicks = 0
                    chargeCooldown = CHARGE_COOLDOWN
                }
            }
            CircleState.BEAM -> { /* 第 4 步：核心光线 */ }
        }
    }
    override fun addAdditionalSaveData(nbt: CompoundTag) {
        super.addAdditionalSaveData(nbt)

    }
    override fun readAdditionalSaveData(compound: CompoundTag) {
        super.readAdditionalSaveData(compound)

    }
    override fun getAllParts(): List<UrCirclePart> {
        return buildList {
            addAll(equator)
            addAll(ecliptic)
            add(earth)
        }
    }
    override fun shouldRecord(): Boolean = isAlive
    override fun addEffect(effectInstance: MobEffectInstance, entity: Entity?): Boolean = false
    override fun canRide(vehicle: Entity): Boolean = false
    override fun canChangeDimensions(): Boolean = false
    override fun isPickable(): Boolean = false
    override fun getExperienceReward() = Enemy.XP_REWARD_BOSS
    override fun isNoGravity(): Boolean = true

    /** 大环的"拒绝引用"事故：3 秒失明 + 自然文案。 */
    override fun createFlickeringMishap(entity: net.minecraft.world.entity.Entity): Mishap = UrCircleFlickerMishap(this)
    /** 被打立刻还击：把（直接/间接）攻击者设为当前目标——凋灵式 HurtByTarget 行为。 */
    override fun hurt(source: DamageSource, amount: Float): Boolean {
        if (!level().isClientSide) {
            val attacker = source.entity
            if (attacker is LivingEntity && attacker !== this && attacker.isAlive) {
                target = attacker
            }
        }
        return super.hurt(source, amount)
    }
    override fun recreateFromPacket(packet: ClientboundAddEntityPacket) {
        super.recreateFromPacket(packet)
        val parts: List<UrCirclePart> = getAllParts()
        for(i in parts.indices) {
            parts[i].setId(packet.id + i + 1)
        }
    }
    companion object {
        val EQUATOR_RADIUS: EntityDataAccessor<Vector3f> = SynchedEntityData.defineId(UrCircleEntity::class.java, EntityDataSerializers.VECTOR3)
        val EQUATOR_NORMAL: EntityDataAccessor<Vector3f> = SynchedEntityData.defineId(UrCircleEntity::class.java, EntityDataSerializers.VECTOR3)
        val EQUATOR_ROTATION: EntityDataAccessor<Float> = SynchedEntityData.defineId(UrCircleEntity::class.java, EntityDataSerializers.FLOAT)
        val ECLIPTIC_RADIUS: EntityDataAccessor<Vector3f> = SynchedEntityData.defineId(UrCircleEntity::class.java, EntityDataSerializers.VECTOR3)
        val ECLIPTIC_NORMAL: EntityDataAccessor<Vector3f> = SynchedEntityData.defineId(UrCircleEntity::class.java, EntityDataSerializers.VECTOR3)
        val ECLIPTIC_ROTATION: EntityDataAccessor<Float> = SynchedEntityData.defineId(UrCircleEntity::class.java, EntityDataSerializers.FLOAT)
        val EARTH_RADIUS: EntityDataAccessor<Vector3f> = SynchedEntityData.defineId(UrCircleEntity::class.java, EntityDataSerializers.VECTOR3)
        val EARTH_NORMAL: EntityDataAccessor<Vector3f> = SynchedEntityData.defineId(UrCircleEntity::class.java, EntityDataSerializers.VECTOR3)
        val EARTH_ROTATION: EntityDataAccessor<Float> = SynchedEntityData.defineId(UrCircleEntity::class.java, EntityDataSerializers.FLOAT)
        val STATE: EntityDataAccessor<Int> = SynchedEntityData.defineId(UrCircleEntity::class.java, EntityDataSerializers.INT)
        val STATE_TICKS: EntityDataAccessor<Int> = SynchedEntityData.defineId(UrCircleEntity::class.java, EntityDataSerializers.INT)
        const val CONTACT_DAMAGE = 6.0F
        const val CONTACT_COOLDOWN = 20
        const val FIRE_INTERVAL = 50
        const val SLATE_SPEED = 1.0F
        const val MIN_FIRE_INTERVAL = 12
        const val MAX_VOLLEY = 3
        const val WINDUP_TICKS = 20
        const val CHARGE_TICKS = 12
        const val STAGGER_TICKS = 30
        const val CHARGE_SPEED = 1.2
        const val CHARGE_COOLDOWN = 100
        const val GROUND_CRATER_COOLDOWN = 40
        const val AMBIENT_SOUND_INTERVAL = 100
        const val BOSS_BAR_RANGE = 128.0
        fun registerAttributes(): AttributeSupplier.Builder = createMobAttributes().add(Attributes.MAX_HEALTH, 500.0).add(Attributes.ARMOR, 20.0).add(Attributes.ARMOR_TOUGHNESS, 10.0)
    }
}