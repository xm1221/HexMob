package pub.pigeon.yggdyy.hexmob.content.ur_circle

import at.petrak.hexcasting.api.casting.mishaps.Mishap
import at.petrak.hexcasting.common.lib.HexBlocks
import at.petrak.hexcasting.common.lib.HexDamageTypes
import at.petrak.hexcasting.common.lib.HexSounds
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.BlockParticleOption
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.level.ServerBossEvent
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.BossEvent
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.damagesource.DamageTypes
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.ExperienceOrb
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.monster.Enemy
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import pub.pigeon.yggdyy.hexmob.HexMob
import pub.pigeon.yggdyy.hexmob.api.entity.FlickeringEntity
import pub.pigeon.yggdyy.hexmob.config.HexMobServerConfig
import pub.pigeon.yggdyy.hexmob.content.IHMMultipartEntity
import pub.pigeon.yggdyy.hexmob.content.guard.GuardDormant
import pub.pigeon.yggdyy.hexmob.content.guard.HexMobGuard
import pub.pigeon.yggdyy.hexmob.content.quench_allay.QuenchAllay
import pub.pigeon.yggdyy.hexmob.content.ur_circle.servant.UrCircleServant
import pub.pigeon.yggdyy.hexmob.content.ur_circle.spells.AmethystTrapSkill
import pub.pigeon.yggdyy.hexmob.content.ur_circle.spells.BeamSkill
import pub.pigeon.yggdyy.hexmob.content.ur_circle.spells.CurseSkill
import pub.pigeon.yggdyy.hexmob.content.ur_circle.spells.DiveGrindSkill
import pub.pigeon.yggdyy.hexmob.content.ur_circle.spells.EruptSerpentSkill
import pub.pigeon.yggdyy.hexmob.content.ur_circle.spells.ExplosionSkill
import pub.pigeon.yggdyy.hexmob.content.ur_circle.spells.LightningSkill
import pub.pigeon.yggdyy.hexmob.content.ur_circle.spells.MishapSkill
import pub.pigeon.yggdyy.hexmob.content.ur_circle.spells.OrbitalBarrageSkill
import pub.pigeon.yggdyy.hexmob.content.ur_circle.spells.PlaneSliceSkill
import pub.pigeon.yggdyy.hexmob.content.ur_circle.spells.RecoverSkill
import pub.pigeon.yggdyy.hexmob.content.ur_circle.spells.RingSpinSkill
import pub.pigeon.yggdyy.hexmob.content.ur_circle.spells.SerpentSkill
import pub.pigeon.yggdyy.hexmob.content.ur_circle.spells.SummonServantSkill
import pub.pigeon.yggdyy.hexmob.content.ur_circle.spells.UrCircleMishap
import pub.pigeon.yggdyy.hexmob.content.ur_circle.spells.UrCircleSkill
import pub.pigeon.yggdyy.hexmob.content.ur_circle.spells.UrCircleStatusTable
import pub.pigeon.yggdyy.hexmob.content.ur_circle.subentities.CubePart
import pub.pigeon.yggdyy.hexmob.content.ur_circle.subentities.SlatePart
import pub.pigeon.yggdyy.hexmob.content.ur_circle.ur_spell.UrCoreBeamEntity
import pub.pigeon.yggdyy.hexmob.registry.HexMobEntities
import pub.pigeon.yggdyy.hexmob.registry.HexMobItems
import pub.pigeon.yggdyy.hexmob.util.rotateDA
import pub.pigeon.yggdyy.hexmob.util.spawnParticle
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin

fun isUrCircleAlly(entity: Entity?): Boolean =
    entity is UrCircleEntity || entity is UrCirclePart || entity is UrCircleServant || entity is HexMobGuard

class UrCircleEntity(entityType: EntityType<out Mob>, level: Level) : Mob(entityType, level), Enemy,
    IHMMultipartEntity<UrCirclePart>, FlickeringEntity {

    private val built = RingTrackFactory.buildAll(this)
    val rings: List<RingTrack> = built.rings
    val earth: CubePart = built.earth
    private val cachedAllParts: List<UrCirclePart> = built.allParts

    val equator: List<UrCirclePart> get() = rings[0].parts
    val ecliptic: List<UrCirclePart> get() = rings[1].parts

    private val flightNav = UrCircleFlightNavigation(this)
    private val mediaWave = UrCircleMediaWave()

    var equatorRotation: Float
        get() = entityData.get(EQUATOR_ROTATION)
        set(value) = entityData.set(EQUATOR_ROTATION, value)
    var eclipticRotation: Float
        get() = entityData.get(ECLIPTIC_ROTATION)
        set(value) = entityData.set(ECLIPTIC_ROTATION, value)
    var outerRotation: Float
        get() = entityData.get(ROT_OUTER)
        set(value) = entityData.set(ROT_OUTER, value)
    var polarRotation: Float
        get() = entityData.get(ROT_POLAR)
        set(value) = entityData.set(ROT_POLAR, value)
    var zenithRotation: Float
        get() = entityData.get(ROT_ZENITH)
        set(value) = entityData.set(ROT_ZENITH, value)
    var apexRotation: Float
        get() = entityData.get(ROT_APEX)
        set(value) = entityData.set(ROT_APEX, value)

    var circleState: CircleState
        get() = CircleState.byId(entityData.get(STATE))
        set(value) = entityData.set(STATE, value.ordinal)
    var stateTicks: Int
        get() = entityData.get(STATE_TICKS)
        set(value) = entityData.set(STATE_TICKS, value)

    var phase: Int
        get() = entityData.get(PHASE)
        set(value) = entityData.set(PHASE, value.coerceIn(1, 2))
    var dormantFold: Float
        get() = entityData.get(DORMANT_FOLD)
        set(value) = entityData.set(DORMANT_FOLD, value.coerceIn(0F, 1F))
    var phase2Reveal: Float
        get() = entityData.get(PHASE2_REVEAL)
        set(value) = entityData.set(PHASE2_REVEAL, value.coerceIn(0F, 1F))
    var foldProgress: Float
        get() = entityData.get(FOLD_PROGRESS)
        set(value) = entityData.set(FOLD_PROGRESS, value.coerceIn(0F, 1F))
    var activeNodeA: Int
        get() = entityData.get(ACTIVE_NODE_A)
        set(value) = entityData.set(ACTIVE_NODE_A, value)
    var activeNodeB: Int
        get() = entityData.get(ACTIVE_NODE_B)
        set(value) = entityData.set(ACTIVE_NODE_B, value)
    var waveColor: Int
        get() = entityData.get(WAVE_COLOR)
        set(value) = entityData.set(WAVE_COLOR, value)

    /** 服务端合拢目标值，驱动 [foldProgress] 平滑插值。 */
    var targetFoldProgress: Float = 0.0F

    /** 出生点：无目标时巡航回这里（仅服务端）。 */
    var homePos: Vec3? = null
    private val contactCooldowns: MutableMap<UUID, Int> = HashMap()
    private var fireCooldown = 0
    private var slateIndex = 0
    private var chargeCooldown = 0
    private var chargeDir = Vec3.ZERO
    private var groundCraterCooldown = 0
    private var servantSummonCooldown = 0
    private var bossEvent: ServerBossEvent? = null

    private var dormant = false
    private var arenaCheckAttempts = 0
    private var wokenOnce = false
    private var internalHealthWrite = false

    private var dpsWindow = 0.0F
    private var dpsWindowStart = 0

    val skills: MutableList<UrCircleSkill> = mutableListOf(
        BeamSkill(),
        OrbitalBarrageSkill(),
        SerpentSkill(),
        EruptSerpentSkill(),
        DiveGrindSkill(),
        PlaneSliceSkill(),
        SummonServantSkill(),
    )

    private var currentSkill: UrCircleSkill? = null
    var beamSkill: UrCircleSkill? = null
    var grindSkill: UrCircleSkill? = null
    private var circleBeamEntity: UrCoreBeamEntity? = null
    private val activeAnchors: MutableList<UrCircleAnchorEntity> = mutableListOf()
    private var channelPulseTimer = 0
    private var channelParticleTimer = 0
    private var skillCooldown = 0

    private var backlash = 0
    private var lastBacklashTick = 0
    private var siphonTimer = 0

    private val hatedTargets: MutableList<LivingEntity> = mutableListOf()

    init {
        noPhysics = true
        IHMMultipartEntity.instances.add(this)
    }

    override fun registerGoals() {
        moveControl = UrCircleMoveControl(this)
        targetSelector.addGoal(1, UrCircleTargetGoal(this))
        goalSelector.addGoal(1, UrCircleCruiseGoal(this))
    }

    override fun defineSynchedData() {
        super.defineSynchedData()
        entityData.define(EQUATOR_ROTATION, 0F)
        entityData.define(ECLIPTIC_ROTATION, 0F)
        entityData.define(ROT_OUTER, 0F)
        entityData.define(ROT_POLAR, 0F)
        entityData.define(ROT_ZENITH, 0F)
        entityData.define(ROT_APEX, 0F)
        entityData.define(STATE, CircleState.CRUISE.ordinal)
        entityData.define(STATE_TICKS, 0)
        entityData.define(TUMBLING, false)
        entityData.define(RING_SPINNING, false)
        entityData.define(PHASE, 1)
        entityData.define(DORMANT, false)
        entityData.define(DORMANT_FOLD, 0F)
        entityData.define(PHASE2_REVEAL, 0F)
        entityData.define(FOLD_PROGRESS, 0F)
        entityData.define(ACTIVE_NODE_A, -1)
        entityData.define(ACTIVE_NODE_B, -1)
        entityData.define(WAVE_COLOR, UrCircleHexVfx.MEDIA_PURPLE)
        entityData.define(SIPHONING, false)
        entityData.define(SLICE_ACTIVE, false)
        entityData.define(SLICE_Y_OFFSET, 0.0F)
    }

    override fun aiStep() {
        super.aiStep()
        updatePartsPos()
        if (!level().isClientSide) {
            if (homePos == null) homePos = position()
            checkArenaDormancy()
            entityData.set(DORMANT, dormant)
            if (dormant) {
                dormantFold = stepToward(dormantFold, 1.0F, 0.05F)
                tryWakeFromDormancy()
                updateBossBar()
                return
            } else {
                dormantFold = stepToward(dormantFold, 0.0F, 0.04F)
            }

            stateTicks += 1
            hatedTargets.removeAll { !it.isAlive || it.isRemoved || it is Enemy || distanceToSqr(it) > HATE_RANGE * HATE_RANGE }
            if (target == null) {
                target = preferredTargets(currentHated()).firstOrNull()
            }

            combatBrain()
            if (circleState != CircleState.DYING && circleState != CircleState.PHASE_TRANSITION) {
                tickSiphonState()
                tickTitanUpdraftParticles()
                idleBehavior()
                hurtContacts()
                tryFireSlate()
                groundContact()
                if (tickCount % AMBIENT_SOUND_INTERVAL == 0) {
                    UrCircleHexVfx.playRingSound(
                        level(),
                        beamOrigin(),
                        ringOuterRadius(),
                        HexSounds.CASTING_AMBIANCE,
                        baseVolume = 1.4F,
                        pitch = if (phase >= 2) 0.85F else 1.0F,
                    )
                }
            }
            updateBossBar()
        }
        updateShape()
    }

    private fun stepToward(current: Float, target: Float, step: Float): Float =
        when {
            current < target -> (current + step).coerceAtMost(target)
            current > target -> (current - step).coerceAtLeast(target)
            else -> current
        }

    fun currentAnger(): Float {
        val t = target
        val tf = if (t != null && t.isAlive) 2.5F else 1.0F
        val hpRatio = (health / maxHealth).coerceIn(0.0F, 1.0F)
        return tf * (1.0F + (1.0F - hpRatio) * 1.5F)
    }

    fun rotationSpeedFactor(): Float = 1.0F + (currentAnger() - 1.0F) * 1.5F

    fun deathSpinFactor(): Float {
        val t = stateTicks.toFloat()
        return when {
            t <= SPIN_UP_TICKS -> 1.0F + (t / SPIN_UP_TICKS) * 2.0F
            t <= SPIN_DOWN_END -> {
                val p = (t - SPIN_UP_TICKS) / (SPIN_DOWN_END - SPIN_UP_TICKS).toFloat()
                3.0F * (1.0F - p)
            }
            else -> 0.0F
        }
    }

    fun ringSpinProgress(): Float {
        if (!entityData.get(RING_SPINNING)) return 0.0F
        return (stateTicks.toFloat() / RingSpinSkill.CHANNEL_TICKS).coerceIn(0.0F, 1.0F)
    }

    fun ringSpinScale(): Float = 1.0F + ringSpinProgress() * (RING_SPIN_MAX_SCALE - 1.0F)

    fun totalScale(): Float = HexMobServerConfig.config.urCircleScale * ringSpinScale()

    fun ringOuterRadius(): Double {
        val scale = totalScale()
        val df = dormantFold
        val p2 = phase2Reveal
        var maxR = 12.0
        for (ring in rings) {
            if (!ring.isVisible(phase, p2)) continue
            val r = ring.effectiveRadius(scale, df, p2)
            if (r > maxR) maxR = r
        }
        return maxR
    }

    fun ringInnerRadius(): Double = rings[0].effectiveRadius(totalScale(), dormantFold, phase2Reveal)

    fun safeFlightDir(desired: Vec3, lookAhead: Double): Vec3? = flightNav.safeFlightDir(desired, lookAhead)

    fun moveSpeedFactor(): Float = 1.0F + (currentAnger() - 1.0F) * 0.3F

    fun fireRateFactor(): Float = 1.0F + (currentAnger() - 1.0F) * 0.6F

    fun isDormantState(): Boolean = entityData.get(DORMANT)

    fun isSiphoning(): Boolean = entityData.get(SIPHONING)

    fun setSiphoning(enabled: Boolean) {
        entityData.set(SIPHONING, enabled)
        if (!enabled) backlash = 0
    }

    fun isPlaneSlicing(): Boolean = entityData.get(SLICE_ACTIVE)

    fun slicePlaneLocalY(): Float = entityData.get(SLICE_Y_OFFSET)

    fun setPlaneSlicing(active: Boolean, localYOffset: Float) {
        entityData.set(SLICE_ACTIVE, active)
        entityData.set(SLICE_Y_OFFSET, localYOffset)
    }

    fun ringRotation(ringIndex: Int): Float =
        when (ringIndex) {
            0 -> equatorRotation
            1 -> eclipticRotation
            2 -> outerRotation
            3 -> polarRotation
            4 -> zenithRotation
            5 -> apexRotation
            else -> 0.0F
        }

    fun partVisualScale(part: UrCirclePart): Float {
        if (part === earth) {
            return totalScale() * (if (phase >= 2) 2.4F else 1.5F)
        }
        val track = rings.getOrNull(part.ringIndex)
        val mul = track?.partScale ?: 1.0F
        val nodeBoost = if (isActiveWaveNode(part)) 1.28F else 1.0F
        return totalScale() * mul * nodeBoost
    }

    fun isActiveWaveNode(part: UrCirclePart): Boolean =
        circleState == CircleState.CHANNELING &&
            (part.globalIndex == activeNodeA || (activeNodeB >= 0 && part.globalIndex == activeNodeB))

    fun channelWaveProgress(): Float {
        if (circleState != CircleState.CHANNELING) return 1.0F
        return (stateTicks % UrCircleMediaWave.STEP_INTERVAL) / UrCircleMediaWave.STEP_INTERVAL.toFloat()
    }

    fun updateShape() {
        if (!level().isClientSide) {
            if (dormant) return
            phase2Reveal = when {
                phase < 2 -> 0.0F
                circleState == CircleState.PHASE_TRANSITION && stateTicks < PHASE_BURST_TICK -> 0.0F
                else -> stepToward(phase2Reveal, 1.0F, 0.032F)
            }
            foldProgress = stepToward(foldProgress, targetFoldProgress, 0.04F)

            val base = rotationSpeedFactor()
            val rot = when {
                circleState == CircleState.SHORT_CIRCUIT -> 0.0F
                circleState == CircleState.PHASE_TRANSITION -> base * 2.5F
                circleState == CircleState.FOLD_GRIND -> base * 2.4F
                circleState == CircleState.CHANNELING && currentSkill is RingSpinSkill ->
                    base * (1.0F + ringSpinProgress() * 2.0F)
                circleState == CircleState.CHANNELING -> {
                    val duration = currentSkill?.channelTicks ?: 1
                    val progress = (stateTicks.toFloat() / duration).coerceIn(0.0F, 1.0F)
                    base * (1.0F - progress)
                }
                circleState == CircleState.DYING -> base * deathSpinFactor()
                else -> base
            }

            equatorRotation += rings[0].spinDir * rot * rings[0].spinSpeedMul
            eclipticRotation += rings[1].spinDir * rot * rings[1].spinSpeedMul
            if (rings.size > 2) outerRotation += rings[2].spinDir * rot * rings[2].spinSpeedMul
            if (rings.size > 3) polarRotation += rings[3].spinDir * rot * rings[3].spinSpeedMul
            if (rings.size > 4) zenithRotation += rings[4].spinDir * rot * rings[4].spinSpeedMul
            if (rings.size > 5) apexRotation += rings[5].spinDir * rot * rings[5].spinSpeedMul

            val tumbling = (circleState == CircleState.CHANNELING && currentSkill?.channelInterruptible == true &&
                currentSkill !is DiveGrindSkill) || circleState == CircleState.DYING
            entityData.set(TUMBLING, tumbling)
            val ringSpinning = circleState == CircleState.CHANNELING && currentSkill is RingSpinSkill
            entityData.set(RING_SPINNING, ringSpinning)
        }
    }

    fun updatePartsPos() {
        val origin: Vec3 = position().add(0.0, bbHeight / 2.0, 0.0)
        val t = target
        val aim = if (t != null && t.isAlive) {
            t.position().add(0.0, t.bbHeight / 2.0, 0.0).subtract(origin).let {
                if (it.lengthSqr() > 1.0E-6) it.normalize() else Vec3(0.0, 0.0, 1.0)
            }
        } else {
            Vec3(0.0, 0.0, 1.0)
        }
        earth.active = true
        earth.applyScale(partVisualScale(earth))
        earth.changeState(origin, aim)

        val tumbling = entityData.get(TUMBLING)
        val spinMul = if (circleState == CircleState.DYING) deathSpinFactor() else 1.0F
        val tumbleDeg = if (tumbling) stateTicks.toFloat() * CHANNEL_TUMBLE_SPEED * spinMul else 0.0F

        val curPhase = phase
        val df = dormantFold
        val p2 = phase2Reveal
        val fp = foldProgress
        val scale = totalScale()

        for (track in rings) {
            if (!track.isVisible(curPhase, p2)) {
                for (p in track.parts) p.active = false
                continue
            }
            val baseN = track.effectiveNormal(fp, df)
            val radAxis = track.radialAxis(baseN)
            val effN = if (tumbling && tumbleDeg != 0.0F) baseN.rotateDA(tumbleDeg, radAxis).normalize() else baseN
            val rLen = track.effectiveRadius(scale, df, p2)
            val baseRot = ringRotation(track.ringIndex)
            val count = track.parts.size
            for (idx in 0 until count) {
                val p = track.parts[idx]
                p.active = true
                p.applyScale(partVisualScale(p))
                val deg = baseRot + (idx.toFloat() / count) * 360F
                val delta = radAxis.rotateDA(deg, effN).scale(rLen)
                val face = if (track.faceTangent) {
                    effN.cross(delta).let { if (it.lengthSqr() > 1.0E-6) it.normalize() else Vec3(0.0, 0.0, 1.0) }
                } else {
                    if (delta.lengthSqr() > 1.0E-6) delta.normalize() else Vec3(0.0, 0.0, 1.0)
                }
                p.changeState(origin.add(delta), face)
            }
        }
    }

    /** 单次包围球查询 + 活跃部件碰撞判定。 */
    private fun hurtContacts() {
        if (level().isClientSide) return
        if (circleState == CircleState.SHORT_CIRCUIT) return
        if (circleState == CircleState.CHANNELING && currentSkill is RingSpinSkill) return
        if (circleState == CircleState.FOLD_GRIND) return

        val it = contactCooldowns.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            val next = e.value - 1
            if (next <= 0) it.remove() else e.setValue(next)
        }

        val origin = position().add(0.0, bbHeight / 2.0, 0.0)
        val dmg = if (circleState == CircleState.CHARGING) CONTACT_DAMAGE * 2 else CONTACT_DAMAGE
        val hull = getPartsBoundingBox()
        for (victim in level().getEntitiesOfClass(LivingEntity::class.java, hull)) {
            if (victim === this || victim is Enemy || isUrCircleAlly(victim)) continue
            if (contactCooldowns.containsKey(victim.uuid)) continue
            val vc = victim.position().add(0.0, victim.bbHeight / 2.0, 0.0)
            var hit = false
            for (part in cachedAllParts) {
                if (!part.active || part.posNow == Vec3.ZERO) continue
                val r = part.bbWidth * 0.6 + victim.bbWidth * 0.5 + 0.45
                if (part.posNow.distanceToSqr(vc) <= r * r) {
                    hit = true
                    break
                }
            }
            if (hit && victim.hurt(level().damageSources().mobAttack(this), dmg)) {
                val kb = victim.position().subtract(origin)
                victim.knockback(0.8, kb.x, kb.z)
                contactCooldowns[victim.uuid] = CONTACT_COOLDOWN
            }
        }
    }

    private fun tryFireSlate() {
        if (circleState != CircleState.CRUISE) return
        val t = target
        if (t == null || !t.isAlive) return
        if (fireCooldown > 0) {
            fireCooldown--
            return
        }
        fireCooldown = (FIRE_INTERVAL / fireRateFactor()).toInt().coerceAtLeast(MIN_FIRE_INTERVAL)
        val volley = (1 + ((currentAnger() - 1.0F) * 1.5F).toInt()).coerceIn(1, MAX_VOLLEY)
        val sourceRing = if (phase >= 2 && rings[2].isVisible(phase, phase2Reveal)) rings[2].parts else ecliptic
        if (sourceRing.isEmpty()) return

        UrCircleHexVfx.playRingSound(level(), beamOrigin(), ringOuterRadius(), HexSounds.CAST_NORMAL, 1.8F, 1.0F)
        val projScale = if (phase >= 2) 1.5F else 1.0F
        for (n in 0 until volley) {
            val part = sourceRing[(slateIndex + n) % sourceRing.size] as? SlatePart ?: continue
            if (!part.active || part.posNow == Vec3.ZERO) continue
            val from = part.posNow
            for (k in 0 until 4) {
                UrCircleHexVfx.spawnConjure(
                    level(),
                    UrCircleHexVfx.MEDIA_PURPLE,
                    from.x, from.y, from.z,
                    (random.nextDouble() - 0.5) * 0.1,
                    random.nextDouble() * 0.05,
                    (random.nextDouble() - 0.5) * 0.1,
                )
            }
            val projectile = SlateProjectile(HexMobEntities.SLATE_PROJECTILE.get(), level())
            projectile.setPos(from.x, from.y, from.z)
            projectile.owner = this
            projectile.pattern = part.pattern
            projectile.visualScale = projScale
            val aim = t.position().add(0.0, t.bbHeight / 2.0, 0.0).subtract(from)
            projectile.shoot(aim.x, aim.y, aim.z, SLATE_SPEED, 1.0F)
            level().addFreshEntity(projectile)
        }
        slateIndex += volley
    }

    private fun groundContact() {
        if (groundCraterCooldown > 0) {
            groundCraterCooldown--
            return
        }
        val feet = BlockPos.containing(position().x, y - 1.0, position().z)
        val state = level().getBlockState(feet)
        if (state.isAir || !state.fluidState.isEmpty) return
        if (state.getDestroySpeed(level(), feet) < 0.0F) return
        craterAround(level(), feet, this)
        groundCraterCooldown = GROUND_CRATER_COOLDOWN
    }

    fun isAwake(): Boolean = !dormant

    fun setDormant(v: Boolean) {
        dormant = v
        entityData.set(DORMANT, v)
        if (v) dormantFold = 1.0F
    }

    private fun tryWakeFromDormancy() {
        val serverLevel = level() as? ServerLevel ?: return
        val nearest = serverLevel.getNearestPlayer(this, WAKE_RANGE)
        if (nearest != null) {
            wokenOnce = true
            dormant = false
            entityData.set(DORMANT, false)
            UrCircleHexVfx.playRingSound(level(), beamOrigin(), ringOuterRadius(), HexSounds.CAST_THOTH, 1.8F, 0.75F)
            for (k in 0 until 24) {
                UrCircleHexVfx.spawnConjure(
                    level(),
                    UrCircleHexVfx.MEDIA_PURPLE,
                    x + (random.nextDouble() - 0.5) * 8.0,
                    y + 2.0 + (random.nextDouble() - 0.5) * 4.0,
                    z + (random.nextDouble() - 0.5) * 8.0,
                    (random.nextDouble() - 0.5) * 0.2,
                    0.12,
                    (random.nextDouble() - 0.5) * 0.2,
                )
            }
        }
    }

    private fun checkArenaDormancy() {
        if (dormant || wokenOnce) return
        if (arenaCheckAttempts++ >= ARENA_CHECK_MAX_TICKS) return
        val serverLevel = level() as? ServerLevel ?: return
        val structure = serverLevel.registryAccess()
            .registryOrThrow(Registries.STRUCTURE)
            .get(HexMob.id("ur_circle_arena")) ?: return
        val start = serverLevel.structureManager().getStructureAt(blockPosition(), structure)
        if (start.isValid()) {
            setDormant(true)
        }
    }

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
        if (dormant || !isAlive) {
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
        discardCircleBeam()
        discardAnchors()
        bossEvent?.removeAllPlayers()
        super.remove(reason)
    }

    override fun die(source: DamageSource) {
        if (circleState == CircleState.DYING) return
        circleState = CircleState.DYING
        stateTicks = 0
        currentSkill = null
        beamSkill = null
        grindSkill = null
        targetFoldProgress = 0.0F
        mediaWave.clear(this)
        setSiphoning(false)
        setPlaneSlicing(false, 0.0F)
        discardCircleBeam()
        discardAnchors()
        bossEvent?.removeAllPlayers()
        bossEvent = null
        for (s in level().getEntitiesOfClass(UrCircleServant::class.java, getBoundingBox().inflate(48.0))) {
            if (s.owner === this) s.remove(Entity.RemovalReason.KILLED)
        }
        hatedTargets.clear()
        target = null

        val origin = beamOrigin()
        UrCircleHexVfx.playRingSound(level(), origin, ringOuterRadius(), HexSounds.SPELL_CIRCLE_FAIL, 3.2F, 0.75F)
        UrCircleHexVfx.playRingSound(level(), origin, ringOuterRadius(), SoundEvents.BEACON_DEACTIVATE, 2.8F, 0.7F)
    }

    override fun tickDeath() {
        // 移除由 DYING 序列结束时的 finishDeath() 统一执行
    }

    private fun tickTitanUpdraftParticles() {
        if (phase < 2 && circleState != CircleState.CHANNELING) return
        val interval = if (phase >= 2) 3 else 6
        if (tickCount % interval != 0) return

        val origin = beamOrigin()
        val spreadR = if (phase >= 2) 48.0 else 22.0
        val count = if (phase >= 2) 5 else 2
        val lvl = level()
        for (i in 0 until count) {
            val ang = random.nextDouble() * Math.PI * 2.0
            val dist = 8.0 + random.nextDouble() * spreadR
            val sx = origin.x + cos(ang) * dist
            val sz = origin.z + sin(ang) * dist
            val groundY = lvl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, net.minecraft.util.Mth.floor(sx), net.minecraft.util.Mth.floor(sz))
            val sy = if (groundY > lvl.minBuildHeight) {
                groundY + 0.3 + random.nextDouble() * 5.0
            } else {
                origin.y - (if (phase >= 2) 34.0 else 16.0) + random.nextDouble() * 14.0
            }
            val vx = (origin.x - sx) * 0.014
            val vy = 0.28 + random.nextDouble() * 0.22
            val vz = (origin.z - sz) * 0.014
            val particle = when {
                i == 0 -> AMETHYST_BREAK
                i == 1 && phase >= 2 -> SLATE_BREAK
                else -> UrCircleHexVfx.conjure(
                    if (i % 2 == 0) UrCircleHexVfx.MEDIA_PURPLE else UrCircleHexVfx.WAVE_GOLD
                )
            }
            spawnParticle(lvl, particle, sx, sy, sz, vx, vy, vz, UrCircleHexVfx.DEFAULT_RANGE)
        }
    }

    private fun tickSiphonState() {
        val activeState = circleState == CircleState.CRUISE || circleState == CircleState.CHANNELING
        if (!activeState || target == null) {
            if (isSiphoning() && (circleState == CircleState.SHORT_CIRCUIT || circleState == CircleState.DYING)) {
                setSiphoning(false)
                siphonTimer = 0
            }
            return
        }
        siphonTimer++
        if (!isSiphoning()) {
            if (siphonTimer >= SIPHON_INTERVAL) {
                setSiphoning(true)
                siphonTimer = 0
                UrCircleHexVfx.playRingSound(level(), beamOrigin(), ringOuterRadius(), HexSounds.ABACUS_SHAKE, 1.8F, 0.85F)
            }
        } else {
            if (tickCount % 3 == 0) {
                val origin = beamOrigin()
                val r = (totalScale() * 2.5).coerceIn(4.0, 14.0)
                for (i in 0 until 4) {
                    val ang = tickCount * 0.25 + i * Math.PI * 0.5
                    val color = if (i % 2 == 0) UrCircleHexVfx.SIPHON_CYAN else UrCircleHexVfx.SHORT_CIRCUIT_RED
                    UrCircleHexVfx.spawnConjure(
                        level(),
                        color,
                        origin.x + cos(ang) * r,
                        origin.y + sin(ang * 2.0) * 1.5,
                        origin.z + sin(ang) * r,
                        0.0,
                        0.05,
                        0.0,
                    )
                }
            }
            if (siphonTimer >= SIPHON_DURATION) {
                setSiphoning(false)
                siphonTimer = 0
            }
        }
    }

    private fun combatBrain() {
        if (circleState == CircleState.DYING) {
            runDeathSequence()
            return
        }
        if (circleState == CircleState.PHASE_TRANSITION) {
            runPhaseTransition()
            return
        }
        if (circleState == CircleState.SHORT_CIRCUIT) {
            runShortCircuit()
            return
        }

        when (circleState) {
            CircleState.CRUISE -> {
                targetFoldProgress = 0.0F
                if (chargeCooldown > 0) chargeCooldown--
                val t = target
                if (t != null && t.isAlive && chargeCooldown <= 0 && stateTicks > 40 && random.nextFloat() < 0.01F) {
                    circleState = CircleState.WINDUP
                    stateTicks = 0
                    return
                }
                if (skillCooldown > 0) skillCooldown--
                if (skillCooldown <= 0) {
                    val skill = pickWeightedSkill()
                    if (skill != null) {
                        beginSkillChannel(skill)
                    }
                }
            }
            CircleState.CHANNELING -> {
                val skill = currentSkill
                if (skill == null) {
                    mediaWave.clear(this)
                    circleState = CircleState.CRUISE
                    stateTicks = 0
                    return
                }
                setDeltaMovement(deltaMovement.scale(0.9))
                skill.onChannelTick(this)
                mediaWave.tick(this, skill)
                if (skill.channelMarksTarget(this)) {
                    target?.addEffect(MobEffectInstance(MobEffects.GLOWING, 12, 0, false, false))
                }
                channelParticleTimer++
                if (channelParticleTimer >= CHANNEL_PARTICLE_INTERVAL) {
                    channelParticleTimer = 0
                    spawnChannelParticles(skill)
                }
                channelPulseTimer++
                if (channelPulseTimer >= skill.channelPulseInterval(this)) {
                    channelPulseTimer = 0
                    UrCircleHexVfx.playRingSound(
                        level(),
                        beamOrigin(),
                        ringOuterRadius(),
                        skill.channelPulseSound(this),
                        skill.channelPulseVolume(this),
                        1.0F,
                    )
                }
                if (stateTicks >= skill.channelTicks) {
                    UrCircleHexVfx.playRingSound(
                        level(),
                        beamOrigin(),
                        ringOuterRadius(),
                        skill.releaseSound(this),
                        skill.releaseVolume(this),
                        1.0F,
                    )
                    mediaWave.clear(this)
                    discardCircleBeam()
                    discardAnchors()
                    skill.cast(this)
                    currentSkill = null
                    skillCooldown = SKILL_COOLDOWN
                    if (circleState != CircleState.BEAM && circleState != CircleState.FOLD_GRIND) {
                        circleState = CircleState.CRUISE
                    }
                    stateTicks = 0
                }
            }
            CircleState.WINDUP -> {
                setDeltaMovement(deltaMovement.scale(0.8))
                for (k in 0 until 4) {
                    val part = equator[random.nextInt(equator.size)]
                    if (part.posNow != Vec3.ZERO) {
                        UrCircleHexVfx.spawnConjure(
                            level(),
                            UrCircleHexVfx.WAVE_GOLD,
                            part.posNow.x, part.posNow.y, part.posNow.z,
                            0.0, 0.08, 0.0,
                        )
                    }
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
                setDeltaMovement(chargeDir.scale(CHARGE_SPEED))
                if (stateTicks >= CHARGE_TICKS) {
                    circleState = CircleState.STAGGER
                    stateTicks = 0
                }
            }
            CircleState.STAGGER -> {
                setDeltaMovement(deltaMovement.scale(0.8))
                if (stateTicks >= STAGGER_TICKS) {
                    circleState = CircleState.CRUISE
                    stateTicks = 0
                    chargeCooldown = CHARGE_COOLDOWN
                }
            }
            CircleState.BEAM -> {
                val skill = beamSkill
                if (skill == null) {
                    discardCircleBeam()
                    circleState = CircleState.CRUISE
                    stateTicks = 0
                } else {
                    skill.onBeamTick(this)
                    if (stateTicks >= skill.beamTicks(this)) {
                        skill.onBeamEnd(this)
                        beamSkill = null
                        setPlaneSlicing(false, 0.0F)
                        discardCircleBeam()
                        circleState = CircleState.CRUISE
                        stateTicks = 0
                    }
                }
            }
            CircleState.FOLD_GRIND -> {
                val skill = grindSkill
                if (skill == null) {
                    targetFoldProgress = 0.0F
                    circleState = CircleState.CRUISE
                    stateTicks = 0
                } else {
                    skill.onGrindTick(this)
                    if (stateTicks >= skill.grindTicks(this)) {
                        skill.onGrindEnd(this)
                        grindSkill = null
                        targetFoldProgress = 0.0F
                        circleState = CircleState.CRUISE
                        stateTicks = 0
                    }
                }
            }
            CircleState.PHASE_TRANSITION, CircleState.SHORT_CIRCUIT, CircleState.DYING -> {}
        }
    }

    private fun beginSkillChannel(skill: UrCircleSkill) {
        currentSkill = skill
        channelPulseTimer = 0
        channelParticleTimer = 0
        circleState = CircleState.CHANNELING
        stateTicks = 0
        mediaWave.start(this, skill)
        if (!level().isClientSide && skill.usesMediaWave) {
            spawnConduitAnchors(skill)
        }
        UrCircleHexVfx.playRingSound(
            level(),
            beamOrigin(),
            ringOuterRadius(),
            skill.channelStartSound(this),
            skill.channelStartVolume(this),
            1.0F,
        )
        spawnChannelParticles(skill)
    }

    private fun runPhaseTransition() {
        val origin = beamOrigin()
        if (stateTicks < PHASE_BURST_TICK) {
            targetFoldProgress = 1.0F
            setDeltaMovement(0.0, 0.14, 0.0)
            if (stateTicks % 2 == 0) {
                val convergeR = (ringOuterRadius() * 0.45).coerceIn(6.0, 18.0)
                for (k in 0 until 8) {
                    val ang = random.nextDouble() * Math.PI * 2.0
                    val sx = origin.x + cos(ang) * convergeR
                    val sy = origin.y + (random.nextDouble() - 0.5) * 6.0
                    val sz = origin.z + sin(ang) * convergeR
                    UrCircleHexVfx.spawnConjure(
                        level(),
                        if (k % 2 == 0) UrCircleHexVfx.WAVE_GOLD else UrCircleHexVfx.MEDIA_PURPLE,
                        sx, sy, sz,
                        (origin.x - sx) * 0.09,
                        (origin.y - sy) * 0.09,
                        (origin.z - sz) * 0.09,
                    )
                }
            }
        } else {
            targetFoldProgress = 0.0F
            if (stateTicks == PHASE_BURST_TICK) {
                foldProgress = 0.0F
                val r = ringOuterRadius()
                UrCircleHexVfx.playRingSound(level(), origin, r, HexSounds.CAST_THOTH, 3.6F, 0.65F)
                UrCircleHexVfx.playRingSound(level(), origin, r, SoundEvents.END_PORTAL_SPAWN, 3.0F, 0.8F)
                UrCircleHexVfx.playRingSound(level(), origin, r, SoundEvents.BEACON_ACTIVATE, 2.8F, 0.7F)
                for (k in 0 until 48) {
                    UrCircleHexVfx.spawnConjure(
                        level(),
                        if (k % 3 == 0) UrCircleHexVfx.WAVE_GOLD else UrCircleHexVfx.BEAM_LAVENDER,
                        origin.x, origin.y, origin.z,
                        (random.nextDouble() - 0.5) * 1.4,
                        (random.nextDouble() - 0.5) * 0.9,
                        (random.nextDouble() - 0.5) * 1.4,
                    )
                }
                val waveBox = AABB(origin, origin).inflate(28.0)
                for (victim in level().getEntitiesOfClass(LivingEntity::class.java, waveBox)) {
                    if (victim === this || victim is Enemy || isUrCircleAlly(victim)) continue
                    val kb = victim.position().subtract(origin)
                    val len = kb.length().coerceAtLeast(0.1)
                    victim.push(kb.x / len * 1.35, 0.45, kb.z / len * 1.35)
                }
            }
            if (stateTicks < 56) {
                setDeltaMovement(0.0, 0.24, 0.0)
            } else {
                setDeltaMovement(deltaMovement.scale(0.85))
            }
            if (stateTicks % 4 == 0) {
                val r = ringOuterRadius()
                UrCircleHexVfx.spawnGroundCircle(level(), origin, r * 0.75, UrCircleHexVfx.WAVE_GOLD, points = 28)
                UrCircleHexVfx.spawnGroundCircle(level(), origin, r, UrCircleHexVfx.MEDIA_PURPLE, points = 36)
            }
        }
        if (stateTicks >= PHASE_TRANSITION_TICKS) {
            circleState = CircleState.CRUISE
            stateTicks = 0
            skillCooldown = 30
        }
    }

    private fun runShortCircuit() {
        targetFoldProgress = 0.0F
        val t = target
        if (t != null && t.isAlive && y > t.y + 3.0) {
            setDeltaMovement(0.0, -0.18, 0.0)
        } else {
            setDeltaMovement(deltaMovement.scale(0.8))
        }
        if (stateTicks % 4 == 0) {
            val origin = beamOrigin()
            for (k in 0 until 6) {
                UrCircleHexVfx.spawnConjure(
                    level(),
                    UrCircleHexVfx.SHORT_CIRCUIT_RED,
                    origin.x + (random.nextDouble() - 0.5) * 6.0,
                    origin.y + (random.nextDouble() - 0.5) * 4.0,
                    origin.z + (random.nextDouble() - 0.5) * 6.0,
                    (random.nextDouble() - 0.5) * 0.25,
                    0.12,
                    (random.nextDouble() - 0.5) * 0.25,
                )
            }
        }
        if (stateTicks >= SHORT_CIRCUIT_TICKS) {
            circleState = CircleState.CRUISE
            stateTicks = 0
        }
    }

    fun beginPhase2Transition() {
        if (phase >= 2 || circleState == CircleState.DYING) return
        phase = 2
        currentSkill = null
        beamSkill = null
        grindSkill = null
        targetFoldProgress = 1.0F
        mediaWave.clear(this)
        setSiphoning(false)
        setPlaneSlicing(false, 0.0F)
        discardCircleBeam()
        discardAnchors()
        circleState = CircleState.PHASE_TRANSITION
        stateTicks = 0
        UrCircleHexVfx.playRingSound(level(), beamOrigin(), ringOuterRadius(), SoundEvents.BEACON_POWER_SELECT, 3.0F, 0.65F)
    }

    fun triggerShortCircuit() {
        if (level().isClientSide || circleState == CircleState.DYING) return
        currentSkill = null
        beamSkill = null
        grindSkill = null
        targetFoldProgress = 0.0F
        mediaWave.clear(this)
        setSiphoning(false)
        setPlaneSlicing(false, 0.0F)
        discardCircleBeam()
        discardAnchors()

        internalHealthWrite = true
        try {
            setHealth(health - SHORT_CIRCUIT_DAMAGE)
        } finally {
            internalHealthWrite = false
        }
        if (health <= 0.0F) {
            die(level().damageSources().generic())
            return
        }

        circleState = CircleState.SHORT_CIRCUIT
        stateTicks = 0
        skillCooldown = SKILL_COOLDOWN

        val origin = beamOrigin()
        UrCircleHexVfx.playRingSound(level(), origin, ringOuterRadius(), HexSounds.SPELL_CIRCLE_FAIL, 2.8F, 0.85F)
        UrCircleHexVfx.playRingSound(level(), origin, ringOuterRadius(), SoundEvents.GLASS_BREAK, 2.2F, 0.75F)
        for (k in 0 until 32) {
            UrCircleHexVfx.spawnConjure(
                level(),
                UrCircleHexVfx.SHORT_CIRCUIT_RED,
                origin.x, origin.y, origin.z,
                (random.nextDouble() - 0.5) * 0.55,
                (random.nextDouble() - 0.5) * 0.55,
                (random.nextDouble() - 0.5) * 0.55,
            )
        }

        if (phase == 1 && health <= maxHealth * 0.5F) {
            beginPhase2Transition()
        }
    }

    // ---- 死亡演出（按环波次泛化） ----

    fun deathWaveOf(ringIndex: Int): Int =
        if (phase >= 2) {
            when (ringIndex) {
                2 -> 0
                5 -> 0
                4 -> 1
                3 -> 1
                1 -> 2
                else -> 3
            }
        } else {
            when (ringIndex) {
                1 -> 0
                3 -> 0
                else -> 1
            }
        }

    fun deathExtinguishTick(part: UrCirclePart): Int {
        if (part === earth) return CORE_BREAK_TICK
        val ring = rings.getOrNull(part.ringIndex) ?: return 20
        val idxInRing = ring.parts.indexOf(part).coerceAtLeast(0)
        return 15 + deathWaveOf(part.ringIndex) * 18 + (idxInRing % 4) * 2
    }

    fun deathVanishTick(part: UrCirclePart): Int {
        if (part === earth) return CORE_BREAK_TICK
        val ring = rings.getOrNull(part.ringIndex) ?: return 40
        val idxInRing = ring.parts.indexOf(part).coerceAtLeast(0)
        return 42 + deathWaveOf(part.ringIndex) * 22 + (idxInRing % 4) * 2
    }

    fun isPartExtinguishedInDeath(part: UrCirclePart): Boolean =
        circleState == CircleState.DYING && stateTicks >= deathExtinguishTick(part)

    fun isPartVanishedInDeath(part: UrCirclePart): Boolean =
        circleState == CircleState.DYING && stateTicks >= deathVanishTick(part)

    private fun runDeathSequence() {
        setDeltaMovement(deltaMovement.scale(0.85))
        val t = stateTicks

        // 1. 失控暴走期与逐环崩碎波次
        for (ring in rings) {
            if (!ring.isVisible(phase, phase2Reveal)) continue
            for (part in ring.parts) {
                if (part.active && t == deathVanishTick(part)) {
                    spawnPartBreak(part, isSlate = part is SlatePart)
                }
            }
        }

        // 2. 临界坍缩期：残余核心内收、强能量倒吸、高频蜂鸣
        if (t == DEATH_COLLAPSE_TICK) {
            targetFoldProgress = 1.0F
            UrCircleHexVfx.playRingSound(level(), beamOrigin(), ringOuterRadius(), SoundEvents.BEACON_POWER_SELECT, 3.2F, 0.55F)
        }
        if (t in (DEATH_COLLAPSE_TICK - 10) until CORE_BREAK_TICK && t % 2 == 0) {
            val origin = beamOrigin()
            val convergeR = (10.0 + (CORE_BREAK_TICK - t) * 0.45).coerceAtLeast(3.0)
            for (k in 0 until 6) {
                val ang = random.nextDouble() * Math.PI * 2.0
                val sx = origin.x + cos(ang) * convergeR
                val sy = origin.y + (random.nextDouble() - 0.5) * 10.0
                val sz = origin.z + sin(ang) * convergeR
                UrCircleHexVfx.spawnConjure(
                    level(),
                    if (k % 2 == 0) UrCircleHexVfx.WAVE_GOLD else UrCircleHexVfx.BEAM_LAVENDER,
                    sx, sy, sz,
                    (origin.x - sx) * 0.12,
                    (origin.y - sy) * 0.12,
                    (origin.z - sz) * 0.12,
                )
            }
        }

        if (t == CORE_BREAK_TICK) {
            spawnCoreBreak()
        }
        if (t >= DEATH_END_TICK) {
            finishDeath()
        }
    }

    private fun spawnPartBreak(part: UrCirclePart, isSlate: Boolean) {
        val level = level()
        val pos = part.posNow
        if (pos == Vec3.ZERO) return
        val particle = if (isSlate) SLATE_BREAK else STONE_BREAK
        for (k in 0 until 16) {
            spawnParticle(
                level, particle,
                pos.x, pos.y, pos.z,
                (random.nextDouble() - 0.5) * 0.65, random.nextDouble() * 0.6, (random.nextDouble() - 0.5) * 0.65,
                UrCircleHexVfx.DEFAULT_RANGE,
            )
        }
        UrCircleHexVfx.spawnConjure(level, UrCircleHexVfx.MEDIA_PURPLE, pos.x, pos.y, pos.z, 0.0, 0.16, 0.0)
        UrCircleHexVfx.playRingSound(level, pos, ringOuterRadius(), SoundEvents.STONE_BREAK, 1.4F, 0.9F + part.ringIndex * 0.1F)
    }

    private fun spawnCoreBreak() {
        val level = level()
        var pos = earth.posNow
        if (pos == Vec3.ZERO) pos = position().add(0.0, bbHeight / 2.0, 0.0)
        for (k in 0 until 84) {
            spawnParticle(
                level, AMETHYST_BREAK,
                pos.x + (random.nextDouble() - 0.5) * 3.0,
                pos.y + (random.nextDouble() - 0.5) * 3.0,
                pos.z + (random.nextDouble() - 0.5) * 3.0,
                (random.nextDouble() - 0.5) * 0.85, random.nextDouble() * 0.75, (random.nextDouble() - 0.5) * 0.85,
                UrCircleHexVfx.DEFAULT_RANGE,
            )
            UrCircleHexVfx.spawnConjure(
                level,
                if (k % 2 == 0) UrCircleHexVfx.WAVE_GOLD else UrCircleHexVfx.MEDIA_PURPLE,
                pos.x, pos.y, pos.z,
                (random.nextDouble() - 0.5) * 1.35,
                (random.nextDouble() - 0.5) * 1.1,
                (random.nextDouble() - 0.5) * 1.35,
            )
            if (k % 3 == 0) {
                spawnParticle(
                    level, ParticleTypes.END_ROD,
                    pos.x, pos.y, pos.z,
                    (random.nextDouble() - 0.5) * 1.2,
                    (random.nextDouble() - 0.2) * 1.1,
                    (random.nextDouble() - 0.5) * 1.2,
                    UrCircleHexVfx.DEFAULT_RANGE,
                )
            }
        }
        spawnParticle(level, ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y, pos.z, 0.0, 0.0, 0.0, UrCircleHexVfx.DEFAULT_RANGE)
        UrCircleHexVfx.playRingSound(level, pos, ringOuterRadius(), SoundEvents.GENERIC_EXPLODE, 4.0F, 0.65F)
        UrCircleHexVfx.playRingSound(level, pos, ringOuterRadius(), HexSounds.CAST_THOTH, 3.6F, 0.7F)
        UrCircleHexVfx.playRingSound(level, pos, ringOuterRadius(), SoundEvents.END_PORTAL_SPAWN, 3.0F, 0.9F)
    }

    private fun finishDeath() {
        if (level().isClientSide) return
        val level = level() as ServerLevel
        val dropPos = position().add(0.0, bbHeight / 2.0, 0.0)
        val item = ItemEntity(level, dropPos.x, dropPos.y, dropPos.z, ItemStack(HexMobItems.UR_CIRCLE_CORE.get()))
        item.setDeltaMovement((random.nextDouble() - 0.5) * 0.3, random.nextDouble() * 0.3, (random.nextDouble() - 0.5) * 0.3)
        level.addFreshEntity(item)
        ExperienceOrb.award(level, dropPos, getExperienceReward())
        remove(Entity.RemovalReason.KILLED)
    }

    private fun pickWeightedSkill(): UrCircleSkill? {
        val usable = skills.filter { phase >= it.minPhase && it.canUse(this) }
        if (usable.isEmpty()) return null
        val total = usable.sumOf { it.weight }
        if (total <= 0) return usable.random()
        var r = random.nextInt(total)
        for (s in usable) {
            r -= s.weight
            if (r < 0) return s
        }
        return usable.last()
    }

    private fun spawnChannelParticles(skill: UrCircleSkill) {
        val progress = (stateTicks.toFloat() / skill.channelTicks).coerceIn(0.0F, 1.0F)
        val count = skill.channelParticlesPerPulse(this) + (progress * 16).toInt()
        val type = skill.channelParticleType(this)
        for (k in 0 until count) {
            val pos = skill.channelParticlePos(this)
            val vel = skill.channelParticleVelocity(this, pos)
            spawnParticle(level(), type, pos.x, pos.y, pos.z, vel.x, vel.y, vel.z, UrCircleHexVfx.DEFAULT_RANGE)
        }
    }

    override fun addAdditionalSaveData(nbt: CompoundTag) {
        super.addAdditionalSaveData(nbt)
        nbt.putBoolean("hexmob:Dormant", dormant)
        nbt.putInt("hexmob:Phase", phase)
    }

    override fun readAdditionalSaveData(compound: CompoundTag) {
        dormant = compound.getBoolean("hexmob:Dormant")
        entityData.set(DORMANT, dormant)
        dormantFold = if (dormant) 1.0F else 0.0F
        internalHealthWrite = true
        super.readAdditionalSaveData(compound)
        internalHealthWrite = false
        val savedPhase = if (compound.contains("hexmob:Phase")) {
            compound.getInt("hexmob:Phase").coerceIn(1, 2)
        } else if (health <= maxHealth * 0.5F) {
            2
        } else {
            1
        }
        phase = savedPhase
        phase2Reveal = if (savedPhase >= 2) 1.0F else 0.0F
    }

    override fun getAllParts(): List<UrCirclePart> = cachedAllParts
    override fun getPartsBoundingBox(): AABB {
        val r = if (phase >= 2) maxOf(ringOuterRadius() * 1.7, 210.0) else maxOf(ringOuterRadius() + 24.0, 96.0)
        val yUp = if (phase >= 2) maxOf(r * 0.9, 170.0) else r
        val yDown = if (phase >= 2) maxOf(r * 0.7, 150.0) else r * 0.85
        val c = position().add(0.0, bbHeight * 0.5, 0.0)
        return AABB(c.x - r, c.y - yDown, c.z - r, c.x + r, c.y + yUp, c.z + r)
    }
    override fun getBoundingBoxForCulling(): AABB = getPartsBoundingBox()
    override fun shouldRenderAtSqrDistance(distance: Double): Boolean {
        val maxDist = if (phase >= 2) 420.0 else 280.0
        return distance <= maxDist * maxDist
    }
    override fun shouldRecord(): Boolean = isAlive

    override fun addEffect(effectInstance: MobEffectInstance, entity: Entity?): Boolean =
        if (effectInstance.effect == MobEffects.GLOWING) super.addEffect(effectInstance, entity) else false

    override fun canRide(vehicle: Entity): Boolean = false
    override fun canChangeDimensions(): Boolean = false
    override fun isPickable(): Boolean = false
    override fun getExperienceReward() = Enemy.XP_REWARD_BOSS
    override fun isNoGravity(): Boolean = true
    override fun isPersistenceRequired(): Boolean = true
    override fun setRemainingFireTicks(ticks: Int) {}

    override fun isReferenceFlickering(gameTime: Long): Boolean = circleState != CircleState.SHORT_CIRCUIT

    override fun createFlickeringMishap(entity: Entity): Mishap = UrCircleFlickerMishap(this)

    fun hurtPart(part: UrCirclePart, source: DamageSource, amount: Float): Boolean {
        if (!level().isClientSide) {
            if (dormant || circleState == CircleState.DYING || circleState == CircleState.PHASE_TRANSITION) return false
            if (source.entity === this || isUrCircleAlly(source.entity) || isUrCircleAlly(source.directEntity)) return false

            val isIndirectSource = source.entity is QuenchAllay ||
                source.directEntity is QuenchAllay ||
                source.`is`(DamageTypes.MAGIC) ||
                source.`is`(DamageTypes.INDIRECT_MAGIC)
            if (isSiphoning() && isIndirectSource && amount > 0.0F) {
                shatterSiphon()
            }

            if (circleState == CircleState.CHANNELING) {
                val active = currentSkill
                if (active != null && active.usesMediaWave && mediaWave.onPartHurt(this, active, part, amount)) {
                    val attacker = source.entity
                    if (attacker is LivingEntity && attacker !== this && attacker.isAlive) {
                        target = attacker
                        addToHated(attacker)
                    }
                    triggerShortCircuit()
                    return true
                }
            }
        }
        return hurt(source, amount)
    }

    private fun shatterSiphon() {
        setSiphoning(false)
        siphonTimer = 0
        val origin = beamOrigin()
        for (k in 0 until 32) {
            UrCircleHexVfx.spawnConjure(
                level(),
                UrCircleHexVfx.SIPHON_CYAN,
                origin.x, origin.y, origin.z,
                (random.nextDouble() - 0.5) * 0.55,
                (random.nextDouble() - 0.5) * 0.55,
                (random.nextDouble() - 0.5) * 0.55,
            )
        }
        UrCircleHexVfx.playRingSound(level(), origin, ringOuterRadius(), HexSounds.ABACUS, 2.2F, 1.4F)
        UrCircleHexVfx.playRingSound(level(), origin, ringOuterRadius(), SoundEvents.GLASS_BREAK, 2.2F, 0.75F)
    }

    override fun hurt(source: DamageSource, amount: Float): Boolean {
        if (!level().isClientSide) {
            if (dormant || circleState == CircleState.DYING || circleState == CircleState.PHASE_TRANSITION) return false
            val attacker = source.entity
            if (attacker === this || isUrCircleAlly(attacker) || isUrCircleAlly(source.directEntity)) return false
            if (attacker is LivingEntity && attacker !== this && attacker.isAlive) {
                target = attacker
                addToHated(attacker)
            }
            if (source.`is`(HexDamageTypes.OVERCAST)) return false
            if (isImmuneDamage(source)) return false
        }
        var dmg = if (isFireDamage(source)) amount * FIRE_DAMAGE_MULTIPLIER else amount
        if (circleState == CircleState.SHORT_CIRCUIT) {
            dmg *= 1.25F
        } else if (!level().isClientSide) {
            dmg *= servantDamageMultiplier()
        }
        if (dmg > SOFT_HIT_CAP) {
            dmg = SOFT_HIT_CAP + (dmg - SOFT_HIT_CAP) * DAMAGE_OVERFLOW_MULTIPLIER
        }
        if (!level().isClientSide) {
            val now = tickCount
            if (now - dpsWindowStart >= DPS_WINDOW_TICKS) {
                dpsWindowStart = now
                dpsWindow = 0.0F
            }
            val room = DPS_CAP_PER_WINDOW - dpsWindow
            val allowed = dmg.coerceAtMost(room)
            if (allowed <= 0.0F) return false
            dpsWindow += allowed
            dmg = allowed
        }
        internalHealthWrite = true
        return try {
            val active = when (circleState) {
                CircleState.CHANNELING -> currentSkill
                CircleState.BEAM -> beamSkill
                else -> null
            }
            val result = if (active != null && !level().isClientSide) {
                active.onChannelHurt(this, source, dmg)
            } else {
                super.hurt(source, dmg)
            }
            if (result && !level().isClientSide && dmg > 0.0F) {
                if (phase == 1 && isAlive && health > 0.0F && health <= maxHealth * 0.5F && circleState != CircleState.DYING) {
                    beginPhase2Transition()
                }
            }
            result
        } finally {
            internalHealthWrite = false
        }
    }

    private fun isImmuneDamage(source: DamageSource): Boolean =
        source.`is`(DamageTypes.FALL) || source.`is`(DamageTypes.FLY_INTO_WALL) ||
            source.`is`(DamageTypes.IN_WALL) || source.`is`(DamageTypes.DROWN) ||
            source.`is`(DamageTypes.DRY_OUT) || source.`is`(DamageTypes.FREEZE) ||
            source.`is`(DamageTypes.CACTUS) || source.`is`(DamageTypes.FELL_OUT_OF_WORLD) ||
            source.`is`(DamageTypes.EXPLOSION) || source.`is`(DamageTypes.PLAYER_EXPLOSION) ||
            source.`is`(DamageTypes.FIREBALL) || source.`is`(DamageTypes.UNATTRIBUTED_FIREBALL)

    private fun isFireDamage(source: DamageSource): Boolean =
        source.`is`(DamageTypes.LAVA) || source.`is`(DamageTypes.IN_FIRE) ||
            source.`is`(DamageTypes.ON_FIRE) || source.`is`(DamageTypes.HOT_FLOOR)

    override fun knockback(strength: Double, x: Double, z: Double) {}

    override fun setHealth(health: Float) {
        if (internalHealthWrite || tickCount <= 1) {
            super.setHealth(health)
        }
    }

    override fun getHurtSound(source: DamageSource): SoundEvent =
        HexBlocks.SLATE.defaultBlockState().getSoundType().getBreakSound()

    fun applyChannelDamage(source: DamageSource, amount: Float): Boolean = super.hurt(source, amount)

    fun interruptChannel() {
        when (circleState) {
            CircleState.CHANNELING -> {
                currentSkill = null
                mediaWave.clear(this)
            }
            CircleState.BEAM -> {
                beamSkill?.onBeamEnd(this)
                beamSkill = null
            }
            CircleState.FOLD_GRIND -> {
                grindSkill?.onGrindEnd(this)
                grindSkill = null
                targetFoldProgress = 0.0F
            }
            else -> return
        }
        discardCircleBeam()
        discardAnchors()
        setPlaneSlicing(false, 0.0F)
        circleState = CircleState.CRUISE
        stateTicks = 0
        skillCooldown = SKILL_COOLDOWN
    }

    fun beginBeam(skill: UrCircleSkill) {
        beamSkill = skill
        circleState = CircleState.BEAM
        stateTicks = 0
    }

    fun beginGrind(skill: UrCircleSkill) {
        grindSkill = skill
        circleState = CircleState.FOLD_GRIND
        stateTicks = 0
    }

    fun syncCircleBeam(
        targetPos: Vec3? = null,
        radius: Float = 0.85F,
        color: Int = UrCircleHexVfx.BEAM_LAVENDER,
    ) {
        if (level().isClientSide) return
        val level = level() as ServerLevel
        var entity = circleBeamEntity
        val origin = beamOrigin()
        val end = targetPos ?: target?.let { t ->
            if (t.isAlive) t.position().add(0.0, t.bbHeight / 2.0, 0.0) else origin
        } ?: origin
        if (entity == null || !entity.isAlive) {
            entity = UrCoreBeamEntity(HexMobEntities.UR_CORE_BEAM.get(), level)
            entity.setPos(origin.x, origin.y, origin.z)
            entity.setBeamTarget(end)
            entity.beamRadius = radius
            entity.beamColor = color
            level.addFreshEntity(entity)
            circleBeamEntity = entity
        } else {
            entity.setPos(origin.x, origin.y, origin.z)
            entity.setBeamTarget(end)
            entity.beamRadius = radius
            entity.beamColor = color
        }
    }

    private fun spawnConduitAnchors(skill: UrCircleSkill) {
        discardAnchors()
        val serverLevel = level() as? ServerLevel ?: return
        val ref = target?.position() ?: position()
        val count = if (phase >= 2) 2 else 1
        val anchorHp = if (phase >= 2) 35.0F else 25.0F
        val baseAngle = random.nextDouble() * Math.PI * 2.0
        for (i in 0 until count) {
            val ang = baseAngle + i * Math.PI
            val dist = 7.0 + random.nextDouble() * 5.0
            val ax = ref.x + cos(ang) * dist
            val az = ref.z + sin(ang) * dist
            val ay = findAnchorY(serverLevel, ax, ref.y, az)
            val anchor = UrCircleAnchorEntity(HexMobEntities.UR_CIRCLE_ANCHOR.get(), serverLevel)
            anchor.setPos(ax, ay, az)
            anchor.owner = this
            anchor.color = skill.waveColor
            anchor.maxHp = anchorHp
            anchor.hp = anchorHp
            anchor.lifeTicks = skill.channelTicks + 12
            anchor.setTetherTarget(beamOrigin())
            serverLevel.addFreshEntity(anchor)
            activeAnchors.add(anchor)
        }
    }

    private fun findAnchorY(level: Level, x: Double, refY: Double, z: Double): Double {
        val bx = BlockPos.containing(x, refY, z)
        for (dy in 4 downTo -6) {
            val p = bx.offset(0, dy, 0)
            if (!level.getBlockState(p).isAir && level.getBlockState(p.above()).isAir) {
                return p.y + 1.2
            }
        }
        return refY + 0.8
    }

    fun onAnchorBroken(anchor: UrCircleAnchorEntity) {
        activeAnchors.remove(anchor)
        discardAnchors()
        triggerShortCircuit()
    }

    private fun discardAnchors() {
        for (a in activeAnchors) {
            if (a.isAlive || a.level().isClientSide) a.discard()
        }
        activeAnchors.clear()
    }

    private fun discardCircleBeam() {
        circleBeamEntity?.let {
            if (it.isAlive || it.level().isClientSide) it.discard()
        }
        circleBeamEntity = null
    }

    fun beamOrigin(): Vec3 {
        val e = earth.posNow
        return if (e == Vec3.ZERO) position().add(0.0, bbHeight / 2.0, 0.0) else e
    }

    fun livingServants(): Int {
        if (level().isClientSide) return 0
        val box = AABB(blockPosition()).inflate(64.0)
        val vex = level().getEntitiesOfClass(UrCircleServant::class.java, box) {
            it.isAlive && it.getOwner() === this
        }.size
        val guards = level().getEntitiesOfClass(Mob::class.java, box) {
            it.isAlive && it is HexMobGuard && it.isSummonedBy(this)
        }.size
        return vex + guards
    }

    fun isProtected(): Boolean = false

    /** 每只存活仆从提供 8% 减伤（上限 50%）；断路僵直期间无视仆从减伤。 */
    fun servantDamageMultiplier(): Float {
        if (circleState == CircleState.SHORT_CIRCUIT) return 1.0F
        return 1.0F - (livingServants() * 0.08F).coerceAtMost(0.50F)
    }

    private fun idleBehavior() {
        if (circleState != CircleState.CRUISE) return
        if (servantSummonCooldown > 0) servantSummonCooldown--
        val t = target
        if (t != null && t.isAlive) return
        val hpRatio = health / maxHealth
        if (hpRatio < PROTECT_HEALTH_RATIO) {
            healSelf(IDLE_REGEN)
            if (tickCount % 10 == 0) {
                val origin = beamOrigin()
                for (k in 0 until 4) {
                    UrCircleHexVfx.spawnConjure(
                        level(),
                        UrCircleHexVfx.SIPHON_CYAN,
                        origin.x + (random.nextDouble() - 0.5) * 5.0,
                        origin.y + (random.nextDouble() - 0.5) * 5.0,
                        origin.z + (random.nextDouble() - 0.5) * 5.0,
                        0.0, 0.25, 0.0,
                    )
                }
            }
            return
        }
        if (livingServants() < SummonServantSkill.MAX_SERVANTS && servantSummonCooldown <= 0) {
            summonServantOne()
            servantSummonCooldown = SERVANT_SUMMON_INTERVAL
        }
    }

    fun summonServantOne() {
        summonPets(1)
    }

    fun summonPets(count: Int) {
        val origin = position().add(0.0, bbHeight / 2.0, 0.0)
        for (i in 0 until count) summonPetOne(origin)
    }

    private fun summonPetOne(origin: Vec3) {
        val level = level()
        if (level.isClientSide) return
        val ang = random.nextDouble() * Math.PI * 2.0
        val pos = Vec3(origin.x + cos(ang) * 2.0, origin.y + 1.0, origin.z + sin(ang) * 2.0)
        val roll = random.nextFloat()
        when {
            roll < 0.4F -> {
                val s = UrCircleServant(HexMobEntities.UR_CIRCLE_SERVANT.get(), level)
                s.setPos(pos.x, pos.y, pos.z)
                s.setOwner(this)
                level.addFreshEntity(s)
            }
            roll < 0.6F -> spawnGuardPet(HexMobEntities.GUARD_ARCHER.get(), pos)
            roll < 0.8F -> spawnGuardPet(HexMobEntities.GUARD_BRUTE.get(), pos)
            else -> spawnGuardPet(HexMobEntities.GUARD_GOLEM.get(), pos)
        }
    }

    private fun spawnGuardPet(type: EntityType<*>, pos: Vec3) {
        val level = level()
        val entity = type.create(level) ?: return
        if (entity is HexMobGuard) {
            val e = entity as Entity
            e.setPos(pos.x, pos.y, pos.z)
            entity.setSummoner(this)
            (entity as? GuardDormant)?.equipOnSummon()
            level.addFreshEntity(e)
        }
    }

    fun addToHated(entity: LivingEntity) {
        if (level().isClientSide) return
        if (entity === this || entity is Enemy || isUrCircleAlly(entity) || !entity.isAlive) return
        if (entity !in hatedTargets && distanceToSqr(entity) <= HATE_RANGE * HATE_RANGE) {
            hatedTargets.add(entity)
        }
    }

    fun currentHated(): List<LivingEntity> =
        hatedTargets.filter { it.isAlive && !it.isRemoved && it !is Enemy && !isUrCircleAlly(it) && distanceToSqr(it) <= HATE_RANGE * HATE_RANGE }

    fun preferredTargets(entities: Collection<LivingEntity>): List<LivingEntity> =
        entities.sortedWith(
            compareByDescending<LivingEntity> { it is Player }
                .thenByDescending { it.health }
        )

    fun onServantKilled() {
        if (level().isClientSide || !isAlive || circleState == CircleState.DYING) return
        internalHealthWrite = true
        try {
            setHealth(health - SERVANT_DEATH_DAMAGE)
        } finally {
            internalHealthWrite = false
        }
        if (health <= 0.0F) {
            die(level().damageSources().generic())
        }
    }

    fun healSelf(amount: Float) {
        if (level().isClientSide || !isAlive || circleState == CircleState.DYING) return
        internalHealthWrite = true
        try {
            setHealth(health + amount)
        } finally {
            internalHealthWrite = false
        }
    }

    fun accumulateBacklash(player: ServerPlayer) {
        if (level().isClientSide || !isSiphoning()) return
        if (tickCount - lastBacklashTick < BACKLASH_TICK_INTERVAL) return
        lastBacklashTick = tickCount
        backlash += 1
        if (backlash >= BACKLASH_THRESHOLD) {
            backlash = 0
            retaliate(player)
        }
    }

    private fun retaliate(player: ServerPlayer) {
        val origin = beamOrigin()
        val playerCenter = player.position().add(0.0, player.bbHeight / 2.0, 0.0)
        UrCircleHexVfx.spawnRingArc(level(), playerCenter, origin, UrCircleHexVfx.SIPHON_CYAN, steps = 16)
        healSelf(20.0F)
        UrCircleHexVfx.playRingSound(level(), origin, ringOuterRadius(), HexSounds.CAST_FAILURE, 2.0F, 0.9F)
        UrCircleMishap.throwAt(player, UrCircleStatusTable.randomMishap(this, random))
    }

    // ---- 调试命令入口 ----

    fun forceSetPhase(targetPhase: Int) {
        val p = targetPhase.coerceIn(1, 2)
        interruptChannel()
        dormant = false
        wokenOnce = true
        entityData.set(DORMANT, false)
        dormantFold = 0.0F
        targetFoldProgress = 0.0F
        foldProgress = 0.0F
        phase = p
        phase2Reveal = if (p >= 2) 1.0F else 0.0F
        circleState = CircleState.CRUISE
        stateTicks = 0
    }

    fun forceDormant(v: Boolean) {
        interruptChannel()
        setSiphoning(false)
        targetFoldProgress = 0.0F
        foldProgress = 0.0F
        dormant = v
        wokenOnce = !v
        entityData.set(DORMANT, v)
        dormantFold = if (v) 1.0F else 0.0F
        if (v) {
            hatedTargets.clear()
            target = null
        }
        circleState = CircleState.CRUISE
        stateTicks = 0
    }

    fun forceTriggerSkill(skillName: String): Boolean {
        val skill = skills.firstOrNull { it.name.equals(skillName, ignoreCase = true) } ?: return false
        if (dormant) forceDormant(false)
        if (skill.minPhase > phase) forceSetPhase(skill.minPhase)
        interruptChannel()
        beginSkillChannel(skill)
        return true
    }

    fun toggleSiphon(enable: Boolean) {
        setSiphoning(enable)
        siphonTimer = 0
    }

    fun forceSetHealth(newHp: Float) {
        internalHealthWrite = true
        try {
            setHealth(newHp.coerceIn(1.0F, maxHealth))
        } finally {
            internalHealthWrite = false
        }
    }

    fun forceResetCooldown(ticks: Int = 0) {
        skillCooldown = ticks.coerceAtLeast(0)
    }

    fun forceTriggerTransition() {
        interruptChannel()
        dormant = false
        wokenOnce = true
        entityData.set(DORMANT, false)
        dormantFold = 0.0F
        phase = 1
        phase2Reveal = 0.0F
        beginPhase2Transition()
    }

    fun forceTriggerDeath() {
        dormant = false
        wokenOnce = true
        entityData.set(DORMANT, false)
        die(level().damageSources().generic())
    }

    fun debugSummary(): String =
        "phase=$phase state=$circleState($stateTicks) hp=${"%.1f".format(health)}/${"%.0f".format(maxHealth)} " +
            "reveal=${"%.2f".format(phase2Reveal)} fold=${"%.2f".format(foldProgress)} dormant=$dormant " +
            "outerR=${"%.1f".format(ringOuterRadius())} rings=${rings.size} parts=${cachedAllParts.size} " +
            "servants=${livingServants()}(dmg*${"%.2f".format(servantDamageMultiplier())}) anchors=${activeAnchors.size}"

    override fun recreateFromPacket(packet: ClientboundAddEntityPacket) {
        super.recreateFromPacket(packet)
        val parts: List<UrCirclePart> = getAllParts()
        for (i in parts.indices) {
            parts[i].setId(packet.id + i + 1)
        }
    }

    companion object {
        val EQUATOR_ROTATION: EntityDataAccessor<Float> = SynchedEntityData.defineId(UrCircleEntity::class.java, EntityDataSerializers.FLOAT)
        val ECLIPTIC_ROTATION: EntityDataAccessor<Float> = SynchedEntityData.defineId(UrCircleEntity::class.java, EntityDataSerializers.FLOAT)
        val ROT_OUTER: EntityDataAccessor<Float> = SynchedEntityData.defineId(UrCircleEntity::class.java, EntityDataSerializers.FLOAT)
        val ROT_POLAR: EntityDataAccessor<Float> = SynchedEntityData.defineId(UrCircleEntity::class.java, EntityDataSerializers.FLOAT)
        val ROT_ZENITH: EntityDataAccessor<Float> = SynchedEntityData.defineId(UrCircleEntity::class.java, EntityDataSerializers.FLOAT)
        val ROT_APEX: EntityDataAccessor<Float> = SynchedEntityData.defineId(UrCircleEntity::class.java, EntityDataSerializers.FLOAT)
        val STATE: EntityDataAccessor<Int> = SynchedEntityData.defineId(UrCircleEntity::class.java, EntityDataSerializers.INT)
        val STATE_TICKS: EntityDataAccessor<Int> = SynchedEntityData.defineId(UrCircleEntity::class.java, EntityDataSerializers.INT)
        val TUMBLING: EntityDataAccessor<Boolean> = SynchedEntityData.defineId(UrCircleEntity::class.java, EntityDataSerializers.BOOLEAN)
        val RING_SPINNING: EntityDataAccessor<Boolean> = SynchedEntityData.defineId(UrCircleEntity::class.java, EntityDataSerializers.BOOLEAN)
        val PHASE: EntityDataAccessor<Int> = SynchedEntityData.defineId(UrCircleEntity::class.java, EntityDataSerializers.INT)
        val DORMANT: EntityDataAccessor<Boolean> = SynchedEntityData.defineId(UrCircleEntity::class.java, EntityDataSerializers.BOOLEAN)
        val DORMANT_FOLD: EntityDataAccessor<Float> = SynchedEntityData.defineId(UrCircleEntity::class.java, EntityDataSerializers.FLOAT)
        val PHASE2_REVEAL: EntityDataAccessor<Float> = SynchedEntityData.defineId(UrCircleEntity::class.java, EntityDataSerializers.FLOAT)
        val FOLD_PROGRESS: EntityDataAccessor<Float> = SynchedEntityData.defineId(UrCircleEntity::class.java, EntityDataSerializers.FLOAT)
        val ACTIVE_NODE_A: EntityDataAccessor<Int> = SynchedEntityData.defineId(UrCircleEntity::class.java, EntityDataSerializers.INT)
        val ACTIVE_NODE_B: EntityDataAccessor<Int> = SynchedEntityData.defineId(UrCircleEntity::class.java, EntityDataSerializers.INT)
        val WAVE_COLOR: EntityDataAccessor<Int> = SynchedEntityData.defineId(UrCircleEntity::class.java, EntityDataSerializers.INT)
        val SIPHONING: EntityDataAccessor<Boolean> = SynchedEntityData.defineId(UrCircleEntity::class.java, EntityDataSerializers.BOOLEAN)
        val SLICE_ACTIVE: EntityDataAccessor<Boolean> = SynchedEntityData.defineId(UrCircleEntity::class.java, EntityDataSerializers.BOOLEAN)
        val SLICE_Y_OFFSET: EntityDataAccessor<Float> = SynchedEntityData.defineId(UrCircleEntity::class.java, EntityDataSerializers.FLOAT)

        const val CONTACT_DAMAGE = 6.0F
        const val CONTACT_COOLDOWN = 20
        const val FIRE_INTERVAL = 50
        const val SLATE_SPEED = 1.95F
        const val MIN_FIRE_INTERVAL = 12
        const val MAX_VOLLEY = 3
        const val WINDUP_TICKS = 20
        const val CHARGE_TICKS = 12
        const val STAGGER_TICKS = 30
        const val CHARGE_SPEED = 1.2
        const val CHARGE_COOLDOWN = 100
        const val GROUND_CRATER_COOLDOWN = 40
        const val AMBIENT_SOUND_INTERVAL = 100
        const val BOSS_BAR_RANGE = 300.0
        const val WAKE_RANGE = 42.0
        const val ARENA_CHECK_MAX_TICKS = 60
        const val SKILL_COOLDOWN = 90
        const val CHANNEL_PARTICLE_INTERVAL = 4
        const val PROTECT_HEALTH_RATIO = 0.9F
        const val IDLE_REGEN = 1.0F
        const val SERVANT_SUMMON_INTERVAL = 40

        const val BACKLASH_RANGE = 48.0
        const val BACKLASH_THRESHOLD = 2
        const val BACKLASH_TICK_INTERVAL = 2
        const val SIPHON_INTERVAL = 260
        const val SIPHON_DURATION = 120

        const val PHASE_BURST_TICK = 28
        const val PHASE_TRANSITION_TICKS = 80
        const val SHORT_CIRCUIT_TICKS = 100
        const val SHORT_CIRCUIT_DAMAGE = 25.0F

        const val HATE_RANGE = 300.0
        const val SERVANT_DEATH_DAMAGE = 5.0F
        const val CHANNEL_TUMBLE_SPEED = 5.0F

        const val SOFT_HIT_CAP = 35.0F
        const val DAMAGE_OVERFLOW_MULTIPLIER = 0.2F
        const val DPS_WINDOW_TICKS = 20
        const val DPS_CAP_PER_WINDOW = 70.0F
        const val FIRE_DAMAGE_MULTIPLIER = 0.1F

        const val SPIN_UP_TICKS = 35
        const val SPIN_DOWN_END = 95
        const val DEATH_COLLAPSE_TICK = 100
        const val CORE_BREAK_TICK = 136
        const val DEATH_END_TICK = 165

        val SLATE_BREAK = BlockParticleOption(ParticleTypes.BLOCK, HexBlocks.SLATE.defaultBlockState())
        val STONE_BREAK = BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState())
        val AMETHYST_BREAK = BlockParticleOption(ParticleTypes.BLOCK, Blocks.AMETHYST_BLOCK.defaultBlockState())

        const val RING_SPIN_MAX_SCALE = 2.0F

        fun registerAttributes(): AttributeSupplier.Builder =
            createMobAttributes()
                .add(Attributes.MAX_HEALTH, 500.0)
                .add(Attributes.ARMOR, 20.0)
                .add(Attributes.ARMOR_TOUGHNESS, 10.0)
    }
}
