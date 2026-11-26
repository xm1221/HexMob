package pub.pigeon.yggdyy.hexmob.content.ur_circle

import at.petrak.hexcasting.api.casting.iota.Iota
import at.petrak.hexcasting.api.casting.mishaps.Mishap
import at.petrak.hexcasting.api.misc.MediaConstants
import at.petrak.hexcasting.common.lib.HexBlocks
import at.petrak.hexcasting.common.lib.HexDamageTypes
import at.petrak.hexcasting.common.lib.HexSounds
import at.petrak.hexcasting.xplat.IXplatAbstractions
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
import pub.pigeon.yggdyy.hexmob.client.UrCircleHudTracker
import pub.pigeon.yggdyy.hexmob.config.HexMobServerConfig
import pub.pigeon.yggdyy.hexmob.content.IHMMultipartEntity
import pub.pigeon.yggdyy.hexmob.content.guard.GuardDormant
import pub.pigeon.yggdyy.hexmob.content.guard.HexMobGuard
import pub.pigeon.yggdyy.hexmob.content.quench_allay.QuenchAllay
import pub.pigeon.yggdyy.hexmob.content.ur_circle.servant.UrCircleServant
import pub.pigeon.yggdyy.hexmob.content.ur_circle.spells.AmethystTrapSkill
import pub.pigeon.yggdyy.hexmob.content.ur_circle.spells.BeamSkill
import pub.pigeon.yggdyy.hexmob.content.ur_circle.spells.CelestialConjunctionSkill
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
import pub.pigeon.yggdyy.hexmob.content.ur_circle.subentities.SlateSymbolType
import pub.pigeon.yggdyy.hexmob.content.ur_circle.ur_spell.UrCoreBeamEntity
import pub.pigeon.yggdyy.hexmob.networking.msg.MsgUrCircleHudS2C
import pub.pigeon.yggdyy.hexmob.networking.msg.SlateHudEntry
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
        set(value) = entityData.set(PHASE, value.coerceIn(1, 3))
    var dormantFold: Float
        get() = entityData.get(DORMANT_FOLD)
        set(value) = entityData.set(DORMANT_FOLD, value.coerceIn(0F, 1F))
    var phase2Reveal: Float
        get() = entityData.get(PHASE2_REVEAL)
        set(value) = entityData.set(PHASE2_REVEAL, value.coerceIn(0F, 1F))
    var phase3Reveal: Float
        get() = entityData.get(PHASE3_REVEAL)
        set(value) = entityData.set(PHASE3_REVEAL, value.coerceIn(0F, 1F))
    var stability: Int
        get() = entityData.get(STABILITY)
        set(value) = entityData.set(STABILITY, value.coerceIn(0, MAX_STABILITY))
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
        RingSpinSkill(),
        CelestialConjunctionSkill(),
    )
    private val skillLastCastTick: MutableMap<String, Int> = HashMap()
    private val recentSkillNames: kotlin.collections.ArrayDeque<String> = kotlin.collections.ArrayDeque(3)

    private val activeSymbolicSlates: MutableList<SlatePart> = mutableListOf()
    private var hudSyncDirty: Boolean = true
    private var stabilityRegenTimer: Int = 0

    private var currentSkill: UrCircleSkill? = null
    var beamSkill: UrCircleSkill? = null
    var grindSkill: UrCircleSkill? = null
    private var circleBeamEntity: UrCoreBeamEntity? = null
    private val activeAnchors: MutableList<UrCircleAnchorEntity> = mutableListOf()
    private var channelPulseTimer = 0
    private var channelParticleTimer = 0
    private var skillCooldown = 0

    private val backlashByPlayer: MutableMap<UUID, Int> = HashMap()
    private val lastBacklashTickByPlayer: MutableMap<UUID, Int> = HashMap()
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
        entityData.define(PHASE3_REVEAL, 0F)
        entityData.define(STABILITY, MAX_STABILITY)
        entityData.define(FOLD_PROGRESS, 0F)
        entityData.define(ACTIVE_NODE_A, -1)
        entityData.define(ACTIVE_NODE_B, -1)
        entityData.define(WAVE_COLOR, UrCircleHexVfx.MEDIA_PURPLE)
        entityData.define(SIPHONING, false)
        entityData.define(SLICE_ACTIVE, false)
        entityData.define(SLICE_Y_OFFSET, 0.0F)
        entityData.define(SLICE_OX, 0.0F)
        entityData.define(SLICE_OY, 0.0F)
        entityData.define(SLICE_OZ, 0.0F)
        entityData.define(SLICE_NX, 0.0F)
        entityData.define(SLICE_NY, 1.0F)
        entityData.define(SLICE_NZ, 0.0F)
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
            hatedTargets.removeAll {
                !it.isAlive || it.isRemoved || it is Enemy ||
                    (it is Player && (it.isCreative || it.isSpectator)) ||
                    distanceToSqr(it) > HATE_RANGE * HATE_RANGE
            }
            if (target == null) {
                target = preferredTargets(currentHated()).firstOrNull()
            }

            combatBrain()
            if (circleState != CircleState.DYING && circleState != CircleState.PHASE_TRANSITION) {
                tickSiphonState()
                tickTitanUpdraftParticles()
                tickArenaUpdraftColumns()
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
        val p3 = phase3Reveal
        var maxR = 12.0
        for (ring in rings) {
            if (!ring.isVisible(phase, p2, p3)) continue
            val r = ring.effectiveRadius(scale, df, p2, p3)
            if (r > maxR) maxR = r
        }
        return maxR
    }

    fun canStandOnRings(): Boolean =
        !isDormantState() &&
            phase >= 2 &&
            phase2Reveal > 0.35F &&
            circleState != CircleState.SHORT_CIRCUIT &&
            circleState != CircleState.DYING &&
            circleState != CircleState.PHASE_TRANSITION

    fun ringInnerRadius(): Double = rings[0].effectiveRadius(totalScale(), dormantFold, phase2Reveal, phase3Reveal)

    fun safeFlightDir(desired: Vec3, lookAhead: Double): Vec3? = flightNav.safeFlightDir(desired, lookAhead)

    fun moveSpeedFactor(): Float = 1.0F + (currentAnger() - 1.0F) * 0.3F

    fun fireRateFactor(): Float = 1.0F + (currentAnger() - 1.0F) * 0.6F

    fun isDormantState(): Boolean = entityData.get(DORMANT)

    fun isSiphoning(): Boolean = entityData.get(SIPHONING)

    fun setSiphoning(enabled: Boolean) {
        entityData.set(SIPHONING, enabled)
        if (!enabled) {
            backlashByPlayer.clear()
            lastBacklashTickByPlayer.clear()
        }
    }

    fun isPlaneSlicing(): Boolean = entityData.get(SLICE_ACTIVE)

    fun slicePlaneLocalY(): Float = entityData.get(SLICE_Y_OFFSET)

    fun slicePlaneLocalOrigin(): Vec3 = Vec3(
        entityData.get(SLICE_OX).toDouble(),
        entityData.get(SLICE_OY).toDouble(),
        entityData.get(SLICE_OZ).toDouble(),
    )

    fun slicePlaneNormal(): Vec3 {
        val n = Vec3(
            entityData.get(SLICE_NX).toDouble(),
            entityData.get(SLICE_NY).toDouble(),
            entityData.get(SLICE_NZ).toDouble(),
        )
        return if (n.lengthSqr() > 1.0E-6) n.normalize() else Vec3(0.0, 1.0, 0.0)
    }

    fun setPlaneSlicing(active: Boolean, localOrigin: Vec3, normal: Vec3) {
        val safeNormal = if (normal.lengthSqr() > 1.0E-6) normal.normalize() else Vec3(0.0, 1.0, 0.0)
        entityData.set(SLICE_ACTIVE, active)
        entityData.set(SLICE_Y_OFFSET, localOrigin.y.toFloat())
        entityData.set(SLICE_OX, localOrigin.x.toFloat())
        entityData.set(SLICE_OY, localOrigin.y.toFloat())
        entityData.set(SLICE_OZ, localOrigin.z.toFloat())
        entityData.set(SLICE_NX, safeNormal.x.toFloat())
        entityData.set(SLICE_NY, safeNormal.y.toFloat())
        entityData.set(SLICE_NZ, safeNormal.z.toFloat())
    }

    fun setPlaneSlicing(active: Boolean, localYOffset: Float) {
        setPlaneSlicing(active, Vec3(0.0, localYOffset.toDouble(), 0.0), Vec3(0.0, 1.0, 0.0))
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
            return totalScale() * when {
                phase >= 3 -> 2.6F
                phase >= 2 -> 2.1F
                else -> 1.5F
            }
        }
        if (part is pub.pigeon.yggdyy.hexmob.content.ur_circle.subentities.RingPlatformPart) {
            return totalScale() * 0.9F
        }
        val track = rings.getOrNull(part.ringIndex)
        val mul = track?.partScale ?: 1.0F
        val nodeBoost = when {
            part is SlatePart && part.symbolType != SlateSymbolType.NONE -> if (part.tamperable) 1.38F else 1.24F
            isActiveWaveNode(part) -> 1.28F
            else -> 1.0F
        }
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
                circleState == CircleState.PHASE_TRANSITION && phase == 2 && stateTicks < PHASE_BURST_TICK -> 0.0F
                else -> stepToward(phase2Reveal, 1.0F, 0.032F)
            }
            phase3Reveal = when {
                phase < 3 -> 0.0F
                circleState == CircleState.PHASE_TRANSITION && phase == 3 && stateTicks < PHASE_BURST_TICK -> 0.0F
                else -> stepToward(phase3Reveal, 1.0F, 0.028F)
            }
            foldProgress = stepToward(foldProgress, targetFoldProgress, 0.04F)

            if (circleState == CircleState.CRUISE && stability < MAX_STABILITY) {
                stabilityRegenTimer++
                if (stabilityRegenTimer >= 30) {
                    stabilityRegenTimer = 0
                    stability += 1
                    hudSyncDirty = true
                }
            } else {
                stabilityRegenTimer = 0
            }

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
        val p3 = phase3Reveal
        val fp = foldProgress
        val scale = totalScale()
        val canStand = canStandOnRings()

        for (track in rings) {
            if (!track.isVisible(curPhase, p2, p3)) {
                for (p in track.parts) p.active = false
                for (d in track.deckParts) d.active = false
                continue
            }
            val baseN = track.effectiveNormal(fp, df)
            val radAxis = track.radialAxis(baseN)
            val effN = if (tumbling && tumbleDeg != 0.0F) baseN.rotateDA(tumbleDeg, radAxis).normalize() else baseN
            val rLen = track.effectiveRadius(scale, df, p2, p3)
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
                val nominalPos = origin.add(delta)
                if (p is SlatePart) {
                    if (!level().isClientSide && p.symbolType != SlateSymbolType.NONE) {
                        tickSymbolicSlatePosition(p, nominalPos, face, origin)
                    } else if (level().isClientSide) {
                        syncClientSlateFromHud(p, nominalPos, face, origin)
                    } else {
                        p.changeState(nominalPos, face)
                    }
                } else {
                    p.changeState(nominalPos, face)
                }
            }

            val deckCount = track.deckParts.size
            if (deckCount > 0) {
                val decksActive = canStand && rLen > 4.0
                val deckTopAlongN = when (track.ringIndex) {
                    2 -> 1.66 * scale
                    1 -> 0.11 * scale
                    else -> 0.0
                }
                for (idx in 0 until deckCount) {
                    val deck = track.deckParts[idx]
                    deck.active = decksActive
                    if (!decksActive) continue
                    deck.applyScale(partVisualScale(deck))
                    val deg = baseRot + (idx.toFloat() / deckCount) * 360F
                    val delta = radAxis.rotateDA(deg, effN).scale(rLen)
                    val tangent = effN.cross(delta).let { if (it.lengthSqr() > 1.0E-6) it.normalize() else Vec3(0.0, 0.0, 1.0) }
                    val surfacePos = origin.add(delta).add(effN.scale(deckTopAlongN))
                    deck.changeState(surfacePos.add(0.0, -deck.bbHeight * 0.5, 0.0), tangent)
                }
            }
        }
        if (canStand) {
            if (!level().isClientSide) {
                UrCircleCarryAlong.applyServerCarryAlong(this)
            } else {
                UrCircleCarryAlong.carryClientPlayerOnPartsUpdate(this)
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
                if (!part.active || part.walkPlatform || part.posNow == Vec3.ZERO) continue
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
        val sourceRing = if (phase >= 2 && rings[2].isVisible(phase, phase2Reveal, phase3Reveal)) rings[2].parts else ecliptic
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
        val prevPlayers = be.players.toList()
        for (player in serverLevel.players()) {
            if (player.distanceToSqr(this) <= BOSS_BAR_RANGE * BOSS_BAR_RANGE) {
                be.addPlayer(player)
            } else {
                be.removePlayer(player)
            }
        }
        if (be.players.any { it !in prevPlayers }) {
            hudSyncDirty = true
        }
        syncHudPacketToWatchers(be, serverLevel)
    }

    private fun syncHudPacketToWatchers(be: ServerBossEvent, serverLevel: ServerLevel) {
        if (!hudSyncDirty && tickCount % 4 != 0) return
        hudSyncDirty = false
        val entries = activeSymbolicSlates.take(4).map { slate ->
            val hasCustomPos = (slate.deployedPos != null || slate.detached) && slate.posNow != Vec3.ZERO
            SlateHudEntry(
                globalIndex = slate.globalIndex,
                symbolType = slate.symbolType.id,
                tamperable = slate.tamperable,
                rebounded = slate.rebounded,
                progress = slate.skillProgress.coerceIn(0F, 1F),
                hasCustomPos = hasCustomPos,
                posX = if (hasCustomPos) slate.posNow.x else 0.0,
                posY = if (hasCustomPos) slate.posNow.y else 0.0,
                posZ = if (hasCustomPos) slate.posNow.z else 0.0,
            )
        }
        val pkt = MsgUrCircleHudS2C(
            entityId = id,
            bossEventUuid = be.id,
            phase = phase,
            circleState = circleState.ordinal,
            stability = stability,
            shieldPct = coreShieldPct(),
            slates = entries,
        )
        val maxSq = BOSS_BAR_RANGE * BOSS_BAR_RANGE
        for (player in serverLevel.players()) {
            if (player.distanceToSqr(this) <= maxSq) {
                pkt.sendToPlayer(player)
            }
        }
    }

    override fun remove(reason: Entity.RemovalReason) {
        discardCircleBeam()
        discardAnchors()
        bossEvent?.removeAllPlayers()
        super.remove(reason)
    }

    private fun cancelActiveSkills() {
        currentSkill?.onChannelCancel(this)
        beamSkill?.let {
            it.onBeamEnd(this)
            it.onChannelCancel(this)
        }
        grindSkill?.let {
            it.onGrindEnd(this)
            it.onChannelCancel(this)
        }
        currentSkill = null
        beamSkill = null
        grindSkill = null
    }

    override fun die(source: DamageSource) {
        if (circleState == CircleState.DYING) return
        circleState = CircleState.DYING
        stateTicks = 0
        cancelActiveSkills()
        targetFoldProgress = 0.0F
        mediaWave.clear(this)
        setSiphoning(false)
        setPlaneSlicing(false, 0.0F)
        discardCircleBeam()
        discardAnchors()
        bossEvent?.removeAllPlayers()
        bossEvent = null
        val minionBox = getBoundingBox().inflate(64.0)
        for (s in level().getEntitiesOfClass(UrCircleServant::class.java, minionBox)) {
            if (s.owner === this) s.remove(Entity.RemovalReason.KILLED)
        }
        for (g in level().getEntitiesOfClass(Mob::class.java, minionBox)) {
            if (g is HexMobGuard && g.isSummonedBy(this)) g.remove(Entity.RemovalReason.KILLED)
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

    /** 二/三阶段外环下方 4 道旋转风柱上升气流：赋予非友方实体稳定浮升与顶缘缓降，支持空中反复登环。 */
    private fun tickArenaUpdraftColumns() {
        if (phase < 2 || !canStandOnRings() || dormant || circleState == CircleState.DYING || circleState == CircleState.SHORT_CIRCUIT) {
            return
        }
        val origin = beamOrigin()
        val lvl = level()
        val colRadius = ringOuterRadius() * 0.85
        if (colRadius < 8.0) return
        val baseAngle = Math.toRadians(ringRotation(2).toDouble())
        val maxY = origin.y + 10.0
        val horizR = 5.2
        val horizRSqr = horizR * horizR
        val emitParticles = tickCount % 2 == 0

        for (k in 0 until 4) {
            val ang = baseAngle + k * (Math.PI * 0.5)
            val cx = origin.x + cos(ang) * colRadius
            val cz = origin.z + sin(ang) * colRadius
            val surfaceY = lvl.getHeight(
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                net.minecraft.util.Mth.floor(cx),
                net.minecraft.util.Mth.floor(cz),
            )
            val colMinY = (if (surfaceY > lvl.minBuildHeight) surfaceY.toDouble() - 1.0 else origin.y - 96.0)
                .coerceAtLeast(lvl.minBuildHeight.toDouble())
            val box = AABB(cx - horizR, colMinY, cz - horizR, cx + horizR, maxY, cz + horizR)
            for (ent in lvl.getEntitiesOfClass(LivingEntity::class.java, box)) {
                if (ent === this || isUrCircleAlly(ent) || !ent.isAlive || ent.isSpectator) continue
                if (ent is Player && ent.abilities.flying) continue
                val dx = ent.x - cx
                val dz = ent.z - cz
                if (dx * dx + dz * dz > horizRSqr) continue

                val nextVy = (ent.deltaMovement.y + 0.16).coerceIn(0.45, 0.78)
                ent.setDeltaMovement(ent.deltaMovement.x * 0.96, nextVy, ent.deltaMovement.z * 0.96)
                ent.fallDistance = 0.0F
                ent.hurtMarked = true
                if (ent.y >= origin.y - 4.0) {
                    ent.addEffect(MobEffectInstance(MobEffects.SLOW_FALLING, 50, 0, true, false))
                }
            }
            if (emitParticles) {
                val heightSpan = (maxY - colMinY).coerceAtLeast(10.0)
                for (step in 0 until 7) {
                    val frac = (step + random.nextDouble() * 0.7) / 7.0
                    val swirlAng = tickCount * 0.35 + k * 1.57 + step * 1.1
                    val px = cx + cos(swirlAng) * (1.2 + random.nextDouble() * 2.4)
                    val py = colMinY + frac * heightSpan
                    val pz = cz + sin(swirlAng) * (1.2 + random.nextDouble() * 2.4)
                    val pType = if (step % 2 == 0) ParticleTypes.CLOUD else UrCircleHexVfx.conjure(UrCircleHexVfx.SIPHON_CYAN)
                    spawnParticle(lvl, pType, px, py, pz, 0.0, 0.36, 0.0, UrCircleHexVfx.DEFAULT_RANGE)
                }
            }
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
                        clearAllSymbolicSlates()
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
                        clearAllSymbolicSlates()
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
                        clearAllSymbolicSlates()
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
        skillLastCastTick[skill.name] = tickCount
        recentSkillNames.addLast(skill.name)
        while (recentSkillNames.size > 2) {
            recentSkillNames.removeFirst()
        }
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
            stability = MAX_STABILITY
            hudSyncDirty = true
            circleState = CircleState.CRUISE
            stateTicks = 0
            // 断路易伤完整结束后再结算跨血线转阶段，避免吞掉刚打出的 5 秒易伤窗口
            checkPhaseThresholds()
        }
    }

    fun beginPhase2Transition() {
        if (phase >= 2 || circleState == CircleState.DYING) return
        phase = 2
        cancelActiveSkills()
        clearAllSymbolicSlates()
        targetFoldProgress = 1.0F
        mediaWave.clear(this)
        setSiphoning(false)
        setPlaneSlicing(false, 0.0F)
        discardCircleBeam()
        discardAnchors()
        circleState = CircleState.PHASE_TRANSITION
        stateTicks = 0
        hudSyncDirty = true
        UrCircleHexVfx.playRingSound(level(), beamOrigin(), ringOuterRadius(), SoundEvents.BEACON_POWER_SELECT, 3.0F, 0.65F)
    }

    fun beginPhase3Transition() {
        if (phase >= 3 || circleState == CircleState.DYING) return
        if (phase < 2) phase2Reveal = 1.0F
        phase = 3
        cancelActiveSkills()
        clearAllSymbolicSlates()
        targetFoldProgress = 1.0F
        mediaWave.clear(this)
        setSiphoning(false)
        setPlaneSlicing(false, 0.0F)
        discardCircleBeam()
        discardAnchors()
        circleState = CircleState.PHASE_TRANSITION
        stateTicks = 0
        hudSyncDirty = true
        UrCircleHexVfx.playRingSound(level(), beamOrigin(), ringOuterRadius(), SoundEvents.BEACON_POWER_SELECT, 3.5F, 0.55F)
    }

    fun triggerShortCircuit() {
        if (level().isClientSide || circleState == CircleState.DYING) return
        cancelActiveSkills()
        clearAllSymbolicSlates()
        stability = 0
        hudSyncDirty = true
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
    }

    private fun checkPhaseThresholds() {
        if (!isAlive || health <= 0.0F || circleState == CircleState.DYING) return
        if (phase < 3 && health <= maxHealth * PHASE3_HP_RATIO) {
            beginPhase3Transition()
        } else if (phase < 2 && health <= maxHealth * PHASE2_HP_RATIO) {
            beginPhase2Transition()
        }
    }

    // ---- M1 象征石板参数化与有条件篡改闭环 ----

    fun getSymbolicSlates(): List<SlatePart> = activeSymbolicSlates

    fun findSymbolicSlate(type: SlateSymbolType): SlatePart? =
        activeSymbolicSlates.firstOrNull { it.symbolType == type && it.active }

    fun deploySymbolicSlate(
        type: SlateSymbolType,
        targetPos: Vec3? = null,
        preferredRing: Int = -1,
    ): SlatePart? {
        if (type == SlateSymbolType.NONE) return null
        val ringCandidates = when {
            preferredRing >= 0 -> listOfNotNull(rings.getOrNull(preferredRing)?.takeIf { it.isVisible(phase, phase2Reveal, phase3Reveal) })
            phase >= 3 -> listOfNotNull(rings.getOrNull(4), rings.getOrNull(2), rings.getOrNull(1))
            phase >= 2 -> listOfNotNull(rings.getOrNull(2), rings.getOrNull(1))
            else -> listOfNotNull(rings.getOrNull(1))
        }
        val slate = ringCandidates
            .flatMap { it.parts }
            .filterIsInstance<SlatePart>()
            .firstOrNull { it.active && it.symbolType == SlateSymbolType.NONE }
            ?: cachedAllParts.filterIsInstance<SlatePart>().firstOrNull { it.active && it.symbolType == SlateSymbolType.NONE }
            ?: return null

        slate.assignSymbol(type, targetPos)
        activeSymbolicSlates.add(slate)
        hudSyncDirty = true
        return slate
    }

    fun clearAllSymbolicSlates() {
        if (activeSymbolicSlates.isEmpty()) return
        for (s in activeSymbolicSlates) {
            s.clearSymbol()
        }
        activeSymbolicSlates.clear()
        hudSyncDirty = true
    }

    fun ampDamageMultiplier(): Float {
        val amp = activeSymbolicSlates.firstOrNull { it.symbolType == SlateSymbolType.AMP && it.active && !it.rebounded }
            ?: return 1.0F
        if (amp.tamperable) return 1.05F
        val dist = if (amp.posNow == Vec3.ZERO) 15.0 else amp.posNow.distanceTo(beamOrigin())
        val prox = (1.0 - (dist / (32.0 * totalScale())).coerceIn(0.0, 1.0)).toFloat()
        return 1.15F + 0.25F * prox
    }

    fun coreShieldPct(): Int {
        if (circleState == CircleState.SHORT_CIRCUIT || circleState == CircleState.DYING) return 0
        val base = when {
            phase >= 3 -> 45
            phase >= 2 -> 30
            else -> 0
        }
        val amp = activeSymbolicSlates.firstOrNull {
            it.symbolType == SlateSymbolType.AMP && it.active && !it.tamperable && !it.rebounded
        }
        val ampBonus = if (amp != null) 15 else 0
        return (base + ampBonus).coerceIn(0, 75)
    }

    fun reduceStability(delta: Int, triggerInstantly: Boolean = true) {
        if (level().isClientSide || circleState == CircleState.SHORT_CIRCUIT || circleState == CircleState.DYING) return
        stability = (stability - delta).coerceIn(0, MAX_STABILITY)
        hudSyncDirty = true
        if (triggerInstantly && stability <= 0) {
            triggerShortCircuit()
        }
    }

    private fun tickSymbolicSlatePosition(slate: SlatePart, nominalPos: Vec3, nominalDir: Vec3, origin: Vec3) {
        slate.deployTicks++
        val total = (currentSkill?.channelTicks ?: beamSkill?.beamTicks(this) ?: 60).coerceAtLeast(1)
        slate.skillProgress = (stateTicks.toFloat() / total).coerceIn(0.0F, 1.0F)

        val currentPos = if (slate.posNow == Vec3.ZERO) nominalPos else slate.posNow
        if (slate.lastNominalPos == Vec3.ZERO) slate.lastNominalPos = nominalPos
        val orbitVel = nominalPos.subtract(slate.lastNominalPos)
        slate.lastNominalPos = nominalPos
        slate.orbitSpeed = orbitVel.length()

        if (slate.slowdownTicks > 0) {
            slate.slowdownTicks--
        }

        // 1. 检测玩家施加的外部冲量（OpAddMotion / 物理击退）
        val ext = slate.deltaMovement
        if (ext.lengthSqr() > 0.004) {
            slate.setDeltaMovement(Vec3.ZERO)
            slate.lastImpulseTick = tickCount
            val opposing = orbitVel.dot(ext) < -0.015 && orbitVel.add(ext).length() < 0.35
            if (!slate.tamperable && (opposing || slate.slowdownTicks > 0)) {
                unlockSlateTamperable(slate, currentPos, orbitVel.add(ext).scale(0.35))
            } else if (!slate.tamperable && ext.length() >= 0.28) {
                slate.detached = true
                slate.detachedPos = currentPos
                slate.detachedVel = ext.scale(1.35)
                if (slate.detachedPos.distanceTo(nominalPos) >= 3.5 || slate.detachedPos.distanceTo(origin) >= ringInnerRadius() + 4.0) {
                    unlockSlateTamperable(slate, slate.detachedPos, slate.detachedVel)
                }
            } else if (slate.tamperable && !slate.rebounded) {
                if (!slate.detached) {
                    slate.detached = true
                    slate.detachedPos = currentPos
                    slate.detachedVel = ext.scale(1.65)
                } else {
                    slate.detachedVel = slate.detachedVel.scale(0.35).add(ext.scale(1.65))
                }
            }
        }

        // 2. 延时开放可篡改态但不强制 detached 冻结，保持追踪技能持续跟随目标直到玩家真实干扰
        if (!slate.tamperable && !slate.rebounded && slate.deployedPos != null && slate.deployTicks >= 24) {
            if (slate.symbolType != SlateSymbolType.AMP || currentPos.distanceTo(origin) > ringInnerRadius() + 3.0) {
                unlockSlateTamperable(slate, currentPos, Vec3.ZERO, detach = false)
            }
        }

        // 3. 计算最终渲染与碰撞坐标
        val finalPos: Vec3
        if (slate.detached) {
            slate.detachedPos = slate.detachedPos.add(slate.detachedVel)
            slate.detachedVel = slate.detachedVel.scale(0.92)
            finalPos = slate.detachedPos
            if (!slate.tamperable && (finalPos.distanceTo(nominalPos) >= 4.0 || slate.slowdownTicks > 0)) {
                unlockSlateTamperable(slate, finalPos, slate.detachedVel)
            }
            // 基础档：处于可篡改态且被轰向核心，碰撞核心触发反噬
            val coreHitR = (earth.bbWidth * 0.55 + 3.2).coerceAtLeast(4.2)
            val toCore = origin.subtract(finalPos)
            if (slate.tamperable && !slate.rebounded && toCore.length() <= coreHitR &&
                (slate.detachedVel.length() >= 0.20 || toCore.normalize().dot(slate.detachedVel.normalize()) > 0.15)
            ) {
                onSlateReboundCore(slate)
            }
        } else {
            val anchor = slate.deployedPos
            if (anchor != null) {
                val toAnchor = anchor.subtract(currentPos)
                val step = toAnchor.length().coerceAtMost(1.35)
                finalPos = if (toAnchor.lengthSqr() > 0.04) currentPos.add(toAnchor.normalize().scale(step)) else anchor
            } else {
                finalPos = nominalPos
            }
        }

        if (slate.tamperable && !slate.rebounded && tickCount % 3 == 0) {
            UrCircleHexVfx.spawnConjure(
                level(),
                UrCircleHexVfx.SIPHON_CYAN,
                finalPos.x + (random.nextDouble() - 0.5) * 0.8,
                finalPos.y + (random.nextDouble() - 0.5) * 0.8,
                finalPos.z + (random.nextDouble() - 0.5) * 0.8,
                0.0, 0.06, 0.0,
            )
        }

        val lookDir = if (slate.deployedPos != null || slate.detached) {
            origin.subtract(finalPos).let { if (it.lengthSqr() > 1.0E-4) it.normalize() else nominalDir }
        } else {
            nominalDir
        }
        slate.changeState(finalPos, lookDir)
    }

    private fun unlockSlateTamperable(slate: SlatePart, pos: Vec3, vel: Vec3, detach: Boolean = true) {
        if (slate.tamperable) return
        slate.tamperable = true
        if (detach) {
            slate.detached = true
            slate.detachedPos = pos
            slate.detachedVel = vel
        }
        hudSyncDirty = true
        UrCircleHexVfx.playRingSound(level(), pos, 32.0, HexSounds.ADD_TO_PATTERN, 1.8F, 1.45F)
        UrCircleHexVfx.spawnRingArc(level(), beamOrigin(), pos, UrCircleHexVfx.SIPHON_CYAN, steps = 10)
    }

    /** 基础档：将可篡改石板轰回核心，伤害本体并大幅削减稳态。 */
    fun onSlateReboundCore(slate: SlatePart) {
        if (level().isClientSide || slate.rebounded || circleState == CircleState.DYING) return
        slate.rebounded = true
        slate.tamperable = false
        slate.detachedVel = Vec3.ZERO
        hudSyncDirty = true

        val origin = beamOrigin()
        val backlashDmg = when (slate.symbolType) {
            SlateSymbolType.TRIGGER -> 32.0F
            SlateSymbolType.AIM -> 22.0F
            else -> 16.0F
        }
        val stabilityCost = when (slate.symbolType) {
            SlateSymbolType.TRIGGER -> 75
            SlateSymbolType.AIM -> 55
            else -> 45
        }

        UrCircleHexVfx.playRingSound(level(), origin, ringOuterRadius(), SoundEvents.GLASS_BREAK, 2.6F, 1.1F)
        UrCircleHexVfx.playRingSound(level(), origin, ringOuterRadius(), HexSounds.CAST_THOTH, 2.4F, 1.3F)
        for (k in 0 until 24) {
            UrCircleHexVfx.spawnConjure(
                level(),
                UrCircleHexVfx.SIPHON_CYAN,
                origin.x, origin.y, origin.z,
                (random.nextDouble() - 0.5) * 0.65,
                (random.nextDouble() - 0.5) * 0.65,
                (random.nextDouble() - 0.5) * 0.65,
            )
        }

        internalHealthWrite = true
        try {
            setHealth(health - backlashDmg)
        } finally {
            internalHealthWrite = false
        }
        if (health <= 0.0F) {
            die(level().damageSources().magic())
            return
        }
        reduceStability(stabilityCost, triggerInstantly = true)
        if (circleState != CircleState.SHORT_CIRCUIT) {
            checkPhaseThresholds()
        }
    }

    private fun syncClientSlateFromHud(slate: SlatePart, nominalPos: Vec3, nominalDir: Vec3, origin: Vec3) {
        val entry = UrCircleHudTracker.getSlateEntry(id, slate.globalIndex)
        if (entry != null) {
            slate.symbolType = SlateSymbolType.byId(entry.symbolType)
            slate.tamperable = entry.tamperable
            slate.rebounded = entry.rebounded
            slate.applyScale(partVisualScale(slate))
            if (entry.hasCustomPos) {
                val targetPos = Vec3(entry.posX, entry.posY, entry.posZ)
                val nextPos = if (slate.posNow != Vec3.ZERO && slate.posNow.distanceToSqr(targetPos) < 16.0 * 16.0) {
                    slate.posNow.lerp(targetPos, 0.45)
                } else {
                    targetPos
                }
                val lookDir = origin.subtract(nextPos).let {
                    if (it.lengthSqr() > 1.0E-4) it.normalize() else nominalDir
                }
                slate.changeState(nextPos, lookDir)
            } else {
                slate.changeState(nominalPos, nominalDir)
            }
        } else {
            if (slate.symbolType != SlateSymbolType.NONE) {
                slate.clearSymbol()
                slate.applyScale(partVisualScale(slate))
            }
            slate.changeState(nominalPos, nominalDir)
        }
    }

    /**
     * 进阶档：玩家使用 OpTheCoolerWrite 覆写可篡改石板图案。
     * - 写入原始图案的镜像逆解图案：触发完整断路反噬（高额贯穿伤害 + 5 紫晶媒质波虹吸 + 立即断路）；
     * - 写入其他任意图案（盲目阻塞）：仅作干扰（消耗 45 稳态并打断当前吟唱，无核心贯穿伤与媒质虹吸）。
     */
    fun onSlateHexOverwrite(slate: SlatePart, writtenIota: Iota) {
        if (level().isClientSide || slate.rebounded || circleState == CircleState.DYING) return
        val expectedPattern = slate.symbolType.hexPattern
        slate.rebounded = true
        slate.tamperable = false
        hudSyncDirty = true

        val origin = beamOrigin()
        val slatePos = if (slate.posNow != Vec3.ZERO) slate.posNow else origin
        if (SlateSymbolType.isCounterSolution(expectedPattern, writtenIota)) {
            UrCircleHexVfx.spawnRingArc(level(), slatePos, origin, UrCircleHexVfx.SIPHON_CYAN, steps = 16)
            UrCircleHexVfx.playRingSound(level(), origin, ringOuterRadius(), HexSounds.SPELL_CIRCLE_FAIL, 2.8F, 1.35F)
            UrCircleHexVfx.playRingSound(level(), origin, ringOuterRadius(), HexSounds.CAST_HERMES, 2.4F, 1.25F)

            // 为周围玩家携带的媒质容器补充媒质作为“虹吸 UrCircleMediaWave 媒质波”收益
            val serverLevel = level() as? ServerLevel
            if (serverLevel != null) {
                val box = AABB(origin, origin).inflate(48.0)
                for (player in serverLevel.getEntitiesOfClass(ServerPlayer::class.java, box)) {
                    var remaining = MediaConstants.CRYSTAL_UNIT * 5L
                    for (stack in listOf(player.mainHandItem, player.offhandItem)) {
                        if (stack.isEmpty || remaining <= 0L) continue
                        val holder = IXplatAbstractions.INSTANCE.findMediaHolder(stack) ?: continue
                        if (holder.canRecharge()) {
                            val accepted = holder.insertMedia(remaining, false)
                            remaining -= accepted
                        }
                    }
                    UrCircleHexVfx.spawnRingArc(serverLevel, origin, player.eyePosition, UrCircleHexVfx.SIPHON_CYAN, steps = 12)
                }
            }

            internalHealthWrite = true
            try {
                setHealth(health - HEX_OVERWRITE_DAMAGE)
            } finally {
                internalHealthWrite = false
            }
            if (health <= 0.0F) {
                die(level().damageSources().magic())
                return
            }
            stability = 0
            triggerShortCircuit()
        } else {
            // 盲写任意图案：仅制造算盘卡顿与局部阻断，削弱 45 稳态并打断当前吟唱，但不授予虹吸媒质与高额反噬伤害
            for (k in 0 until 14) {
                UrCircleHexVfx.spawnConjure(
                    level(),
                    UrCircleHexVfx.BEAM_LAVENDER,
                    slatePos.x + (random.nextDouble() - 0.5) * 1.1,
                    slatePos.y + (random.nextDouble() - 0.5) * 1.1,
                    slatePos.z + (random.nextDouble() - 0.5) * 1.1,
                    (random.nextDouble() - 0.5) * 0.25,
                    (random.nextDouble() - 0.5) * 0.25,
                    (random.nextDouble() - 0.5) * 0.25,
                )
            }
            UrCircleHexVfx.spawnRingArc(level(), slatePos, origin, UrCircleHexVfx.BEAM_LAVENDER, steps = 10)
            UrCircleHexVfx.playRingSound(level(), slatePos, ringOuterRadius(), HexSounds.ABACUS, 2.2F, 1.25F)
            reduceStability(45, triggerInstantly = true)
            if (circleState == CircleState.CHANNELING) {
                interruptChannel()
                skillCooldown = SKILL_COOLDOWN / 2
            }
        }
    }

    fun onProtectedSlateWriteAttempt(slate: SlatePart) {
        if (level().isClientSide) return
        val p = if (slate.posNow != Vec3.ZERO) slate.posNow else beamOrigin()
        UrCircleHexVfx.playRingSound(level(), p, 24.0, HexSounds.CAST_FAILURE, 1.6F, 1.3F)
        for (k in 0 until 8) {
            UrCircleHexVfx.spawnConjure(
                level(),
                UrCircleHexVfx.SHORT_CIRCUIT_RED,
                p.x, p.y, p.z,
                (random.nextDouble() - 0.5) * 0.25,
                (random.nextDouble() - 0.5) * 0.25,
                (random.nextDouble() - 0.5) * 0.25,
            )
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
            if (!ring.isVisible(phase, phase2Reveal, phase3Reveal)) continue
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
        val allUsable = skills.filter { phase >= it.minPhase && it.canUse(this) }
        if (allUsable.isEmpty()) return null
        val offCooldown = allUsable.filter { s ->
            val last = skillLastCastTick[s.name]
            last == null || (tickCount - last) >= s.perSkillCooldownTicks
        }
        val pool = if (offCooldown.isNotEmpty()) offCooldown else allUsable
        val weighted = pool.map { s ->
            val base = s.weight.toDouble().coerceAtLeast(1.0)
            val recencyMul = when {
                recentSkillNames.lastOrNull() == s.name -> 0.2
                s.name in recentSkillNames -> 0.5
                else -> 1.0
            }
            s to (base * recencyMul)
        }
        val total = weighted.sumOf { it.second }
        if (total <= 0.0) return pool.random()
        var r = random.nextDouble() * total
        for ((s, w) in weighted) {
            r -= w
            if (r <= 0.0) return s
        }
        return pool.last()
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
        nbt.putBoolean("hexmob:WokenOnce", wokenOnce)
        nbt.putInt("hexmob:Phase", phase)
        nbt.putInt("hexmob:Stability", stability)
        nbt.putString("hexmob:CircleState", circleState.name)
        nbt.putInt("hexmob:StateTicks", stateTicks)
        homePos?.let { home ->
            nbt.putDouble("hexmob:HomeX", home.x)
            nbt.putDouble("hexmob:HomeY", home.y)
            nbt.putDouble("hexmob:HomeZ", home.z)
        }
    }

    override fun readAdditionalSaveData(compound: CompoundTag) {
        dormant = compound.getBoolean("hexmob:Dormant")
        entityData.set(DORMANT, dormant)
        dormantFold = if (dormant) 1.0F else 0.0F
        internalHealthWrite = true
        super.readAdditionalSaveData(compound)
        internalHealthWrite = false
        val savedPhase = if (compound.contains("hexmob:Phase")) {
            compound.getInt("hexmob:Phase").coerceIn(1, 3)
        } else if (health <= maxHealth * PHASE3_HP_RATIO) {
            3
        } else if (health <= maxHealth * PHASE2_HP_RATIO) {
            2
        } else {
            1
        }
        phase = savedPhase
        phase2Reveal = if (savedPhase >= 2) 1.0F else 0.0F
        phase3Reveal = if (savedPhase >= 3) 1.0F else 0.0F
        stability = if (compound.contains("hexmob:Stability")) {
            compound.getInt("hexmob:Stability").coerceIn(0, MAX_STABILITY)
        } else {
            MAX_STABILITY
        }
        if (compound.contains("hexmob:HomeX") && compound.contains("hexmob:HomeY") && compound.contains("hexmob:HomeZ")) {
            homePos = Vec3(
                compound.getDouble("hexmob:HomeX"),
                compound.getDouble("hexmob:HomeY"),
                compound.getDouble("hexmob:HomeZ"),
            )
        }
        wokenOnce = if (compound.contains("hexmob:WokenOnce")) {
            compound.getBoolean("hexmob:WokenOnce")
        } else {
            !dormant && (savedPhase > 1 || health < maxHealth)
        }
        val savedState = if (compound.contains("hexmob:CircleState")) {
            runCatching { CircleState.valueOf(compound.getString("hexmob:CircleState")) }.getOrDefault(CircleState.CRUISE)
        } else {
            CircleState.CRUISE
        }
        val savedStateTicks = if (compound.contains("hexmob:StateTicks")) {
            compound.getInt("hexmob:StateTicks").coerceAtLeast(0)
        } else {
            0
        }
        if (health <= 0.0F || savedState == CircleState.DYING) {
            dormant = false
            wokenOnce = true
            entityData.set(DORMANT, false)
            dormantFold = 0.0F
            if (health > 0.0F) {
                internalHealthWrite = true
                try {
                    setHealth(0.0F)
                } finally {
                    internalHealthWrite = false
                }
            }
            circleState = CircleState.DYING
            stateTicks = if (savedState == CircleState.DYING) savedStateTicks else 0
            targetFoldProgress = if (stateTicks >= DEATH_COLLAPSE_TICK) 1.0F else 0.0F
        } else {
            when (savedState) {
                CircleState.PHASE_TRANSITION, CircleState.SHORT_CIRCUIT -> {
                    circleState = savedState
                    stateTicks = savedStateTicks
                    if (savedState == CircleState.PHASE_TRANSITION && savedStateTicks < PHASE_BURST_TICK) {
                        if (savedPhase == 2) phase2Reveal = 0.0F
                        if (savedPhase == 3) phase3Reveal = 0.0F
                        targetFoldProgress = 1.0F
                    } else {
                        targetFoldProgress = 0.0F
                    }
                }
                else -> {
                    circleState = CircleState.CRUISE
                    stateTicks = 0
                    targetFoldProgress = 0.0F
                }
            }
        }
    }

    override fun getAllParts(): List<UrCirclePart> = cachedAllParts
    override fun getPartsBoundingBox(): AABB {
        val outerR = ringOuterRadius()
        val r = when {
            phase >= 3 -> maxOf(outerR * 2.8, 440.0)
            phase >= 2 -> maxOf(outerR * 1.8, 240.0)
            else -> maxOf(outerR + 24.0, 96.0)
        }
        val yUp = when {
            phase >= 3 -> maxOf(outerR * 2.8, 400.0)
            phase >= 2 -> maxOf(outerR * 1.6, 190.0)
            else -> r
        }
        val yDown = when {
            phase >= 3 -> maxOf(outerR * 1.6, 260.0)
            phase >= 2 -> maxOf(outerR * 1.4, 250.0)
            else -> r * 0.85
        }
        val c = position().add(0.0, bbHeight * 0.5, 0.0)
        return AABB(c.x - r, c.y - yDown, c.z - r, c.x + r, c.y + yUp, c.z + r)
    }
    override fun getBoundingBoxForCulling(): AABB = getPartsBoundingBox()
    override fun shouldRenderAtSqrDistance(distance: Double): Boolean {
        val maxDist = when {
            phase >= 3 -> 680.0
            phase >= 2 -> 480.0
            else -> 300.0
        }
        return distance <= maxDist * maxDist
    }
    override fun shouldRecord(): Boolean = isAlive && !isRemoved

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
            if (!isAlive || isDeadOrDying) return false
            if (source.entity === this || isUrCircleAlly(source.entity) || isUrCircleAlly(source.directEntity)) return false
            if (amount <= 0.0F || isInvulnerableTo(source) || source.`is`(HexDamageTypes.OVERCAST)) return false

            val isIndirectSource = source.entity is QuenchAllay ||
                source.directEntity is QuenchAllay ||
                source.`is`(DamageTypes.MAGIC) ||
                source.`is`(DamageTypes.INDIRECT_MAGIC)
            if (isSiphoning() && isIndirectSource) {
                shatterSiphon()
            }

            if (part is SlatePart && part.symbolType != SlateSymbolType.NONE) {
                if (source.`is`(DamageTypes.FREEZE)) {
                    part.slowdownTicks = 80
                    if (!part.tamperable) {
                        unlockSlateTamperable(part, part.posNow, Vec3.ZERO, detach = true)
                    } else if (!part.detached) {
                        part.detached = true
                        part.detachedPos = part.posNow
                        part.detachedVel = Vec3.ZERO
                    }
                }
                val fromPos = source.sourcePosition ?: source.directEntity?.position() ?: source.entity?.position()
                val pushDir = if (fromPos != null) {
                    part.posNow.subtract(fromPos).let { if (it.lengthSqr() > 1.0E-4) it.normalize() else source.entity?.lookAngle ?: Vec3.ZERO }
                } else {
                    source.entity?.lookAngle ?: Vec3.ZERO
                }
                if (pushDir.lengthSqr() > 1.0E-4) {
                    val pushStrength = if (part.tamperable) 1.05 + (amount / 14.0).coerceAtMost(0.85) else 0.45 + (amount / 18.0).coerceAtMost(0.45)
                    part.setDeltaMovement(part.deltaMovement.add(pushDir.scale(pushStrength)))
                }
            }

            if (isImmuneDamage(source)) return false

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
        val partScaled = if (part is SlatePart && part.symbolType != SlateSymbolType.NONE && !part.tamperable) {
            amount * 0.35F
        } else {
            amount
        }
        return hurt(source, partScaled)
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
            if (!isAlive || isDeadOrDying) return false
            val attacker = source.entity
            if (attacker === this || isUrCircleAlly(attacker) || isUrCircleAlly(source.directEntity)) return false
            if (amount <= 0.0F || isInvulnerableTo(source)) return false
            if (source.`is`(HexDamageTypes.OVERCAST)) return false
            if (isImmuneDamage(source)) return false
            if (attacker is LivingEntity && attacker !== this && attacker.isAlive) {
                target = attacker
                addToHated(attacker)
            }
        }
        var dmg = if (isFireDamage(source)) amount * FIRE_DAMAGE_MULTIPLIER else amount
        if (circleState == CircleState.SHORT_CIRCUIT) {
            dmg *= 1.45F
        } else if (!level().isClientSide) {
            val attackerLiving = source.entity as? LivingEntity
            val closeR = ringInnerRadius() + 8.0
            val boarderBypass = attackerLiving != null && (
                UrCircleCarryAlong.isEntityOnRing(this, attackerLiving) ||
                    attackerLiving.position().distanceToSqr(beamOrigin()) <= closeR * closeR
                )
            val shieldMul = if (boarderBypass) 1.0F else (1.0F - coreShieldPct() / 100.0F)
            dmg *= servantDamageMultiplier() * shieldMul
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
            val room = (DPS_CAP_PER_WINDOW - dpsWindow).coerceAtLeast(0.0F)
            val allowed = dmg.coerceAtMost(room)
            if (allowed <= 0.0F) return false
            dmg = allowed
        }
        val hpBefore = health
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
            if (result && !level().isClientSide) {
                val actualDealt = (hpBefore - health).coerceAtLeast(0.0F)
                if (actualDealt > 0.0F) {
                    dpsWindow += actualDealt
                    if (circleState != CircleState.SHORT_CIRCUIT) {
                        checkPhaseThresholds()
                    }
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
                currentSkill?.onChannelCancel(this)
                currentSkill = null
                mediaWave.clear(this)
            }
            CircleState.BEAM -> {
                beamSkill?.let {
                    it.onBeamEnd(this)
                    it.onChannelCancel(this)
                }
                beamSkill = null
            }
            CircleState.FOLD_GRIND -> {
                grindSkill?.let {
                    it.onGrindEnd(this)
                    it.onChannelCancel(this)
                }
                grindSkill = null
                targetFoldProgress = 0.0F
            }
            else -> return
        }
        clearAllSymbolicSlates()
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
        val origin = beamOrigin()
        val baseDeg = random.nextFloat() * 360.0F
        val anchorHp = if (phase >= 2) 35.0F else 25.0F

        val ringSlots: List<Pair<Int, Float>> = when {
            phase >= 3 -> listOf(2 to baseDeg, 4 to (baseDeg + 180.0F))
            phase >= 2 -> listOf(2 to baseDeg, 2 to (baseDeg + 180.0F))
            else -> emptyList()
        }

        if (ringSlots.isEmpty()) {
            val ref = target?.position() ?: position()
            val ang = random.nextDouble() * Math.PI * 2.0
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
            anchor.setTetherTarget(origin)
            serverLevel.addFreshEntity(anchor)
            activeAnchors.add(anchor)
            return
        }

        for ((ringIdx, degOffset) in ringSlots) {
            val anchor = UrCircleAnchorEntity(HexMobEntities.UR_CIRCLE_ANCHOR.get(), serverLevel)
            val track = rings.getOrNull(ringIdx)
            val mountedPos = if (track != null && track.isVisible(phase, phase2Reveal, phase3Reveal)) {
                val effN = track.effectiveNormal(foldProgress, dormantFold)
                val radAxis = track.radialAxis(effN)
                val rLen = track.effectiveRadius(totalScale(), dormantFold, phase2Reveal, phase3Reveal)
                anchor.mountedRingIndex = ringIdx
                anchor.mountedAngleOffsetDeg = degOffset
                anchor.mountedYOffset = 2.8
                origin.add(radAxis.rotateDA(ringRotation(ringIdx) + degOffset, effN).scale(rLen)).add(0.0, 2.8, 0.0)
            } else {
                val ref = target?.position() ?: position()
                val rad = Math.toRadians(degOffset.toDouble())
                val ax = ref.x + cos(rad) * 9.0
                val az = ref.z + sin(rad) * 9.0
                Vec3(ax, findAnchorY(serverLevel, ax, ref.y, az), az)
            }
            anchor.setPos(mountedPos.x, mountedPos.y, mountedPos.z)
            anchor.owner = this
            anchor.color = skill.waveColor
            anchor.maxHp = anchorHp
            anchor.hp = anchorHp
            anchor.lifeTicks = skill.channelTicks + 12
            anchor.setTetherTarget(origin)
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
        if (entity is Player && (entity.isCreative || entity.isSpectator)) return
        if (entity !in hatedTargets && distanceToSqr(entity) <= HATE_RANGE * HATE_RANGE) {
            hatedTargets.add(entity)
        }
    }

    fun currentHated(): List<LivingEntity> =
        hatedTargets.filter {
            it.isAlive && !it.isRemoved && it !is Enemy && !isUrCircleAlly(it) &&
                !(it is Player && (it.isCreative || it.isSpectator)) &&
                distanceToSqr(it) <= HATE_RANGE * HATE_RANGE
        }

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
        val uid = player.uuid
        val lastTick = lastBacklashTickByPlayer[uid]
        if (lastTick != null && tickCount - lastTick < BACKLASH_TICK_INTERVAL) return
        lastBacklashTickByPlayer[uid] = tickCount
        val count = (backlashByPlayer[uid] ?: 0) + 1
        if (count >= BACKLASH_THRESHOLD) {
            backlashByPlayer[uid] = 0
            retaliate(player)
        } else {
            backlashByPlayer[uid] = count
            val origin = beamOrigin()
            val playerCenter = player.position().add(0.0, player.bbHeight / 2.0, 0.0)
            UrCircleHexVfx.spawnRingArc(level(), origin, playerCenter, UrCircleHexVfx.SIPHON_CYAN, steps = 10)
            for (k in 0 until 6) {
                UrCircleHexVfx.spawnConjure(
                    level(),
                    UrCircleHexVfx.SIPHON_CYAN,
                    playerCenter.x + (random.nextDouble() - 0.5) * 1.2,
                    playerCenter.y + (random.nextDouble() - 0.5) * 1.2,
                    playerCenter.z + (random.nextDouble() - 0.5) * 1.2,
                    0.0, 0.06, 0.0,
                )
            }
            UrCircleHexVfx.playRingSound(level(), player.position(), 24.0, HexSounds.ABACUS_SHAKE, 1.4F, 1.25F)
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
        val p = targetPhase.coerceIn(1, 3)
        interruptChannel()
        clearAllSymbolicSlates()
        dormant = false
        wokenOnce = true
        entityData.set(DORMANT, false)
        dormantFold = 0.0F
        targetFoldProgress = 0.0F
        foldProgress = 0.0F
        phase = p
        phase2Reveal = if (p >= 2) 1.0F else 0.0F
        phase3Reveal = if (p >= 3) 1.0F else 0.0F
        stability = MAX_STABILITY
        hudSyncDirty = true
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
        if (phase >= 2) {
            phase = 2
            phase2Reveal = 1.0F
            phase3Reveal = 0.0F
            beginPhase3Transition()
        } else {
            phase = 1
            phase2Reveal = 0.0F
            phase3Reveal = 0.0F
            beginPhase2Transition()
        }
    }

    fun forceTriggerDeath() {
        dormant = false
        wokenOnce = true
        entityData.set(DORMANT, false)
        die(level().damageSources().generic())
    }

    fun debugSummary(): String =
        "phase=$phase state=$circleState($stateTicks) hp=${"%.1f".format(health)}/${"%.0f".format(maxHealth)} " +
            "stab=$stability shield=${coreShieldPct()}% slates=${activeSymbolicSlates.size} " +
            "reveal=${"%.2f".format(phase2Reveal)}/${"%.2f".format(phase3Reveal)} fold=${"%.2f".format(foldProgress)} dormant=$dormant " +
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
        val PHASE3_REVEAL: EntityDataAccessor<Float> = SynchedEntityData.defineId(UrCircleEntity::class.java, EntityDataSerializers.FLOAT)
        val STABILITY: EntityDataAccessor<Int> = SynchedEntityData.defineId(UrCircleEntity::class.java, EntityDataSerializers.INT)
        val FOLD_PROGRESS: EntityDataAccessor<Float> = SynchedEntityData.defineId(UrCircleEntity::class.java, EntityDataSerializers.FLOAT)
        val ACTIVE_NODE_A: EntityDataAccessor<Int> = SynchedEntityData.defineId(UrCircleEntity::class.java, EntityDataSerializers.INT)
        val ACTIVE_NODE_B: EntityDataAccessor<Int> = SynchedEntityData.defineId(UrCircleEntity::class.java, EntityDataSerializers.INT)
        val WAVE_COLOR: EntityDataAccessor<Int> = SynchedEntityData.defineId(UrCircleEntity::class.java, EntityDataSerializers.INT)
        val SIPHONING: EntityDataAccessor<Boolean> = SynchedEntityData.defineId(UrCircleEntity::class.java, EntityDataSerializers.BOOLEAN)
        val SLICE_ACTIVE: EntityDataAccessor<Boolean> = SynchedEntityData.defineId(UrCircleEntity::class.java, EntityDataSerializers.BOOLEAN)
        val SLICE_Y_OFFSET: EntityDataAccessor<Float> = SynchedEntityData.defineId(UrCircleEntity::class.java, EntityDataSerializers.FLOAT)
        val SLICE_OX: EntityDataAccessor<Float> = SynchedEntityData.defineId(UrCircleEntity::class.java, EntityDataSerializers.FLOAT)
        val SLICE_OY: EntityDataAccessor<Float> = SynchedEntityData.defineId(UrCircleEntity::class.java, EntityDataSerializers.FLOAT)
        val SLICE_OZ: EntityDataAccessor<Float> = SynchedEntityData.defineId(UrCircleEntity::class.java, EntityDataSerializers.FLOAT)
        val SLICE_NX: EntityDataAccessor<Float> = SynchedEntityData.defineId(UrCircleEntity::class.java, EntityDataSerializers.FLOAT)
        val SLICE_NY: EntityDataAccessor<Float> = SynchedEntityData.defineId(UrCircleEntity::class.java, EntityDataSerializers.FLOAT)
        val SLICE_NZ: EntityDataAccessor<Float> = SynchedEntityData.defineId(UrCircleEntity::class.java, EntityDataSerializers.FLOAT)

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
        const val BOSS_BAR_RANGE = 420.0
        const val WAKE_RANGE = 42.0
        const val ARENA_CHECK_MAX_TICKS = 60
        const val SKILL_COOLDOWN = 90
        const val CHANNEL_PARTICLE_INTERVAL = 4
        const val PROTECT_HEALTH_RATIO = 0.9F
        const val IDLE_REGEN = 1.0F
        const val SERVANT_SUMMON_INTERVAL = 40

        const val BACKLASH_RANGE = 88.0
        const val BACKLASH_THRESHOLD = 2
        const val BACKLASH_TICK_INTERVAL = 2
        const val SIPHON_INTERVAL = 260
        const val SIPHON_DURATION = 120

        const val PHASE_BURST_TICK = 28
        const val PHASE_TRANSITION_TICKS = 80
        const val SHORT_CIRCUIT_TICKS = 100
        const val SHORT_CIRCUIT_DAMAGE = 25.0F
        const val HEX_OVERWRITE_DAMAGE = 22.0F
        const val MAX_STABILITY = 100
        const val PHASE2_HP_RATIO = 0.65F
        const val PHASE3_HP_RATIO = 0.30F

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
