package pub.pigeon.yggdyy.hexmob.content.ur_circle

import at.petrak.hexcasting.api.misc.MediaConstants
import at.petrak.hexcasting.common.items.magic.ItemMediaHolder
import at.petrak.hexcasting.common.lib.HexBlocks
import at.petrak.hexcasting.common.lib.HexItems
import at.petrak.hexcasting.xplat.IXplatAbstractions
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.SlotAccess
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ClickAction
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.item.UseAnim
import net.minecraft.world.level.Level

/**
 * 大环核心战利品（quenched-media-cube 后勤终极解）：
 * - 近似无限容量（1 万亿单位，严格按真实存入数值消耗，不白嫖创造无限媒质）；
 * - 支持存入任意紫水晶/淬灵结晶与容器媒质（潜行右键副手，或背包拖拽左击存入）；
 * - 支持切换提取形态（紫水晶粉 / 紫水晶碎片 / 充能紫水晶 / 淬灵晶碎片 / 淬灵晶块，背包空光标右击切换），
 *   空副手潜行右键即可按所选形态等价提取 1 枚结晶；
 * - 保留“可食用（tasty to eat）”彩蛋：普通长按右键无限品尝，赋予短时媒质共鸣效果。
 */
class UrCircleCoreItem(properties: Properties) : ItemMediaHolder(properties) {

    enum class ExtractForm(val labelKey: String, val unitProvider: () -> Long, val itemProvider: () -> Item) {
        AMETHYST_DUST("item.hexmob.ur_circle_core.form.dust", { at.petrak.hexcasting.api.mod.HexConfig.common().dustMediaAmount() }, { HexItems.AMETHYST_DUST }),
        AMETHYST_SHARD("item.hexmob.ur_circle_core.form.shard", { at.petrak.hexcasting.api.mod.HexConfig.common().shardMediaAmount() }, { Items.AMETHYST_SHARD }),
        CHARGED_AMETHYST("item.hexmob.ur_circle_core.form.charged", { at.petrak.hexcasting.api.mod.HexConfig.common().chargedCrystalMediaAmount() }, { HexItems.CHARGED_AMETHYST }),
        QUENCHED_SHARD("item.hexmob.ur_circle_core.form.quenched_shard", { MediaConstants.QUENCHED_SHARD_UNIT }, { HexItems.QUENCHED_SHARD }),
        QUENCHED_BLOCK("item.hexmob.ur_circle_core.form.quenched_block", { MediaConstants.QUENCHED_BLOCK_UNIT }, { HexBlocks.QUENCHED_ALLAY.asItem() });

        fun next(): ExtractForm = entries[(ordinal + 1) % entries.size]
    }

    override fun getMaxMedia(stack: ItemStack): Long = MAX_MEDIA

    override fun canProvideMedia(stack: ItemStack): Boolean = getMedia(stack) > 0L

    override fun canRecharge(stack: ItemStack): Boolean = getMedia(stack) < MAX_MEDIA

    override fun getUseDuration(stack: ItemStack): Int = 24

    override fun getUseAnimation(stack: ItemStack): UseAnim = UseAnim.EAT

    fun getExtractForm(stack: ItemStack): ExtractForm {
        val idx = stack.tag?.getInt(TAG_EXTRACT_FORM) ?: 0
        return ExtractForm.entries.getOrElse(idx.coerceIn(0, ExtractForm.entries.lastIndex)) { ExtractForm.CHARGED_AMETHYST }
    }

    fun cycleExtractForm(stack: ItemStack): ExtractForm {
        val next = getExtractForm(stack).next()
        stack.orCreateTag.putInt(TAG_EXTRACT_FORM, next.ordinal)
        return next
    }

    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResultHolder<ItemStack> {
        val stack = player.getItemInHand(hand)
        if (player.isShiftKeyDown) {
            val otherHand = if (hand == InteractionHand.MAIN_HAND) InteractionHand.OFF_HAND else InteractionHand.MAIN_HAND
            val other = player.getItemInHand(otherHand)
            if (!other.isEmpty) {
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
            } else {
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
        }

        // 普通长按右键：可食用（tasty to eat），不消耗物品
        player.startUsingItem(hand)
        return InteractionResultHolder.consume(stack)
    }

    override fun finishUsingItem(stack: ItemStack, level: Level, entity: LivingEntity): ItemStack {
        if (entity is Player && !level.isClientSide) {
            entity.foodData.eat(8, 1.2F)
            entity.addEffect(MobEffectInstance(MobEffects.NIGHT_VISION, 240, 0, true, false))
            entity.addEffect(MobEffectInstance(MobEffects.DIG_SPEED, 200, 0, true, false))
            level.playSound(null, entity.x, entity.y, entity.z, SoundEvents.GENERIC_EAT, SoundSource.PLAYERS, 1.0F, 1.15F)
            level.playSound(null, entity.x, entity.y, entity.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.9F, 1.45F)
        }
        return stack.copy()
    }

    override fun overrideOtherStackedOnMe(
        stack: ItemStack,
        other: ItemStack,
        slot: Slot,
        action: ClickAction,
        player: Player,
        access: SlotAccess,
    ): Boolean {
        if (action == ClickAction.SECONDARY && other.isEmpty) {
            val next = cycleExtractForm(stack)
            if (!player.level().isClientSide) {
                player.displayClientMessage(
                    Component.translatable("message.hexmob.ur_circle_core.mode_switched", Component.translatable(next.labelKey)),
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
        if (source.isEmpty || source.item === this) return 0L
        val cur = getMedia(core)
        val room = (MAX_MEDIA - cur).coerceAtLeast(0L)
        if (room <= 0L) return 0L

        // 1. 合法晶体物品整组存入
        val crystalUnit = crystalMediaUnit(source.item)
        if (crystalUnit != null && crystalUnit > 0L) {
            val maxItems = (room / crystalUnit).coerceAtMost(source.count.toLong()).toInt()
            if (maxItems <= 0) return 0L
            val gained = maxItems * crystalUnit
            setMedia(core, cur + gained)
            source.shrink(maxItems)
            return gained
        }

        // 2. 其他 Hex 媒质容器抽吸转移
        val holder = IXplatAbstractions.INSTANCE.findMediaHolder(source) ?: return 0L
        if (!holder.canProvide()) return 0L
        val pulled = holder.withdrawMedia(room, false)
        if (pulled > 0L) {
            setMedia(core, cur + pulled)
        }
        return pulled
    }

    private fun crystalMediaUnit(item: Item): Long? =
        ExtractForm.entries.firstOrNull { it.itemProvider() === item }?.unitProvider?.invoke()

    override fun appendHoverText(
        stack: ItemStack,
        level: Level?,
        tooltip: MutableList<Component>,
        flag: TooltipFlag,
    ) {
        super.appendHoverText(stack, level, tooltip, flag)
        val form = getExtractForm(stack)
        tooltip.add(
            Component.translatable("tooltip.hexmob.ur_circle_core.mode", Component.translatable(form.labelKey))
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

    companion object {
        const val MAX_MEDIA = 1_000_000_000_000L
        private const val TAG_EXTRACT_FORM = "hexmob:ExtractForm"
    }
}
