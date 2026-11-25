package pub.pigeon.yggdyy.hexmob.client

import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth
import net.minecraft.world.BossEvent
import pub.pigeon.yggdyy.hexmob.HexMob
import pub.pigeon.yggdyy.hexmob.content.ur_circle.CircleState
import pub.pigeon.yggdyy.hexmob.content.ur_circle.subentities.SlateSymbolType
import kotlin.math.sin

/**
 * M3 贴图驱动自定义三层大环 Boss 血条渲染器（`textures/gui/ur_circle_bossbar.png` 256×256 图集）：
 * 1. 外框冠饰 + 紫晶鎏金渐变主血条（带 65%/30% 宝石刻度、延迟掉血残影、短路态裂纹高闪）；
 * 2. 法阵稳态副条（0%~100% 青晶条，低稳态转橙红警报）；
 * 3. 底部象征石板槽（贴图牌框 + 9×9 像素图标 + 短状态徽标 + 进度微条）。
 */
object UrCircleBossBarRenderer {
    private val BOSSBAR_TEX = HexMob.id("textures/gui/ur_circle_bossbar.png")
    private const val TEX_W = 256
    private const val TEX_H = 256
    private const val BAR_W = 182
    private const val BAR_H = 7
    private const val CHIP_W = 44
    private const val CHIP_H = 13

    @JvmStatic
    fun tryRenderCustomBar(guiGraphics: GuiGraphics, x: Int, y: Int, event: BossEvent): Boolean {
        val snap = UrCircleHudTracker.getByBossEvent(event.id) ?: return false
        val pkt = snap.packet
        val client = Minecraft.getInstance()
        val font = client.font
        val time = (client.level?.gameTime ?: 0L).toFloat()

        val realRatio = event.progress.coerceIn(0.0F, 1.0F)
        snap.lastRealHpRatio = realRatio
        if (snap.delayedHpRatio < realRatio) snap.delayedHpRatio = realRatio

        val shortCircuit = pkt.circleState == CircleState.SHORT_CIRCUIT.ordinal
        val blink = sin((time * 0.45F).toDouble()) > 0.0

        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()

        // 1. 贴图外框冠饰底板（196×24）
        val frameV = when {
            shortCircuit -> 52F
            pkt.phase >= 3 -> 26F
            else -> 0F
        }
        guiGraphics.blit(BOSSBAR_TEX, x - 7, y - 11, 0F, frameV, 196, 24, TEX_W, TEX_H)

        // 2. 顶部左侧阶段徽章贴图（22×9）+ 右侧状态图标与精简文案（避开居中原版 Boss 名称）
        val crestU = when (pkt.phase) {
            1 -> 0F
            2 -> 24F
            else -> 48F
        }
        guiGraphics.blit(BOSSBAR_TEX, x - 3, y - 9, crestU, 188F, 22, 9, TEX_W, TEX_H)

        val statusComp = when {
            shortCircuit -> Component.translatable("hud.hexmob.ur_circle.state.short_circuit")
            pkt.shieldPct > 0 -> Component.translatable("hud.hexmob.ur_circle.shield", pkt.shieldPct)
            else -> Component.translatable("hud.hexmob.ur_circle.state.exposed")
        }
        val statusCol = when {
            shortCircuit -> if (blink) 0xFF_62FFF6.toInt() else 0xFF_FFE484.toInt()
            pkt.shieldPct > 0 -> 0xFF_CFA8FF.toInt()
            else -> 0xFF_F4D884.toInt()
        }
        val statusIconU = when {
            shortCircuit -> 24F
            pkt.shieldPct > 0 -> 0F
            else -> 12F
        }
        val sw = font.width(statusComp)
        val textX = x + BAR_W + 3 - sw
        guiGraphics.blit(BOSSBAR_TEX, textX - 10, y - 9, statusIconU, 202F, 9, 9, TEX_W, TEX_H)
        guiGraphics.drawString(font, statusComp, textX, y - 9, statusCol, true)

        // 3. 主血条（182×7）：空槽 + 白色延迟残影 + 紫晶鎏金晶面填充 + 65%/30% 宝石刻度
        guiGraphics.blit(BOSSBAR_TEX, x, y, 0F, 78F, BAR_W, BAR_H, TEX_W, TEX_H)

        val trailW = Mth.floor(snap.delayedHpRatio * BAR_W).coerceIn(0, BAR_W)
        val fillW = Mth.floor(realRatio * BAR_W).coerceIn(0, BAR_W)
        if (trailW > fillW) {
            guiGraphics.blit(BOSSBAR_TEX, x + fillW, y, fillW.toFloat(), 114F, trailW - fillW, BAR_H, TEX_W, TEX_H)
        }
        if (fillW > 0) {
            val fillV = when {
                shortCircuit && blink -> 96F
                shortCircuit -> 105F
                else -> 87F
            }
            guiGraphics.blit(BOSSBAR_TEX, x, y, 0F, fillV, fillW, BAR_H, TEX_W, TEX_H)
        }

        val tick65X = x + Mth.floor(BAR_W * 0.65F) - 2
        val tick30X = x + Mth.floor(BAR_W * 0.30F) - 2
        guiGraphics.blit(BOSSBAR_TEX, tick65X, y - 1, 0F, 124F, 5, 9, TEX_W, TEX_H)
        guiGraphics.blit(BOSSBAR_TEX, tick30X, y - 1, 8F, 124F, 5, 9, TEX_W, TEX_H)

        // 4. 次级副条：法阵稳态槽（182×4）
        val stabY = y + 13
        guiGraphics.blit(BOSSBAR_TEX, x, stabY, 0F, 136F, BAR_W, 4, TEX_W, TEX_H)
        val stabW = Mth.floor(BAR_W * (pkt.stability.coerceIn(0, 100) / 100.0F)).coerceIn(0, BAR_W)
        if (stabW > 0) {
            val stabV = if (pkt.stability <= 35) 148F else 142F
            guiGraphics.blit(BOSSBAR_TEX, x, stabY, 0F, stabV, stabW, 4, TEX_W, TEX_H)
        }

        // 5. 底部象征石板槽（贴图牌框 + 9×9 类型像素图标 + 简短状态标签 + 40×2 进度条）
        if (pkt.slates.isNotEmpty()) {
            val rowY = stabY + 5
            val count = pkt.slates.size.coerceAtMost(4)
            val gap = 2
            val totalW = count * CHIP_W + (count - 1) * gap
            val startX = x + (BAR_W - totalW) / 2
            for (i in 0 until count) {
                val slate = pkt.slates[i]
                val cx = startX + i * (CHIP_W + gap)
                val sym = SlateSymbolType.byId(slate.symbolType)

                val chipU = when {
                    slate.rebounded -> 138F
                    slate.tamperable && blink -> 46F
                    slate.tamperable -> 92F
                    else -> 0F
                }
                guiGraphics.blit(BOSSBAR_TEX, cx, rowY, chipU, 156F, CHIP_W, CHIP_H, TEX_W, TEX_H)

                val iconU = when (sym) {
                    SlateSymbolType.AIM -> 0F
                    SlateSymbolType.LOCK -> 12F
                    SlateSymbolType.AMP -> 24F
                    SlateSymbolType.TRIGGER -> 36F
                    SlateSymbolType.NONE -> 48F
                }
                guiGraphics.blit(BOSSBAR_TEX, cx + 2, rowY + 1, iconU, 174F, 9, 9, TEX_W, TEX_H)

                val stateComp = when {
                    slate.rebounded -> Component.translatable("hud.hexmob.ur_circle.slate_state.rebounded")
                    slate.tamperable -> Component.translatable("hud.hexmob.ur_circle.slate_state.tamperable")
                    else -> Component.translatable("hud.hexmob.ur_circle.slate_state.shielded")
                }
                val textCol = when {
                    slate.rebounded -> 0xFF_A2909A.toInt()
                    slate.tamperable -> 0xFF_72FFF6.toInt()
                    else -> 0xFF_EFE4FF.toInt()
                }
                guiGraphics.drawString(font, stateComp, cx + 13, rowY + 2, textCol, false)

                val pw = Mth.floor(40.0F * slate.progress.coerceIn(0F, 1F)).coerceIn(0, 40)
                if (pw > 0) {
                    val progV = when {
                        slate.rebounded -> 164F
                        slate.tamperable -> 160F
                        else -> 156F
                    }
                    guiGraphics.blit(BOSSBAR_TEX, cx + 2, rowY + 10, 186F, progV, pw, 2, TEX_W, TEX_H)
                }
            }
        }

        RenderSystem.disableBlend()
        return true
    }
}
