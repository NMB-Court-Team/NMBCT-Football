package net.astrorbits.football

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.context.CommandContext
import net.astrorbits.football.config.FootballConfigs
import net.astrorbits.football.config.server.FootballServerConfigHolder
import net.astrorbits.football.network.FootballNetworking
import net.astrorbits.football.util.FootballKickUtil
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component

object FootballCommand {
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        val command = Commands.literal("football").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
        command.then(Commands.literal("summon")
            .executes { context ->
                val source = context.source
                val level = source.level
                val pos = source.position
                val football = Football(Football.ENTITY_TYPE, level)
                football.setPos(pos.x, pos.y, pos.z)
                level.addFreshEntity(football)
                source.sendSuccess({ Component.translatable("command.nmbct-football.summon") }, true)
                1
            }
        ).then(Commands.literal("config")
            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .executes { context ->
                val player = context.source.player
                if (player == null) {
                    context.source.sendFailure(Component.translatable("command.nmbct-football.config.player_only"))
                    return@executes 0
                }
                FootballNetworking.sendServerConfigSync(player, FootballServerConfigHolder.current)
                context.source.sendSuccess(
                    { Component.translatable("command.nmbct-football.config.opened") },
                    true,
                )
                1
            }
        ).then(Commands.literal("kick")
            .then(Commands.argument("force", DoubleArgumentType.doubleArg(0.0, 10.0))
                .executes { context -> executeKick(context, heightOffset = 0.0, angleDegrees = 0.0) }
                .then(Commands.argument("height", DoubleArgumentType.doubleArg(-0.5, 1.0))
                    .executes { context ->
                        executeKick(context, DoubleArgumentType.getDouble(context, "height"), angleDegrees = 0.0)
                    }
                    .then(Commands.argument("angle", DoubleArgumentType.doubleArg(-90.0, 90.0))
                        .executes { context ->
                            executeKick(
                                context,
                                DoubleArgumentType.getDouble(context, "height"),
                                DoubleArgumentType.getDouble(context, "angle")
                            )
                        }
                    )
                )
            )
        )

        dispatcher.register(command)
    }

    private fun executeKick(
        context: CommandContext<CommandSourceStack>,
        heightOffset: Double,
        angleDegrees: Double
    ): Int {
        val source = context.source
        val player = source.player
        if (player == null) {
            source.sendFailure(Component.translatable("command.nmbct-football.kick.player_only"))
            return 0
        }

        val force = DoubleArgumentType.getDouble(context, "force")
        val football = FootballKickUtil.findNearestFootball(player, FootballConfigs.server.playerInput.commandKickRange)
        if (football == null) {
            source.sendFailure(Component.translatable("command.nmbct-football.kick.no_football_nearby"))
            return 0
        }

        val params = net.astrorbits.football.util.KickParams(
            force = force,
            angleDegrees = angleDegrees,
            heightOffset = heightOffset
        )
        FootballKickUtil.applyKickToFootball(player, football, params)
        source.sendSuccess({
            Component.translatable(
                "command.nmbct-football.kick.success",
                force,
                heightOffset,
                angleDegrees,
            )
        }, true)
        return 1
    }
}
