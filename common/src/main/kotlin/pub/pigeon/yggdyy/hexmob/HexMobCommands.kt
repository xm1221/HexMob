package pub.pigeon.yggdyy.hexmob

import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.FloatArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import dev.architectury.event.events.common.CommandRegistrationEvent
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.AABB
import pub.pigeon.yggdyy.hexmob.config.HexMobServerConfig
import pub.pigeon.yggdyy.hexmob.content.ur_circle.UrCircleEntity

/** 服务端调试/管理命令。 */
object HexMobCommands {
    fun init() {
        CommandRegistrationEvent.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(
                Commands.literal("hexmob")
                    .requires { src -> src.hasPermission(2) }
                    .then(
                        Commands.literal("scale")
                            .then(
                                Commands.argument(
                                    "value",
                                    FloatArgumentType.floatArg(
                                        HexMobServerConfig.MIN_UR_CIRCLE_SCALE,
                                        HexMobServerConfig.MAX_UR_CIRCLE_SCALE,
                                    ),
                                ).executes { ctx ->
                                    val value = FloatArgumentType.getFloat(ctx, "value")
                                    HexMobServerConfig.setUrCircleScale(value)
                                    ctx.source.sendSuccess(
                                        { Component.literal("大环体积倍率已设为 $value（已同步所有在线玩家）") },
                                        true,
                                    )
                                    1
                                },
                            )
                            .executes { ctx ->
                                val cur = HexMobServerConfig.config.urCircleScale
                                ctx.source.sendSuccess(
                                    { Component.literal("当前大环体积倍率：$cur（范围 ${HexMobServerConfig.MIN_UR_CIRCLE_SCALE}~${HexMobServerConfig.MAX_UR_CIRCLE_SCALE}，用法 /hexmob scale <倍率>）") },
                                    true,
                                )
                                1
                            },
                    )
                    .then(
                        Commands.literal("urcircle")
                            .then(
                                Commands.literal("phase")
                                    .then(
                                        Commands.argument("value", IntegerArgumentType.integer(1, 2))
                                            .executes { ctx ->
                                                val circle = requireNearestCircle(ctx.source) ?: return@executes 0
                                                val p = IntegerArgumentType.getInteger(ctx, "value")
                                                circle.forceSetPhase(p)
                                                ctx.source.sendSuccess({ Component.literal("大环阶段已切换为 $p") }, true)
                                                1
                                            },
                                    ),
                            )
                            .then(
                                Commands.literal("wake")
                                    .executes { ctx ->
                                        val circle = requireNearestCircle(ctx.source) ?: return@executes 0
                                        circle.forceDormant(false)
                                        ctx.source.sendSuccess({ Component.literal("大环已唤醒") }, true)
                                        1
                                    },
                            )
                            .then(
                                Commands.literal("dormant")
                                    .executes { ctx ->
                                        val circle = requireNearestCircle(ctx.source) ?: return@executes 0
                                        circle.forceDormant(true)
                                        ctx.source.sendSuccess({ Component.literal("大环已切为沉睡折叠态") }, true)
                                        1
                                    },
                            )
                            .then(
                                Commands.literal("shortcircuit")
                                    .executes { ctx ->
                                        val circle = requireNearestCircle(ctx.source) ?: return@executes 0
                                        circle.triggerShortCircuit()
                                        ctx.source.sendSuccess({ Component.literal("已触发大环法术环断路僵直") }, true)
                                        1
                                    },
                            )
                            .then(
                                Commands.literal("siphon")
                                    .then(
                                        Commands.argument("enabled", BoolArgumentType.bool())
                                            .executes { ctx ->
                                                val circle = requireNearestCircle(ctx.source) ?: return@executes 0
                                                val enabled = BoolArgumentType.getBool(ctx, "enabled")
                                                circle.toggleSiphon(enabled)
                                                ctx.source.sendSuccess({ Component.literal("大环媒质虹吸状态：$enabled") }, true)
                                                1
                                            },
                                    ),
                            )
                            .then(
                                Commands.literal("skill")
                                    .then(
                                        Commands.argument("name", StringArgumentType.word())
                                            .executes { ctx ->
                                                val circle = requireNearestCircle(ctx.source) ?: return@executes 0
                                                val name = StringArgumentType.getString(ctx, "name")
                                                if (!circle.forceTriggerSkill(name)) {
                                                    val names = circle.skills.joinToString(", ") { it.name }
                                                    ctx.source.sendFailure(Component.literal("未知技能 '$name'，可用：$names"))
                                                    return@executes 0
                                                }
                                                ctx.source.sendSuccess({ Component.literal("已强制大环开始吟唱技能：$name") }, true)
                                                1
                                            },
                                    ),
                            ),
                    ),
            )
        }
    }

    private fun requireNearestCircle(src: CommandSourceStack): UrCircleEntity? {
        val pos = src.position
        val circle = src.level.getEntitiesOfClass(
            UrCircleEntity::class.java,
            AABB(pos, pos).inflate(128.0),
        ) { it.isAlive }.minByOrNull { it.distanceToSqr(pos) }
        if (circle == null) {
            src.sendFailure(Component.literal("128 格内未找到存活的大环实体"))
        }
        return circle
    }
}
