package pub.pigeon.yggdyy.hexmob.content.now

import at.petrak.hexcasting.api.HexAPI
import at.petrak.hexcasting.api.misc.MediaConstants
import at.petrak.hexcasting.common.items.magic.ItemMediaHolder
import at.petrak.hexcasting.common.lib.HexAttributes
import at.petrak.hexcasting.common.lib.HexBlocks
import at.petrak.hexcasting.common.lib.HexItems
import at.petrak.hexcasting.xplat.IXplatAbstractions
import com.google.common.collect.HashMultimap
import com.google.common.collect.Multimap
import net.minecraft.ChatFormatting
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.SlotAccess
import net.minecraft.world.entity.ai.attributes.Attribute
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ClickAction
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.item.UseAnim
import net.minecraft.world.level.Level
import pub.pigeon.yggdyy.hexmob.content.ur_circle.UrCircleCoreItem
import java.util.UUID

/**
 * 淬灵媒质立方（everything_in_now）：与"当下"相关的终局物品，严格作为 [UrCircleCoreItem] 的上位替代：
 * - **媒质容器**：容量与大环核心一致（1 万亿单位），可存可取、施法供能；
 * - **无损存取**：
 *   - 背包拖拽左击：将晶体或 Hex 媒质容器中的媒质无损存入立方；
 *   - 背包空光标右击：循环切换空手提取形态或「副手存入」模式；
 *   - 潜行右键：
 *     - 副手空：按所选提取形态取出 1 枚结晶；
 *     - 副手持有结晶态媒质（非存入模式下）：消耗对应媒质使副手结晶 +1（严格校验堆叠上限）；
 *     - 副手持有其他媒质容器（或处于存入模式）：从副手无损存入媒质；
 * - **无限食用**：随时可吃，回复饱食并保留夜视/急迫共鸣效果，且不消耗物品；
 * - **手持范围**：主/副手持有自动获得 +[AMBIT_BONUS] 施法范围、+[SENTINEL_BONUS] 哨卫范围；
 * - **右键玩家**：授予其 `hexcasting:enlightenment`（启迪）成就（已启迪时不重复刷屏）。
 */
class EverythingInNowItem(properties: Properties) : ItemMediaHolder(properties) {

    override fun getMaxMedia(stack: ItemStack): Long = MAX_MEDIA

    override fun canProvideMedia(stack: ItemStack): Boolean = getMedia(stack) > 0L

    override fun canRecharge(stack: ItemStack): Boolean = getMedia(stack) < getMaxMedia(stack)

    // ---- 模式与形态管理（0..4 = UrCircleCoreItem.ExtractForm，5 = 副手存入模式） ----
    private fun getModeIndex(stack: ItemStack): Int {
        val raw = stack.tag?.getInt(TAG_MODE_INDEX) ?: 0
        return raw.coerceIn(0, MODE_DEPOSIT)
    }

    private fun isDepositMode(stack: ItemStack): Boolean = getModeIndex(stack) == MODE_DEPOSIT

    private fun getExtractForm(stack: ItemStack): UrCircleCoreItem.ExtractForm {
        val idx = getModeIndex(stack)
        val forms = UrCircleCoreItem.ExtractForm.entries
        return if (idx in forms.indices) forms[idx] else UrCircleCoreItem.ExtractForm.AMETHYST_DUST
    }

    private fun cycleMode(stack: ItemStack): Int {
        val next = (getModeIndex(stack) + 1) % MODE_COUNT
        stack.orCreateTag.putInt(TAG_MODE_INDEX, next)
        return next
    }

    private fun modeDisplayName(modeIndex: Int): Component {
        val forms = UrCircleCoreItem.ExtractForm.entries
        return if (modeIndex in forms.indices) {
            Component.translatable(forms[modeIndex].labelKey)
        } else {
            Component.translatableWithFallback("tooltip.hexmob.everything_in_now.deposit_mode", "Deposit (Offhand)")
        }
    }

    // ---- 食用：随时可食、不消耗、保留核心共振效果 ----
    override fun getUseDuration(stack: ItemStack): Int = 32

    override fun getUseAnimation(stack: ItemStack): UseAnim = UseAnim.EAT

    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResultHolder<ItemStack> {
        val stack = player.getItemInHand(hand)
        if (player.isShiftKeyDown) {
            val otherHand = if (hand == InteractionHand.MAIN_HAND) InteractionHand.OFF_HAND else InteractionHand.MAIN_HAND
            val other = player.getItemInHand(otherHand)

            // 副手为空：按当前选定形态提取 1 枚结晶（保留 UrCircleCoreItem 体验）
            if (other.isEmpty) {
                return extractFormToInventory(stack, level, player)
            }

            // 处于「副手存入」模式：尝试将副手结晶或媒质容器存入立方
            if (isDepositMode(stack)) {
                return depositOffhand(stack, other, level, player)
            }

            // 默认模式下副手为结晶态媒质：提取对应形态使副手 +1（校验 maxStackSize 防止溢出）
            val targetEntry = CRYSTALLINE_MEDIA.entries.firstOrNull { (id, _) ->
                BuiltInRegistries.ITEM.getKey(other.item) == id
            }
            if (targetEntry != null) {
                if (other.count >= other.maxStackSize) {
                    return InteractionResultHolder.fail(stack)
                }
                val (_, mediaPerUnit) = targetEntry
                if (getMedia(stack) < mediaPerUnit) {
                    if (!level.isClientSide) {
                        player.displayClientMessage(
                            Component.translatable("message.hexmob.everything_in_now.insufficient_media"), true,
                        )
                    }
                    return InteractionResultHolder.fail(stack)
                }
                if (!level.isClientSide) {
                    setMedia(stack, getMedia(stack) - mediaPerUnit)
                    other.grow(1)
                    level.playSound(null, player.x, player.y, player.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 0.9F, 1.4F)
                }
                return InteractionResultHolder.sidedSuccess(stack, level.isClientSide)
            }

            // 副手为非结晶媒质容器（媒质瓶/电池/大环核心等）：直接吸入存入
            return depositOffhand(stack, other, level, player)
        }

        // 随时可食用（与大环核心一致）
        player.startUsingItem(hand)
        return InteractionResultHolder.consume(stack)
    }

    private fun extractFormToInventory(
        stack: ItemStack,
        level: Level,
        player: Player,
    ): InteractionResultHolder<ItemStack> {
        val form = getExtractForm(stack)
        val cost = form.unitProvider()
        val cur = getMedia(stack)
        if (cur < cost) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.hexmob.everything_in_now.insufficient_media"), true)
            }
            return InteractionResultHolder.fail(stack)
        }
        if (!level.isClientSide) {
            setMedia(stack, cur - cost)
            val out = ItemStack(form.itemProvider(), 1)
            if (!player.inventory.add(out)) {
                player.drop(out, false)
            }
            level.playSound(null, player.x, player.y, player.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 0.9F, 1.4F)
            player.displayClientMessage(
                Component.translatable("message.hexmob.ur_circle_core.extracted", out.hoverName),
                true,
            )
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide)
    }

    private fun depositOffhand(
        stack: ItemStack,
        other: ItemStack,
        level: Level,
        player: Player,
    ): InteractionResultHolder<ItemStack> {
        val deposited = tryDepositFromStack(stack, other)
        if (deposited > 0L) {
            if (!level.isClientSide) {
                level.playSound(null, player.x, player.y, player.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.25F)
                player.displayClientMessage(
                    Component.translatable("message.hexmob.ur_circle_core.deposited", deposited / MediaConstants.DUST_UNIT),
                    true,
                )
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide)
        }
        return InteractionResultHolder.fail(stack)
    }

    override fun finishUsingItem(stack: ItemStack, level: Level, entity: LivingEntity): ItemStack {
        if (entity is Player && !level.isClientSide) {
            entity.foodData.eat(FOOD_NUTRITION, FOOD_SATURATION)
            entity.addEffect(MobEffectInstance(MobEffects.NIGHT_VISION, 240, 0, true, false))
            entity.addEffect(MobEffectInstance(MobEffects.DIG_SPEED, 200, 0, true, false))
            level.playSound(null, entity.x, entity.y, entity.z, SoundEvents.GENERIC_EAT, SoundSource.PLAYERS, 1.0F, 1.0F)
            level.playSound(null, entity.x, entity.y, entity.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.9F, 1.45F)
        }
        return stack.copy()
    }

    // ---- 背包交互：右键空光标切形态/存入模式，左键拖拽存入结晶或媒质容器 ----
    override fun overrideOtherStackedOnMe(
        stack: ItemStack,
        other: ItemStack,
        slot: Slot,
        action: ClickAction,
        player: Player,
        access: SlotAccess,
    ): Boolean {
        if (action == ClickAction.SECONDARY && other.isEmpty) {
            val next = cycleMode(stack)
            if (!player.level().isClientSide) {
                player.displayClientMessage(
                    Component.translatable("message.hexmob.ur_circle_core.mode_switched", modeDisplayName(next)),
                    true,
                )
            }
            return true
        }
        if (action == ClickAction.PRIMARY && !other.isEmpty) {
            val deposited = tryDepositFromStack(stack, other)
            if (deposited > 0L) {
                if (!player.level().isClientSide) {
                    player.level().playSound(null, player.x, player.y, player.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.9F, 1.3F)
                }
                return true
            }
        }
        return false
    }

    private fun tryDepositFromStack(core: ItemStack, source: ItemStack): Long {
        if (source.isEmpty || source === core || source.item === this) return 0L
        val cur = getMedia(core)
        val room = (MAX_MEDIA - cur).coerceAtLeast(0L)
        if (room <= 0L) return 0L

        val crystalUnit = CRYSTALLINE_MEDIA[BuiltInRegistries.ITEM.getKey(source.item)]
        if (crystalUnit != null && crystalUnit > 0L) {
            val maxItems = (room / crystalUnit).coerceAtMost(source.count.toLong()).toInt()
            if (maxItems <= 0) return 0L
            val gained = maxItems * crystalUnit
            setMedia(core, cur + gained)
            source.shrink(maxItems)
            return gained
        }

        val holder = IXplatAbstractions.INSTANCE.findMediaHolder(source) ?: return 0L
        if (!holder.canProvide()) return 0L
        val pulled = holder.withdrawMedia(room, false)
        if (pulled > 0L) {
            setMedia(core, cur + pulled)
        }
        return pulled
    }

    override fun appendHoverText(
        stack: ItemStack,
        level: Level?,
        tooltip: MutableList<Component>,
        flag: TooltipFlag,
    ) {
        super.appendHoverText(stack, level, tooltip, flag)
        tooltip.add(
            Component.translatable("tooltip.hexmob.ur_circle_core.mode", modeDisplayName(getModeIndex(stack)))
                .withStyle(ChatFormatting.AQUA),
        )
        tooltip.add(
            Component.translatable("tooltip.hexmob.ur_circle_core.hint_use")
                .withStyle(ChatFormatting.GRAY),
        )
        tooltip.add(
            Component.translatable("tooltip.hexmob.ur_circle_core.hint_slot")
                .withStyle(ChatFormatting.GRAY),
        )
        tooltip.add(
            Component.translatable("tooltip.hexmob.ur_circle_core.tasty")
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC),
        )
    }

    // ---- 手持范围：主/副手 attribute ----
    override fun getDefaultAttributeModifiers(slot: EquipmentSlot): Multimap<Attribute, AttributeModifier> {
        val out = HashMultimap.create<Attribute, AttributeModifier>()
        if (slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND) {
            out.put(HexAttributes.AMBIT_RADIUS, AMBIT_MOD)
            out.put(HexAttributes.SENTINEL_RADIUS, SENTINEL_MOD)
        }
        return out
    }

    // ---- 右键玩家：授予启迪成就（仅实际有条件被授予时提示） ----
    override fun interactLivingEntity(
        stack: ItemStack,
        player: Player,
        target: LivingEntity,
        hand: InteractionHand,
    ): InteractionResult {
        if (target !is Player) return InteractionResult.PASS
        if (target is ServerPlayer && !target.level().isClientSide) {
            grantAdvancement(target, HexAPI.modLoc("amethyst_dust"))
            grantAdvancement(target, HexAPI.modLoc("y_u_no_cast_angy"))
            if (grantAdvancement(target, HexAPI.modLoc("enlightenment"))) {
                target.displayClientMessage(
                    Component.translatable("message.hexmob.everything_in_now.enlightened")
                        .withStyle(ChatFormatting.LIGHT_PURPLE), true
                )
            }
        }
        return InteractionResult.sidedSuccess(target.level().isClientSide)
    }

    private fun grantAdvancement(player: ServerPlayer, id: ResourceLocation): Boolean {
        val advancement = player.server.advancements.getAdvancement(id) ?: return false
        val progress = player.advancements.getOrStartProgress(advancement)
        val remaining = progress.remainingCriteria.toList()
        if (remaining.isEmpty()) return false
        for (criteria in remaining) {
            player.advancements.award(advancement, criteria)
        }
        return progress.isDone
    }

    companion object {
        /** 最大媒质容量：与大环核心对齐（1 万亿单位），避免合成立方后容量倒退。 */
        const val MAX_MEDIA = 1_000_000_000_000L

        private const val TAG_MODE_INDEX = "hexmob:ModeIndex"
        private val MODE_DEPOSIT = UrCircleCoreItem.ExtractForm.entries.size
        private val MODE_COUNT = MODE_DEPOSIT + 1

        /** 手持时施法范围加成（格）。 */
        const val AMBIT_BONUS = 16.0

        /** 手持时哨卫范围加成（格）。 */
        const val SENTINEL_BONUS = 32.0

        /** 食用恢复的饱食度点数。 */
        private const val FOOD_NUTRITION = 20

        /** 食用恢复的饱和度点数。 */
        private const val FOOD_SATURATION = 20.0F

        private val AMBIT_MOD = AttributeModifier(
            UUID.fromString("6f7f2c38-9a02-4b3e-9f1e-e0c1a35d2b12"),
            "Everything in Now ambit range", AMBIT_BONUS,
            AttributeModifier.Operation.ADDITION,
        )
        private val SENTINEL_MOD = AttributeModifier(
            UUID.fromString("a3d7a81f-6b24-4f2d-b097-86c4e02c47ae"),
            "Everything in Now sentinel range", SENTINEL_BONUS,
            AttributeModifier.Operation.ADDITION,
        )

        /** 结晶态媒质物品换算表：物品引用 -> 单份媒质值。 */
        val CRYSTALLINE_MEDIA: Map<ResourceLocation, Long> by lazy {
            val common = at.petrak.hexcasting.api.mod.HexConfig.common()
            mapOf(
                BuiltInRegistries.ITEM.getKey(HexItems.AMETHYST_DUST) to common.dustMediaAmount(),
                BuiltInRegistries.ITEM.getKey(Items.AMETHYST_SHARD) to common.shardMediaAmount(),
                BuiltInRegistries.ITEM.getKey(HexItems.CHARGED_AMETHYST) to common.chargedCrystalMediaAmount(),
                BuiltInRegistries.ITEM.getKey(HexItems.QUENCHED_SHARD) to MediaConstants.QUENCHED_SHARD_UNIT,
                BuiltInRegistries.ITEM.getKey(HexBlocks.QUENCHED_ALLAY.asItem()) to MediaConstants.QUENCHED_BLOCK_UNIT,
            )
        }
    }
}